package org.aarchdroid.dragonterminal.backend

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Servidor de socket AF_UNIX abstracto singleton para recibir frames de display.
 *
 * Acepta múltiples clientes concurrentes. Cada cliente corre en su propio hilo
 * y tiene su propio listener, permitiendo que múltiples tools (iris, canvas, etc.)
 * envíen frames simultáneamente al singleton.
 *
 * El listener se asigna por conexión vía [onNewConnection].
 */
class CanvasSocketServer private constructor() {
    interface CanvasFrameListener {
        fun onStart(width: Int, height: Int, scale: Float = 1f)
        fun onFrame(frameId: Int, argbPixels: IntArray, width: Int, height: Int)
        fun onEnd()
    }

    companion object {
        private const val TAG = "CanvasSocket"
        private const val SOCKET_NAME = "canvas-display"
        private const val MAX_FRAME_SIZE = 20 * 1024 * 1024  // 20MB sanity cap
        private const val HEADER_SIZE = 20
        private const val MAGIC = 0x4D4E444C  // "MNDL"

        @Volatile
        private var instance: CanvasSocketServer? = null

        fun getInstance(): CanvasSocketServer {
            return instance ?: synchronized(this) {
                instance ?: CanvasSocketServer().also {
                    instance = it
                }
            }
        }
    }

    /**
     * Callback invoked cada vez que un nuevo cliente se conecta.
     * Debe retornar un [CanvasFrameListener] para ese cliente, o null para ignorarlo.
     */
    @Volatile
    var onNewConnection: ((connectionId: Int) -> CanvasFrameListener?)? = null

    private val nextConnectionId = AtomicInteger(0)
    private val isRunning = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var serverSocket: LocalServerSocketExt? = null
    private var acceptThread: Thread? = null

    /** Active client connections keyed by connId (for sending commands back) */
    private val clientOutputs = ConcurrentHashMap<Int, OutputStream>()

    fun setListener(l: CanvasFrameListener?) {
        onNewConnection = if (l != null) {
            { _ -> l }
        } else {
            null
        }
    }

    /**
     * Send a text command to ALL connected native clients.
     * Command is written as UTF-8 with trailing \n.
     * Used for sending resize requests back to the native process.
     */
    fun sendToAll(command: String) {
        val payload = (command + "\n").toByteArray(Charsets.UTF_8)
        val deadIds = mutableListOf<Int>()
        for ((id, out) in clientOutputs) {
            try {
                out.write(payload)
                out.flush()
            } catch (e: Exception) {
                Log.w(TAG, "[#$id] Failed to send command: ${e.message}")
                deadIds.add(id)
            }
        }
        for (id in deadIds) {
            clientOutputs.remove(id)
        }
    }

    /**
     * Send a text command to a SPECIFIC connected native client by connId.
     * Command is written as UTF-8 with trailing \n.
     */
    fun sendToClient(connId: Int, command: String) {
        val out = clientOutputs[connId] ?: return
        try {
            out.write((command + "\n").toByteArray(Charsets.UTF_8))
            out.flush()
        } catch (e: Exception) {
            Log.w(TAG, "[#$connId] Failed to send command: ${e.message}")
            clientOutputs.remove(connId)
        }
    }

