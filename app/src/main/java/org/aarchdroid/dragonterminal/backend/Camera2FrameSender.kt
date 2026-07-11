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
import android.view.Surface
import java.io.OutputStream
import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captura frames de la cámara trasera via Camera2 API y los envía
 * al socket abstracto "cam-0" donde Iris (chroot) los espera.
 *
 * Protocolo: [u32 frame_id][u32 width][u32 height][BGRA pixels]
 * Mismo formato que FrameHeader en iris/types.h.
 */
class Camera2FrameSender private constructor() {

    interface CameraFrameListener {
        fun onCameraConnected()
        fun onCameraDisconnected()
    }

    companion object {
        private const val TAG = "Camera2Frame"
        private const val SOCKET_NAME = "cam-0"
        private const val WIDTH = 640
        private const val HEIGHT = 480
        private const val HEADER_SIZE = 12
        private const val RETRY_INTERVAL_MS = 2000L
        private const val MAX_RETRIES = 5

        @Volatile
        private var instance: Camera2FrameSender? = null

        fun getInstance(): Camera2FrameSender {
            return instance ?: synchronized(this) {
                instance ?: Camera2FrameSender().also { instance = it }
            }
        }
    }

    @Volatile
    private var listener: CameraFrameListener? = null

    @Volatile
    private var running = false
    private var connectThread: Thread? = null
    private var socket: LocalSocket? = null
    private var outputStream: OutputStream? = null

    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequest: CaptureRequest? = null

    private var frameId = 0

    fun setListener(l: CameraFrameListener?) {
        listener = l
    }

    fun start(context: Context) {
        if (running) return
        running = true
        val appCtx = context.applicationContext
        connectThread = Thread {
            connectWithRetry(appCtx)
        }.also { it.name = "Camera2Connect"; it.start() }
    }

    private fun connectWithRetry(context: Context) {
        var attempts = 0
        while (running && attempts < MAX_RETRIES) {
            try {
                val sock = LocalSocket()
                sock.connect(LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT))
                socket = sock
                outputStream = sock.outputStream
                Log.d(TAG, "Connected to $SOCKET_NAME")
                listener?.onCameraConnected()
                openCamera(context)
                return
            } catch (e: Exception) {
                attempts++
                Log.w(TAG, "Connect attempt $attempts/$MAX_RETRIES failed: ${e.message}")
                if (!running) return
                Thread.sleep(RETRY_INTERVAL_MS)
            }
        }
        Log.w(TAG, "Failed to connect after $MAX_RETRIES attempts")
        running = false
        listener?.onCameraDisconnected()
    }

    private fun openCamera(context: Context) {
        cameraThread = HandlerThread("Camera2Thread").also { it.start() }
        cameraHandler = Handler(cameraThread!!.looper)

        cameraHandler!!.post {
            try {
                val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraId = "0"  // back camera

                //noinspection MissingPermission
                manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        Log.d(TAG, "Camera opened: $cameraId")
                        cameraDevice = camera
                        createCaptureSession(camera)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        Log.w(TAG, "Camera disconnected")
                        stop()
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        Log.e(TAG, "Camera error: $error")
                        stop()
                    }
                }, cameraHandler)
            } catch (e: SecurityException) {
                Log.e(TAG, "Camera permission not granted", e)
                stop()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open camera", e)
                stop()
            }
        }
    }

    private fun createCaptureSession(camera: CameraDevice) {
        val reader = ImageReader.newInstance(WIDTH, HEIGHT, ImageFormat.YUV_420_888, 3)
        imageReader = reader

        reader.setOnImageAvailableListener({ reader ->
            if (!running) return@setOnImageAvailableListener
            val image = reader.acquireLatestImage()
            if (image == null) return@setOnImageAvailableListener

            try {
                val bgra = yuv420ToBgra(image)
                sendFrame(bgra)
            } catch (e: Exception) {
                Log.e(TAG, "Frame processing error", e)
            } finally {
                image.close()
            }
        }, cameraHandler)

        try {
            val surface: Surface = reader.surface
            camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    Log.d(TAG, "Capture session configured")
                    captureSession = session
                    try {
                        val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        requestBuilder.addTarget(surface)
                        requestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        previewRequest = requestBuilder.build()
                        session.setRepeatingRequest(previewRequest!!, null, cameraHandler)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start repeating request", e)
                        stop()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Capture session configure failed")
                    stop()
                }
            }, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create capture session", e)
            stop()
        }
    }

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

        // Copy planes to byte arrays for fast access
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

                // BGRA little-endian (matching iris::Canvas format)
                outBuf.putInt(-0x1000000 or (b shl 16) or (g shl 8) or r)
            }
        }

        outBuf.flip()
        return outBuf
    }

    private fun sendFrame(bgra: ByteBuffer) {
        val os = outputStream ?: return
        try {
            val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            header.putInt(frameId++)
            header.putInt(WIDTH)
            header.putInt(HEIGHT)
            header.flip()

            val hdr = ByteArray(HEADER_SIZE)
            header.get(hdr)
            os.write(hdr)

            val pixels = ByteArray(bgra.remaining())
            bgra.get(pixels)
            os.write(pixels)
            os.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Send error: ${e.message}")
            stop()
        }
    }

    fun stop() {
        running = false
        try { captureSession?.close() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        try { cameraDevice?.close() } catch (_: Exception) {}
        try { cameraThread?.quitSafely() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        outputStream = null
        captureSession = null
        imageReader = null
        cameraDevice = null
        cameraThread = null
        cameraHandler = null
        socket = null
        connectThread = null
        instance = null
        Log.d(TAG, "Stopped")
    }
}
