package org.aarchdroid.dragonterminal.utils

import android.content.Intent
import org.aarchdroid.AArchDroidApp
import org.aarchdroid.dragonterminal.ui.crash.CrashActivity

/**
 * @author kiva
 */
object CrashHandler : Thread.UncaughtExceptionHandler {
    private lateinit var defaultHandler: Thread.UncaughtExceptionHandler

    fun init() {
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(t: Thread?, e: Throwable?) {
        e?.printStackTrace()

        val intent = Intent(AArchDroidApp.get(), CrashActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("exception", e)
        AArchDroidApp.get().startActivity(intent)
        defaultHandler.uncaughtException(t, e)
    }
}
