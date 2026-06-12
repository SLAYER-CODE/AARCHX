package org.aarchdroid.neovim

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.*
import org.aarchdroid.dragonterminal.bridge.Bridge
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

class NeovimEditorActivity : AppCompatActivity(), NeovimClient.Callback {

    companion object {
        private const val TAG = "NeovimEditor"
        private const val REQUEST_OPEN_FILE = 1001
        private const val PORT = 9999
        private const val HOST = "127.0.0.1"
    }

    private lateinit var editorView: NeovimEditorView
    private lateinit var statusLine: TextView
    private lateinit var toolbar: Toolbar

    private val launcher = NeovimLauncher(this)
    private val client = NeovimClient(HOST, PORT)
    private val buffer = NeovimBuffer()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var connected = AtomicBoolean(false)
    private var currentFilePath: String? = null
    private var currentFileName: String = "untitled"
    private var fileUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.neovim_editor_activity)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Neovim"

        editorView = findViewById(R.id.editor_view)
        statusLine = findViewById(R.id.status_line)

        client.setCallback(this)
        editorView.onInput = { keys -> scope.launch { client.input(keys) } }
        editorView.onResize = { rows, cols -> scope.launch { client.request("nvim_ui_try_resize", cols, rows) } }
        editorView.onModeChange = { mode -> updateStatusLine() }
        editorView.fontChanged()

        statusLine.text = "Starting Neovim..."

        scope.launch {
            val launched = launcher.launch()
            if (!launched) {
                statusLine.text = "Neovim not found! Install nvim first."
                return@launch
            }

            val connectedOk = client.connect()
            if (!connectedOk) {
                statusLine.text = "Connection failed"
                return@launch
            }

            connected.set(true)
            delay(100)
            client.apiInfo()
            delay(50)
            client.uiAttach(80, 28)
            // Defensive: ensure buffer matches requested size even if grid_resize is delayed
            buffer.resize(80, 28)

            // Sync terminal size with view (onSizeChanged may have fired before connect)
            val (viewCols, viewRows) = withContext(Dispatchers.Main) { editorView.getGridSize() }
            if (viewCols != 80 || viewRows != 28) {
                client.request("nvim_ui_try_resize", viewCols, viewRows)
            }

            withContext(Dispatchers.Main) {
                statusLine.text = "Connected — tap for keyboard"
                supportActionBar?.title = "Neovim"
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
                updateStatusLine()
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
        Log.d(TAG, "Connected")
    }

    override fun onDisconnected() {
        connected.set(false)
        scope.launch(Dispatchers.Main) {
            statusLine.text = "Disconnected — tap Reconnect"
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d(TAG, "onWindowFocusChanged hasFocus=$hasFocus connected=${connected.get()}")
    }

    override fun onRedraw(updates: List<NeovimClient.RedrawEvent>) {
        val names = updates.map { "${it.name}(${it.args.size})" }
        Log.d(TAG, "onRedraw events=${updates.size}: $names")
        try {
            for (update in updates) {
                processRedrawEvent(update)
            }
            val snapshot = takeBufferSnapshot()
            scope.launch(Dispatchers.Main) {
                editorView.updateBuffer(snapshot)
                updateStatusLine()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Redraw error", e)
        }
    }

    private fun takeBufferSnapshot(): NeovimBuffer {
        return buffer.copySnapshot()
    }

    override fun onError(error: String) {
        scope.launch(Dispatchers.Main) {
            statusLine.text = "Error: $error"
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
                    buffer.resize(w, h)
                } else {
                    Log.w(TAG, "grid_resize unexpected args: ${event.args}")
                }
            }
            "grid_line" -> {
                var totalCells = 0
                for (arg in event.args) {
                    if (arg.size >= 4) {
                        val row = arg[1].asIntegerValue().toInt()
                        val colStart = arg[2].asIntegerValue().toInt()
                        val cellsData = arg[3].asArrayValue().list()

                        var col = colStart
                        var i = 0
                        while (i < cellsData.size) {
                            val cellVal = cellsData[i]
                            if (cellVal.isStringValue) {
                                val text = cellVal.asStringValue().asString()
                                for (ch in text) {
                                    if (col < buffer.gridWidth) {
                                        buffer.setCell(row, col, NeovimCell(char = ch))
                                        totalCells++
                                    }
                                    col++
                                }
                                i++
                            } else if (cellVal.isArrayValue) {
                                val cellArr = cellVal.asArrayValue().list()
                                val text = cellArr[0].asStringValue().asString()
                                val hlId = if (cellArr.size > 1 && cellArr[1].isIntegerValue) cellArr[1].asIntegerValue().toInt() else -1
                                val repeat = if (cellArr.size > 2 && cellArr[2].isIntegerValue) cellArr[2].asIntegerValue().toInt() else 1
                                val cell = NeovimCell(char = text[0], foregroundId = hlId.takeIf { it >= 0 } ?: -1)
                                for (k in 0 until repeat) {
                                    if (col < buffer.gridWidth) {
                                        buffer.setCell(row, col, cell)
                                        totalCells++
                                    }
                                    col++
                                }
                                i++
                            } else {
                                i++
                            }
                        }
                        // Clear remaining cells in this row beyond what grid_line set
                        while (col < buffer.gridWidth) {
                            buffer.setCell(row, col, NeovimCell())
                            col++
                        }
                    } else if (arg.size >= 3) {
                        val row = arg[1].asIntegerValue().toInt()
                        val text = arg[2].asStringValue().asString()
                        for ((col, ch) in text.withIndex()) {
                            if (col < buffer.gridWidth) {
                                buffer.setCell(row, col, NeovimCell(char = ch))
                                totalCells++
                            }
                        }
                        // Clear remaining cells in this row
                        var col = text.length
                        while (col < buffer.gridWidth) {
                            buffer.setCell(row, col, NeovimCell())
                            col++
                        }
                    }
                }
                Log.d(TAG, "grid_line: $totalCells cells set")
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
                }
            }
            "grid_scroll" -> {
                if (event.args.isNotEmpty()) {
                    val a = event.args[0]
                    val top = a[1].asIntegerValue().toInt()
                    val bot = a[2].asIntegerValue().toInt()
                    val left = a[3].asIntegerValue().toInt()
                    val right = a[4].asIntegerValue().toInt()
                    val rows = a[5].asIntegerValue().toInt()
                    val cols = if (a.size > 6) a[6].asIntegerValue().toInt() else 0
                    buffer.scroll(top, bot, left, right, rows, cols)
                }
            }
            "grid_clear" -> {
                buffer.clear()
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
                    buffer.mode.name = event.args[0][0].asStringValue().asString()
                    buffer.cursor.shape = when (buffer.mode.name) {
                        "i", "ic", "ix" -> "vertical"
                        "R", "Rx", "Rvc" -> "horizontal"
                        else -> "block"
                    }
                }
            }
            "option_set" -> {
                if (event.args.isNotEmpty() && event.args[0].size >= 2) {
                    val name = event.args[0][0].asStringValue().asString()
                    val value = event.args[0][1]
                    Log.d(TAG, "Option: $name = $value")
                }
            }
            "default_colors_set" -> {
                if (event.args.isNotEmpty() && event.args[0].size >= 3) {
                    val fg = event.args[0][0].asIntegerValue().toInt()
                    val bg = event.args[0][1].asIntegerValue().toInt()
                    val sp = event.args[0][2].asIntegerValue().toInt()
                }
            }
        }
    }

    private fun updateStatusLine() {
        val mode = buffer.mode.name.uppercase().take(4)
        val line = buffer.cursor.row + 1
        val col = buffer.cursor.col + 1
        val file = currentFileName
        val text = " $mode  $file  Ln $line, Col $col "
        statusLine.text = text
        Log.v(TAG, "status: $text")
    }

    private fun openFilePicker() {
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

                    scope.launch {
                        client.command("enew!")
                        val escaped = content
                            .replace("\\", "\\\\")
                            .replace("'", "'\\''")
                        client.command("0put = '$escaped'")
                        client.command("1delete_")
                        client.command("file " + escapeVimPath(currentFileName))
                        client.input("<Esc>gg")
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
        val cmd = "nvim --listen $HOST:$PORT --remote-ui"
        val intent = Bridge.createExecuteIntent(cmd)
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
                    client.disconnect()
                    launcher.shutdown()
                }
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private suspend fun reconnect() {
        launcher.shutdown()
        delay(500)
        val launched = launcher.launch()
        if (launched) {
            val ok = client.connect()
            if (ok) {
                delay(100)
                client.uiAttach(80, 28)
                buffer.resize(80, 28)
                val (viewCols, viewRows) = withContext(Dispatchers.Main) { editorView.getGridSize() }
                if (viewCols != 80 || viewRows != 28) {
                    client.request("nvim_ui_try_resize", viewCols, viewRows)
                }
                withContext(Dispatchers.Main) {
                    statusLine.text = "Reconnected"
                }
            }
        }
    }
}
