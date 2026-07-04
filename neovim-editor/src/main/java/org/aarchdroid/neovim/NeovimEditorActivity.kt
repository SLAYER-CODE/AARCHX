package org.aarchdroid.neovim

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.provider.OpenableColumns
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.msgpack.value.Value
import org.msgpack.value.ValueFactory

import org.aarchdroid.dragonterminal.bridge.Bridge
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

class NeovimEditorActivity : AppCompatActivity(), NeovimClient.Callback {

    companion object {
        private const val TAG = "NeovimEditor"
        private const val REQUEST_OPEN_FILE = 1001
    }

    private lateinit var editorView: NeovimEditorView
    private lateinit var toolbar: Toolbar
    private lateinit var posView: TextView

    private val launcher = NeovimLauncher(this)
    private val client = NeovimClient()
    private val buffer = NeovimBuffer()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val inputQueue = Channel<String>(Channel.UNLIMITED)

    private var connected = AtomicBoolean(false)
    private var currentFilePath: String? = null
    private var currentFileName: String = "untitled"
    private var fileUri: Uri? = null
    private var prevCursorRow = 0
    private var prevCursorCol = 0
    private val hlAttrs = mutableMapOf<Int, HighlightAttrs>()
    private var defaultFg: Int = NeovimColor.WHITE
    private var defaultBg: Int = 0xFF000000.toInt()
    private val rowLineMaxCol = mutableMapOf<Int, Int>()
    private var pendingFullRefresh = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.neovim_editor_activity)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.title = "Neovim"

        posView = TextView(this).apply {
            layoutParams = Toolbar.LayoutParams(
                Toolbar.LayoutParams.WRAP_CONTENT,
                Toolbar.LayoutParams.WRAP_CONTENT,
                Gravity.RIGHT
            )
            text = "1,1"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            includeFontPadding = false
        }
        toolbar.addView(posView)

        editorView = findViewById(R.id.editor_view)
        editorView.setDefaultColors(defaultFg, defaultBg)
        buffer.defaultCell = NeovimCell(foreground = defaultFg, background = defaultBg)

        findViewById<View>(R.id.key_esc).setOnClickListener {
            sendInput("<Esc>")
        }
        findViewById<View>(R.id.key_ins).setOnClickListener {
            sendInput("i")
            showKeyboard()
        }
        findViewById<View>(R.id.key_left).setOnClickListener { sendInput("<Left>") }
        findViewById<View>(R.id.key_down).setOnClickListener { sendInput("<Down>") }
        findViewById<View>(R.id.key_up).setOnClickListener { sendInput("<Up>") }
        findViewById<View>(R.id.key_right).setOnClickListener { sendInput("<Right>") }
        findViewById<View>(R.id.key_ime).setOnClickListener { toggleKeyboard() }

        client.setCallback(this)
        // Single consumer: processes keystrokes FIFO, waiting for connection+uiAttach
        scope.launch {
            for (keys in inputQueue) {
                try {
                    while (!connected.get()) delay(50)
                    client.input(keys)
                } catch (e: Exception) {
                    Log.e(TAG, "input consumer failed for \"$keys\"", e)
                }
            }
        }
        editorView.onInput = { keys ->
            Log.d(TAG, "onInput: \"${keys}\"")
            inputQueue.trySend(keys)
        }
        editorView.onResize = { rows, cols -> scope.launch { client.request("nvim_ui_try_resize", cols, rows) } }
        editorView.onModeChange = { mode -> updateToolbarTitle(mode, buffer.cursor.row, buffer.cursor.col) }
        editorView.fontChanged()

        supportActionBar?.title = "Starting Neovim..."

        scope.launch {
            val proc = launcher.launch()
            if (proc == null) {
                supportActionBar?.title = "Neovim not found!"
                Toast.makeText(this@NeovimEditorActivity, "Install nvim first", Toast.LENGTH_LONG).show()
                return@launch
            }

            val connectedOk = client.connect(proc)
            if (!connectedOk) {
                supportActionBar?.title = "Connection failed"
                return@launch
            }

            // Wait until view is laid out, then get actual size
            val (initCols, initRows) = withContext(Dispatchers.Main) {
                editorView.waitForLayout()
                editorView.getGridSize()
            }

            // Pipeline: send all setup commands immediately (msgpack pipelining)
            client.command("set laststatus=0 noshowmode noshowcmd noruler")
            client.uiAttach(initCols, initRows)
            buffer.resize(initCols, initRows)
            client.command("startinsert")

            // Discard keystrokes typed before connection was ready (would be sent in normal mode)
            while (inputQueue.tryReceive().isSuccess) { }
            // Signal consumer: socket + uiAttach are ready
            connected.set(true)
            withContext(Dispatchers.Main) {
                editorView.isReady = true
                editorView.requestKeyboard("connect")
            }

            withContext(Dispatchers.Main) {
                supportActionBar?.title = currentFileName
                editorView.fileName = currentFileName
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Run cleanup before cancelling scope
        runBlocking {
            client.uiDetach()
            client.disconnect()
            launcher.shutdown()
        }
        scope.cancel()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                showExitDialog()
                true
            }
            R.id.action_open -> {
                openFilePicker()
                true
            }
            R.id.action_new -> {
                scope.launch { client.command("enew") }
                currentFilePath = null
                currentFileName = "untitled"
                fileUri = null
                editorView.fileName = "untitled"
                updateToolbarTitle(buffer.mode.name, buffer.cursor.row, buffer.cursor.col)
                true
            }
            R.id.action_save -> {
                saveCurrentFile()
                true
            }
            R.id.action_font_increase -> {
                editorView.increaseFontSize()
                true
            }
            R.id.action_font_decrease -> {
                editorView.decreaseFontSize()
                true
            }
            R.id.action_terminal -> {
                openInTerminal()
                true
            }
            R.id.action_reconnect -> {
                scope.launch { reconnect() }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.neovim_menu, menu)
        return true
    }

    override fun onBackPressed() {
        showExitDialog()
    }

    override fun onConnected() {
        Log.d(TAG, "onConnected called")
        pendingFullRefresh = false
        rowLineMaxCol.clear()
    }

    override fun onDisconnected() {
        connected.set(false)
        scope.launch(Dispatchers.Main) {
            supportActionBar?.title = "Disconnected"
            editorView.isReady = false
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (editorView.keyboardActive && connected.get()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window?.insetsController?.show(WindowInsets.Type.ime())
            }
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(editorView, android.view.inputmethod.InputMethodManager.SHOW_FORCED)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d(TAG, "onWindowFocusChanged hasFocus=$hasFocus connected=${connected.get()}")
    }

    override fun onRedraw(updates: List<NeovimClient.RedrawEvent>) {
        var hasFlush = false
        val names = updates.map { "${it.name}(${it.args.size})" }
        Log.d(TAG, "onRedraw events=${updates.size}: $names")
        for (update in updates) {
            try {
                if (update.name == "grid_resize" || update.name == "grid_clear") pendingFullRefresh = true
                if (update.name == "flush") hasFlush = true
                processRedrawEvent(update)
            } catch (e: Exception) {
                Log.e(TAG, "Redraw error processing ${update.name}", e)
            }
        }
        // Deferred tilde fill: after grid_resize/grid_clear, fill rows not covered
        // by grid_line with ~. rowLineMaxCol is NOT cleared per-batch (only on
        // grid_resize/grid_clear), so grid_line events from prior batches are
        // reflected when flush arrives.
        if (pendingFullRefresh && hasFlush && rowLineMaxCol.isNotEmpty()) {
            pendingFullRefresh = false
            val tilde = NeovimCell(char = '~', foreground = defaultFg, background = defaultBg)
            for (r in 0 until buffer.gridHeight) {
                if (r !in rowLineMaxCol) {
                    for (c in 0 until buffer.gridWidth) {
                        buffer.setCell(r, c, tilde)
                    }
                }
            }
        }
        // Log first row cells for debugging
        val firstRowChars = (0 until minOf(buffer.gridWidth, 42)).map { c ->
            buffer.getCell(0, c)?.char ?: '?'
        }.joinToString("")
        Log.d(TAG, "row0 chars: \"$firstRowChars\" rowsInRowLineMaxCol=${rowLineMaxCol.size}")
        // Track cursor for next redraw
        prevCursorRow = buffer.cursor.row
        prevCursorCol = buffer.cursor.col
        val snapshot = buffer.copySnapshot()
        val modeName = snapshot.mode.name
        val cursorRow = snapshot.cursor.row
        val cursorCol = snapshot.cursor.col
        scope.launch(Dispatchers.Main) {
            editorView.updateBuffer(snapshot)
            updateToolbarTitle(modeName, cursorRow, cursorCol)
        }
    }

    override fun onError(error: String) {
        scope.launch(Dispatchers.Main) {
            supportActionBar?.title = "Error: $error"
            Toast.makeText(this@NeovimEditorActivity, error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun processRedrawEvent(event: NeovimClient.RedrawEvent) {
        when (event.name) {
            "grid_resize" -> {
                var w = -1; var h = -1
                if (event.args.size >= 3 && event.args[0].size == 1) {
                    // Positional: ["grid_resize", grid, width, height] → per-arg [[grid], [width], [height]]
                    w = event.args[1][0].asIntegerValue().toInt()
                    h = event.args[2][0].asIntegerValue().toInt()
                } else if (event.args.size >= 1 && event.args[0].size >= 3) {
                    // Array: ["grid_resize", [grid, width, height]] → per-arg [[grid, width, height]]
                    w = event.args[0][1].asIntegerValue().toInt()
                    h = event.args[0][2].asIntegerValue().toInt()
                }
                if (w >= 0 && h >= 0) {
                    Log.d(TAG, "grid_resize $w x $h")
                    rowLineMaxCol.clear()
                    buffer.resize(w, h)
                } else {
                    Log.w(TAG, "grid_resize unexpected args: ${event.args}")
                }
            }
            "grid_line" -> {
                var totalCells = 0
                var segmentInfo = mutableListOf<String>()
                for (arg in event.args) {
                    if (arg.size >= 4) {
                        val grid = arg[0].asIntegerValue().toInt()
                        val row = arg[1].asIntegerValue().toInt()
                        val colStart = arg[2].asIntegerValue().toInt()
                        val cellsData = arg[3].asArrayValue().list()
                        var segCells = 0
                        val chars = StringBuilder()

                        var col = colStart
                        var i = 0
                        while (i < cellsData.size) {
                            val cellVal = cellsData[i]
                            if (cellVal.isStringValue) {
                                val text = cellVal.asStringValue().asString()
                                for (ch in text) {
                                    if (col < buffer.gridWidth) {
                                        buffer.setCell(row, col, NeovimCell(char = ch, foreground = defaultFg, background = defaultBg))
                                        totalCells++
                                        segCells++
                                        chars.append(ch)
                                    }
                                    col++
                                }
                                i++
                            } else if (cellVal.isArrayValue) {
                                val cellArr = cellVal.asArrayValue().list()
                                val text = cellArr[0].asStringValue().asString()
                                val hlId = if (cellArr.size > 1 && cellArr[1].isIntegerValue) cellArr[1].asIntegerValue().toInt() else -1
                                val repeat = if (cellArr.size > 2 && cellArr[2].isIntegerValue) cellArr[2].asIntegerValue().toInt() else 1
                                val attrs = if (hlId >= 0) hlAttrs[hlId] else null
                                val fg = attrs?.foreground?.takeIf { it >= 0 } ?: defaultFg
                                val bg = attrs?.background?.takeIf { it >= 0 } ?: defaultBg
                                val cell = NeovimCell(
                                    char = text[0],
                                    foreground = fg,
                                    background = bg,
                                    bold = attrs?.bold ?: false,
                                    italic = attrs?.italic ?: false,
                                    underline = attrs?.underline ?: false
                                )
                                if (repeat == 0) {
                                    Log.v(TAG, "grid_line: repeat=0 at row=$row col=$col")
                                }
                                for (k in 0 until repeat) {
                                    if (col < buffer.gridWidth) {
                                        buffer.setCell(row, col, cell)
                                        totalCells++
                                        segCells++
                                        chars.append(text[0])
                                    }
                                    col++
                                }
                                i++
                            } else {
                                i++
                            }
                        }
                        val display = chars.toString().replace(' ', '·')
                        val capped = if (display.length > 40) display.take(40) + "…" else display
                        segmentInfo.add("g${grid}r${row}c${colStart}[$segCells:\"$capped\"]")
                        if (col > (rowLineMaxCol[row] ?: -1)) rowLineMaxCol[row] = col
                        else if (row !in rowLineMaxCol) rowLineMaxCol[row] = colStart
                    } else if (arg.size >= 3) {
                        val grid = arg[0].asIntegerValue().toInt()
                        val row = arg[1].asIntegerValue().toInt()
                        val text = arg[2].asStringValue().asString()
                        val display = text.replace(' ', '·')
                        val capped = if (display.length > 40) display.take(40) + "…" else display
                        segmentInfo.add("g${grid}r${row}text[${text.length}:\"$capped\"]")
                        for ((col, ch) in text.withIndex()) {
                            if (col < buffer.gridWidth) {
                                buffer.setCell(row, col, NeovimCell(char = ch, foreground = defaultFg, background = defaultBg))
                                totalCells++
                            }
                        }
                        if (text.length > (rowLineMaxCol[row] ?: -1)) rowLineMaxCol[row] = text.length
                        else if (row !in rowLineMaxCol) rowLineMaxCol[row] = 0
                    }
                }
                Log.d(TAG, "grid_line: total=$totalCells segments=${segmentInfo.joinToString(" ")}")
            }
            "grid_cursor_goto" -> {
                var row = -1; var col = -1
                if (event.args.size >= 3 && event.args[0].size == 1) {
                    // Positional: [[grid], [row], [col]]
                    row = event.args[1][0].asIntegerValue().toInt()
                    col = event.args[2][0].asIntegerValue().toInt()
                } else if (event.args.size >= 1 && event.args[0].size >= 3) {
                    // Array: [[grid, row, col]]
                    row = event.args[0][1].asIntegerValue().toInt()
                    col = event.args[0][2].asIntegerValue().toInt()
                }
                if (row >= 0 && col >= 0) {
                    buffer.setCursor(row, col)
                    Log.d(TAG, "grid_cursor_goto: row=$row col=$col")
                }
            }
            "grid_scroll" -> {
                var grid = -1; var top = -1; var bot = -1; var left = -1; var right = -1; var rows = 0; var cols = 0
                if (event.args.size >= 6 && event.args[0].size == 1) {
                    // Positional: [[grid], [top], [bot], [left], [right], [rows], [cols]?]
                    grid = event.args[0][0].asIntegerValue().toInt()
                    top = event.args[1][0].asIntegerValue().toInt()
                    bot = event.args[2][0].asIntegerValue().toInt()
                    left = event.args[3][0].asIntegerValue().toInt()
                    right = event.args[4][0].asIntegerValue().toInt()
                    rows = event.args[5][0].asIntegerValue().toInt()
                    if (event.args.size > 6) cols = event.args[6][0].asIntegerValue().toInt()
                } else if (event.args.isNotEmpty() && event.args[0].size >= 6) {
                    // Array: [[grid, top, bot, left, right, rows, cols?]]
                    val a = event.args[0]
                    grid = a[0].asIntegerValue().toInt()
                    top = a[1].asIntegerValue().toInt()
                    bot = a[2].asIntegerValue().toInt()
                    left = a[3].asIntegerValue().toInt()
                    right = a[4].asIntegerValue().toInt()
                    rows = a[5].asIntegerValue().toInt()
                    if (a.size > 6) cols = a[6].asIntegerValue().toInt()
                }
                if (top >= 0) {
                    buffer.scroll(top, bot, left, right, rows, cols)
                    Log.d(TAG, "grid_scroll: grid=$grid top=$top bot=$bot left=$left right=$right rows=$rows cols=$cols")
                } else {
                    // Log RAW args structure
                    val dump = event.args.joinToString(" | ") { arg ->
                        arg.joinToString(",") { v ->
                            when { v.isIntegerValue -> "Int(${v.asIntegerValue().toInt()})"
                                   v.isStringValue -> "Str(${v.asStringValue().asString()})"
                                   v.isArrayValue -> "Arr(${v.asArrayValue().list().size})"
                                   else -> v.toString()
                            }
                        }
                    }
                    Log.w(TAG, "grid_scroll: UNPARSED args.size=${event.args.size} args[0].size=${event.args[0].size}: $dump")
                }
            }
            "win_viewport" -> {
                try {
                    fun Value.toDoubleValSafe(): Double {
                        return when {
                            isFloatValue -> asFloatValue().toDouble()
                            isIntegerValue -> asIntegerValue().toDouble()
                            isNilValue -> 0.0
                            else -> { Log.w(TAG, "toDoubleValSafe: unexpected ${this.valueType} = $this"); 0.0 }
                        }
                    }
                    fun Value.toIntValSafe(): Int {
                        return when {
                            isIntegerValue -> asIntegerValue().toInt()
                            isNilValue -> 0
                            else -> { Log.w(TAG, "toIntValSafe: unexpected ${this.valueType} = $this"); 0 }
                        }
                    }
                    if (event.args.size >= 5 && event.args[0].size == 1) {
                        val grid = event.args[0][0].toIntValSafe()
                        val win = event.args[1][0].toIntValSafe()
                        val topLine = event.args[2][0].toDoubleValSafe()
                        val botLine = event.args[3][0].toDoubleValSafe()
                        val curLine = event.args[4][0].toDoubleValSafe()
                        val curCol = if (event.args.size > 5) event.args[5][0].toDoubleValSafe() else 0.0
                        val scrollDelta = if (event.args.size > 6) event.args[6][0].toDoubleValSafe() else null
                        Log.d(TAG, "win_viewport: grid=$grid win=$win top=$topLine bot=$botLine cur=($curLine,$curCol) delta=$scrollDelta")
                    } else if (event.args.isNotEmpty() && event.args[0].size >= 5) {
                        val a = event.args[0]
                        val grid = a[0].toIntValSafe()
                        val win = a[1].toIntValSafe()
                        val topLine = a[2].toDoubleValSafe()
                        val botLine = a[3].toDoubleValSafe()
                        val curLine = a[4].toDoubleValSafe()
                        val curCol = if (a.size > 5) a[5].toDoubleValSafe() else 0.0
                        val scrollDelta = if (a.size > 7) a[7].toDoubleValSafe() else null
                        Log.d(TAG, "win_viewport: grid=$grid win=$win top=$topLine bot=$botLine cur=($curLine,$curCol) delta=$scrollDelta")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "win_viewport: exception ${e.message} args=${event.args}")
                }
            }
            "grid_clear" -> {
                // Clear old content. grid_line will overwrite window rows;
                // untouched rows get filled with ~ at batch end (via fullRefresh).
                rowLineMaxCol.clear()
                val empty = NeovimCell(char = ' ', foreground = defaultFg, background = defaultBg)
                for (r in 0 until buffer.gridHeight) {
                    for (c in 0 until buffer.gridWidth) {
                        buffer.setCell(r, c, empty)
                    }
                }
            }
            "flush" -> {
                // Signal to render
            }
            "set_title" -> {
                if (event.args.isNotEmpty()) {
                    val title = event.args[0][0].asStringValue().asString()
                    currentFileName = title.substringAfterLast("/").substringBeforeLast(".")
                    scope.launch(Dispatchers.Main) {
                        supportActionBar?.title = currentFileName
                        editorView.fileName = currentFileName
                    }
                }
            }
            "mode_info_set" -> {
                if (event.args.isNotEmpty() && event.args[0].size >= 2) {
                    val cursorStylesValue = event.args[0][1]
                    if (cursorStylesValue.isArrayValue) {
                        @Suppress("UNUSED_VARIABLE")
                        val cursorStyles = cursorStylesValue.asArrayValue().list()
                    }
                }
            }
            "mode_change" -> {
                if (event.args.isNotEmpty() && event.args[0].size >= 2) {
                    val modeName = event.args[0][0].asStringValue().asString()
                    Log.d(TAG, "mode_change: $modeName")
                    buffer.applyModeChange(modeName)
                } else {
                    Log.w(TAG, "mode_change: unexpected args=${event.args}")
                }
            }
            "option_set" -> {
                if (event.args.isNotEmpty() && event.args[0].size >= 2) {
                    val name = event.args[0][0].asStringValue().asString()
                    val value = event.args[0][1]
                    Log.d(TAG, "Option: $name = $value")
                }
            }
            "hl_attr_define" -> {
                if (event.args.isNotEmpty() && event.args[0].size >= 4) {
                    val id = event.args[0][0].asIntegerValue().toInt()
                    val rgbAttr = event.args[0][1]
                    if (rgbAttr.isMapValue) {
                        val map = rgbAttr.asMapValue().map()
                        val fg = map[ValueFactory.newString("foreground")]?.let {
                            if (it.isIntegerValue) it.asIntegerValue().toInt() else -1
                        } ?: -1
                        val bg = map[ValueFactory.newString("background")]?.let {
                            if (it.isIntegerValue) it.asIntegerValue().toInt() else -1
                        } ?: -1
                        val bold = map[ValueFactory.newString("bold")]?.let {
                            it.isBooleanValue && it.asBooleanValue().boolean
                        } ?: false
                        val italic = map[ValueFactory.newString("italic")]?.let {
                            it.isBooleanValue && it.asBooleanValue().boolean
                        } ?: false
                        val underline = map[ValueFactory.newString("underline")]?.let {
                            it.isBooleanValue && it.asBooleanValue().boolean
                        } ?: false
                        val reverse = map[ValueFactory.newString("reverse")]?.let {
                            it.isBooleanValue && it.asBooleanValue().boolean
                        } ?: false
                        hlAttrs[id] = HighlightAttrs(
                            foreground = if (fg >= 0) NeovimColor.from24Bit(fg) else -1,
                            background = if (bg >= 0) NeovimColor.from24Bit(bg) else -1,
                            bold = bold, italic = italic, underline = underline, reverse = reverse
                        )
                    }
                }
            }
            "default_colors_set" -> {
                if (event.args.isNotEmpty() && event.args[0].size >= 3) {
                    val fg = event.args[0][0].asIntegerValue().toInt()
                    val bg = event.args[0][1].asIntegerValue().toInt()
                    defaultFg = NeovimColor.from24Bit(fg)
                    defaultBg = NeovimColor.from24Bit(bg)
                    buffer.defaultCell = NeovimCell(foreground = defaultFg, background = defaultBg)
                    editorView.setDefaultColors(defaultFg, defaultBg)
                }
            }
        }
    }

    private fun sendInput(keys: String) {
        Log.d(TAG, "extraKey: \"$keys\"")
        editorView.onInput?.invoke(keys)
    }

    private fun toggleKeyboard() {
        if (editorView.keyboardActive) {
            editorView.notifyKeyboardClosed()
            hideKeyboard()
        } else {
            editorView.keyboardActive = true
            editorView.requestKeyboard("toggle")
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(editorView.windowToken, 0)
    }

    private fun showKeyboard() {
        editorView.requestKeyboard("extrakey")
    }

    private var lastTitle = ""
    private var lastPos = ""
    private fun updateToolbarTitle(modeName: String, cursorRow: Int, cursorCol: Int) {
        val mode = modeName.uppercase().take(4)
        val line = cursorRow + 1
        val col = cursorCol + 1
        val title = "$mode  $currentFileName"
        val pos = "$line,$col"
        if (title != lastTitle) { supportActionBar?.title = title; lastTitle = title }
        if (pos != lastPos) { posView.text = pos; lastPos = pos }
    }

    private fun openFilePicker() {
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(editorView.windowToken, 0)
        editorView.keyboardActive = false
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQUEST_OPEN_FILE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OPEN_FILE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val content = readFileContent(uri)
                if (content != null) {
                    fileUri = uri
                    currentFilePath = uri.toString()
                    currentFileName = getFileName(uri)
                    editorView.fileName = currentFileName

                    scope.launch {
                        Log.d(TAG, "openFile: ${currentFileName} size=${content.length}")
                        // Read current view size on Main thread, then take max of
                        // maxGrid (historical max) and current grid (for safety).
                        val (curCols, curRows) = withContext(Dispatchers.Main) {
                            if (editorView.maxGridWidth <= 0 || editorView.maxGridHeight <= 0) {
                                editorView.waitForLayout()
                            }
                            editorView.getGridSize()
                        }
                        val fullCols = maxOf(editorView.maxGridWidth.coerceAtLeast(20), curCols)
                        val fullRows = maxOf(editorView.maxGridHeight.coerceAtLeast(8), curRows)
                        Log.d(TAG, "openFile: grid=${fullCols}x${fullRows} (cur=${curCols}x${curRows} max=${editorView.maxGridWidth}x${editorView.maxGridHeight})")
                        // Split content BEFORE neovim commands (parallel CPU work)
                        val lines = withContext(Dispatchers.Default) { content.split('\n') }
                        Log.d(TAG, "openFile: lines=${lines.size}")
                        // Notify (fire-and-forget) for resize and enew — saves 2 round-trips.
                        // Neovim processes them in order, then nvim_buf_set_lines fills content.
                        client.notify("nvim_ui_try_resize", fullCols, fullRows)
                        client.notify("nvim_command", "enew!")
                        client.request("nvim_buf_set_lines", 0, 0, -1, true, lines)
                        client.notify("nvim_command", "file " + escapeVimPath(currentFileName))
                        client.input("<Esc>gg")
                        // Force full redraw to ensure all rows are populated
                        client.notify("nvim_command", "redraw!")
                        Log.d(TAG, "openFile: done")
                    }
                    Toast.makeText(this, "Opened: $currentFileName", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun escapeVimPath(path: String): String {
        return "'" + path.replace("'", "'\"'\"'") + "'"
    }

    private fun getFileName(uri: Uri): String {
        var name = "untitled"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx) ?: "untitled"
            }
        }
        return name
    }

    private fun readFileContent(uri: Uri): String? {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input)).readText()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Read error: ${e.message}")
            null
        }
    }

    private fun saveCurrentFile() {
        scope.launch {
            // Get content from neovim
            // Since we can't easily get the buffer content via RPC without a full eval,
            // we'll use a simple approach: prompt to save via :w in neovim
            client.command("w")
            withContext(Dispatchers.Main) {
                Toast.makeText(this@NeovimEditorActivity, "Saved (if file was named)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openInTerminal() {
        val intent = Bridge.createExecuteIntent("nvim")
        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        startActivity(intent)
    }

    private fun showExitDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exit?")
            .setMessage("Close Neovim editor?")
            .setPositiveButton("Exit") { _, _ ->
                scope.launch {
                    client.command("qa!")
                    delay(200)
                    finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private suspend fun reconnect() {
        withContext(Dispatchers.IO) { client.disconnect() }
        launcher.shutdown()
        delay(500)
        val proc = launcher.launch()
        if (proc != null) {
            val ok = client.connect(proc)
            if (ok) {
                val (initCols, initRows) = withContext(Dispatchers.Main) { editorView.getGridSize() }
                client.command("set laststatus=0 noshowmode noshowcmd noruler")
                client.uiAttach(initCols, initRows)
                client.command("startinsert")
                while (inputQueue.tryReceive().isSuccess) { }
                connected.set(true)
                withContext(Dispatchers.Main) { editorView.isReady = true }
                withContext(Dispatchers.Main) {
                    supportActionBar?.title = currentFileName
                    editorView.fileName = currentFileName
                }
            }
        }
    }
}
