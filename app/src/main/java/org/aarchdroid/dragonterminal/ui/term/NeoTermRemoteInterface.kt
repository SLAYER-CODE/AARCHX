package org.aarchdroid.dragonterminal.ui.term

import android.app.Activity
import android.util.Log
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.widget.ArrayAdapter
import android.widget.ListView
import org.aarchdroid.AArchDroidApp
import org.aarchdroid.R
import org.aarchdroid.dragonterminal.bridge.Bridge.*
import org.aarchdroid.dragonterminal.bridge.SessionId
import org.aarchdroid.dragonterminal.component.userscript.UserScript
import org.aarchdroid.dragonterminal.component.userscript.UserScriptComponent
import org.aarchdroid.dragonterminal.data.CommandInterceptor
import org.aarchdroid.dragonterminal.data.SessionHistory
import org.aarchdroid.dragonterminal.frontend.component.ComponentManager
import org.aarchdroid.dragonterminal.frontend.config.NeoPreference
import org.aarchdroid.dragonterminal.frontend.session.shell.ShellParameter
import org.aarchdroid.dragonterminal.frontend.session.shell.client.TermSessionCallback
import org.aarchdroid.dragonterminal.services.NeoTermService
import org.aarchdroid.dragonterminal.utils.MediaUtils
import org.aarchdroid.dragonterminal.utils.TerminalUtils
import java.io.File

/**
 * @author kiva
 */
class NeoTermRemoteInterface : AppCompatActivity(), ServiceConnection {
    private var termService: NeoTermService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val serviceIntent = Intent(this, NeoTermService::class.java)
        startService(serviceIntent)
        if (!bindService(serviceIntent, this, 0)) {
            AArchDroidApp.get().errorDialog(this, R.string.service_connection_failed, { finish() })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (termService != null) {
            if (termService!!.sessions.isEmpty()) {
                termService!!.stopSelf()
            }
            termService = null
            unbindService(this)
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        if (termService != null) {
            finish()
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        termService = (service as NeoTermService.NeoTermBinder).service
        if (termService == null) {
            finish()
            return
        }

        handleIntent()
    }

    private fun handleIntent() {
        val className = intent.component?.className?.substringAfterLast('.') ?: "Normal"
        when (className) {
            "TermHere" -> handleTermHere()
            "UserScript" -> handleUserScript()
            else -> handleNormal()
        }
    }

    private fun handleNormal() {
        when (intent.action) {
            ACTION_EXECUTE -> {
                if (!intent.hasExtra(EXTRA_COMMAND)) {
                    AArchDroidApp.get().errorDialog(this, R.string.no_command_extra)
                    { finish() }
                    return
                }
                val command = intent.getStringExtra(EXTRA_COMMAND)
                val foreground = intent.getBooleanExtra(EXTRA_FOREGROUND, true)
                val session = intent.getStringExtra(EXTRA_SESSION_ID)
                val iconResId = intent.getIntExtra(EXTRA_ICON_RES_ID, 0)
                val toolKey = intent.getStringExtra("tool_key") ?: ""

                openTerm(command, SessionId.of(session), foreground, iconResId, toolKey)
            }

            else -> openTerm(null, null)
        }
        finish()
    }

    private fun handleTermHere() {
        if (intent.hasExtra(Intent.EXTRA_STREAM)) {
            val extra = intent.extras?.get(Intent.EXTRA_STREAM)
            if (extra is Uri) {
                val path = MediaUtils.getPath(this, extra)
                val file = File(path)
                val dirPath = if (file.isDirectory) path else file.parent
                val command = "cd " + TerminalUtils.escapeString(dirPath)
                openTerm(command, null)
            }
            finish()
        } else {
            AArchDroidApp.get().errorDialog(this,
                    getString(R.string.unsupported_term_here, intent?.toString())) {
                finish()
            }
        }
    }

    private fun handleUserScript() {
        val filesToHandle = mutableListOf<String>()
        val userScriptService = ComponentManager.getComponent<UserScriptComponent>()
        val userScripts = userScriptService.userScripts
        if (userScripts.isEmpty()) {
            AArchDroidApp.get().errorDialog(this, R.string.no_user_script_found, { finish() })
            return
        }

        if (intent.hasExtra(Intent.EXTRA_STREAM)) {
            // action send
            val extra = intent.extras?.get(Intent.EXTRA_STREAM)

            when (extra) {
                is ArrayList<*> -> {
                    extra.takeWhile { it is Uri }
                            .mapTo(filesToHandle) {
                                val uri = it as Uri
                                File(MediaUtils.getPath(this, uri)).absolutePath
                            }
                }
                is Uri -> {
                    filesToHandle.add(File(MediaUtils.getPath(this, extra)).absolutePath)
                }
            }
        } else if (intent.data != null) {
            // action view
            filesToHandle.add(File(intent.data!!.path).absolutePath)
        }

        if (filesToHandle.isNotEmpty()) {
            setupUserScriptView(filesToHandle, userScripts)
        } else {
            AArchDroidApp.get().errorDialog(this,
                    getString(R.string.no_files_selected, intent?.toString())
            ) { finish() }
        }
    }

    private fun setupUserScriptView(filesToHandle: MutableList<String>, userScripts: List<UserScript>) {
        setContentView(R.layout.ui_user_script_list)
        val filesList = findViewById<ListView>(R.id.user_script_file_list)
        val filesAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, filesToHandle)
        filesList.adapter = filesAdapter
        filesList.setOnItemClickListener { _, _, position, _ ->
            AlertDialog.Builder(this@NeoTermRemoteInterface)
                    .setMessage(R.string.confirm_remove_file_from_list)
                    .setPositiveButton(android.R.string.yes) { _, _ ->
                        filesToHandle.removeAt(position)
                        filesAdapter.notifyDataSetChanged()
                    }
                    .setNegativeButton(android.R.string.no, null)
                    .show()
        }

        val scriptsList = findViewById<ListView>(R.id.user_script_script_list)
        val scriptsListItem = mutableListOf<String>()
        userScripts.mapTo(scriptsListItem, { it.scriptFile.nameWithoutExtension })

        val scriptsAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, scriptsListItem)
        scriptsList.adapter = scriptsAdapter

        scriptsList.setOnItemClickListener { _, _, position, _ ->
            val userScript = userScripts[position]
            val userScriptPath = userScript.scriptFile.absolutePath
            val arguments = buildUserScriptArgument(userScriptPath, filesToHandle)

            openCustomExecTerm(userScriptPath, arguments, userScript.scriptFile.parent)
            finish()
        }
    }

