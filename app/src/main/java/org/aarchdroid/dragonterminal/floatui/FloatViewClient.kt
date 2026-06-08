package org.aarchdroid.dragonterminal.floatui

import android.content.Context
import android.media.AudioManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import org.aarchdroid.dragonterminal.backend.KeyHandler
import org.aarchdroid.dragonterminal.backend.TerminalSession
import org.aarchdroid.dragonterminal.frontend.terminal.TerminalViewClient

class FloatViewClient(
    private val view: FloatWindowView,
    private val sessionClient: FloatSessionClient
) : TerminalViewClient {

    private var virtualCtrlDown = false
    private var virtualFnDown = false
    private var fontSize = 14

    fun initFloatView() {
        view.terminalView.isFocusable = true
        view.terminalView.isFocusableInTouchMode = true
        fontSize = org.aarchdroid.dragonterminal.frontend.config.NeoPreference.getFontSize()
        view.terminalView.setTextSize(fontSize)
    }

    override fun onSingleTapUp(e: MotionEvent) {
        view.gainFocus()
    }

    override fun onScale(scale: Float): Float {
        if (scale < 0.9f || scale > 1.1f) {
            changeFontSize(scale > 1f)
            return 1.0f
        }
        return scale
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun copyModeChanged(copyMode: Boolean) {
    }

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        if (handleVirtualKeys(keyCode, e, true)) return true

        if (e.isCtrlPressed && e.isAltPressed) {
            val unicodeChar = e.getUnicodeChar(0)
            when {
                unicodeChar == 'k'.code || unicodeChar == 'K'.code -> {
                    view.toggleKeyboard()
                }
                unicodeChar == '+'.code || e.getUnicodeChar(KeyEvent.META_SHIFT_ON) == '+'.code ->
                    changeFontSize(true)
                unicodeChar == '-'.code -> changeFontSize(false)
            }
            return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean {
        return handleVirtualKeys(keyCode, e, false)
    }

    override fun readControlKey(): Boolean = virtualCtrlDown

    override fun readAltKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        if (!virtualFnDown) return false

        var resultKeyCode = -1
        var resultCodePoint = -1
        var altDown = false
        val lower = Character.toLowerCase(codePoint)

        when (lower) {
            'w'.code -> resultKeyCode = KeyEvent.KEYCODE_DPAD_UP
            'a'.code -> resultKeyCode = KeyEvent.KEYCODE_DPAD_LEFT
            's'.code -> resultKeyCode = KeyEvent.KEYCODE_DPAD_DOWN
            'd'.code -> resultKeyCode = KeyEvent.KEYCODE_DPAD_RIGHT
            'p'.code -> resultKeyCode = KeyEvent.KEYCODE_PAGE_UP
            'n'.code -> resultKeyCode = KeyEvent.KEYCODE_PAGE_DOWN
            't'.code -> resultKeyCode = KeyEvent.KEYCODE_TAB
            'i'.code -> resultKeyCode = KeyEvent.KEYCODE_INSERT
            'h'.code -> resultCodePoint = '~'.code
            'u'.code -> resultCodePoint = '_'.code
            'l'.code -> resultCodePoint = '|'.code
            'e'.code -> resultCodePoint = 27
            in '1'.code..'9'.code -> resultKeyCode = (codePoint - '1'.code) + KeyEvent.KEYCODE_F1
            '0'.code -> resultKeyCode = KeyEvent.KEYCODE_F10
            'b'.code, 'f'.code, 'x'.code -> {
                resultCodePoint = codePoint
                altDown = true
            }
            'v'.code -> {
                resultCodePoint = -1
                val audio = view.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_SAME,
                    AudioManager.FLAG_SHOW_UI
                )
            }
        }

        if (resultKeyCode != -1) {
            val emulator = session.emulator
            session.write(
                KeyHandler.getCode(
                    resultKeyCode, 0,
                    emulator.isCursorKeysApplicationMode,
                    emulator.isKeypadApplicationMode
                )
            )
        } else if (resultCodePoint != -1) {
            session.writeCodePoint(altDown, resultCodePoint)
        }
        return true
    }

    override fun onLongPress(event: MotionEvent): Boolean {
        view.enterDragMode()
        view.getLocationOnScreen(view.location)
        view.initialX = view.location[0]
        view.initialY = view.location[1]
        view.initialTouchX = event.rawX
        view.initialTouchY = event.rawY
        return true
    }

    private fun handleVirtualKeys(keyCode: Int, event: KeyEvent, down: Boolean): Boolean {
        val device = event.device
        if (device != null && device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC) {
            return false
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                virtualCtrlDown = down
                true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                virtualFnDown = down
                true
            }
            else -> false
        }
    }

    fun changeFontSize(increase: Boolean) {
        fontSize += if (increase) 1 else -1
        fontSize = fontSize.coerceIn(8, 48)
        view.terminalView.setTextSize(fontSize)
    }
}
