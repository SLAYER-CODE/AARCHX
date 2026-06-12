package org.aarchdroid.neovim

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager


class NeovimEditorView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val buffer = NeovimBuffer()
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        isAntiAlias = true
        isSubpixelText = true
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cursorPaint = Paint().apply {
        style = Paint.Style.FILL
    }
    private val underlinePaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private var cellWidth = 0f
    private var cellHeight = 0f
    private var fontSize = 14f
    private var gridOffsetX = 0f
    private var gridOffsetY = 0f

    private val cursorBlinkInterval = 530L
    private var cursorVisible = true
    private var lastCursorToggle = 0L

    var onInput: ((String) -> Unit)? = null
    var onResize: ((Int, Int) -> Unit)? = null
    var onModeChange: ((String) -> Unit)? = null
    var onPathChange: ((String) -> Unit)? = null
    var isCursorBlinkingEnabled = true

    fun getGridSize(): Pair<Int, Int> {
        val cw = maxOf(cellWidth, 1f)
        val ch = maxOf(cellHeight, 1f)
        val cols = (width / cw).toInt().coerceAtLeast(20)
        val rows = (height / ch).toInt().coerceAtLeast(8)
        return Pair(cols, rows)
    }

    private var savedLines = ""
    var statusLine: String = ""
        set(value) {
            field = value; postInvalidate()
        }

    private val blinkHandler = Handler(Looper.getMainLooper())
    private val blinkRunnable: Runnable = object : Runnable {
        override fun run() {
            cursorVisible = !cursorVisible
            postInvalidate()
            blinkHandler.postDelayed(this, cursorBlinkInterval)
        }
    }

    init {
        focusable = ViewGroup.FOCUSABLE
        isFocusableInTouchMode = true

        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                Log.v("NeovimEditorView", "ACTION_DOWN x=${event.x} y=${event.y}")
                requestFocus()
                showKeyboard("touch")
                val col = ((event.x - gridOffsetX) / cellWidth).toInt()
                val row = ((event.y - gridOffsetY) / cellHeight).toInt()
                if (col in 0 until buffer.gridWidth && row in 0 until buffer.gridHeight) {
                    onInput?.invoke("<LeftMouse><${col + 1},${row + 1}>")
                }
            }
            true
        }

        setOnLongClickListener {
            Log.v("NeovimEditorView", "onLongClick")
            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            clip?.primaryClip?.getItemAt(0)?.text?.let { text ->
                onInput?.invoke(text.toString().replace("\n", "<CR>"))
            }
            true
        }
        isLongClickable = true

    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            Log.v("NeovimEditorView", "onKeyDown keyCode=$keyCode")
            val input = keyToNeovim(keyCode, event)
            if (input.isNotEmpty()) {
                onInput?.invoke(input)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        return object : BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                val result = super.commitText(text, newCursorPosition)
                if (text.isNotEmpty()) {
                    sendText(text.toString())
                }
                return result
            }

            override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
                val result = super.setComposingText(text, newCursorPosition)
                if (text.isNotEmpty()) sendText(text.toString())
                return result
            }

            override fun finishComposingText(): Boolean {
                val result = super.finishComposingText()
                return result
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength) { onInput?.invoke("<BS>") }
                repeat(afterLength) { onInput?.invoke("<Del>") }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val input = keyToNeovim(event.keyCode, event)
                    if (input.isNotEmpty()) {
                        onInput?.invoke(input)
                    }
                }
                return true
            }
        }
    }

    private fun sendText(text: String) {
        for (ch in text) {
            when {
                ch == '\n' -> onInput?.invoke("<CR>")
                ch.code <= 31 -> {
                    val ctrl = ('a' + ch.code - 1)
                    onInput?.invoke("<C-$ctrl>")
                }
                else -> onInput?.invoke(ch.toString())
            }
        }
    }

    private var keyboardRetryCount = 0

    fun requestKeyboard(source: String) {
        Log.d("NeovimEditorView", "requestKeyboard src=$source isFocused=$isFocused hasWindowToken=$windowToken")
        keyboardRetryCount = 0
        keyboardPost(source)
    }

    private fun keyboardPost(source: String) {
        post {
            Log.v("NeovimEditorView", "  keyboardPost src=$source attempt=$keyboardRetryCount")
            if (!isFocused) {
                val f1 = requestFocusFromTouch()
                val f2 = requestFocus(android.view.View.FOCUS_DOWN)
                Log.v("NeovimEditorView", "  requestFocusFromTouch=$f1 requestFocus=$f2 isFocused=$isFocused")
            }
            showKeyboard(source, keyboardRetryCount)
            if (!isFocused && keyboardRetryCount < 3) {
                keyboardRetryCount++
                postDelayed({ keyboardPost(source) }, 250)
            }
        }
    }

    private fun showKeyboard(source: String, attempt: Int = 0) {
        Log.d("NeovimEditorView", "showKeyboard src=$source attempt=$attempt isFocused=$isFocused windowToken=$windowToken")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val wic = windowInsetsController
                Log.v("NeovimEditorView", "  wic=$wic")
                if (wic != null) {
                    wic.show(WindowInsets.Type.ime())
                    Log.d("NeovimEditorView", "  WindowInsetsController.show(ime) OK")
                }
            }
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            if (imm != null) {
                Log.d("NeovimEditorView", "  isAcceptingText=${imm.isAcceptingText} isActive=${imm.isActive(this)}")
                val result = imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                Log.d("NeovimEditorView", "  showSoftInput result=$result")
            } else {
                Log.w("NeovimEditorView", "  imm is NULL")
            }
        } catch (e: Exception) {
            Log.e("NeovimEditorView", "  showKeyboard error", e)
        }
    }

    private fun hideKeyboard() {
        Log.d("NeovimEditorView", "hideKeyboard")
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        if (imm != null && windowToken != null) {
            imm.hideSoftInputFromWindow(windowToken, 0)
        }
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        Log.d("NeovimEditorView", "onFocusChanged gainFocus=$gainFocus direction=$direction")
        if (gainFocus) {
            cursorVisible = true
            blinkHandler.postDelayed(blinkRunnable, cursorBlinkInterval)
            showKeyboard("focus")
        } else {
            blinkHandler.removeCallbacks(blinkRunnable)
            hideKeyboard()
        }
    }

    fun updateBuffer(newBuffer: NeovimBuffer) {
        if (newBuffer.cells.size != newBuffer.gridHeight ||
            (newBuffer.cells.isNotEmpty() && newBuffer.cells[0].size != newBuffer.gridWidth)) {
            Log.w("NeovimEditorView", "updateBuffer: snapshot invalid (cells=${newBuffer.cells.size}x${newBuffer.cells.firstOrNull()?.size ?: 0}, grid=${newBuffer.gridWidth}x${newBuffer.gridHeight})")
            postInvalidate()
            return
        }
        Log.v("NeovimEditorView", "updateBuffer: ${newBuffer.gridWidth}x${newBuffer.gridHeight} cursor=(${newBuffer.cursor.row},${newBuffer.cursor.col}) cells=${newBuffer.cells.size}")
        buffer.gridWidth = newBuffer.gridWidth
        buffer.gridHeight = newBuffer.gridHeight
        buffer.cells.clear()
        buffer.cells.addAll(newBuffer.cells.map { row -> row.toMutableList() })
        buffer.cursor.row = newBuffer.cursor.row
        buffer.cursor.col = newBuffer.cursor.col
        buffer.mode = newBuffer.mode.copy()
        postInvalidate()
    }

    fun setFontSize(size: Int) {
        fontSize = size.coerceIn(8, 36).toFloat()
        fontChanged()
    }

    fun increaseFontSize() { setFontSize((fontSize + 2).toInt()) }
    fun decreaseFontSize() { setFontSize((fontSize - 2).toInt()) }

    internal fun fontChanged() {
        textPaint.typeface = Typeface.MONOSPACE
        textPaint.textSize = fontSize * context.resources.displayMetrics.density
        val metrics = textPaint.fontMetrics
        cellWidth = textPaint.measureText("M")
        cellHeight = metrics.descent - metrics.ascent + 2f
        cellWidth = maxOf(cellWidth, 1f)
        cellHeight = maxOf(cellHeight, 1f)
        requestLayout()
        postInvalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (cellWidth <= 0 || cellHeight <= 0) fontChanged()
        val cols = (w / cellWidth).toInt().coerceAtLeast(20)
        val rows = (h / cellHeight).toInt().coerceAtLeast(8)
        gridOffsetX = (w - cols * cellWidth) / 2f
        gridOffsetY = 0f
        if (cols != buffer.gridWidth || rows != buffer.gridHeight) {
            onResize?.invoke(rows, cols)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBackground(canvas)

        // Draw grid — iterate cells directly, recover on mismatch
        val rows = buffer.cells.size.coerceAtMost(buffer.gridHeight)
        val cols = buffer.gridWidth
        if (rows <= 0 || cols <= 0) {
            Log.w("NeovimEditorView", "onDraw: empty cells (rows=$rows cols=$cols gridH=${buffer.gridHeight} cellCount=${buffer.cells.size})")
        }
        for (r in 0 until rows) {
            val rowCells = buffer.cells.getOrNull(r) ?: break
            val y = gridOffsetY + r * cellHeight
            val fontMetrics = textPaint.fontMetrics
            val baseline = y - fontMetrics.ascent

            for (c in 0 until cols.coerceAtMost(rowCells.size)) {
                val cell = rowCells[c]
                val x = gridOffsetX + c * cellWidth

                // Background
                bgPaint.color = cell.background
                canvas.drawRect(x, y, x + cellWidth, y + cellHeight, bgPaint)

                // Foreground text
                if (cell.char != ' ') {
                    textPaint.color = cell.foreground
                    textPaint.isFakeBoldText = cell.bold
                    textPaint.textSkewX = if (cell.italic) -0.25f else 0f
                    val charStr = cell.char.toString()
                    canvas.drawText(charStr, x + cellWidth * 0.1f, baseline, textPaint)
                    textPaint.isFakeBoldText = false
                    textPaint.textSkewX = 0f

                    if (cell.underline) {
                        underlinePaint.color = cell.foreground
                        canvas.drawRect(x, y + cellHeight - 2f, x + cellWidth, y + cellHeight - 1f, underlinePaint)
                    }
                }
            }
        }

        // Cursor
        if (cursorVisible && isFocused) {
            val cx = gridOffsetX + buffer.cursor.col * cellWidth
            val cy = gridOffsetY + buffer.cursor.row * cellHeight
            val mode = buffer.mode.name

            cursorPaint.color = when {
                mode.contains("insert") -> 0xFF00FF00.toInt()
                mode.contains("visual") -> 0xFFFF8800.toInt()
                else -> 0xFF88CCFF.toInt()
            }

            val shape = buffer.cursor.shape
            when {
                shape == "horizontal" -> canvas.drawRect(cx, cy + cellHeight * 0.8f, cx + cellWidth, cy + cellHeight, cursorPaint)
                shape == "vertical" -> canvas.drawRect(cx, cy, cx + cellWidth * 0.15f, cy + cellHeight, cursorPaint)
                else -> canvas.drawRect(cx, cy, cx + cellWidth, cy + cellHeight, cursorPaint)
            }

            // Crosshair
            if (mode == "normal") {
                cursorPaint.alpha = 20
                canvas.drawRect(0f, cy, width.toFloat(), cy + cellHeight, cursorPaint)
                canvas.drawRect(cx, 0f, cx + cellWidth, height.toFloat(), cursorPaint)
                cursorPaint.alpha = 255
            }
        }
    }

    private fun drawBackground(canvas: Canvas) {
        bgPaint.color = 0xFF1E1E1E.toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
    }

    private fun keyToNeovim(keyCode: Int, event: KeyEvent): String {
        if (keyCode == KeyEvent.KEYCODE_ENTER) return "<CR>"
        if (keyCode == KeyEvent.KEYCODE_SPACE) return " "

        // Modifier-only keys
        if (keyCode in listOf(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
                KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT,
                KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT)) return ""

        return when (keyCode) {
            KeyEvent.KEYCODE_TAB -> "<Tab>"
            KeyEvent.KEYCODE_DEL -> "<BS>"
            KeyEvent.KEYCODE_FORWARD_DEL -> "<Del>"
            KeyEvent.KEYCODE_ESCAPE -> "<Esc>"
            KeyEvent.KEYCODE_DPAD_UP -> "<Up>"
            KeyEvent.KEYCODE_DPAD_DOWN -> "<Down>"
            KeyEvent.KEYCODE_DPAD_LEFT -> "<Left>"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "<Right>"
            KeyEvent.KEYCODE_PAGE_UP -> "<PageUp>"
            KeyEvent.KEYCODE_PAGE_DOWN -> "<PageDown>"
            KeyEvent.KEYCODE_HOME -> "<Home>"
            KeyEvent.KEYCODE_MOVE_END -> "<End>"
            KeyEvent.KEYCODE_INSERT -> "<Insert>"

            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                val c = '0' + (keyCode - KeyEvent.KEYCODE_0)
                when {
                    event.isAltPressed -> "<M-$c>"
                    event.isCtrlPressed -> "<C-$c>"
                    else -> if (event.isShiftPressed) "!$c" else c.toString()
                }
            }

            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> {
                val c = 'a' + (keyCode - KeyEvent.KEYCODE_A)
                when {
                    event.isAltPressed -> "<M-$c>"
                    event.isCtrlPressed -> "<C-$c>"
                    event.isShiftPressed -> c.uppercase()
                    else -> c.toString()
                }
            }

            KeyEvent.KEYCODE_COMMA -> if (event.isShiftPressed) "<" else ","
            KeyEvent.KEYCODE_PERIOD -> if (event.isShiftPressed) ">" else "."
            KeyEvent.KEYCODE_SEMICOLON -> if (event.isShiftPressed) ":" else ";"
            KeyEvent.KEYCODE_APOSTROPHE -> if (event.isShiftPressed) "\"" else "'"
            KeyEvent.KEYCODE_SLASH -> if (event.isShiftPressed) "?" else "/"
            KeyEvent.KEYCODE_GRAVE -> if (event.isShiftPressed) "~" else "`"
            KeyEvent.KEYCODE_BACKSLASH -> if (event.isShiftPressed) "|" else "\\"
            KeyEvent.KEYCODE_MINUS -> if (event.isShiftPressed) "_" else "-"
            KeyEvent.KEYCODE_EQUALS -> if (event.isShiftPressed) "+" else "="
            KeyEvent.KEYCODE_LEFT_BRACKET -> if (event.isShiftPressed) "{" else "["
            KeyEvent.KEYCODE_RIGHT_BRACKET -> if (event.isShiftPressed) "}" else "]"

            else -> ""
        }
    }
}
