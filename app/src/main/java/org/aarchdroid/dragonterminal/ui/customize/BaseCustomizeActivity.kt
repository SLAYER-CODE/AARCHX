package org.aarchdroid.dragonterminal.ui.customize

import android.annotation.SuppressLint
import android.os.Handler
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.view.MenuItem
import org.aarchdroid.AArchDroidApp
import org.aarchdroid.R
import org.aarchdroid.dragonterminal.backend.TerminalSession
import org.aarchdroid.dragonterminal.frontend.config.NeoTermPath
import org.aarchdroid.dragonterminal.frontend.session.shell.ShellParameter
import org.aarchdroid.dragonterminal.frontend.session.shell.client.BasicSessionCallback
import org.aarchdroid.dragonterminal.frontend.session.shell.client.BasicViewClient
import org.aarchdroid.dragonterminal.frontend.terminal.TerminalView
import org.aarchdroid.dragonterminal.frontend.terminal.extrakey.ExtraKeysView
import org.aarchdroid.dragonterminal.utils.TerminalUtils

/**
 * @author kiva
 */
@SuppressLint("Registered")
open class BaseCustomizeActivity : AppCompatActivity() {
    lateinit var terminalView: TerminalView
    lateinit var viewClient: BasicViewClient
    lateinit var sessionCallback: BasicSessionCallback
    lateinit var session: TerminalSession
    lateinit var extraKeysView: ExtraKeysView

    fun initCustomizationComponent(layoutId: Int) {
        setContentView(layoutId)

        val toolbar = findViewById<Toolbar>(R.id.custom_toolbar)
        setSupportActionBar(toolbar)
        toolbar.setBackgroundColor(ContextCompat.getColor(applicationContext,R.color.blackfull))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        terminalView = findViewById(R.id.terminal_view)
        extraKeysView = findViewById(R.id.custom_extra_keys)
        viewClient = BasicViewClient(terminalView)
        sessionCallback = BasicSessionCallback(terminalView)
        TerminalUtils.setupTerminalView(terminalView, viewClient)
        TerminalUtils.setupExtraKeysView(extraKeysView)

        val script = resources.getStringArray(R.array.custom_preview_script_colors)
        val parameter = ShellParameter()
                .executablePath(AArchDroidApp.get().filesDir.absolutePath + "/bin/testcolors.sh")
                .arguments(arrayOf("testcolors.sh"))
                .callback(sessionCallback)
                .systemShell(true)

        session = TerminalUtils.createSession(this, parameter)

        Handler().postDelayed({

            terminalView.attachSession(session)

        }, 1000)




    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item?.itemId) {
            android.R.id.home -> finish()
        }
        return super.onOptionsItemSelected(item)
    }
}