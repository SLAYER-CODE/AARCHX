package org.aarchdroid.dragonterminal.backend

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import org.aarchdroid.dragonterminal.frontend.session.shell.client.event.CameraPermissionEvent
import org.greenrobot.eventbus.EventBus
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Server socket que escucha en "cam-ctrl" (abstracto) comandos desde el chroot:
 *   "camera 0|1" — cambiar cámara (back/front)
 *   "size WxH"   — cambiar resolución
 *   "stop"       — apagar cámara
 *
 * Posee un único CameraFrameSender que se reconfigure según los comandos.
 */
class CameraControlServer(private val context: Context) {
    companion object {
        private const val TAG = "CameraCtrlSrv"
        const val CTRL_SOCKET = "cam-ctrl"
    }

    @Volatile
    private var running = false
    private var serverThread: Thread? = null
    private var sender: CameraFrameSender? = null

    @Volatile
    private var pendingCameraId: String = "0"
    @Volatile
    private var pendingWidth: Int = 640
    @Volatile
    private var pendingHeight: Int = 480

    fun start() {
        if (running) return
        running = true
        serverThread = Thread({ run() }, "cam-ctrl-server")
        serverThread?.start()
        // No iniciar cámara aquí — esperar a que iris se conecte a cam-ctrl
    }

    /**
     * Llamar desde NeoTabDecorator cuando se concede el permiso de cámara.
     */
    fun retryCamera() {
        if (running) restartCamera()
    }

    fun stop() {
        running = false
        sender?.stop()
        sender = null
        serverThread?.interrupt()
        serverThread = null
    }

    private fun run() {
        while (running) {
            try {
                val serverSocket = LocalServerSocketExt(CTRL_SOCKET)
                Log.d(TAG, "Listening on $CTRL_SOCKET")
                while (running) {
                    val client = serverSocket.accept()
                    handleClient(client)
                }
            } catch (e: Exception) {
                if (running) {
                    Log.e(TAG, "Server error: ${e.message}")
                    Thread.sleep(1000)
                }
            }
        }
    }

    private fun handleClient(client: android.net.LocalSocket) {
        var changed = false
        try {
            val reader = BufferedReader(InputStreamReader(client.inputStream))
            var line = reader.readLine()
            while (running && line != null) {
                val cmd = line.trim()
                if (cmd.isEmpty()) continue
                Log.d(TAG, "Command: $cmd")
                when {
                    cmd.startsWith("camera ") -> {
                        val id = cmd.removePrefix("camera ").trim()
                        if (id == "0" || id == "1") {
                            pendingCameraId = id
                            changed = true
                        } else {
                            Log.w(TAG, "Invalid camera id: $id")
                        }
                    }
                    cmd == "btn hide" -> {
                        OverlayButtonState.hide()
                    }
                    cmd.startsWith("btn ") -> {
                        val parts = cmd.removePrefix("btn ").trim().split(" ")
                        if (parts.size == 2) {
                            val bx = parts[0].toIntOrNull()
                            val by = parts[1].toIntOrNull()
                            if (bx != null && by != null) {
                                OverlayButtonState.show(bx, by)
                            }
                        }
                    }
                    cmd.startsWith("size ") -> {
                        val parts = cmd.removePrefix("size ").trim().split("x")
                        if (parts.size == 2) {
                            val w = parts[0].toIntOrNull()
                            val h = parts[1].toIntOrNull()
                            if (w != null && h != null && w > 0 && h > 0) {
                                pendingWidth = w
                                pendingHeight = h
                                changed = true
                            } else {
                                Log.w(TAG, "Invalid size: $cmd")
                            }
                        } else {
                            Log.w(TAG, "Invalid size format: $cmd")
                        }
                    }
                    cmd == "stop" -> {
                        sender?.stop()
                        sender = null
                        changed = false
                    }
                }
                line = reader.readLine()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Client handler error: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
            // Aplicar todos los cambios juntos cuando el cliente se desconecta
            if (changed) restartCamera()
        }
    }

    private fun restartCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "CAMERA permission not granted — posting event")
            EventBus.getDefault().post(CameraPermissionEvent())
            return
        }
        sender?.stop()
        sender = null
        try { Thread.sleep(200) } catch (_: InterruptedException) {}
        val socketName = "cam-$pendingCameraId"
        CameraFrameSender(pendingCameraId, socketName, pendingWidth, pendingHeight).also {
            sender = it
            it.start(context)
        }
        Log.d(TAG, "Started camera id=$pendingCameraId socket=$socketName size=${pendingWidth}x$pendingHeight")
    }
}
