package org.aarchdroid.dragonterminal.backend

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Servidor de socket AF_UNIX abstracto singleton para recibir frames de Mandela.
 *
 * Acepta múltiples clientes concurrentes. Cada cliente corre en su propio hilo
 * y tiene su propio listener, permitiendo que múltiples tools (Mandela, Iris)
 * envíen frames simultáneamente al singleton.
 *
 * El listener se asigna por conexión vía [onNewConnection].
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
    }

    /**
     * Callback invoked cada vez que un nuevo cliente se conecta.
     * Debe retornar un [MandelaFrameListener] para ese cliente, o null para ignorarlo.
     */
    @Volatile
    var onNewConnection: ((connectionId: Int) -> MandelaFrameListener?)? = null

    private val nextConnectionId = AtomicInteger(0)
    private val isRunning = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var serverSocket: LocalServerSocketExt? = null
    private var acceptThread: Thread? = null

    fun setListener(l: MandelaFrameListener?) {
        onNewConnection = if (l != null) {
            { _ -> l }
        } else {
            null
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
                    val connId = nextConnectionId.getAndIncrement()
                    Log.d(TAG, "Client #$connId connected")
                    val handler = Thread {
                        handleClient(client, connId)
                    }
                    handler.name = "Mandela-$connId"
                    handler.start()
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e(TAG, "Server error", e)
                }
            } finally {
                cleanup()
            }
        }
        t.name = "MandelaSocketAccept"
        acceptThread = t
        t.start()
    }

    private fun handleClient(client: android.net.LocalSocket, connId: Int) {
        val listener = onNewConnection?.invoke(connId)
        if (listener == null) {
            Log.w(TAG, "No listener for client #$connId — closing")
            try { client.close() } catch (_: Exception) {}
            return
        }

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
            Log.d(TAG, "[#$connId] First frame header: id=$firstId ${firstW}x$firstH")
            val firstPixels = readFrame(input, frameBuf, firstW, firstH)
            if (firstPixels != null) {
                val fw = firstW; val fh = firstH; val fid = firstId
                mainHandler.post {
                    listener.onMandelaStart(fw, fh)
                    listener.onMandelaFrame(fid, firstPixels, fw, fh)
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
                val fw = w; val fh = h; val fid = frameId
                mainHandler.post {
                    listener.onMandelaFrame(fid, pixels, fw, fh)
                }
            }
        } catch (e: java.io.EOFException) {
            Log.d(TAG, "[#$connId] Client disconnected")
        } catch (e: Exception) {
            if (isRunning.get()) Log.e(TAG, "[#$connId] Client handler error", e)
        } finally {
            try { client.close() } catch (_: Exception) {}
            mainHandler.post { listener.onMandelaEnd() }
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
        acceptThread?.interrupt()
        acceptThread = null
    }

    private fun cleanup() {
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }
}
