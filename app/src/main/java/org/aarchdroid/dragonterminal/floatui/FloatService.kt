package org.aarchdroid.dragonterminal.floatui

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import org.aarchdroid.AArchDroidApp
import org.aarchdroid.R
import org.aarchdroid.dragonterminal.ui.term.NeoTermActivity
import org.aarchdroid.dragonterminal.backend.TerminalSession
import org.aarchdroid.dragonterminal.data.CommandInterceptor
import org.aarchdroid.dragonterminal.data.SessionHistory
import org.aarchdroid.dragonterminal.backend.TerminalSession.SessionChangedCallback
import org.aarchdroid.dragonterminal.frontend.config.DefaultValues
import org.aarchdroid.dragonterminal.frontend.config.NeoTermPath
import org.aarchdroid.dragonterminal.frontend.session.shell.ShellProfile
import org.aarchdroid.dragonterminal.frontend.session.shell.ShellTermSession

class FloatService : Service() {

    private val floatWindows = mutableListOf<FloatWindowView>()

    companion object {
        private const val TAG = "FloatService"
        private const val NOTIFICATION_ID = 1002
        const val ACTION_STOP = "aarchdroid.float.action.stop"
        const val ACTION_TAKEOVER = "aarchdroid.float.action.takeover"
        private const val CHANNEL_ID = "aarchdroid_float_channel"
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service starting")
        runForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runForeground()

        if (intent?.action == ACTION_TAKEOVER) {
            val taken = AArchDroidApp.transferredSession
            AArchDroidApp.transferredSession = null
            if (taken == null) {
                Log.w(TAG, "TAKEOVER: no transferred session available")
                stopSelf()
                return START_NOT_STICKY
            }
            addWindow(taken)
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_STOP -> {
                for (w in floatWindows.toList()) {
                    removeWindow(w)
                }
                return START_NOT_STICKY
            }
            else -> {
                addWindow()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroying")
        for (w in floatWindows.toList()) {
            w.session?.finishIfRunning()
            w.closeOverlay()
        }
        floatWindows.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun removeWindow(window: FloatWindowView) {
        Log.d(TAG, "Removing window, ${floatWindows.size - 1} remaining")
        window.session?.finishIfRunning()
        window.session = null
        window.closeOverlay()
        floatWindows.remove(window)
        if (floatWindows.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            updateNotification()
        }
    }

    fun anchorWindow(window: FloatWindowView) {
        Log.d(TAG, "Anchoring window back to terminal activity")
        val session = window.session
        window.session = null
        window.closeOverlay()
        floatWindows.remove(window)
        if (floatWindows.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            updateNotification()
        }
        if (session != null) {
            // Mark exit destiny as back to gestor
            CommandInterceptor.getContext(session.mHandle)?.let { ctx ->
                SessionHistory.updateTerminalDestiny(this, ctx.terminalId, "gestor")
            }
            AArchDroidApp.transferredSession = session
            startActivity(
                Intent(this, NeoTermActivity::class.java)
                    .setAction(NeoTermActivity.ACTION_ANCHOR)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }

    private fun addWindow(takenSession: TerminalSession? = null) {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val v = inflater.inflate(R.layout.float_window, null) as FloatWindowView
        v.bindToService(this)

        if (takenSession != null) {
            val callback = object : SessionChangedCallback {
                override fun onTextChanged(session: TerminalSession) {
                    v.sessionClient?.onTextChanged(session)
                }
                override fun onSessionFinished(session: TerminalSession) {
                    v.sessionClient?.onSessionFinished(session)
                    CommandInterceptor.getContext(session.mHandle)?.let { ctx ->
                        SessionHistory.updateTerminalDestiny(this@FloatService, ctx.terminalId, "cerrada")
                        SessionHistory.closeSession(this@FloatService, ctx.sessionId)
                    }
                    removeWindow(v)
                }
                override fun onTitleChanged(session: TerminalSession) {}
                override fun onClipboardText(session: TerminalSession, text: String) {
                    v.sessionClient?.onCopyText(text)
                }
                override fun onBell(session: TerminalSession) {
                    v.sessionClient?.onBell()
                }
                override fun onColorsChanged(session: TerminalSession) {
                    v.sessionClient?.onColorsChanged()
                }
            }
            takenSession.setChangeCallback(callback)
            v.session = takenSession
            v.terminalView.attachSession(takenSession)
            v.sessionClient.checkFontAndColors()
        } else {
            createSessionFor(v)
        }

        val prefs = v.preferences
        val defaultW = prefs.windowWidth.coerceAtLeast(50)
        val defaultH = prefs.windowHeight.coerceAtLeast(50)
        val pos = calculateCascadePosition(floatWindows.size, defaultW, defaultH)
        Log.d(TAG, "New window #${floatWindows.size} at x=${pos[0]} y=${pos[1]}")

        try {
            v.launchOverlay(pos[0], pos[1], pos[2], pos[3])
            floatWindows.add(v)
        } catch (e: Exception) {
            Log.e(TAG, "Overlay failed: ${e.message}")
            startActivity(
                Intent(this, OverlayPermissionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            if (floatWindows.isEmpty()) {
                stopSelf()
            }
        }
    }

    private fun createSessionFor(view: FloatWindowView) {
        val callback = object : SessionChangedCallback {
            override fun onTextChanged(session: TerminalSession) {
                view.sessionClient?.onTextChanged(session)
            }
            override fun onSessionFinished(session: TerminalSession) {
                view.sessionClient?.onSessionFinished(session)
                // Mark exit destiny as closed
                CommandInterceptor.getContext(session.mHandle)?.let { ctx ->
                    SessionHistory.updateTerminalDestiny(this@FloatService, ctx.terminalId, "cerrada")
                    SessionHistory.closeSession(this@FloatService, ctx.sessionId)
                }
                removeWindow(view)
            }
            override fun onTitleChanged(session: TerminalSession) {}
            override fun onClipboardText(session: TerminalSession, text: String) {
                view.sessionClient?.onCopyText(text)
            }
            override fun onBell(session: TerminalSession) {
                view.sessionClient?.onBell()
            }
            override fun onColorsChanged(session: TerminalSession) {
                view.sessionClient?.onColorsChanged()
            }
        }

        val s = createShellSession(callback)
        s?.let {
            it.initializeEmulator(80, 24)
            // Register in session history with launchSource="flotante"
            val sessId = SessionHistory.startSession(this).id
            val term = SessionHistory.startTerminal(this, sessId, "flotante", "flotante")
            CommandInterceptor.registerSession(it.mHandle, sessId, "flotante")
            CommandInterceptor.setTerminalId(it.mHandle, term.id)
            view.session = it
            view.terminalView.attachSession(it)
            view.sessionClient.checkFontAndColors()
        }
    }

    internal fun createShellSession(callback: SessionChangedCallback): TerminalSession? {
        val profile = ShellProfile()
        val defaultScript = AArchDroidApp.get().filesDir.absolutePath + "/bin/" + DefaultValues.loginShell

        val builder = ShellTermSession.Builder()
            .currentWorkingDirectory(NeoTermPath.HOME_PATH)
            .callback(callback)
            .systemShell(false)
            .profile(profile)

        if (!profile.enableExecveWrapper && profile.loginShell == defaultScript) {
            builder.executablePath("su")
            val inlineCmd = "mount -o remount,exec,suid,dev,rw /data 2>/dev/null; exec chroot /data/local/aarchdroid /bin/bash --rcfile /root/.bashrc"
            builder.argArray(arrayOf("su", "-c", inlineCmd))
        } else {
            builder.executablePath(profile.loginShell)
        }

        return builder.create(this)
    }

    private fun calculateCascadePosition(index: Int, winW: Int, winH: Int): IntArray {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val size = Point()
        display.getSize(size)
        val screenW = size.x
        val screenH = size.y

        val density = resources.displayMetrics.density
        val margin = (8 * density).toInt()
        val offset = (30 * density).toInt()
        val statusBarH = (50 * density).toInt()
        val baseX = margin
        val baseY = statusBarH + margin

        val maxInRow = ((screenW - winW) / offset).coerceAtLeast(1)
        val col = index % maxInRow
        val row = index / maxInRow
        var x = baseX + col * offset
        var y = baseY + row * offset

        if (y + winH > screenH) {
            x = baseX
            y = baseY
        }

        return intArrayOf(x, y, winW, winH)
    }

    private fun runForeground() {
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = android.app.NotificationChannel(
            CHANNEL_ID,
            getString(R.string.float_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = getString(R.string.float_notification_channel_desc)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val count = floatWindows.size
        val text = "$count ventana(s) flotante(s)"

        val exitIntent = Intent(this, FloatService::class.java).setAction(ACTION_STOP)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.float_notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_delete,
                getString(R.string.float_notification_exit),
                PendingIntent.getService(
                    this, 1, exitIntent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        PendingIntent.FLAG_IMMUTABLE else 0
                )
            )

        return builder.build()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }
}
