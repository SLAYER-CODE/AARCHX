package org.aarchdroid.dragonterminal.utils

import android.app.Activity
import android.content.Context
import org.aarchdroid.dragonterminal.backend.TerminalSession
import org.aarchdroid.dragonterminal.component.font.FontComponent
import org.aarchdroid.dragonterminal.component.session.SessionComponent
import org.aarchdroid.dragonterminal.frontend.component.ComponentManager
import org.aarchdroid.dragonterminal.frontend.config.NeoPreference
import org.aarchdroid.dragonterminal.frontend.session.shell.ShellParameter
import org.aarchdroid.dragonterminal.frontend.session.xorg.XParameter
import org.aarchdroid.dragonterminal.frontend.session.xorg.XSession
import org.aarchdroid.dragonterminal.frontend.terminal.TerminalView
import org.aarchdroid.dragonterminal.frontend.terminal.TerminalViewClient
import org.aarchdroid.dragonterminal.frontend.terminal.extrakey.ExtraKeysView

/**
 * @author kiva
 */
object TerminalUtils {
    fun setupTerminalView(terminalView: TerminalView?, terminalViewClient: TerminalViewClient? = null) {
        terminalView?.textSize = NeoPreference.getFontSize()

        val fontComponent = ComponentManager.getComponent<FontComponent>()
        fontComponent.applyFont(terminalView, null, fontComponent.getCurrentFont())

        if (terminalViewClient != null) {
            terminalView?.setTerminalViewClient(terminalViewClient)
        }
    }

    fun setupExtraKeysView(extraKeysView: ExtraKeysView?) {
        val fontComponent = ComponentManager.getComponent<FontComponent>()
        val font = fontComponent.getCurrentFont()
        fontComponent.applyFont(null, extraKeysView, font)
    }

    fun createSession(context: Context, parameter: ShellParameter): TerminalSession {
        val sessionComponent = ComponentManager.getComponent<SessionComponent>()
        return sessionComponent.createSession(context, parameter)
    }

    fun createSession(activity: Activity, parameter: XParameter) : XSession {
        val sessionComponent = ComponentManager.getComponent<SessionComponent>()
        return sessionComponent.createSession(activity, parameter)
    }

    fun escapeString(s: String?): String {
        if (s == null) {
            return ""
        }

        val builder = StringBuilder()
        val specialChars = "\"\\$`!"
        builder.append('"')
        val length = s.length
        for (i in 0..length - 1) {
            val c = s[i]
            if (specialChars.indexOf(c) >= 0) {
                builder.append('\\')
            }
            builder.append(c)
        }
        builder.append('"')
        return builder.toString()
    }
}