    private fun buildUserScriptArgument(userScriptPath: String, files: List<String>): Array<String> {
        val arguments = mutableListOf(userScriptPath)
        arguments.addAll(files)
        return arguments.toTypedArray()
    }

    private fun openTerm(parameter: ShellParameter,
                         foreground: Boolean = true,
                         iconResId: Int = 0,
                         toolName: String = "") {
        val session = termService!!.createTermSession(parameter)
        Log.d("AArchDroid", "NTRI: openTerm — session created handle=" + session.mHandle + " foreground=" + foreground + " toolName=" + toolName)

        // Delete pending marker — session was created, wrapper will run
        if (toolName.isNotEmpty()) {
            val pending = File(filesDir, "install-state/${toolName}.pending")
            if (pending.exists()) {
                pending.delete()
                Log.d("AArchDroid", "NTRI: deleted pending marker for $toolName")
            }
        }

        // Register in session history with launchSource="herramienta"
        val sessId = SessionHistory.startSession(this).id
        val type = if (toolName.isNotEmpty()) "herramienta:$toolName" else "herramienta"
        val term = SessionHistory.startTerminal(this, sessId, type, "herramienta", iconResId)
        CommandInterceptor.registerSession(session.mHandle, sessId, "herramienta")
        CommandInterceptor.setTerminalId(session.mHandle, term.id)

        val data = Intent()
        data.putExtra(EXTRA_SESSION_ID, session.mHandle)
        setResult(Activity.RESULT_OK, data)

        if (foreground) {
            // Set current session to our new one
            // In order to switch to it when entering NeoTermActivity
            NeoPreference.storeCurrentSession(session)
            Log.d("AArchDroid", "NTRI: session stored as current — handle=" + session.mHandle)

            val intent = Intent(this, NeoTermActivity::class.java)
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            Log.d("AArchDroid", "NTRI: starting NeoTermActivity with NEW_TASK|REORDER_TO_FRONT")
            startActivity(intent)
            Log.d("AArchDroid", "NTRI: startActivity returned")
        } else {
            Log.d("AArchDroid", "NTRI: foreground=false — NOT starting NeoTermActivity")
        }
    }

    private fun openTerm(initialCommand: String?,
                         sessionId: SessionId? = null,
                         foreground: Boolean = true,
                         iconResId: Int = 0,
                         toolKey: String = "") {
        val parameter = ShellParameter()
                .initialCommand(initialCommand)
                .callback(TermSessionCallback())
                .systemShell(detectSystemShell())
                .session(sessionId)

        // Same su wrapper as NeoTermActivity.addNewSessionWithProfile()
        val defaultScript = AArchDroidApp.get().filesDir.absolutePath + "/bin/archdroid.sh"
        val loginShell = NeoPreference.getLoginShellPath()
        if (!detectSystemShell() && loginShell == defaultScript) {
            parameter.executablePath("su")
            val inlineCmd = "mount -o remount,exec,suid,dev,rw /data 2>/dev/null; exec chroot /data/local/aarchdroid /bin/bash --rcfile /root/.bashrc"
            parameter.arguments(arrayOf("su", "-c", inlineCmd))
        }

        val toolName = toolKey.ifEmpty {
            initialCommand?.split(" ")?.firstOrNull()?.trim() ?: ""
        }
        openTerm(parameter, foreground, iconResId, toolName)
    }

    private fun openCustomExecTerm(executablePath: String?, arguments: Array<String>?, cwd: String?) {
        val parameter = ShellParameter()
                .executablePath(executablePath)
                .arguments(arguments)
                .currentWorkingDirectory(cwd)
                .callback(TermSessionCallback())
                .systemShell(detectSystemShell())
        val toolName = executablePath?.substringAfterLast("/")?.trim() ?: ""
        openTerm(parameter, toolName = toolName)
    }

    private fun detectSystemShell(): Boolean {
        return false
    }
}