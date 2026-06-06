package org.aarchdroid.dragonterminal.utils

import android.content.Context
import org.aarchdroid.dragonterminal.backend.TerminalSession
import org.aarchdroid.dragonterminal.component.pm.PackageComponent
import org.aarchdroid.dragonterminal.component.pm.SourceManager
import org.aarchdroid.dragonterminal.frontend.component.ComponentManager
import org.aarchdroid.dragonterminal.frontend.config.NeoTermPath
import org.aarchdroid.dragonterminal.frontend.floating.TerminalDialog
import java.io.File

/**
 * @author kiva
 */
object PackageUtils {
    fun apt(context: Context, subCommand: String, extraArgs: Array<String>?, callback: (Int, TerminalDialog) -> Unit) {
        val argArray =
                if (extraArgs != null) arrayOf("", subCommand, *extraArgs)
                else arrayOf("", subCommand)

        TerminalDialog(context)
                .onFinish(object : TerminalDialog.SessionFinishedCallback {
                    override fun onSessionFinished(dialog: TerminalDialog, finishedSession: TerminalSession?) {
                        val exit = finishedSession?.exitStatus ?: 1
                        callback(exit, dialog)
                    }
                })
                .imeEnabled(true)
                .execute("", argArray)
                .show("apt $subCommand")
    }
}