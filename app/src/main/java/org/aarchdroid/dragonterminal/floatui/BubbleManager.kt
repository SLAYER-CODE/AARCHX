package org.aarchdroid.dragonterminal.floatui

import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import org.aarchdroid.dragonterminal.frontend.terminal.TerminalView
import org.aarchdroid.R

class BubbleManager(private val floatView: FloatWindowView) {

    companion object {
        private const val DEFAULT_BUBBLE_SIZE_DP = 56
    }

    private val bubbleSizePx: Int =
        (DEFAULT_BUBBLE_SIZE_DP * floatView.resources.displayMetrics.density).toInt()

    private var isMinimized = false
    private var originalWidth = 0
    private var originalHeight = 0
    private var hasCaptured = false
    private var originalTermBg: Drawable? = null
    private var originalFloatBg: Drawable? = null

    fun toggle() {
        if (isMinimized) restore()
        else minimize()
    }

    fun isMinimized(): Boolean = isMinimized

    private fun minimize() {
        captureOriginals()

        val lp = layoutParams
        lp.width = bubbleSizePx
        lp.height = bubbleSizePx

        val tv = terminalView
        val stroke = tv.resources.getDimension(R.dimen.bubble_outline_stroke_width).toInt()

        tv.outlineProvider = object : ViewOutlineProvider() {
            @Suppress("SuspiciousNameCombination")
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(stroke, stroke, view.width - stroke, view.height - stroke)
            }
        }
        tv.clipToOutline = true

        floatView.setBackgroundResource(R.drawable.round_button_with_outline)
        floatView.clipToOutline = true
        floatView.hideKeyboard()
        floatView.loseFocus()

        floatView.findViewById<View>(R.id.window_controls).visibility = View.GONE

        windowManager.updateViewLayout(floatView, lp)
        isMinimized = true
    }

    private fun restore() {
        val lp = layoutParams
        lp.width = originalWidth
        lp.height = originalHeight

        val tv = terminalView
        tv.background = originalTermBg
        tv.outlineProvider = null
        tv.clipToOutline = false

        floatView.background = originalFloatBg
        floatView.clipToOutline = false

        floatView.findViewById<View>(R.id.window_controls).visibility = View.VISIBLE

        windowManager.updateViewLayout(floatView, lp)
        isMinimized = false
        hasCaptured = false
    }

    private fun captureOriginals() {
        if (!hasCaptured) {
            val lp = layoutParams
            originalWidth = lp.width
            originalHeight = lp.height
            originalTermBg = terminalView.background
            originalFloatBg = floatView.background
            hasCaptured = true
        }
    }

    fun cleanup() {
        originalTermBg = null
        originalFloatBg = null
    }

    private val layoutParams: WindowManager.LayoutParams
        get() = floatView.layoutParams as WindowManager.LayoutParams

    private val terminalView: TerminalView
        get() = floatView.terminalView

    private val windowManager: WindowManager
        get() = floatView.windowManager!!
}
