package org.aarchdroid.dragonterminal.xorg

import android.content.Context
import android.view.View
import android.view.Window
import android.view.WindowManager

interface NeoXorgViewClient {
    fun getContext(): Context
    fun isKeyboardWithoutTextInputShown(): Boolean
    fun isPaused(): Boolean
    fun runOnUiThread(runnable: Runnable?)
    fun getGLView(): View?
    fun getWindow(): Window
    fun getWindowManager(): WindowManager
    fun showScreenKeyboardWithoutTextInputField(keyboard: Int)
    fun setScreenKeyboardHintMessage(hideMessage: String?)
    fun isScreenKeyboardShown(): Boolean
    fun showScreenKeyboard(oldText: String?)
    fun hideScreenKeyboard()
    fun updateScreenOrientation()
    fun initScreenOrientation()
    fun isRunningOnOUYA(): Boolean
    fun setSystemMousePointerVisible(visible: Int)
}
