package org.aarchdroid.dragonterminal.backend

object OverlayButtonState {
    private var visible = false
    private var btnX = -1
    private var btnY = -1
    private val listeners = mutableListOf<(Boolean, Int, Int) -> Unit>()

    fun show(x: Int, y: Int) {
        btnX = x; btnY = y; visible = true
        notifyListeners()
    }

    fun hide() {
        btnX = -1; btnY = -1; visible = false
        notifyListeners()
    }

    fun observe(listener: (Boolean, Int, Int) -> Unit) {
        listeners.add(listener)
        listener(visible, btnX, btnY)
    }

    fun unobserve(listener: (Boolean, Int, Int) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it(visible, btnX, btnY) }
    }
}
