package org.aarchdroid.dragonterminal.ui.term.tab

import org.aarchdroid.dragonterminal.frontend.web.AetherWebView

/**
 * Tab del navegador Aether. Empaqueta la [AetherWebView] como contenido
 * de un tab, igual que [CanvasTab] envuelve un CanvasOverlayView.
 */
class AetherTab(title: CharSequence, val webView: AetherWebView) : NeoTab(title) {

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
