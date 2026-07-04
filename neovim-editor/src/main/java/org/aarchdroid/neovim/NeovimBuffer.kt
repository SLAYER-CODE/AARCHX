package org.aarchdroid.neovim

data class NeovimCell(
    val char: Char = ' ',
    val foreground: Int = NeovimColor.WHITE,
    val background: Int = 0xFF000000.toInt(),
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val reverse: Boolean = false,
    val foregroundId: Int = -1,
    val backgroundId: Int = -1
)

data class NeovimCursor(
    var row: Int = 0,
    var col: Int = 0,
    var visible: Boolean = true,
    var shape: String = "block",
    var scrollRegion: IntArray? = null
)

data class NeovimMode(
    var name: String = "normal",
    var index: Int = 0
)

object NeovimColor {
    val BLACK = 0xFF000000.toInt()
    val WHITE = 0xFFFFFFFF.toInt()

    fun from4Bit(idx: Int): Int = when (idx) {
        0 -> 0xFF000000.toInt()
        1 -> 0xFF800000.toInt()
        2 -> 0xFF008000.toInt()
        3 -> 0xFF808000.toInt()
        4 -> 0xFF000080.toInt()
        5 -> 0xFF800080.toInt()
        6 -> 0xFF008080.toInt()
        7 -> 0xFFC0C0C0.toInt()
        8 -> 0xFF808080.toInt()
        9 -> 0xFFFF0000.toInt()
        10 -> 0xFF00FF00.toInt()
        11 -> 0xFFFFFF00.toInt()
        12 -> 0xFF0000FF.toInt()
        13 -> 0xFFFF00FF.toInt()
        14 -> 0xFF00FFFF.toInt()
        15 -> 0xFFFFFFFF.toInt()
        else -> WHITE
    }

    fun from24Bit(rgb: Int): Int = rgb or 0xFF000000.toInt()
}

data class HighlightAttrs(
    val foreground: Int = -1,
    val background: Int = -1,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val reverse: Boolean = false
)

data class NeovimWindow(
    val grid: Int,
    val row: Int,
    val col: Int,
    val width: Int,
    val height: Int,
    var isFloating: Boolean = false
)

class NeovimBuffer {
    @Volatile var gridWidth: Int = 80
    @Volatile var gridHeight: Int = 24
    val cells: MutableList<MutableList<NeovimCell>> = mutableListOf()
    var cursor = NeovimCursor()
    var mode = NeovimMode()
    var windows: MutableMap<Int, NeovimWindow> = mutableMapOf()
    var currentGrid: Int = 1
    var defaultCell: NeovimCell = NeovimCell()

    private val lock = Any()
    private val defaultColors = mutableMapOf<Int, NeovimCell>()

    init {
        resize(80, 24)
    }

    fun resize(width: Int, height: Int) {
        synchronized(lock) {
            val oldCells = cells.map { it.toMutableList() }
            val oldWidth = gridWidth
            gridWidth = width
            gridHeight = height
            cells.clear()
            for (r in 0 until height) {
                val row = MutableList(width) { defaultCell }
                if (r < oldCells.size) {
                    val copyCount = minOf(width, oldWidth)
                    for (c in 0 until copyCount) {
                        row[c] = oldCells[r][c]
                    }
                } else if (oldCells.isNotEmpty()) {
                    // New rows beyond old grid: don't pre-fill ~ — let grid_line
                    // set content and tilde fill in onRedraw handle EOF lines.
                    // Keep defaultCell (space) to avoid flash of ~ before grid_line.
                }
                // else: first init (oldCells empty) → keep defaultCell (space)
                cells.add(row)
            }
        }
    }

    fun getCell(row: Int, col: Int): NeovimCell? {
        if (row in 0 until gridHeight && col in 0 until gridWidth) {
            synchronized(lock) {
                if (row < cells.size && col < cells[row].size) {
                    return cells[row][col]
                }
            }
        }
        return null
    }

    fun setCell(row: Int, col: Int, cell: NeovimCell) {
        if (row in 0 until gridHeight && col in 0 until gridWidth) {
            synchronized(lock) {
                if (row < cells.size && col < cells[row].size) {
                    cells[row][col] = cell
                }
            }
        }
    }

