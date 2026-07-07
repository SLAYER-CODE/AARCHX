package org.aarchdroid.dragonterminal.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import androidx.core.content.ContextCompat
import android.view.MenuItem
import org.aarchdroid.R
import org.aarchdroid.dragonterminal.component.keyboard.KeyboardModule
import org.aarchdroid.dragonterminal.frontend.config.NeoPreference
import org.aarchdroid.dragonterminal.utils.PackageUtils

/**
 * @author kiva
 */
class GeneralSettingsActivity : BasePreferenceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = getString(R.string.general_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setBackgroundDrawable(ContextCompat.getDrawable(applicationContext,R.color.blackfull))
        addPreferencesFromResource(R.xml.setting_general)

        val keyboardPref = findPreference(getString(R.string.key_keyboard_mode))
        if (keyboardPref != null) {
            val isInstalled = KeyboardModule.isHackersKeyboardInstalled(this)
            keyboardPref.isEnabled = isInstalled
            keyboardPref.summary = if (isInstalled) {
                getString(R.string.pref_keyboard_mode_enabled)
            } else {
                getString(R.string.pref_keyboard_mode_desc)
            }
        }

        /** val currentShell = NeoPreference.getLoginShellName()
        findPreference(getString(R.string.key_general_shell)).setOnPreferenceChangeListener { _, value ->
            val shellName = value.toString()
            val newShell = NeoPreference.findLoginProgram(shellName)
            if (newShell == null) {
                //requestInstallShell(shellName, currentShell)
            } else {
                postChangeShell(shellName)
            }
            return@setOnPreferenceChangeListener true
        } **/
    }

    private fun postChangeShell(shellName: String) {
        NeoPreference.setLoginShellName(shellName)
    }

    private fun requestInstallShell(shellName: String, currentShell: String) {
        AlertDialog.Builder(this)
                .setTitle(getString(R.string.shell_not_found, shellName))
                .setMessage(R.string.shell_not_found_message)
                .setPositiveButton(R.string.install, { _, _ ->
                    PackageUtils.pacman(this, arrayOf("pacman", "-S", "--needed", "--noconfirm", shellName), { exitStatus, dialog ->
                        if (exitStatus == 0) {
                            dialog.dismiss()
                            postChangeShell(shellName)
                        } else {
                            dialog.setTitle(getString(R.string.error))
                        }
                    })
                })
                .setNegativeButton(android.R.string.no, null)
                .setOnDismissListener {
                    postChangeShell(currentShell)
                }
                .show()
    }

    override fun onBuildHeaders(target: MutableList<Header>?) {
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item?.itemId) {
            android.R.id.home ->
                finish()
        }
        return super.onOptionsItemSelected(item)
    }
}