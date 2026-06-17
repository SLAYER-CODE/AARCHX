package org.aarchdroid.dragonterminal.utils

import android.content.Context
import org.aarchdroid.dragonterminal.backend.TerminalSession
import org.aarchdroid.dragonterminal.frontend.floating.TerminalDialog

object PackageUtils {
    fun pacman(context: Context, args: Array<String>, callback: (Int, TerminalDialog) -> Unit) {
        val command = args.joinToString(" ")
        TerminalDialog(context)
                .onFinish(object : TerminalDialog.SessionFinishedCallback {
                    override fun onSessionFinished(dialog: TerminalDialog, finishedSession: TerminalSession?) {
                        val exit = finishedSession?.exitStatus ?: 1
                        callback(exit, dialog)
                    }
                })
                .imeEnabled(true)
                .execute("su", arrayOf("-M", "-c",
                        "env PACMAN_DISABLE_SANDBOX=1 chroot /data/local/aarchdroid /usr/bin/$command"))
                .show(command)
    }
}