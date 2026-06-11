package org.aarchdroid.neovim

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.util.AttributeSet
import android.view.*
import android.view.inputmethod.InputMethodManager
import java.util.concurrent.CopyOnWriteArrayList

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

    private var savedLines = ""
    var statusLine: String = ""
        set(value) {
            field = value; postInvalidate()
        }

    init {
        focusable = ViewGroup.FOCUSABLE
        isFocusableInTouchMode = true
        setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                handleKey(keyCode, event)
                true
            } else false
        }

        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                requestFocus()
                val col = ((event.x - gridOffsetX) / cellWidth).toInt()
                val row = ((event.y - gridOffsetY) / cellHeight).toInt()
                if (col in 0 until buffer.gridWidth && row in 0 until buffer.gridHeight) {
                    onInput?.invoke("<LeftMouse><${col + 1},${row + 1}>")
                }
            }
            true
        }

        setOnLongClickListener {
            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            clip?.primaryClip?.getItemAt(0)?.text?.let { text ->
                onInput?.invoke(text.toString().replace("\n", "<CR>"))
            }
            true
        }
        isLongClickable = true
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        if (gainFocus) {
            imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        } else {
            imm?.hideSoftInputFromWindow(windowToken, 0)
        }
    }

    fun updateBuffer(newBuffer: NeovimBuffer) {
        buffer.gridWidth = newBuffer.gridWidth
        buffer.gridHeight = newBuffer.gridHeight
        buffer.cells.clear()
        buffer.cells.addAll(newBuffer.cells.map { row -> row.toMutableList() })
        buffer.cursor.row = newBuffer.cursor.row
        buffer.cursor.col = newBuffer.cursor.col
        buffer.mode = newBuffer.mode
        savedLines = (0 until buffer.gridHeight).joinToString("\n") { buffer.getLineText(it).trimEnd() }
        postInvalidate()
    }

    fun setFontSize(size: Int) {
        fontSize = size.coerceIn(8, 36).toFloat()
        fontChanged()
    }

    fun increaseFontSize() { setFontSize((fontSize + 2).toInt()) }
    fun decreaseFontSize() { setFontSize((fontSize - 2).toInt()) }

    internal fun fontChanged() {
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

        val now = System.currentTimeMillis()
        if (isCursorBlinkingEnabled && now - lastCursorToggle > cursorBlinkInterval) {
            cursorVisible = !cursorVisible
            lastCursorToggle = now
            if (isFocused) postInvalidateDelayed(cursorBlinkInterval)
        }

        // Draw grid
        try {
            for (r in 0 until buffer.gridHeight) {
                val y = gridOffsetY + r * cellHeight
                val fontMetrics = textPaint.fontMetrics
                val baseline = y - fontMetrics.ascent

                for (c in 0 until buffer.gridWidth) {
                    val cell = buffer.cells[r][c]
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
        } catch (_: Exception) {}

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

    private fun handleKey(keyCode: Int, event: KeyEvent) {
        val input = when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> "<CR>"
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
            KeyEvent.KEYCODE_SPACE -> " "
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> ""
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> ""
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> ""

            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                val c = '0' + (keyCode - KeyEvent.KEYCODE_0)
                val prefix = if (event.isShiftPressed) "!" else ""
                when {
                    event.isAltPressed -> "<M-$c>"
                    event.isCtrlPressed -> "<C-$c>"
                    else -> prefix + c.toString()
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

        if (input.isNotEmpty()) {
            onInput?.invoke(input)
        }
    }
}
