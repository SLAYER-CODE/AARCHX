package org.aarchdroid.dragonterminal.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.app.NotificationCompat
import org.aarchdroid.dragonterminal.ui.term.NeoTermActivity
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class CameraCaptureService : Service() {
    private val isRunning = AtomicBoolean(false)
    private var cameraHandler: Handler? = null
    private var cameraThread: HandlerThread? = null
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var cameraManager: CameraManager? = null
    private var socket: LocalSocket? = null
    private var socketOut: java.io.OutputStream? = null
    private var frameCount = 0

    private var captureWidth = 640
    private var captureHeight = 480
    private var socketName = "cam-0"
    private var cameraId = "0"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannel()
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning.compareAndSet(false, true)) {
            Log.w(TAG, "Already running")
            return START_STICKY
        }

        captureWidth = intent?.getIntExtra("width", 640) ?: 640
        captureHeight = intent?.getIntExtra("height", 480) ?: 480
        socketName = intent?.getStringExtra("socket_name") ?: "cam-0"
        cameraId = intent?.getStringExtra("camera_id") ?: getDefaultCameraId() ?: "0"

        Log.d(TAG, "Starting: camera=$cameraId ${captureWidth}x$captureHeight -> socket=$socketName")

        startForeground(NOTIFICATION_ID, createNotification("Starting camera..."))
        startBackground()
        return START_STICKY
    }

    private fun startBackground() {
        cameraThread = HandlerThread("CameraCaptureThread").apply { start() }
        cameraHandler = Handler(cameraThread!!.looper)
        cameraHandler?.post { openCamera() }
    }

    private fun getDefaultCameraId(): String? {
        try {
            for (id in cameraManager!!.cameraIdList) {
                val chars = cameraManager!!.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK)
                    return id
            }
            if (cameraManager!!.cameraIdList.isNotEmpty())
                return cameraManager!!.cameraIdList[0]
        } catch (e: Exception) {
            Log.e(TAG, "Error listing cameras", e)
        }
        return null
    }

    private fun openCamera() {
        try {
            if (checkSelfPermission(android.Manifest.permission.CAMERA)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "CAMERA permission not granted")
                stopSelf()
                return
            }
            cameraManager!!.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.d(TAG, "Camera opened: $cameraId")
                    cameraDevice = camera
                    startPreview(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "Camera disconnected: $cameraId")
                    camera.close()
                    cameraDevice = null
                    stopSelf()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                    camera.close()
                    cameraDevice = null
                    stopSelf()
                }
            }, cameraHandler)
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission denied", e)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera", e)
            stopSelf()
        }
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

            val sessionCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    Log.d(TAG, "Capture session configured")
                    cameraCaptureSession = session
                    try {
                        session.setRepeatingRequest(request.build(), null, cameraHandler)
                        connectSocket()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start repeating request", e)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Capture session configure failed")
                    stopSelf()
                }
            }
            camera.createCaptureSession(listOf(surface), sessionCallback, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start preview", e)
            stopSelf()
        }
    }

    private fun connectSocket() {
        var attempts = 0
        while (isRunning.get() && attempts < 60) {
            try {
                val s = LocalSocket()
                s.connect(LocalSocketAddress(
                    socketName, LocalSocketAddress.Namespace.ABSTRACT))
                socket = s
                socketOut = s.outputStream
                Log.d(TAG, "Connected to socket: $socketName")
                updateNotification("Camera streaming to $socketName")
                return
            } catch (e: IOException) {
                Log.w(TAG, "Socket connect attempt $attempts failed: ${e.message}")
                attempts++
                try { Thread.sleep(2000) } catch (_: InterruptedException) { return }
            }
        }
        Log.e(TAG, "Failed to connect to socket after $attempts attempts")
        stopSelf()
    }

    private fun processFrame(image: Image) {
        if (socketOut == null) return

        val planes = image.planes
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val w = image.width
        val h = image.height

        val yRowStride = yPlane.rowStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride

        val isNv21 = (uPixelStride == 2 || vPlane.pixelStride == 2) && uBuf === vBuf

        val pixelBytes = w * h * 4
        val bgra = ByteBuffer.allocateDirect(pixelBytes)
        bgra.order(ByteOrder.LITTLE_ENDIAN)

        for (y in 0 until h) {
            val yRowOff = y * yRowStride
            val uvRowOff = (y shr 1) * uRowStride
            for (x in 0 until w) {
                val yy = yBuf.get(yRowOff + x).toInt() and 0xFF
                val uvOff = uvRowOff + (x shr 1) * if (isNv21) 2 else uPixelStride

                val u: Int
                val v: Int
                if (isNv21) {
                    v = uBuf.get(uvOff).toInt() and 0xFF
                    u = uBuf.get(uvOff + 1).toInt() and 0xFF
                } else {
                    u = uBuf.get(uvOff).toInt() and 0xFF
                    v = vBuf.get(uvOff).toInt() and 0xFF
                }

                val c = yy - 16
                val d = u - 128
                val e = v - 128
                val r = ((298 * c + 409 * e + 128) shr 8).coerceIn(0, 255)
                val g = ((298 * c - 100 * d - 208 * e + 128) shr 8).coerceIn(0, 255)
                val b = ((298 * c + 516 * d + 128) shr 8).coerceIn(0, 255)

                bgra.put(b.toByte())
                bgra.put(g.toByte())
                bgra.put(r.toByte())
                bgra.put(0xFF.toByte())
            }
        }

        bgra.flip()

        // Send header + pixels over socket
        try {
            frameCount++
            val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            header.putInt(frameCount)
            header.putInt(w)
            header.putInt(h)
            header.flip()

            val out = socketOut!!
            out.write(header.array())
            out.write(bgra.array(), 0, pixelBytes)
            out.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Socket write error: ${e.message}")
            socketOut = null
            try { socket?.close() } catch (_: Exception) {}
            socket = null
            // Retry connection
            connectSocket()
        }
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, NeoTermActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Camera Capture")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pi)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID, "Camera Capture", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Camera capture service for Iris"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        isRunning.set(false)
        try { cameraCaptureSession?.close() } catch (_: Exception) {}
        try { cameraDevice?.close() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        cameraHandler?.post { cameraThread?.quitSafely() }
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "CameraCapture"
        private const val NOTIFICATION_ID = 1003
        private const val CHANNEL_ID = "camera_capture"
        private const val HEADER_SIZE = 12

        fun start(context: Context, cameraId: String? = null,
                  socketName: String? = null, width: Int = 640, height: Int = 480) {
            val intent = Intent(context, CameraCaptureService::class.java)
            cameraId?.let { intent.putExtra("camera_id", it) }
            socketName?.let { intent.putExtra("socket_name", it) }
            intent.putExtra("width", width)
            intent.putExtra("height", height)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CameraCaptureService::class.java))
        }
    }
}
