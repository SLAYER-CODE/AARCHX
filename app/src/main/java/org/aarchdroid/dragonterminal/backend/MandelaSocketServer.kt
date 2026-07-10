package org.aarchdroid.dragonterminal.backend

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Servidor de socket AF_UNIX abstracto para recibir frames de Mandela.
 *
 * Mandela (C++ dentro del chroot) se conecta al socket abstracto "mandela-overlay"
 * y envía frames raw: [u32 frame_id][u32 width][u32 height][BGRA pixels].
 *
 * Este server corre en su propio thread de IO, parsea los frames y los entrega
 * al listener en el main thread.
 */
class MandelaSocketServer(
    private val listener: MandelaFrameListener
) {
    interface MandelaFrameListener {
        fun onMandelaStart(width: Int, height: Int)
        fun onMandelaFrame(frameId: Int, argbPixels: IntArray, width: Int, height: Int)
        fun onMandelaEnd()
    }

    companion object {
        private const val TAG = "MandelaSocket"
        private const val SOCKET_NAME = "mandela-overlay"
        private const val MAX_FRAME_SIZE = 4 * 1024 * 1024  // 4MB

        // [frame_id:4][width:4][height:4] = 12 bytes header
        private const val HEADER_SIZE = 12
    }

    private val isRunning = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var serverSocket: LocalServerSocketExt? = null
    private var readThread: Thread? = null

    fun start() {
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
            var frameId = 0

            while (isRunning.get()) {
                // Leer header exactamente (SOCK_SEQPACKET da mensajes completos,
                // pero leemos con readFully por seguridad)
                readFully(input, headerBuf)
                val bb = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)
                frameId = bb.getInt()
                val w = bb.getInt()
                val h = bb.getInt()

                val pixelBytes = w * h * 4
                if (pixelBytes <= 0 || pixelBytes > MAX_FRAME_SIZE) {
                    Log.w(TAG, "Invalid frame size: $w x $h = $pixelBytes")
                    continue
                }

                readFully(input, frameBuf, pixelBytes)

                val argbPixels = IntArray(w * h)
                val pixelBb = ByteBuffer.wrap(frameBuf, 0, pixelBytes).order(ByteOrder.LITTLE_ENDIAN)
                pixelBb.asIntBuffer().get(argbPixels)

                // BGRA → ARGB swap (Skia escribe BGRA en little-endian)
                for (i in argbPixels.indices) {
                    val p = argbPixels[i]
                    argbPixels[i] = (p and 0xFF00FF00.toInt()) or ((p shr 16) and 0xFF) or ((p shl 16) and 0xFF0000.toInt())
                }

                val fw = w
                val fh = h
                val fid = frameId
                mainHandler.post {
                    listener.onMandelaStart(fw, fh)
                    listener.onMandelaFrame(fid, argbPixels, fw, fh)
                }
            }
        } catch (e: java.io.EOFException) {
            Log.d(TAG, "Client disconnected")
        } catch (e: Exception) {
            if (isRunning.get()) Log.e(TAG, "Client handler error", e)
        } finally {
            try { client.close() } catch (_: Exception) {}
            mainHandler.post { listener.onMandelaEnd() }
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

    private fun readFully(input: InputStream, buf: ByteArray, len: Int = buf.size) {
        var offset = 0
        while (offset < len) {
            val n = input.read(buf, offset, len - offset)
            if (n == -1) throw java.io.EOFException("Stream closed reading ${len - offset} more bytes")
            offset += n
        }
    }
}