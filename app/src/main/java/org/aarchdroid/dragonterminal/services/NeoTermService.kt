package org.aarchdroid.dragonterminal.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.preference.PreferenceManager
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.view.View
import android.util.Log
import android.widget.Button
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.aarchdroid.R
import org.aarchdroid.dragonterminal.backend.EmulatorDebug
import org.aarchdroid.dragonterminal.backend.TerminalSession
import org.aarchdroid.dragonterminal.frontend.logging.NLog
import org.aarchdroid.dragonterminal.frontend.session.shell.client.event.KillTerminalEvent
import org.greenrobot.eventbus.EventBus
import org.aarchdroid.dragonterminal.frontend.session.shell.ShellParameter
import org.aarchdroid.dragonterminal.frontend.session.xorg.XParameter
import org.aarchdroid.dragonterminal.frontend.session.xorg.XSession
import org.aarchdroid.dragonterminal.ui.term.NeoTermActivity
import org.aarchdroid.dragonterminal.utils.TerminalUtils
import java.io.File
import java.util.Collections


/**
 * @author kiva
 */

class NeoTermService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {
    inner class NeoTermBinder : Binder() {
        var service = this@NeoTermService
    }

    private val serviceBinder = NeoTermBinder()
    private val mTerminalSessions = Collections.synchronizedList(ArrayList<TerminalSession>())
    private val mXSessions = Collections.synchronizedList(ArrayList<XSession>())
    private var mWakeLock: PowerManager.WakeLock? = null
    private var mWifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("AArchDroid", "NeoTermService: onCreate() — service starting")
        createNotificationChannel()
        tryStartForeground()
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(this)
        // Session creation is handled by NeoTermActivity.enterMain()
        // based on root/chroot status — no pre-creation needed.
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (key == "notif_silent") {
            updateNotification()
        }
    }

    private fun tryStartForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("AArchDroid", "NeoTermService: POST_NOTIFICATIONS not granted, starting without foreground notification")
                return
            }
        }
        try {
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
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

    override fun onUnbind(intent: Intent?): Boolean {
        val sessions = synchronized(mTerminalSessions) {
            synchronized(mXSessions) {
                mTerminalSessions.size + mXSessions.size
            }
        }
        if (sessions == 0) {
            stopSelf()
        }
        return super.onUnbind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return Service.START_STICKY
        val action = intent.action
        when (action) {
            ACTION_SERVICE_STOP -> {
                val sessions = synchronized(mTerminalSessions) {
                    ArrayList(mTerminalSessions)
                }
                for (s in sessions) {
                    s.finishIfRunning()
                    EventBus.getDefault().post(KillTerminalEvent(s.mHandle))
                }
                synchronized(mTerminalSessions) {
                    mTerminalSessions.clear()
                }
                updateNotification()
            }

            ACTION_FORCE_STOP -> {
                val sessions = synchronized(mTerminalSessions) {
                    ArrayList(mTerminalSessions)
                }
                for (s in sessions) {
                    s.finishIfRunning()
                    EventBus.getDefault().post(KillTerminalEvent(s.mHandle))
                }
                synchronized(mTerminalSessions) {
                    mTerminalSessions.clear()
                }
                stopForeground(true)
                stopSelf()
            }

            ACTION_KILL_SESSION -> {
                val handle = intent.getStringExtra("handle")
                if (handle != null) {
                    val session = synchronized(mTerminalSessions) {
                        mTerminalSessions.find { it.mHandle == handle }
                    }
                    session?.let {
                        it.finishIfRunning()
                        synchronized(mTerminalSessions) {
                            mTerminalSessions.remove(it)
                        }
                        EventBus.getDefault().post(KillTerminalEvent(it.mHandle))
                    }
                }
                updateNotification()
                checkStopSelf()
            }

            ACTION_ACQUIRE_LOCK -> acquireLock()

            ACTION_RELEASE_LOCK -> releaseLock()
        }

        return Service.START_STICKY
    }

    override fun onDestroy() {
        stopForeground(true)

        val sessionsToFinish = synchronized(mTerminalSessions) {
            ArrayList(mTerminalSessions)
        }
        for (s in sessionsToFinish)
            s.finishIfRunning()
        synchronized(mTerminalSessions) {
            mTerminalSessions.clear()
        }
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
        val indexOfRemoved: Int
        synchronized(mTerminalSessions) {
            indexOfRemoved = mTerminalSessions.indexOf(sessionToRemove)
            if (indexOfRemoved >= 0) {
                mTerminalSessions.removeAt(indexOfRemoved)
            }
        }
        if (indexOfRemoved >= 0) {
            checkStopSelf()
        }
        return indexOfRemoved
    }

    fun takeSession(sessionHandle: String): TerminalSession? {
        synchronized(mTerminalSessions) {
            val idx = mTerminalSessions.indexOfFirst { it.mHandle == sessionHandle }
            if (idx >= 0) {
                return mTerminalSessions.removeAt(idx)
            }
        }
        return null
    }

    fun addExistingSession(session: TerminalSession) {
        synchronized(mTerminalSessions) {
            mTerminalSessions.add(session)
        }
        updateNotification()
    }

    fun createXSession(activity: Activity, parameter: XParameter): XSession {
        val session = TerminalUtils.createSession(activity, parameter)
        synchronized(mXSessions) {
            mXSessions.add(session)
        }
        updateNotification()
        return session
    }

    fun removeXSession(sessionToRemove: XSession): Int {
        val indexOfRemoved: Int
        synchronized(mXSessions) {
            indexOfRemoved = mXSessions.indexOf(sessionToRemove)
            if (indexOfRemoved >= 0) {
                mXSessions.removeAt(indexOfRemoved)
            }
        }
        if (indexOfRemoved >= 0) {
            checkStopSelf()
        }
        return indexOfRemoved
    }

    private fun checkStopSelf() {
        val sessions = synchronized(mTerminalSessions) {
            synchronized(mXSessions) {
                mTerminalSessions.size + mXSessions.size
            }
        }
        if (sessions == 0) {
            // Keep notification alive with "Andrax Ejecutándose" text.
            // Service will be stopped in onUnbind() when the activity is destroyed.
            updateNotification()
        } else {
            updateNotification()
        }
    }

    private fun createOrFindSession(parameter: ShellParameter): TerminalSession {
        if (parameter.willCreateNewSession()) {
            Log.d("AArchDroid", "NeoTermService: createOrFindSession — creating new session")
            val session = TerminalUtils.createSession(this, parameter)
            Log.d("AArchDroid", "NeoTermService: session created, handle=" + session.mHandle)
            synchronized(mTerminalSessions) {
                mTerminalSessions.add(session)
            }
            return session
        }

        val sessionId = parameter.sessionId!!
        Log.d("AArchDroid", "NeoTermService: createOrFindSession — finding session by id $sessionId")

        val session = synchronized(mTerminalSessions) {
            mTerminalSessions.find { it.mHandle == sessionId.getSessionId() }
        } ?: throw IllegalArgumentException("cannot find session by given id")

        session.write(parameter.initialCommand + "\n")
        return session
    }

    fun updateNotification() {
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.notify(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        Log.d("AArchDroid", "NeoTermService: createNotification()")
        val notifyIntent = Intent(this, NeoTermActivity::class.java)
        notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(this, 0, notifyIntent, PendingIntent.FLAG_IMMUTABLE)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val silent = prefs.getBoolean("notif_silent", true)

        val channelId = if (silent) CHANNEL_ID_LOW else CHANNEL_ID_HIGH
        val priority = if (silent) Notification.PRIORITY_LOW else Notification.PRIORITY_HIGH

        val termSessions: List<TerminalSession>
        val xSessionsCount: Int
        synchronized(mTerminalSessions) {
            synchronized(mXSessions) {
                termSessions = ArrayList(mTerminalSessions)
                xSessionsCount = mXSessions.size
            }
        }
        val sessionCount = termSessions.size + xSessionsCount

        val builder = NotificationCompat.Builder(this, channelId)
        builder.setContentTitle("Arch")
        builder.setSmallIcon(R.drawable.ic_terminal_running)
        builder.setContentIntent(pendingIntent)
        builder.setOngoing(true)
        builder.setShowWhen(false)
        builder.color = 0xFF000000.toInt()
        builder.priority = priority

        val compactText = if (sessionCount == 0) "Esperando Terminales" else "AArchx segundo plano"
        builder.setContentText(compactText)
        builder.setStyle(NotificationCompat.BigTextStyle().bigText(compactText))

        val newTermIntent = Intent(this, NeoTermActivity::class.java).setAction(ACTION_NEW_TERMINAL)
        newTermIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val newTermPi = PendingIntent.getActivity(this, 98, newTermIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        if (Build.VERSION.SDK_INT >= 24) {
            val compact = RemoteViews(packageName, R.layout.notification_compact)
            compact.setTextViewText(R.id.compact_text, compactText)

            val forceStopIntent = Intent(this, NeoTermService::class.java).setAction(ACTION_FORCE_STOP)
            val forceStopPi = PendingIntent.getService(this, 99, forceStopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

            if (sessionCount == 0) {
                compact.setViewVisibility(R.id.compact_new, View.VISIBLE)
                compact.setOnClickPendingIntent(R.id.compact_new, newTermPi)
            } else {
                compact.setViewVisibility(R.id.compact_new, View.GONE)
            }
            compact.setOnClickPendingIntent(R.id.compact_kill, forceStopPi)

            val hidePi = PendingIntent.getActivity(this, 1, notifyIntent, PendingIntent.FLAG_IMMUTABLE)
            compact.setOnClickPendingIntent(R.id.compact_hide, hidePi)

            builder.setCustomContentView(compact)

            if (sessionCount > 0) {
                // Big (expanded) — session rows + [Kill All] [Ocultar]
                val big = RemoteViews(packageName, R.layout.notification_terminals)

                val killAllIntent = Intent(this, NeoTermService::class.java).setAction(ACTION_SERVICE_STOP)
                val killAllPi = PendingIntent.getService(this, 0, killAllIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                big.setOnClickPendingIntent(R.id.big_kill_all, killAllPi)

                val bigHidePi = PendingIntent.getActivity(this, 1, notifyIntent, PendingIntent.FLAG_IMMUTABLE)
                big.setOnClickPendingIntent(R.id.big_hide, bigHidePi)

                val rowIds = intArrayOf(R.id.row_0, R.id.row_1, R.id.row_2, R.id.row_3)
                val titleIds = intArrayOf(R.id.title_0, R.id.title_1, R.id.title_2, R.id.title_3)
                val killIds = intArrayOf(R.id.kill_0, R.id.kill_1, R.id.kill_2, R.id.kill_3)

                for (i in rowIds.indices) {
                    if (i < termSessions.size) {
                        val session = termSessions[i]
                        big.setViewVisibility(rowIds[i], View.VISIBLE)
                        big.setTextViewText(titleIds[i], session.title ?: "Terminal")
                        val killSessionIntent = Intent(this, NeoTermService::class.java)
                            .setAction(ACTION_KILL_SESSION)
                            .putExtra("handle", session.mHandle)
                        val pi = PendingIntent.getService(this, i + 100, killSessionIntent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                        big.setOnClickPendingIntent(killIds[i], pi)
                    } else {
                        big.setViewVisibility(rowIds[i], View.GONE)
                    }
                }

                builder.setCustomBigContentView(big)
            } else {
                // No sessions — expanded shows same as compact
                val big = RemoteViews(packageName, R.layout.notification_compact)
                big.setTextViewText(R.id.compact_text, compactText)
                big.setOnClickPendingIntent(R.id.compact_new, newTermPi)
                big.setOnClickPendingIntent(R.id.compact_kill, forceStopPi)
                big.setOnClickPendingIntent(R.id.compact_hide, hidePi)
                builder.setCustomBigContentView(big)
            }
            builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
        } else {
            // Fallback for old API — addAction buttons
            if (sessionCount > 0) {
                val inbox = NotificationCompat.InboxStyle()
                inbox.setBigContentTitle("Arch - $sessionCount sesión(es)")
                for (session in termSessions) {
                    inbox.addLine("• ${session.title ?: "Terminal"}")
                }
                if (xSessionsCount > 0) {
                    inbox.addLine("• X ($xSessionsCount)")
                }
                builder.setStyle(inbox)

                for ((i, session) in termSessions.withIndex()) {
                    val title = session.title ?: "Terminal ${i + 1}"
                    val killSessionIntent = Intent(this, NeoTermService::class.java)
                        .setAction(ACTION_KILL_SESSION)
                        .putExtra("handle", session.mHandle)
                    builder.addAction(android.R.drawable.ic_delete,
                        getString(R.string.kill) + " $title",
                        PendingIntent.getService(this, i + 100, killSessionIntent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                }
            }

            builder.addAction(0, "New", newTermPi)

            val forceStopIntent = Intent(this, NeoTermService::class.java).setAction(ACTION_FORCE_STOP)
            builder.addAction(android.R.drawable.ic_delete, getString(R.string.kill),
                PendingIntent.getService(this, 99, forceStopIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))

            val hidePi = PendingIntent.getActivity(this, 1, notifyIntent, PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, getString(R.string.hide), hidePi)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val high = NotificationChannel(CHANNEL_ID_HIGH, "Arch", NotificationManager.IMPORTANCE_HIGH)
        high.description = "Notificaciones de Arch con sonido"
        manager.createNotificationChannel(high)
        val low = NotificationChannel(CHANNEL_ID_LOW, "Arch (silenciosa)", NotificationManager.IMPORTANCE_LOW)
        low.description = "Notificaciones de Arch silenciosas"
        low.setSound(null, null)
        low.enableVibration(false)
        manager.createNotificationChannel(low)
    }

    @Synchronized
    @SuppressLint("WakelockTimeout")
    private fun acquireLock() {
        if (mWakeLock != null) return

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                EmulatorDebug.LOG_TAG + ":")
        mWakeLock!!.acquire()

        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, EmulatorDebug.LOG_TAG)
        mWifiLock!!.acquire()

        updateNotification()
    }

    @Synchronized
    private fun releaseLock() {
        mWakeLock?.let {
            it.release()
            mWakeLock = null
        }
        mWifiLock?.let {
            it.release()
            mWifiLock = null
        }
        updateNotification()
    }

    companion object {
        val ACTION_SERVICE_STOP = "neoterm.action.service.stop"
        val ACTION_FORCE_STOP = "neoterm.action.service.force.stop"
        val ACTION_KILL_SESSION = "neoterm.action.service.kill.session"
        val ACTION_NEW_TERMINAL = "neoterm.action.new.terminal"
        val ACTION_ACQUIRE_LOCK = "neoterm.action.service.lock.acquire"
        val ACTION_RELEASE_LOCK = "neoterm.action.service.lock.release"
        private val NOTIFICATION_ID = 52019

        val CHANNEL_ID_HIGH = "neoterm_channel_high"
        val CHANNEL_ID_LOW = "neoterm_channel_low"
        val DEFAULT_CHANNEL_ID = CHANNEL_ID_HIGH
    }
}
