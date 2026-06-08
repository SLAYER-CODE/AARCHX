package org.aarchdroid

import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.*
import android.os.Build
import android.widget.Toast
import android.view.Gravity
import android.annotation.SuppressLint
import org.aarchdroid.dragonterminal.backend.TerminalSession
import org.aarchdroid.dragonterminal.component.NeoInitializer
import org.aarchdroid.dragonterminal.frontend.config.NeoPreference
import org.aarchdroid.dragonterminal.ui.bonus.BonusActivity
import org.aarchdroid.dragonterminal.utils.AssetsUtils
import java.io.*

class AArchDroidApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Global uncaught exception handler — never let the app die silently
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = java.io.StringWriter()
            val pw = java.io.PrintWriter(sw)
            throwable.printStackTrace(pw)
            val stack = sw.toString()
            android.util.Log.e("AArchDroid", "UNCAUGHT EXCEPTION on thread ${thread.name}:\n$stack")
            try {
                val crashLog = File("${filesDir.absolutePath}/crash.log")
                crashLog.writeText("=== CRASH at ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())} ===\n$stack\n")
            } catch (_: Exception) {}
        }

        app = this
        NeoPreference.init(this)
        NeoInitializer.init(this)

        val sharedPref = this.getSharedPreferences(this.packageName, Context.MODE_PRIVATE)
        val firstRun = sharedPref.getBoolean("aarchdroid_setup_done", false)

        if (!firstRun) {
            // Clean old bin/scripts
            run("rm -rf ${filesDir.absolutePath}/bin")
            run("rm -rf ${filesDir.absolutePath}/scripts")

            // Extract our scripts
            val scriptsDir = File("${filesDir.absolutePath}/scripts")
            scriptsDir.mkdirs()
            val binDir = File("${filesDir.absolutePath}/bin")
            binDir.mkdirs()

            AssetsUtils.extractAssetsDir(this, "all/scripts", "${filesDir.absolutePath}/scripts")
            setPermissions(scriptsDir)

            AssetsUtils.extractAssetsDir(this, "arm/static/bin", "${filesDir.absolutePath}/bin")
            setPermissions(binDir)

            // Remount /data with exec
            run("mount -o remount,exec,suid,dev,rw /data")
            run("dumpsys deviceidle whitelist +${this.packageName}")

            with(sharedPref.edit()) {
                putBoolean("aarchdroid_setup_done", true)
                commit()
            }
        }
    }

    fun isRooted(c: Context): Boolean {
        var result = false
        try {
            val process = Runtime.getRuntime().exec("su")
            val stdin = process.outputStream
            val stdout = process.inputStream
            DataOutputStream(stdin).use { os ->
                os.writeBytes("id\n")
                os.writeBytes("exit\n")
                os.flush()
            }
            var n = 0
            BufferedReader(InputStreamReader(stdout)).use { reader ->
                while (reader.readLine() != null) n++
            }
            if (n > 0) result = true
        } catch (_: IOException) { }

        if (!result) {
            android.util.Log.w("AArchDroid", "Root check failed — no root access")
        }
        return result
    }

    fun run(cmd: String) {
        try {
            val process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).use { os ->
                os.writeBytes("$cmd\n")
                os.writeBytes("exit 0\n")
                os.flush()
            }
            process.waitFor()
        } catch (_: Exception) { }
    }

    fun setPermissions(path: File?) {
        if (path == null || !path.exists()) return
        path.setReadable(true, false)
        path.setExecutable(true, false)
        path.listFiles()?.forEach { f ->
            if (f.isDirectory) setPermissions(f)
            f.setReadable(true, false)
            f.setExecutable(true, false)
        }
        run("chmod -R 755 ${path.absolutePath}")
    }

    fun checkcoreversion() {
        // version check stub
    }

    fun errorDialog(activity: android.app.Activity, messageResId: Int, onDismiss: () -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(activity)
                .setMessage(messageResId)
                .setPositiveButton(android.R.string.yes) { _, _ -> onDismiss() }
                .setOnDismissListener { onDismiss() }
                .show()
    }

    fun errorDialog(activity: android.app.Activity, message: String, onDismiss: () -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(activity)
                .setMessage(message)
                .setPositiveButton(android.R.string.yes) { _, _ -> onDismiss() }
                .setOnDismissListener { onDismiss() }
                .show()
    }

    fun openHelpLink() {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://opencode.ai"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    val dataDir: String get() = filesDir.absolutePath + "/ANDRAX"

    val loginShell: String get() = filesDir.absolutePath + "/bin/archdroid.sh"

    fun easterEgg(context: Context, message: String) {
        val happyCount = NeoPreference.loadInt(NeoPreference.KEY_HAPPY_EGG, 0) + 1
        NeoPreference.store(NeoPreference.KEY_HAPPY_EGG, happyCount)
        val trigger = NeoPreference.VALUE_HAPPY_EGG_TRIGGER
        if (happyCount == trigger / 2) {
            @SuppressLint("ShowToast")
            val toast = Toast.makeText(this, message, Toast.LENGTH_LONG)
            toast.setGravity(Gravity.CENTER, 0, 0)
            toast.show()
        } else if (happyCount > trigger) {
            NeoPreference.store(NeoPreference.KEY_HAPPY_EGG, 0)
            context.startActivity(Intent(context, BonusActivity::class.java))
        }
    }

    companion object {
        private var app: AArchDroidApp? = null
        fun get(): AArchDroidApp = app!!

        @Volatile
        var transferredSession: TerminalSession? = null
    }
}
