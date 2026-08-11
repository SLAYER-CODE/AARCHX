package org.aarchdroid.dragonterminal.ui.term.tab

import org.aarchdroid.dragonterminal.frontend.web.AcWebView

/**
 * Tab del navegador (API ac). Empaqueta la [AcWebView] como contenido
 * de un tab, igual que [CanvasTab] envuelve un CanvasOverlayView.
 */
class AcTab(title: CharSequence, val webView: AcWebView) : NeoTab(title) {

    override fun onPause() {
        webView.pauseWebView()
    }

    override fun onResume() {
        webView.resumeWebView()
    }

    override fun onDestroy() {
        webView.destroyWebView()
    }
}
