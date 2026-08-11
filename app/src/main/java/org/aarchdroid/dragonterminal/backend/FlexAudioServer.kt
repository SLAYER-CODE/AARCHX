package org.aarchdroid.dragonterminal.backend

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Servidor de audio PCM para Flex/Echo.
 *
 * Expone un socket AF_UNIX abstracto "flex_audio" (mismo mecanismo que
 * canvas-display): Flex dentro del chroot envía PCM s16le 44100 Hz estéreo
 * por el socket y aquí se reproduce vía AudioTrack (MODE_STREAM).
 * Reemplaza al binario externo audioplay_socket_android.
 *
 * Además expone un socket de control "flex_audio_ctl" para sincronización:
 *   P → pause del AudioTrack (pausa real, sin cola residual)
 *   R → reanudar
 *   F → flush (descarta PCM buffered en la app; usar antes de un seek)
 *   Q → responder "frames reproducidos" (getPlaybackHeadPosition)
 */
class FlexAudioServer private constructor() {

    companion object {
        private const val TAG = "FlexAudio"
        private const val SOCKET_NAME = "flex_audio"
        private const val CTL_SOCKET_NAME = "flex_audio_ctl"
        private const val SAMPLE_RATE = 44100
        private const val BYTES_PER_SEC = SAMPLE_RATE * 2 * 2 // 16-bit stereo

        @Volatile
        private var instance: FlexAudioServer? = null

        fun getInstance(): FlexAudioServer {
            return instance ?: synchronized(this) {
                instance ?: FlexAudioServer().also { instance = it }
            }
        }
    }

    private val isRunning = AtomicBoolean(false)
    private val clients = CopyOnWriteArrayList<android.net.LocalSocket>()
    private val tracks = CopyOnWriteArrayList<AudioTrack>()
    private var serverSocket: LocalServerSocketExt? = null
    private var ctlSocket: LocalServerSocketExt? = null
    private var acceptThread: Thread? = null
    private var ctlThread: Thread? = null

    fun start() {
        if (!isRunning.compareAndSet(false, true)) {
            Log.w(TAG, "start() called but already running")
            return
        }
        val t = Thread {
            try {
                Log.w(TAG, "Creating LocalServerSocket on '$SOCKET_NAME'...")
                var bound = false
                for (attempt in 1..10) {
                    try {
                        serverSocket = LocalServerSocketExt(SOCKET_NAME)
                        bound = true
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "Bind retry $attempt: ${e.message}")
                        Thread.sleep(200)
                    }
                }
                if (!bound) {
                    Log.e(TAG, "FlexAudioServer failed to bind after retries")
                    isRunning.set(false)
                    return@Thread
                }
                Log.w(TAG, "Server listening on abstract socket: $SOCKET_NAME")
                while (isRunning.get()) {
                    val client = serverSocket?.accept() ?: break
                    clients.add(client)
                    val h = Thread { handleClient(client) }
                    h.name = "FlexAudio-$SOCKET_NAME"
                    h.start()
                }
            } catch (e: Exception) {
                if (isRunning.get()) Log.e(TAG, "FlexAudioServer failed: ${e.message}", e)
                isRunning.set(false)
            } finally {
                cleanup()
            }
        }
        t.name = "FlexAudioAccept"
        acceptThread = t
        t.start()

        val tc = Thread {
            try {
                var bound = false
                for (attempt in 1..10) {
                    try {
                        ctlSocket = LocalServerSocketExt(CTL_SOCKET_NAME)
                        bound = true
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "Ctl bind retry $attempt: ${e.message}")
                        Thread.sleep(200)
                    }
                }
                if (!bound) return@Thread
                while (isRunning.get()) {
                    val ctl = ctlSocket?.accept() ?: break
                    val h = Thread { handleCtl(ctl) }
                    h.name = "FlexAudioCtl"
                    h.start()
                }
            } catch (e: Exception) {
                if (isRunning.get()) Log.e(TAG, "FlexAudioCtl failed: ${e.message}", e)
            }
        }
        tc.name = "FlexAudioCtlAccept"
        ctlThread = tc
        tc.start()
    }

    private fun handleCtl(ctl: android.net.LocalSocket) {
        try {
            val input = ctl.inputStream
            val output = ctl.outputStream
            val buf = ByteArray(16)
            while (isRunning.get()) {
                val n = input.read(buf, 0, 1)
                if (n <= 0) break
                when (buf[0].toInt().toChar()) {
                    'P' -> for (tr in tracks) { try { tr.pause() } catch (_: Exception) {} }
                    'R' -> for (tr in tracks) { try { tr.play() } catch (_: Exception) {} }
                    'F' -> for (tr in tracks) { try { tr.flush() } catch (_: Exception) {} }
                    'Q' -> {
                        var frames = 0L
                        for (tr in tracks) {
                            try { frames = maxOf(frames, tr.playbackHeadPosition.toLong()) } catch (_: Exception) {}
                        }
                        try {
                            output.write("$frames\n".toByteArray(Charsets.US_ASCII))
                            output.flush()
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            if (isRunning.get()) Log.w(TAG, "FlexAudioCtl client error: ${e.message}")
        } finally {
            try { ctl.close() } catch (_: Exception) {}
        }
    }

    private fun handleClient(client: android.net.LocalSocket) {
        var track: AudioTrack? = null
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            // ~600ms: margen para el feed del master clock de Flex (alimenta hasta
            // ~450ms adelante del reloj; 200ms dejaba el sink lleno al limite → micro-cortes)
            val bufSize = maxOf(minBuf, BYTES_PER_SEC * 3 / 5)
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufSize)
                .build()
            tracks.add(track)
            // Nueva stream: pausa+flush de tracks viejas (seek/restart limpio).
            // flush() es no-op si la track no está pausada; pause() primero para que
            // el playbackHeadPosition se resetee a 0 y no envenene el Q del master
            // clock de Flex (Q devuelve el max sobre TODAS las tracks).
            for (tr in tracks) {
                if (tr !== track) { try { tr.pause(); tr.flush() } catch (_: Exception) {} }
            }
            track.play()
            Log.w(TAG, "audio track created, routed device: ${track.routedDevice?.type ?: "none"} (id=${track.routedDevice?.id ?: -1})")

            val input: InputStream = client.inputStream
            val buf = ByteArray(8192)
            while (isRunning.get()) {
                val n = input.read(buf)
                if (n <= 0) break
                track.write(buf, 0, n)
            }
        } catch (e: Exception) {
            if (isRunning.get()) Log.e(TAG, "Audio client error: ${e.message}", e)
        } finally {
            try {
                track?.pause()
                track?.flush()
                track?.release()
            } catch (_: Exception) {}
            tracks.remove(track)
            try { client.close() } catch (_: Exception) {}
            clients.remove(client)
        }
    }

    fun stop() {
        isRunning.set(false)
        for (c in clients) {
            try { c.close() } catch (_: Exception) {}
        }
        clients.clear()
        cleanup()
        acceptThread?.interrupt()
        acceptThread = null
        ctlThread?.interrupt()
        ctlThread = null
    }

    private fun cleanup() {
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        try { ctlSocket?.close() } catch (_: Exception) {}
        ctlSocket = null
    }
}