    fun start() {
        if (!isRunning.compareAndSet(false, true)) {
            Log.w(TAG, "start() called but already running (isRunning=$isRunning)")
            return
        }
        val t = Thread {
            try {
                Log.w(TAG, "Creating LocalServerSocket on '$SOCKET_NAME'...")
                serverSocket = LocalServerSocketExt(SOCKET_NAME)
                Log.w(TAG, "Server listening on abstract socket: $SOCKET_NAME")
                Log.w(TAG, "Socket FD: ${serverSocket?.let { "ok" } ?: "null"}")

                while (isRunning.get()) {
                    val client = serverSocket?.accept() ?: break
                    val connId = nextConnectionId.getAndIncrement()
                    Log.w(TAG, "Client #$connId connected (fd=${client.fileDescriptor})")
                    val handler = Thread {
                        handleClient(client, connId)
                    }
                    handler.name = "Canvas-$connId"
                    handler.start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "CanvasSocketServer failed to start: ${e.message}", e)
                // Reseteamos isRunning para permitir reintento
                isRunning.set(false)
            } finally {
                cleanup()
            }
        }
        t.name = "CanvasSocketAccept"
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

        // Register output stream for reverse commands
        try {
            clientOutputs[connId] = client.outputStream
        } catch (e: Exception) {
            Log.w(TAG, "[#$connId] Cannot get outputStream: ${e.message}")
        }

        try {
            val input: InputStream = client.inputStream
            val headerBuf = ByteArray(HEADER_SIZE)
            // Dynamic buffers: grow as needed based on actual frame dimensions
            var frameBuf = ByteArray(HEADER_SIZE) // start small
            var pixelPool = Array(3) { IntArray(0) }
            var poolIdx = 0

            fun ensureBuffers(pixelBytes: Int) {
                if (pixelBytes <= 0 || pixelBytes > MAX_FRAME_SIZE) {
                    throw java.io.IOException("Frame pixel bytes out of range: $pixelBytes")
                }
                if (frameBuf.size < pixelBytes) {
                    frameBuf = ByteArray(pixelBytes)
                }
                val intsNeeded = pixelBytes / 4
                if (pixelPool[0].size < intsNeeded) {
                    pixelPool = Array(3) { IntArray(intsNeeded) }
                    poolIdx = 0
                }
            }

            fun nextBuffer(): IntArray {
                val buf = pixelPool[poolIdx]
                poolIdx = (poolIdx + 1) % 3
                return buf
            }

            // First frame: read header, validate magic
            readFully(input, headerBuf)
            val bb0 = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)
            val magic = bb0.getInt()
            if (magic != MAGIC) {
                val hex = headerBuf.joinToString("") { "%02x".format(it) }
                Log.w(TAG, "[#$connId] Bad magic: expected ${"0x%x".format(MAGIC)} got ${"0x%x".format(magic)} raw=[$hex]")
                Log.w(TAG, "[#$connId] If raw shows pixel values (e.g. 00 00 00 FF), the C binary is not sending valid headers")
                return
            }
            val firstId = bb0.getInt()
            val firstW = bb0.getInt()
            val firstH = bb0.getInt()
            val firstScaleDenom = bb0.getInt()
            val firstScale = if (firstScaleDenom > 0) firstScaleDenom / 100f else 1f
            Log.w(TAG, "[#$connId] First frame: id=$firstId ${firstW}x$firstH scale=$firstScale")
            ensureBuffers(firstW * firstH * 4)
            val firstBuf = nextBuffer()
            if (!readFrame(input, frameBuf, firstW, firstH, firstBuf)) {
                Log.e(TAG, "[#$connId] Invalid first frame dimensions: $firstW x $firstH")
                return
            }
            val fw = firstW; val fh = firstH; val fid = firstId; val fs = firstScale
            mainHandler.post {
                val copy = firstBuf.copyOf(fw * fh)
                listener.onStart(fw, fh, fs)
                listener.onFrame(fid, copy, fw, fh)
            }

            // Subsequent frames: verify magic too
            var consecutiveErrors = 0
            while (isRunning.get()) {
                readFully(input, headerBuf)
                val bb = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)
                val magic = bb.getInt()
                if (magic != MAGIC) {
                    consecutiveErrors++
                    if (consecutiveErrors > 3) {
                        val hex = headerBuf.joinToString("") { "%02x".format(it) }
                        Log.e(TAG, "[#$connId] Too many bad frames, last raw=[$hex]")
                        break
                    }
                    continue
                }
                consecutiveErrors = 0
                val frameId = bb.getInt()
                val w = bb.getInt()
                val h = bb.getInt()
                val scaleDenom = bb.getInt()
                ensureBuffers(w * h * 4)
                val frameBuf2 = nextBuffer()
                if (!readFrame(input, frameBuf, w, h, frameBuf2)) {
                    consecutiveErrors++
                    if (consecutiveErrors > 3) {
                        Log.e(TAG, "[#$connId] Too many invalid frame dimensions")
                        break
                    }
                    continue
                }
                val nfw = w; val nfh = h; val nfid = frameId
                mainHandler.post {
                    // Copy buffer to prevent torn frames if pool cycles before main thread reads
                    val copy = frameBuf2.copyOf(nfw * nfh)
                    listener.onFrame(nfid, copy, nfw, nfh)
                }
            }
        } catch (e: java.io.EOFException) {
            Log.d(TAG, "[#$connId] Client disconnected")
        } catch (e: Exception) {
            if (isRunning.get()) Log.e(TAG, "[#$connId] Client handler error", e)
        } finally {
            clientOutputs.remove(connId)
            try { client.close() } catch (_: Exception) {}
            mainHandler.post { listener.onEnd() }
        }
    }

    private fun readFrame(input: InputStream, buf: ByteArray, w: Int, h: Int, outPixels: IntArray): Boolean {
        val pixelBytes = w * h * 4
        if (pixelBytes <= 0 || w * h > outPixels.size) {
            Log.w(TAG, "Invalid frame size: $w x $h = $pixelBytes")
            return false
        }
        readFully(input, buf, pixelBytes)
        val pixelBb = ByteBuffer.wrap(buf, 0, pixelBytes).order(ByteOrder.LITTLE_ENDIAN)
        pixelBb.asIntBuffer().get(outPixels, 0, w * h)
        return true
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
        clientOutputs.clear()
        cleanup()
        acceptThread?.interrupt()
        acceptThread = null
    }

    private fun cleanup() {
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }
}
