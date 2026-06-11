package org.aarchdroid.neovim

data class NeovimCell(
    val char: Char = ' ',
    val foreground: Int = NeovimColor.WHITE,
    val background: Int = NeovimColor.BLACK,
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

data class NeovimWindow(
    val grid: Int,
    val row: Int,
    val col: Int,
    val width: Int,
    val height: Int,
    var isFloating: Boolean = false
)

class NeovimBuffer {
    var gridWidth: Int = 80
    var gridHeight: Int = 24
    val cells: MutableList<MutableList<NeovimCell>> = mutableListOf()
    var cursor = NeovimCursor()
    var mode = NeovimMode()
    var windows: MutableMap<Int, NeovimWindow> = mutableMapOf()
    var currentGrid: Int = 1

    private val defaultColors = mutableMapOf<Int, NeovimCell>()

    init {
        resize(80, 24)
    }

    fun resize(width: Int, height: Int) {
        gridWidth = width
        gridHeight = height
        cells.clear()
        for (r in 0 until height) {
            val row = MutableList(width) { NeovimCell() }
            cells.add(row)
        }
    }

    fun setCell(row: Int, col: Int, cell: NeovimCell) {
        if (row in 0 until gridHeight && col in 0 until gridWidth) {
            cells[row][col] = cell
        }
    }

    fun clear(foreground: Int = NeovimColor.WHITE, background: Int = NeovimColor.BLACK) {
        for (r in 0 until gridHeight) {
            for (c in 0 until gridWidth) {
                cells[r][c] = NeovimCell(foreground = foreground, background = background)
            }
        }
    }

    fun scroll(top: Int, bottom: Int, left: Int, right: Int, rows: Int, cols: Int) {
        val count = rows
        if (count > 0) {
            for (r in top until bottom - count) {
                for (c in left..right) {
                    cells[r][c] = cells[r + count][c]
                }
            }
            for (r in (bottom - count) until bottom) {
                for (c in left..right) {
                    cells[r][c] = NeovimCell()
                }
            }
        } else if (count < 0) {
            val absCount = -count
            for (r in (bottom - 1) downTo (top + absCount)) {
                for (c in left..right) {
                    cells[r][c] = cells[r - absCount][c]
                }
            }
            for (r in top until (top + absCount)) {
                for (c in left..right) {
                    cells[r][c] = NeovimCell()
                }
            }
        }
    }

    fun getLineText(row: Int): String {
        if (row < 0 || row >= gridHeight) return ""
        return cells[row].map { it.char }.joinToString("")
    }

    fun setCursor(row: Int, col: Int) {
        cursor.row = row
        cursor.col = col
    }
}
