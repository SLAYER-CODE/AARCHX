package org.aarchdroid.dragonterminal.ui.term

import org.aarchdroid.dragonterminal.backend.TerminalSession
import org.aarchdroid.dragonterminal.frontend.session.xorg.XSession
import org.aarchdroid.dragonterminal.services.NeoTermService
import org.aarchdroid.dragonterminal.ui.term.tab.TermTab
import org.aarchdroid.dragonterminal.ui.term.tab.XSessionTab

/**
 * @author kiva
 */
object SessionRemover {
    fun removeSession(termService: NeoTermService?, tab: TermTab) {
        tab.termData.termSession?.finishIfRunning()
        removeFinishedSession(termService, tab.termData.termSession)
        tab.cleanup()
    }

    fun removeXSession(termService: NeoTermService?, tab: XSessionTab?) {
        removeFinishedSession(termService, tab?.session)
    }

    private fun removeFinishedSession(termService: NeoTermService?, finishedSession: TerminalSession?) {
        if (termService == null || finishedSession == null) {
            return
        }

        termService.removeTermSession(finishedSession)
    }

    private fun removeFinishedSession(termService: NeoTermService?, finishedSession: XSession?) {
        if (termService == null || finishedSession == null) {
            return
        }

        termService.removeXSession(finishedSession)
    }
}
