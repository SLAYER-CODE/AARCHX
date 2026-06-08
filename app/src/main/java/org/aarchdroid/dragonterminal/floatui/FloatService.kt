package org.aarchdroid.dragonterminal.floatui

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import androidx.core.app.NotificationCompat
import org.aarchdroid.AArchDroidApp
import org.aarchdroid.R
import org.aarchdroid.dragonterminal.backend.TerminalSession
import org.aarchdroid.dragonterminal.backend.TerminalSession.SessionChangedCallback
import org.aarchdroid.dragonterminal.frontend.config.DefaultValues
import org.aarchdroid.dragonterminal.frontend.config.NeoPreference
import org.aarchdroid.dragonterminal.frontend.config.NeoTermPath
import org.aarchdroid.dragonterminal.frontend.session.shell.ShellProfile
import org.aarchdroid.dragonterminal.frontend.session.shell.ShellTermSession

class FloatService : Service() {

    private var floatView: FloatWindowView? = null
    private var session: TerminalSession? = null
    private var visible = true

    companion object {
        private const val TAG = "FloatService"
        private const val NOTIFICATION_ID = 1002
        const val ACTION_STOP = "aarchdroid.float.action.stop"
        const val ACTION_SHOW = "aarchdroid.float.action.show"
        const val ACTION_HIDE = "aarchdroid.float.action.hide"
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

        if (floatView == null && !initFloatView()) {
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_STOP -> {
                session?.finishIfRunning()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW -> setVisible(true)
            ACTION_HIDE -> setVisible(false)
            else -> {
                if (!visible) setVisible(true)
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroying")
        floatView?.closeOverlay()
        floatView = null
        session?.finishIfRunning()
        session = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun requestStopService() {
        Log.d(TAG, "Requesting stop")
        session?.finishIfRunning()
        session = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    val currentSession: TerminalSession?
        get() = session

    private fun runForeground() {
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
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
        val text = if (visible) getString(R.string.float_notification_visible)
            else getString(R.string.float_notification_hidden)
        val toggleAction = if (visible) ACTION_HIDE else ACTION_SHOW

        val toggleIntent = Intent(this, FloatService::class.java).setAction(toggleAction)
        val togglePending = PendingIntent.getService(
            this, 0, toggleIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE else 0
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.float_notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(togglePending)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        val exitIntent = Intent(this, FloatService::class.java).setAction(ACTION_STOP)
        builder.addAction(
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

    private fun initFloatView(): Boolean {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val v = inflater.inflate(R.layout.float_window, null) as FloatWindowView
        v.bindToService(this)
        floatView = v

        createSession()

        try {
            v.launchOverlay()
        } catch (e: Exception) {
            Log.e(TAG, "Overlay failed: ${e.message}")
            startActivity(
                Intent(this, OverlayPermissionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            stopSelf()
            return false
        }

        return true
    }

    private fun createSession() {
        val profile = ShellProfile()
        val defaultScript = AArchDroidApp.get().filesDir.absolutePath + "/bin/" + DefaultValues.loginShell

        val callback = object : SessionChangedCallback {
            override fun onTextChanged(session: TerminalSession) {
                floatView?.sessionClient?.onTextChanged(session)
            }
            override fun onSessionFinished(session: TerminalSession) {
                floatView?.sessionClient?.onSessionFinished(session)
                requestStopService()
            }
            override fun onTitleChanged(session: TerminalSession) {}
            override fun onClipboardText(session: TerminalSession, text: String) {
                floatView?.sessionClient?.onCopyText(text)
            }
            override fun onBell(session: TerminalSession) {
                floatView?.sessionClient?.onBell()
            }
            override fun onColorsChanged(session: TerminalSession) {
                floatView?.sessionClient?.onColorsChanged()
            }
        }

        val builder = ShellTermSession.Builder()
            .currentWorkingDirectory(NeoTermPath.HOME_PATH)
            .callback(callback)
            .systemShell(false)
            .profile(profile)

        // SELinux Enforcing blocks execvp() of app_data_file by untrusted_app.
        // Run archdroid.sh via su so it runs as root (ksu domain), matching NeoTermActivity.
        if (!profile.enableExecveWrapper && profile.loginShell == defaultScript) {
            builder.executablePath("su")
            builder.argArray(arrayOf("su", "-c", "/system/bin/sh " + profile.loginShell))
        } else {
            builder.executablePath(profile.loginShell)
        }

        session = builder.create(this)

        session?.let { s ->
            s.initializeEmulator(80, 24)
            floatView?.terminalView?.attachSession(s)
            floatView?.sessionClient?.checkFontAndColors()
        }
    }

    private fun setVisible(show: Boolean) {
        visible = show
        floatView?.setWindowVisibility(show)
        updateNotification()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }
}
