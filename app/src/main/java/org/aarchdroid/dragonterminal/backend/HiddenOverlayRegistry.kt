package org.aarchdroid.dragonterminal.backend

import org.aarchdroid.dragonterminal.frontend.terminal.CanvasOverlayView

object HiddenOverlayRegistry {
    private val overlays = mutableListOf<CanvasOverlayView>()

    fun register(view: CanvasOverlayView) {
        if (!overlays.contains(view)) {
            overlays.add(view)
        }
    }

    fun unregister(view: CanvasOverlayView) {
        overlays.remove(view)
    }

    fun hasOverlays(): Boolean = overlays.isNotEmpty()

    fun getOverlays(): List<CanvasOverlayView> = overlays.toList()

    fun clear() {
        overlays.clear()
    }
}
