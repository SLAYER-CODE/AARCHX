package org.aarchdroid.dragonterminal.backend

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class CameraCaptureManager(private val context: Context) {
    private val isRunning = AtomicBoolean(false)
    private var cameraHandler: Handler? = null
    private var cameraThread: HandlerThread? = null
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var socket: LocalSocket? = null
    private var socketOut: java.io.OutputStream? = null
    private var frameCount = 0

    private var captureWidth = 640
    private var captureHeight = 480
    private var socketName = "cam-0"
    private var cameraId = "0"

    fun start(cameraId: String = "0", width: Int = 640, height: Int = 480,
              socketName: String = "cam-0") {
        if (!isRunning.compareAndSet(false, true)) return
        this.cameraId = cameraId
        this.captureWidth = width
        this.captureHeight = height
        this.socketName = socketName

        cameraThread = HandlerThread("CameraCaptureThread").apply { start() }
        cameraHandler = Handler(cameraThread!!.looper)
        cameraHandler?.post { openCamera() }
    }

    fun stop() {
        isRunning.set(false)
        try { cameraCaptureSession?.close() } catch (_: Exception) {}
        try { cameraDevice?.close() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        cameraHandler?.post { cameraThread?.quitSafely() }
    }

    private fun openCamera() {
        try {
            Log.d(TAG, "Opening camera '$cameraId'...")
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val camIds = manager.cameraIdList.joinToString()
            Log.d(TAG, "Available camera IDs: [$camIds]")
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.d(TAG, "Camera opened: $cameraId")
                    cameraDevice = camera
                    startPreview(camera)
                }
                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "Camera disconnected")
                    camera.close()
                    cameraDevice = null
                    stop()
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error (${cameraErrorString(error)})")
                    camera.close()
                    cameraDevice = null
                    stop()
                }
            }, cameraHandler)
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission denied", e)
            stop()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera", e)
            stop()
        }
    }

    private fun cameraErrorString(error: Int): String = when (error) {
        CameraDevice.StateCallback.ERROR_CAMERA_IN_USE -> "CAMERA_IN_USE"
        CameraDevice.StateCallback.ERROR_CAMERA_DEVICE -> "CAMERA_DEVICE"
        CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> "CAMERA_DISABLED"
        CameraDevice.StateCallback.ERROR_CAMERA_SERVICE -> "CAMERA_SERVICE"
        CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> "MAX_CAMERAS_IN_USE"
        else -> "UNKNOWN($error)"
    }

    private fun startPreview(camera: CameraDevice) {
        try {
            imageReader = ImageReader.newInstance(
                captureWidth, captureHeight, ImageFormat.YUV_420_888, 2)
            imageReader!!.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                processFrame(image)
                image.close()
            }, cameraHandler)

            val surface = imageReader!!.surface
            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            request.addTarget(surface)

            camera.createCaptureSession(listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        Log.d(TAG, "Capture session configured")
                        cameraCaptureSession = session
                        try {
                            session.setRepeatingRequest(request.build(), null, cameraHandler)
                            connectSocketAsync()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start repeating request", e)
                        }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Capture session configure failed")
                        stop()
                    }
                }, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start preview", e)
            stop()
        }
    }

    private fun connectSocketAsync() {
        Thread {
            var attempts = 0
            while (isRunning.get() && attempts < 60) {
                try {
                    val s = LocalSocket(LocalSocket.SOCKET_SEQPACKET)
                    s.connect(LocalSocketAddress(
                        socketName, LocalSocketAddress.Namespace.ABSTRACT))
                    socket = s
                    socketOut = s.outputStream
                    Log.d(TAG, "Connected to socket '$socketName' (attempt $attempts)")
                    return@Thread
                } catch (e: IOException) {
                    Log.w(TAG, "Socket connect attempt $attempts: ${e.message}")
                    attempts++
                    try { Thread.sleep(2000) } catch (_: InterruptedException) { return@Thread }
                }
            }
            Log.e(TAG, "Failed to connect to socket '$socketName' after $attempts attempts")
            stop()
        }.apply { name = "CameraConnectThread" }.start()
    }

    private fun processFrame(image: Image) {
        if (socketOut == null) return
        val planes = image.planes
        val yBuf = planes[0].buffer
        val uBuf = planes[1].buffer
        val vBuf = planes[2].buffer
        val w = image.width
        val h = image.height
        val yRowStride = planes[0].rowStride
        val uRowStride = planes[1].rowStride
        val isNv21 = (planes[1].pixelStride == 2 || planes[2].pixelStride == 2) && uBuf === vBuf

        val pixelBytes = w * h * 4
        val bgra = ByteBuffer.allocateDirect(pixelBytes)
        bgra.order(ByteOrder.LITTLE_ENDIAN)

        for (y in 0 until h) {
            val yRowOff = y * yRowStride
            val uvRowOff = (y shr 1) * uRowStride
            for (x in 0 until w) {
                val yy = yBuf.get(yRowOff + x).toInt() and 0xFF
                val uvOff = uvRowOff + (x shr 1) * if (isNv21) 2 else planes[1].pixelStride
                val u: Int; val v: Int
                if (isNv21) {
                    v = uBuf.get(uvOff).toInt() and 0xFF
                    u = uBuf.get(uvOff + 1).toInt() and 0xFF
                } else {
                    u = uBuf.get(uvOff).toInt() and 0xFF
                    v = vBuf.get(uvOff).toInt() and 0xFF
                }
                val c = yy - 16; val d = u - 128; val e = v - 128
                val r = ((298 * c + 409 * e + 128) shr 8).coerceIn(0, 255)
                val g = ((298 * c - 100 * d - 208 * e + 128) shr 8).coerceIn(0, 255)
                val b = ((298 * c + 516 * d + 128) shr 8).coerceIn(0, 255)
                bgra.put(b.toByte()); bgra.put(g.toByte()); bgra.put(r.toByte()); bgra.put(0xFF.toByte())
            }
        }
        bgra.flip()

        try {
            frameCount++
            val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            header.putInt(frameCount); header.putInt(w); header.putInt(h); header.flip()
            socketOut!!.write(header.array())
            socketOut!!.write(bgra.array(), 0, pixelBytes)
            socketOut!!.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Socket write error: ${e.message}")
            socketOut = null; try { socket?.close() } catch (_: Exception) {}; socket = null
            connectSocketAsync()
        }
    }

    companion object {
        private const val TAG = "CameraCaptureMgr"
    }
}
