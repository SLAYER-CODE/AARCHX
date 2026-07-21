package org.aarchdroid.dragonterminal.backend

import android.content.Context
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import org.aarchdroid.dragonterminal.frontend.session.shell.client.event.CameraPermissionEvent
import org.greenrobot.eventbus.EventBus
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private data class FrameData(val buffer: ByteBuffer, val width: Int, val height: Int)

class CameraFrameSender(
    private val cameraId: String = "0",
    private val socketName: String = "cam-0",
    private val width: Int = 640,
    private val height: Int = 480,
    private val rotationOverride: Int = 0 // 0 = auto (sensor), -1 = none (original)
) {
    companion object {
        private const val TAG = "CameraFrame"
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

    // Buffers reusables para evitar alocar memoria en cada frame
    private var reuseYBuf: ByteArray? = null
    private var reuseUBuf: ByteArray? = null
    private var reuseVBuf: ByteArray? = null
    private var reuseOutBuf: ByteBuffer? = null
    private var reuseHeader: ByteArray? = null
    private var reuseRotateBuf: ByteBuffer? = null

    private var sensorOrientation: Int = 0

    fun start(context: Context) {
        if (running) return
        running = true
        contextRef = context.applicationContext
        mainThread = Thread {
            mainLoop()
        }.also { it.name = "CameraMain-$socketName"; it.start() }
    }

    // ── Main loop ────────────────────────────────────────────────────

    private fun mainLoop() {
        Log.d(tag, "Main loop started (cam=$cameraId socket=$socketName)")
        while (running) {
            workerThread = HandlerThread("CameraWorker-$socketName").also { it.start() }
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
                try {
                    Thread.sleep(RETRY_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                }
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

            // Leer orientación del sensor
            val characteristics = manager.getCameraCharacteristics(cameraId)
            sensorOrientation = characteristics.get(
                android.hardware.camera2.CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

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
                    val frame = yuv420ToBgra(img)
                    if (!writeFrame(frame)) {
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

            // Block CameraMain until error or stop()
            streamLatch?.await()

        } catch (e: SecurityException) {
            Log.e(tag, "Camera permission denied — posting event")
            EventBus.getDefault().post(CameraPermissionEvent())
        } catch (e: Exception) {
            Log.e(tag, "Stream error", e)
        }
    }

    // ── Write frame ─────────────────────────────────────────────────

    private fun writeFrame(frame: FrameData): Boolean {
        val os = outputStream ?: return false
        try {
            var hdr = reuseHeader
            if (hdr == null) {
                hdr = ByteArray(HEADER_SIZE).also { reuseHeader = it }
            }
            val buf = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(frameId++)
            buf.putInt(frame.width)
            buf.putInt(frame.height)
            os.write(hdr)

            // frame.buffer es heap-based (ByteBuffer.allocate), tiene array()
            val fb = frame.buffer
            os.write(fb.array(), fb.arrayOffset(), fb.remaining())
            os.flush()
            return true
        } catch (e: Exception) {
            Log.w(tag, "Send error: ${e.message}")
            return false
        }
    }

    // ── Rotación ────────────────────────────────────────────────────

    private fun rotateBuffer(frame: FrameData, degrees: Int): FrameData {
        if (degrees == 0) return frame

        val src = frame.buffer
        val srcW = frame.width
        val srcH = frame.height

        when (degrees) {
            180 -> {
                // 180°: swap píxeles opuestos en el mismo buffer (in-place)
                val totalPixels = srcW * srcH
                val intBuf = src.duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
                for (i in 0 until totalPixels / 2) {
                    val j = totalPixels - 1 - i
                    val tmp = intBuf.get(i)
                    intBuf.put(i, intBuf.get(j))
                    intBuf.put(j, tmp)
                }
                return frame
            }
            90, 270 -> {
                val dstW = srcH
                val dstH = srcW
                val dstBytes = dstW * dstH * 4
                var dst: ByteBuffer = reuseRotateBuf ?: ByteBuffer.allocate(dstBytes).also {
                    it.order(ByteOrder.LITTLE_ENDIAN)
                    reuseRotateBuf = it
                }
                if (dst.capacity() < dstBytes) {
                    dst = ByteBuffer.allocate(dstBytes).also {
                        it.order(ByteOrder.LITTLE_ENDIAN)
                        reuseRotateBuf = it
                    }
                }
                dst.clear()
                dst.order(ByteOrder.LITTLE_ENDIAN)

                val srcIntBuf = src.duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()

                for (row in 0 until srcH) {
                    for (col in 0 until srcW) {
                        val srcOff = row * srcW + col
                        val dstRow: Int
                        val dstCol: Int
                        if (degrees == 90) {
                            dstRow = col
                            dstCol = srcH - 1 - row
                        } else {
                            dstRow = srcW - 1 - col
                            dstCol = row
                        }
                        dst.asIntBuffer().put(dstRow * dstW + dstCol, srcIntBuf.get(srcOff))
                    }
                }
                return FrameData(dst, dstW, dstH)
            }
            else -> return frame
        }
    }

    // ── YUV420 → BGRA ───────────────────────────────────────────────

    private fun yuv420ToBgra(image: Image): FrameData {
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

        // Reusar o redimensionar buffers Y/U/V
        val yRem = yBuf.remaining()
        val uRem = uBuf.remaining()
        val vRem = vBuf.remaining()

        val yb: ByteArray
        if (reuseYBuf == null || reuseYBuf!!.size < yRem) {
            yb = ByteArray(yRem).also { reuseYBuf = it }
        } else {
            yb = reuseYBuf!!
        }
        yBuf.get(yb, 0, yRem)

        val ub: ByteArray
        if (reuseUBuf == null || reuseUBuf!!.size < uRem) {
            ub = ByteArray(uRem).also { reuseUBuf = it }
        } else {
            ub = reuseUBuf!!
        }
        uBuf.get(ub, 0, uRem)

        val vb: ByteArray
        if (reuseVBuf == null || reuseVBuf!!.size < vRem) {
            vb = ByteArray(vRem).also { reuseVBuf = it }
        } else {
            vb = reuseVBuf!!
        }
        vBuf.get(vb, 0, vRem)

        // Reusar o redimensionar buffer de salida (heap para poder usar array() en writeFrame)
        val outCap = w * h * 4
        val out: ByteBuffer
        if (reuseOutBuf == null || reuseOutBuf!!.capacity() < outCap) {
            out = ByteBuffer.allocate(outCap).also { reuseOutBuf = it }
        } else {
            out = reuseOutBuf!!
            out.clear()
        }
        out.order(ByteOrder.LITTLE_ENDIAN)

        for (row in 0 until h) {
            val yRowOff = row * yRowStride
            val uvRow = row / 2
            val uRowOff = uvRow * uRowStride
            val vRowOff = uvRow * vRowStride

            for (col in 0 until w) {
                val y = yb[yRowOff + col].toInt() and 0xFF
                val uvCol = col / 2
                val u = ub[uRowOff + uvCol * uPixelStride].toInt() and 0xFF
                val v = vb[vRowOff + uvCol * vPixelStride].toInt() and 0xFF

                val c = y - 16
                val d = u - 128
                val e = v - 128
                val r = ((298 * c + 409 * e + 128) shr 8).coerceIn(0, 255)
                val g = ((298 * c - 100 * d - 208 * e + 128) shr 8).coerceIn(0, 255)
                val b = ((298 * c + 516 * d + 128) shr 8).coerceIn(0, 255)

                out.putInt(-0x1000000 or (r shl 16) or (g shl 8) or b)
            }
        }

        out.flip()
        return FrameData(out, w, h)
    }

    // ── Cleanup ─────────────────────────────────────────────────────

    private fun cleanupAll() {
        // Cerrar worker handler PRIMERO para no recibir más callbacks de ImageReader
        workerThread?.quitSafely()
        workerThread?.join(1000)
        workerThread = null
        workerHandler = null

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
        reuseYBuf = null
        reuseUBuf = null
        reuseVBuf = null
        reuseOutBuf = null
        reuseRotateBuf = null
        reuseHeader = null
    }

    fun stop() {
        Log.d(tag, "Stop requested")
        running = false
        // Cerrar socket primero para desbloquear mainLoop() rápido
        try { socket?.close() } catch (_: Exception) {}
        streamLatch?.countDown()
        mainThread?.interrupt()
    }
}
