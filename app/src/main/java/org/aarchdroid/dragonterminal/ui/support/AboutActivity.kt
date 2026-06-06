package org.aarchdroid.dragonterminal.ui.support

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import org.aarchdroid.AArchDroidApp
import org.aarchdroid.R
import org.aarchdroid.dragonterminal.ui.setup.SetupActivity


/**
 * @author kiva
 */
class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ui_about)
        setSupportActionBar(findViewById(R.id.about_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        try {
            val version = packageManager.getPackageInfo(packageName, 0).versionName
            (findViewById<TextView>(R.id.app_version)).text = version
        } catch (ignored: PackageManager.NameNotFoundException) {
        }

        findViewById<View>(R.id.about_developers_view).setOnClickListener {
            AlertDialog.Builder(this)
                    .setTitle(R.string.about_developers_label)
                    .setMessage(R.string.about_developers)
                    .setPositiveButton(android.R.string.yes, null)
                    .show()
        }

        findViewById<View>(R.id.about_licenses_view).setOnClickListener {
            val sb = StringBuilder()
            sb.appendLine("ADBToolkitInstaller - GPL v3")
            sb.appendLine("Android-Terminal-Emulator - Apache 2.0")
            sb.appendLine("ChromeLikeTabSwitcher - Apache 2.0")
            sb.appendLine("Color-O-Matic - GPL v3")
            sb.appendLine("EventBus - Apache 2.0")
            sb.appendLine("RecyclerTabLayout - Apache 2.0")
            sb.appendLine("RecyclerView-FastScroll - Apache 2.0")
            sb.appendLine("Termux - GPL v3")
            AlertDialog.Builder(this)
                    .setTitle(R.string.about_libraries_label)
                    .setMessage(sb.toString())
                    .setPositiveButton(android.R.string.yes, null)
                    .show()
        }

        findViewById<View>(R.id.about_version_view).setOnClickListener {
            AArchDroidApp.get().easterEgg(this, "Emmmmmm...")
        }

        findViewById<View>(R.id.about_source_code_view).setOnClickListener {
            openUrl("https://github.com/NeoTerm/NeoTerm")
        }

        findViewById<View>(R.id.about_donate_view).setOnClickListener {
            AlertDialog.Builder(this)
                    .setTitle(R.string.support_donate_label)
                    .setMessage(R.string.support_donate_dialog_text)
                    .setPositiveButton(R.string.support_donate_alipay, { _, _ ->
                        Donation.donateByAlipay(this, "FKX025062MBLAG6E90RYBC")
                    })
                    .setNeutralButton(android.R.string.no, null)
                    .show()
        }

        findViewById<View>(R.id.about_show_help_view).setOnClickListener {
            AArchDroidApp.get().openHelpLink();
        }

        findViewById<View>(R.id.about_reset_app_view).setOnClickListener {
            AlertDialog.Builder(this)
                    .setMessage(R.string.reset_app_warning)
                    .setPositiveButton(android.R.string.yes, { _, _ ->
                        resetApp()
                    })
                    .setNegativeButton(android.R.string.no, null)
                    .show()
        }
    }

    private fun resetApp() {
        startActivity(Intent(this, SetupActivity::class.java))
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item?.itemId) {
            android.R.id.home ->
                finish()
        }
        return super.onOptionsItemSelected(item)
    }
}