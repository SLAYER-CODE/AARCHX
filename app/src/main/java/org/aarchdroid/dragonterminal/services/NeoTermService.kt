package org.aarchdroid.dragonterminal.services

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import org.aarchdroid.R
import org.aarchdroid.dragonterminal.backend.EmulatorDebug
import org.aarchdroid.dragonterminal.backend.TerminalSession
import org.aarchdroid.dragonterminal.frontend.logging.NLog
import org.aarchdroid.dragonterminal.frontend.session.shell.ShellParameter
import org.aarchdroid.dragonterminal.frontend.session.xorg.XParameter
import org.aarchdroid.dragonterminal.frontend.session.xorg.XSession
import org.aarchdroid.dragonterminal.ui.term.NeoTermActivity
import org.aarchdroid.dragonterminal.utils.TerminalUtils


/**
 * @author kiva
 */

class NeoTermService : Service() {
    inner class NeoTermBinder : Binder() {
        var service = this@NeoTermService
    }

    private val serviceBinder = NeoTermBinder()
    private val mTerminalSessions = ArrayList<TerminalSession>()
    private val mXSessions = ArrayList<XSession>()
    private var mWakeLock: PowerManager.WakeLock? = null
    private var mWifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("AArchDroid", "NeoTermService: onCreate() — service starting")
        createNotificationChannel()
        try {
            startForeground(NOTIFICATION_ID, createNotification())
            Log.d("AArchDroid", "NeoTermService: startForeground succeeded")
        } catch (e: SecurityException) {
            Log.w("AArchDroid", "NeoTermService: startForeground denied (no permission) — running without notification")
        } catch (e: Exception) {
            Log.e("AArchDroid", "NeoTermService: startForeground failed — " + e.message)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return serviceBinder
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val action = intent.action
        when (action) {
            ACTION_SERVICE_STOP -> {
                for (i in mTerminalSessions.indices)
                    mTerminalSessions[i].finishIfRunning()
                stopSelf()
            }

            ACTION_ACQUIRE_LOCK -> acquireLock()

            ACTION_RELEASE_LOCK -> releaseLock()
        }

        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForeground(true)

        for (i in mTerminalSessions.indices)
            mTerminalSessions[i].finishIfRunning()
        mTerminalSessions.clear()
    }

    val sessions: List<TerminalSession>
        get() = mTerminalSessions

    val xSessions: List<XSession>
        get() = mXSessions

    fun createTermSession(parameter: ShellParameter): TerminalSession {
        val session = createOrFindSession(parameter)
        updateNotification()
        return session
    }

    fun removeTermSession(sessionToRemove: TerminalSession): Int {
        val indexOfRemoved = mTerminalSessions.indexOf(sessionToRemove)
        if (indexOfRemoved >= 0) {
            mTerminalSessions.removeAt(indexOfRemoved)
            if (mTerminalSessions.isEmpty() && mXSessions.isEmpty()) {
                Log.d("AArchDroid", "NeoTermService: no sessions left, stopping self")
                stopSelf()
            } else {
                updateNotification()
            }
        }
        return indexOfRemoved
    }

    fun createXSession(activity: Activity, parameter: XParameter): XSession {
        val session = TerminalUtils.createSession(activity, parameter)
        mXSessions.add(session)
        updateNotification()
        return session
    }

    fun removeXSession(sessionToRemove: XSession): Int {
        val indexOfRemoved = mXSessions.indexOf(sessionToRemove)
        if (indexOfRemoved >= 0) {
            mXSessions.removeAt(indexOfRemoved)
            if (mTerminalSessions.isEmpty() && mXSessions.isEmpty()) {
                Log.d("AArchDroid", "NeoTermService: no sessions left, stopping self")
                stopSelf()
            } else {
                updateNotification()
            }
        }
        return indexOfRemoved
    }

    private fun createOrFindSession(parameter: ShellParameter): TerminalSession {
        if (parameter.willCreateNewSession()) {
            Log.d("AArchDroid", "NeoTermService: createOrFindSession — creating new session")
            val session = TerminalUtils.createSession(this, parameter)
            Log.d("AArchDroid", "NeoTermService: session created, handle=" + session.mHandle)
            mTerminalSessions.add(session)
            return session
        }

        val sessionId = parameter.sessionId!!
        Log.d("AArchDroid", "NeoTermService: createOrFindSession — finding session by id $sessionId")

        val session = mTerminalSessions.find { it.mHandle == sessionId.getSessionId() }
                ?: throw IllegalArgumentException("cannot find session by given id")

        session.write(parameter.initialCommand + "\n")
        return session
    }

    private fun updateNotification() {
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.notify(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        Log.d("AArchDroid", "NeoTermService: createNotification()")
        val notifyIntent = Intent(this, NeoTermActivity::class.java)
        notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(this, 0, notifyIntent, PendingIntent.FLAG_IMMUTABLE)

        val sessionCount = mTerminalSessions.size + mXSessions.size
        val contentText = "Arch | $sessionCount sesión" + if (sessionCount != 1) "es" else ""

        val builder = NotificationCompat.Builder(this, DEFAULT_CHANNEL_ID)
        builder.setContentTitle("Arch")
        builder.setContentText(contentText)
        builder.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
        builder.setSmallIcon(R.drawable.ic_terminal_running)
        builder.setContentIntent(pendingIntent)
        builder.setOngoing(true)
        builder.setShowWhen(false)
        builder.color = 0xFF000000.toInt()
        builder.priority = Notification.PRIORITY_HIGH

        val exitIntent = Intent(this, NeoTermService::class.java).setAction(ACTION_SERVICE_STOP)
        builder.addAction(android.R.drawable.ic_delete, getString(R.string.exit),
                PendingIntent.getService(this, 0, exitIntent, PendingIntent.FLAG_IMMUTABLE))

        builder.addAction(0, getString(R.string.hide),
                PendingIntent.getActivity(this, 1, notifyIntent, PendingIntent.FLAG_IMMUTABLE))

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(DEFAULT_CHANNEL_ID, "Arch", NotificationManager.IMPORTANCE_HIGH)
        channel.description = "Notificaciones de Arch"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireLock() {
        if (mWakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    EmulatorDebug.LOG_TAG + ":")
            mWakeLock!!.acquire()

            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, EmulatorDebug.LOG_TAG)
            mWifiLock!!.acquire()

            updateNotification()
        }
    }

    private fun releaseLock() {
        if (mWakeLock != null) {
            mWakeLock!!.release()
            mWakeLock = null

            mWifiLock!!.release()
            mWifiLock = null

            updateNotification()
        }
    }

    companion object {
        val ACTION_SERVICE_STOP = "neoterm.action.service.stop"
        val ACTION_ACQUIRE_LOCK = "neoterm.action.service.lock.acquire"
        val ACTION_RELEASE_LOCK = "neoterm.action.service.lock.release"
        private val NOTIFICATION_ID = 52019

        val DEFAULT_CHANNEL_ID = "neoterm_notification_channel"
    }
}
