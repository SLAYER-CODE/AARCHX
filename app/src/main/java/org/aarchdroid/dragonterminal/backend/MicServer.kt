package org.aarchdroid.dragonterminal.backend

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import org.aarchdroid.dragonterminal.frontend.session.shell.client.event.MicPermissionEvent
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Servidor de microfono PCM para Mifo.
 *
 * Expone un socket AF_UNIX abstracto "mic-0" (mismo mecanismo que cam-0 y
 * canvas-display): Mifo dentro del chroot recibe PCM s16le 44100 Hz mono
 * por el socket. La captura se hace via AudioRecord (HAL del host) porque
 * el chroot no puede configurar los PCM ALSA del DSP.
 *
 * Protocolo: al conectar el cliente recibe una linea de cabecera
 * `mic <rate> <channels> <bits>\n` y despues PCM s16le crudo y continuo.
 */
class MicServer private constructor() {

    companion object {
        private const val TAG = "MicServer"
        private const val SOCKET_NAME = "mic-0"
        private const val SAMPLE_RATE = 44100
        private const val CHANNELS = 1
        private const val ENCODING_BITS = 16
        private const val CHUNK_BYTES = 4096

        @Volatile
        private var instance: MicServer? = null

        fun getInstance(): MicServer {
            return instance ?: synchronized(this) {
                instance ?: MicServer().also { instance = it }
            }
        }
    }

    private val isRunning = AtomicBoolean(false)
    private val clients = CopyOnWriteArrayList<android.net.LocalSocket>()
    private var serverSocket: LocalServerSocketExt? = null
    private var acceptThread: Thread? = null
    private var captureThread: Thread? = null
    private var recorder: AudioRecord? = null
    private var context: Context? = null

    fun start(context: Context) {
        this.context = context.applicationContext
        if (!isRunning.compareAndSet(false, true)) {
            Log.w(TAG, "start() called but already running")
            return
        }
        val t = Thread {
            try {
                Log.w(TAG, "Creating LocalServerSocket on '$SOCKET_NAME'...")
                serverSocket = LocalServerSocketExt(SOCKET_NAME)
                Log.w(TAG, "Server listening on abstract socket: $SOCKET_NAME")
                while (isRunning.get()) {
                    val client = serverSocket?.accept() ?: break
                    clients.add(client)
                    Log.d(TAG, "Client connected ($SOCKET_NAME)")
                    try {
                        val header = "mic $SAMPLE_RATE $CHANNELS $ENCODING_BITS\n".toByteArray()
                        client.outputStream.write(header)
                        client.outputStream.flush()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to send header: ${e.message}")
                    }
                    startCaptureIfNeeded()
                }
            } catch (e: Exception) {
                if (isRunning.get()) Log.e(TAG, "MicServer failed: ${e.message}", e)
                isRunning.set(false)
            } finally {
                cleanup()
            }
        }
        t.name = "MicAccept"
        acceptThread = t
        t.start()
    }

    private fun startCaptureIfNeeded() {
        if (captureThread != null && recorder != null) return
        val ctx = context ?: return
        if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO not granted — posting MicPermissionEvent")
            EventBus.getDefault().post(MicPermissionEvent())
            return
        }
        synchronized(this) {
            if (captureThread != null) return
            try {
                val minBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufSize = maxOf(minBuf * 4, CHUNK_BYTES * 4)
                val rec = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufSize)
                    .build()
                recorder = rec
                val ct = Thread {
                    captureLoop(rec)
                }
                ct.name = "MicCapture"
                captureThread = ct
                ct.start()
                Log.d(TAG, "Capture started (rate=$SAMPLE_RATE ch=$CHANNELS buf=$bufSize)")
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord init failed: ${e.message}", e)
                recorder = null
                captureThread = null
            }
        }
    }

    private fun captureLoop(rec: AudioRecord) {
        try {
            rec.startRecording()
            val buf = ByteArray(CHUNK_BYTES)
            while (isRunning.get()) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) {
                    if (n == AudioRecord.ERROR_DEAD_OBJECT) break
                    continue
                }
                if (clients.isEmpty()) {
                    Thread.sleep(10)
                    continue
                }
                for (client in clients) {
                    try {
                        client.outputStream.write(buf, 0, n)
                    } catch (e: Exception) {
                        clients.remove(client)
                        try { client.close() } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            if (isRunning.get()) Log.e(TAG, "Capture loop error: ${e.message}", e)
        } finally {
            try { rec.stop() } catch (_: Exception) {}
            try { rec.release() } catch (_: Exception) {}
            synchronized(this) {
                recorder = null
                captureThread = null
            }
            Log.d(TAG, "Capture stopped")
        }
    }

    fun retry() {
        startCaptureIfNeeded()
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
    }

    private fun cleanup() {
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }
}
