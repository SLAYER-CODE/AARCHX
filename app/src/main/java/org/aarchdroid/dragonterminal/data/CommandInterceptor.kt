package org.aarchdroid.dragonterminal.data

import android.util.Log
import org.aarchdroid.dragonterminal.backend.TerminalSession

object CommandInterceptor {
    private val sessionContexts = HashMap<String, SessionContext>()

    data class SessionContext(
        val sessionId: String,
        var terminalId: String = "",
        var currentDir: String = "~"
    )

    fun registerSession(mHandle: String, sessionId: String) {
        sessionContexts[mHandle] = SessionContext(sessionId = sessionId)
    }

    fun setTerminalId(mHandle: String, terminalId: String) {
        sessionContexts[mHandle]?.terminalId = terminalId
    }

    fun setCurrentDir(mHandle: String, dir: String) {
        sessionContexts[mHandle]?.currentDir = dir
    }

    fun getContext(mHandle: String): SessionContext? = sessionContexts[mHandle]

    @JvmStatic
    fun onCommand(session: TerminalSession, cmd: String) {
        val ctx = sessionContexts[session.mHandle] ?: return
        if (cmd.startsWith("cd ")) {
            val newDir = cmd.substring(3).trim()
            if (newDir.startsWith("/")) {
                ctx.currentDir = newDir
            } else if (newDir == "-") {
                ctx.currentDir = "-"
            } else if (newDir == "~" || newDir.isEmpty()) {
                ctx.currentDir = "~"
            } else {
                if (ctx.currentDir.endsWith("/")) {
                    ctx.currentDir = ctx.currentDir + newDir
                } else {
                    ctx.currentDir = ctx.currentDir + "/" + newDir
                }
            }
            // Normalize
            ctx.currentDir = normalizePath(ctx.currentDir)
        }
        Log.d("AArchDroid", "CommandInterceptor: cmd='$cmd' dir='${ctx.currentDir}'")
    }

    fun unregisterSession(mHandle: String) {
        sessionContexts.remove(mHandle)
    }

    private fun normalizePath(path: String): String {
        val parts = path.split("/").toMutableList()
        val result = mutableListOf<String>()
        for (p in parts) {
            when (p) {
                ".", "" -> {}
                ".." -> if (result.isNotEmpty()) result.removeAt(result.lastIndex)
                else -> result.add(p)
            }
        }
        if (path.startsWith("/")) return "/" + result.joinToString("/")
        return result.joinToString("/").ifEmpty { "~" }
    }
}
