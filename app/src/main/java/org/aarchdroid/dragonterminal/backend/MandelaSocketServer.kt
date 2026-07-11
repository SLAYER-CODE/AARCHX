package org.aarchdroid.dragonterminal.backend

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Servidor de socket AF_UNIX abstracto singleton para recibir frames de Mandela.
 *
 * Mandela (C++ dentro del chroot) se conecta al socket abstracto "mandela-overlay"
 * y envía frames raw: [u32 frame_id][u32 width][u32 height][BGRA pixels].
 *
 * Singleton por app — un solo socket acepta conexiones de todos los tabs.
 * El listener se actualiza dinámicamente según el tab activo.
 */
class MandelaSocketServer private constructor() {
    interface MandelaFrameListener {
        fun onMandelaStart(width: Int, height: Int)
        fun onMandelaFrame(frameId: Int, argbPixels: IntArray, width: Int, height: Int)
        fun onMandelaEnd()
    }

    companion object {
        private const val TAG = "MandelaSocket"
        private const val SOCKET_NAME = "mandela-overlay"
        private const val MAX_FRAME_SIZE = 4 * 1024 * 1024  // 4MB
        private const val HEADER_SIZE = 12

        @Volatile
        private var instance: MandelaSocketServer? = null

        fun getInstance(): MandelaSocketServer {
            return instance ?: synchronized(this) {
                instance ?: MandelaSocketServer().also {
                    instance = it
                    it.start()
                }
            }
        }

        fun getInstanceOpt(): MandelaSocketServer? = instance
    }

    @Volatile
    var persistedScale: Float = 1f
    @Volatile
    var persistedOffsetX: Float = 0f
    @Volatile
    var persistedOffsetY: Float = 0f

    @Volatile
    private var listener: MandelaFrameListener? = null

    @Volatile
    private var connected = false
    private var lastStartW = 0
    private var lastStartH = 0

    private val isRunning = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var serverSocket: LocalServerSocketExt? = null
    private var readThread: Thread? = null

    fun setListener(l: MandelaFrameListener?) {
        listener = l
        if (connected && l != null) {
            val w = lastStartW; val h = lastStartH
            mainHandler.post { l.onMandelaStart(w, h) }
        }
    }

    private fun start() {
        if (!isRunning.compareAndSet(false, true)) return
        val t = Thread {
            try {
                serverSocket = LocalServerSocketExt(SOCKET_NAME)
                Log.d(TAG, "Server listening on abstract socket: $SOCKET_NAME")

                while (isRunning.get()) {
                    val client = serverSocket?.accept() ?: break
                    Log.d(TAG, "Client connected")
                    handleClient(client)
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e(TAG, "Server error", e)
                }
            } finally {
                cleanup()
            }
        }
        t.name = "MandelaSocketReader"
        readThread = t
        t.start()
    }

    private fun handleClient(client: android.net.LocalSocket) {
        try {
            val input: InputStream = client.inputStream
            val headerBuf = ByteArray(HEADER_SIZE)
            val frameBuf = ByteArray(MAX_FRAME_SIZE)

            // First frame: decode and fire onMandelaStart + onMandelaFrame
            readFully(input, headerBuf)
            val bb0 = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)
            val firstId = bb0.getInt()
            val firstW = bb0.getInt()
            val firstH = bb0.getInt()
            lastStartW = firstW; lastStartH = firstH
            val firstPixels = readFrame(input, frameBuf, firstW, firstH)
            connected = true
            if (firstPixels != null) {
                val l = listener
                if (l != null) {
                    val fw = firstW; val fh = firstH; val fid = firstId
                    mainHandler.post {
                        l.onMandelaStart(fw, fh)
                        l.onMandelaFrame(fid, firstPixels, fw, fh)
                    }
                }
            }

            // Subsequent frames: onMandelaFrame only
            while (isRunning.get()) {
                readFully(input, headerBuf)
                val bb = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)
                val frameId = bb.getInt()
                val w = bb.getInt()
                val h = bb.getInt()
                val pixels = readFrame(input, frameBuf, w, h) ?: continue
                val l = listener
                if (l != null) {
                    val fw = w; val fh = h; val fid = frameId
                    mainHandler.post {
                        l.onMandelaFrame(fid, pixels, fw, fh)
                    }
                }
            }
        } catch (e: java.io.EOFException) {
            Log.d(TAG, "Client disconnected")
        } catch (e: Exception) {
            if (isRunning.get()) Log.e(TAG, "Client handler error", e)
        } finally {
            connected = false
            try { client.close() } catch (_: Exception) {}
            val l = listener
            if (l != null) {
                mainHandler.post { l.onMandelaEnd() }
            }
        }
    }

    private fun readFrame(input: InputStream, buf: ByteArray, w: Int, h: Int): IntArray? {
        val pixelBytes = w * h * 4
        if (pixelBytes <= 0 || pixelBytes > MAX_FRAME_SIZE) {
            Log.w(TAG, "Invalid frame size: $w x $h = $pixelBytes")
            return null
        }
        readFully(input, buf, pixelBytes)
        val argbPixels = IntArray(w * h)
        val pixelBb = ByteBuffer.wrap(buf, 0, pixelBytes).order(ByteOrder.LITTLE_ENDIAN)
        pixelBb.asIntBuffer().get(argbPixels)
        for (i in argbPixels.indices) {
            val p = argbPixels[i]
            argbPixels[i] = (p and 0xFF00FF00.toInt()) or ((p shr 16) and 0xFF) or ((p shl 16) and 0xFF0000.toInt())
        }
        return argbPixels
    }

    private fun readFully(input: InputStream, buf: ByteArray, len: Int = buf.size) {
        var offset = 0
        while (offset < len) {
            val n = input.read(buf, offset, len - offset)
            if (n == -1) throw java.io.EOFException("Stream closed reading ${len - offset} more bytes")
            offset += n
        }
    }

    fun stop() {
        isRunning.set(false)
        cleanup()
        readThread?.interrupt()
        readThread = null
    }

    private fun cleanup() {
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }
}
