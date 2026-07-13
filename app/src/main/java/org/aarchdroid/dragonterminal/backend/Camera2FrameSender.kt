package org.aarchdroid.dragonterminal.backend

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Camera2FrameSender(
    private val cameraId: String = "0",
    private val socketName: String = "cam-0",
    private val width: Int = 640,
    private val height: Int = 480
) {
    companion object {
        private const val TAG = "Camera2Frame"
        private const val HEADER_SIZE = 12
        private const val RETRY_INTERVAL_MS = 2000L
        private const val LATCH_TIMEOUT_MS = 20000L
    }

    @Volatile
    private var running = false
    private var contextRef: Context? = null
    private val tag = "$TAG-$socketName"
    private var mainThread: Thread? = null

    private var socket: LocalSocket? = null
    private var outputStream: OutputStream? = null
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var captureSession: CameraCaptureSession? = null
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var streamLatch: CountDownLatch? = null
    @Volatile
    private var frameId = 0

    fun start(context: Context) {
        if (running) return
        running = true
        contextRef = context.applicationContext
        mainThread = Thread {
            mainLoop()
        }.also { it.name = "Camera2Main-$socketName"; it.start() }
    }

    // ── Main loop ────────────────────────────────────────────────────

    private fun mainLoop() {
        Log.d(tag, "Main loop started (cam=$cameraId socket=$socketName)")
        while (running) {
            workerThread = HandlerThread("Camera2Worker-$socketName").also { it.start() }
            workerHandler = Handler(workerThread!!.looper)

            val sock = connectSocket() ?: break
            streamCamera(sock)
            cleanupAll()

            workerThread?.quitSafely()
            workerThread = null
            workerHandler = null

            if (running) {
                Log.d(tag, "Reconnecting in ${RETRY_INTERVAL_MS}ms...")
                Thread.sleep(RETRY_INTERVAL_MS)
            }
        }
        cleanupAll()
        Log.d(tag, "Main loop ended")
    }

    // ── Socket ───────────────────────────────────────────────────────

    private fun connectSocket(): LocalSocket? {
        while (running) {
            try {
                val sock = LocalSocket()
                sock.connect(LocalSocketAddress(socketName,
                    LocalSocketAddress.Namespace.ABSTRACT))
                socket = sock
                outputStream = sock.outputStream
                Log.d(tag, "Connected to $socketName")
                return sock
            } catch (e: Exception) {
                if (!running) return null
                Thread.sleep(RETRY_INTERVAL_MS)
            }
        }
        return null
    }

    // ── Camera ───────────────────────────────────────────────────────

    private fun streamCamera(sock: LocalSocket) {
        val ctx = contextRef ?: return
        val handler = workerHandler ?: return
        frameId = 0

        streamLatch = CountDownLatch(1)
        var camera: CameraDevice? = null

        try {
            val manager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            // ── Open camera (async → callback on HandlerThread) ──
            val openLatch = CountDownLatch(1)
            var openError: String? = null

            //noinspection MissingPermission
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    camera = cam
                    openLatch.countDown()
                }
                override fun onDisconnected(cam: CameraDevice) {
                    openError = "disconnected"
                    openLatch.countDown()
                }
                override fun onError(cam: CameraDevice, error: Int) {
                    openError = "error:$error"
                    openLatch.countDown()
                }
            }, handler)

            if (!openLatch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.e(tag, "Timeout opening camera $cameraId")
                return
            }
            if (!running) return
            if (openError != null) {
                Log.e(tag, "Camera $cameraId open failed: $openError")
                return
            }

            val cam = camera ?: return
            cameraDevice = cam

            // ── Image reader (callback on HandlerThread) ──
            val reader = ImageReader.newInstance(width, height,
                ImageFormat.YUV_420_888, 3)
            imageReader = reader

            reader.setOnImageAvailableListener({ r ->
                if (!running) return@setOnImageAvailableListener
                val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val bgra = yuv420ToBgra(img)
                    if (!writeFrame(bgra)) {
                        streamLatch?.countDown()
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Frame error", e)
                } finally {
                    img.close()
                }
            }, handler)

            // ── Capture session (callback on HandlerThread) ──
            val configLatch = CountDownLatch(1)
            var configOk = false

            cam.createCaptureSession(listOf(reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            val req = cam.createCaptureRequest(
                                CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(reader.surface)
                                set(CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            }.build()
                            session.setRepeatingRequest(req, null, handler)
                            configOk = true
                            configLatch.countDown()
                            Log.d(tag, "Streaming started ($width x $height)")
                        } catch (e: Exception) {
                            configLatch.countDown()
                        }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(tag, "Session configure failed")
                        configLatch.countDown()
                    }
                }, handler)

            if (!configLatch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.e(tag, "Timeout configuring session")
                return
            }
            if (!running) return
            if (!configOk) return

            Log.d(tag, "Streaming active, waiting for stop signal")

            // Block Camera2Main until error or stop()
            streamLatch?.await()

        } catch (e: SecurityException) {
            Log.e(tag, "Camera permission denied", e)
        } catch (e: Exception) {
            Log.e(tag, "Stream error", e)
        }
    }

    // ── Write frame ─────────────────────────────────────────────────

    private fun writeFrame(bgra: ByteBuffer): Boolean {
        val os = outputStream ?: return false
        try {
            val header = ByteBuffer.allocate(HEADER_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN)
            header.putInt(frameId++)
            header.putInt(width)
            header.putInt(height)
            header.flip()

            val hdr = ByteArray(HEADER_SIZE)
            header.get(hdr)
            os.write(hdr)

            val pixels = ByteArray(bgra.remaining())
            bgra.get(pixels)
            os.write(pixels)
            os.flush()
            return true
        } catch (e: Exception) {
            Log.w(tag, "Send error: ${e.message}")
            return false
        }
    }

    // ── YUV420 → BGRA ───────────────────────────────────────────────

    private fun yuv420ToBgra(image: Image): ByteBuffer {
        val planes = image.planes
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        val w = image.width
        val h = image.height

        val yBytes = ByteArray(yBuf.remaining()).also { yBuf.get(it) }
        val uBytes = ByteArray(uBuf.remaining()).also { uBuf.get(it) }
        val vBytes = ByteArray(vBuf.remaining()).also { vBuf.get(it) }

        val outBuf = ByteBuffer.allocateDirect(w * h * 4)
        outBuf.order(ByteOrder.LITTLE_ENDIAN)

        for (row in 0 until h) {
            val yRowOff = row * yRowStride
            val uvRow = row / 2
            val uRowOff = uvRow * uRowStride
            val vRowOff = uvRow * vRowStride

            for (col in 0 until w) {
                val y = yBytes[yRowOff + col].toInt() and 0xFF
                val uvCol = col / 2
                val u = uBytes[uRowOff + uvCol * uPixelStride].toInt() and 0xFF
                val v = vBytes[vRowOff + uvCol * vPixelStride].toInt() and 0xFF

                val c = y - 16
                val d = u - 128
                val e = v - 128
                val r = ((298 * c + 409 * e + 128) shr 8).coerceIn(0, 255)
                val g = ((298 * c - 100 * d - 208 * e + 128) shr 8).coerceIn(0, 255)
                val b = ((298 * c + 516 * d + 128) shr 8).coerceIn(0, 255)

                outBuf.putInt(-0x1000000 or (b shl 16) or (g shl 8) or r)
            }
        }

        outBuf.flip()
        return outBuf
    }

    // ── Cleanup ─────────────────────────────────────────────────────

    private fun cleanupAll() {
        try { captureSession?.close() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        try { cameraDevice?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        captureSession = null
        imageReader = null
        cameraDevice = null
        socket = null
        outputStream = null
        streamLatch = null
        frameId = 0
    }

    fun stop() {
        Log.d(tag, "Stop requested")
        running = false
        streamLatch?.countDown()
        mainThread?.interrupt()
        try { socket?.close() } catch (_: Exception) {}
    }
}