    fun clear(foreground: Int = NeovimColor.WHITE, background: Int = 0xFF000000.toInt()) {
        synchronized(lock) {
            for (r in 0 until cells.size.coerceAtMost(gridHeight)) {
                val row = cells[r]
                for (c in 0 until row.size.coerceAtMost(gridWidth)) {
                    row[c] = NeovimCell(foreground = foreground, background = background)
                }
            }
        }
    }

    fun scroll(top: Int, bottom: Int, left: Int, right: Int, rows: Int, cols: Int) {
        synchronized(lock) {
            val safeTop = top.coerceIn(0, gridHeight - 1)
            val safeBottom = bottom.coerceIn(safeTop + 1, gridHeight)
            val safeLeft = left.coerceIn(0, gridWidth - 1)
            val safeRight = right.coerceIn(safeLeft, gridWidth - 1)
            if (rows > 0) {
                // rows>0: move rectangle UP (happens when scrolling down in file)
                // Content shifts UP, new space at bottom
                val count = rows.coerceAtMost(safeBottom - safeTop)
                for (r in safeTop until safeBottom - count) {
                    for (c in safeLeft..safeRight) {
                        cells[r][c] = cells[r + count][c]
                    }
                }
                for (r in (safeBottom - count).coerceAtLeast(safeTop) until safeBottom) {
                    for (c in safeLeft..safeRight) {
                        cells[r][c] = defaultCell
                    }
                }
            } else if (rows < 0) {
                // rows<0: move rectangle DOWN (happens when scrolling up in file)
                // Content shifts DOWN, new space at top
                val absCount = (-rows).coerceAtMost(safeBottom - safeTop)
                for (r in safeBottom - 1 downTo safeTop + absCount) {
                    for (c in safeLeft..safeRight) {
                        cells[r][c] = cells[r - absCount][c]
                    }
                }
                for (r in safeTop until (safeTop + absCount).coerceAtMost(safeBottom)) {
                    for (c in safeLeft..safeRight) {
                        cells[r][c] = defaultCell
                    }
                }
            }
            if (cols > 0) {
                // scroll right: content moves RIGHT, copy from left, clear left
                val count = cols.coerceAtMost(safeRight - safeLeft)
                for (r in safeTop until safeBottom) {
                    for (c in safeRight downTo safeLeft + count) {
                        cells[r][c] = cells[r][c - count]
                    }
                    for (c in safeLeft until (safeLeft + count).coerceAtMost(safeRight)) {
                        cells[r][c] = defaultCell
                    }
                }
            } else if (cols < 0) {
                // scroll left: content moves LEFT, copy from right, clear right
                val absCount = (-cols).coerceAtMost(safeRight - safeLeft)
                for (r in safeTop until safeBottom) {
                    for (c in safeLeft until safeRight - absCount) {
                        cells[r][c] = cells[r][c + absCount]
                    }
                    for (c in (safeRight - absCount).coerceAtLeast(safeLeft)..safeRight) {
                        cells[r][c] = defaultCell
                    }
                }
            }
        }
    }

    fun copySnapshot(): NeovimBuffer {
        synchronized(lock) {
            val snap = NeovimBuffer()
            snap.gridWidth = gridWidth
            snap.gridHeight = gridHeight
            snap.cursor = cursor.copy()
            snap.mode = mode.copy()
            snap.cells.clear()
            for (r in 0 until gridHeight) {
                val row = mutableListOf<NeovimCell>()
                for (c in 0 until gridWidth) {
                    if (r < cells.size && c < cells[r].size) {
                        row.add(cells[r][c])
                    } else {
                        row.add(defaultCell)
                    }
                }
                snap.cells.add(row)
            }
            return snap
        }
    }

    fun getLineText(row: Int): String {
        if (row < 0 || row >= gridHeight) return ""
        synchronized(lock) {
            return if (row < cells.size) cells[row].map { it.char }.joinToString("") else ""
        }
    }

    fun setCursor(row: Int, col: Int) {
        synchronized(lock) {
            cursor.row = row
            cursor.col = col
        }
    }

    fun applyModeChange(name: String) {
        synchronized(lock) {
            mode.name = name
            cursor.shape = when {
                name in listOf("insert", "i", "ic", "ix") -> "vertical"
                name in listOf("replace", "R", "Rx", "Rvc") -> "horizontal"
                else -> "block"
            }
        }
    }
}
