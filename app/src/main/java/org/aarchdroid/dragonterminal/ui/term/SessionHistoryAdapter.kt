package org.aarchdroid.dragonterminal.ui.term

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.aarchdroid.R
import org.aarchdroid.dragonterminal.data.SessionHistoryData
import org.aarchdroid.dragonterminal.data.SessionRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionHistoryAdapter(
    private var data: SessionHistoryData,
    private val onRestoreSession: (SessionRecord) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_DATE_HEADER = 0
        private const val VIEW_TYPE_SESSION_CARD = 1
        private const val VIEW_TYPE_SPACER = 2
    }

    private data class LineInfo(val text: String, val color: Int)

    private data class FlatItem(
        val type: Int,
        val session: SessionRecord? = null,
        val line: String = "",
        val lineColor: Int = 0xFF888888.toInt(),
        val lines: List<LineInfo> = emptyList(),
        val iconResId: Int = 0
    )

    private val flatItems = mutableListOf<FlatItem>()

    init { rebuild() }

    fun updateData(newData: SessionHistoryData) {
        data = newData
        rebuild()
    }

    private fun resolveIconId(iconResId: Int, launchSource: String): Int {
        if (iconResId != 0) return iconResId
        return when (launchSource) {
            "flotante" -> R.mipmap.float_terminal_al
            "herramienta" -> R.drawable.crunch
            else -> R.mipmap.ic_launcher
        }
    }

    private fun buildTerminalLines(term: org.aarchdroid.dragonterminal.data.TerminalRecord,
                                    isLast: Boolean, lines: MutableList<LineInfo>) {
        val isLanz = term.type == "lanzador"
        val termColor = if (isLanz) 0xFF557755.toInt() else 0xFF338833.toInt()
        val rootLabel = when (term.launchSource) {
            "flotante" -> "Float@Root"
            "herramienta" -> {
                val toolName = if (term.type.startsWith("herramienta:")) {
                    term.type.removePrefix("herramienta:")
                } else {
                    term.commands.firstOrNull()?.cmd?.split(" ")?.firstOrNull()?.trim() ?: "Terminal"
                }
                "${toolName.replaceFirstChar { it.uppercase() }}@Root"
            }
            else -> "Terminal@Root"
        }
        val termPrefix = if (isLast) "\u2514\u2500 " else "\u251C\u2500 "
        val termCmdCount = "(${term.commands.size} Comandos)"
        lines.add(LineInfo("$termPrefix$rootLabel  $termCmdCount", termColor))

        val srcName = when (term.launchSource) {
            "flotante" -> "Flotante"
            "herramienta" -> {
                if (term.type.startsWith("herramienta:")) {
                    term.type.removePrefix("herramienta:").replaceFirstChar { it.uppercase() }
                } else "Herramienta"
            }
            else -> "Gestor"
        }
        val destName = when (term.exitDestiny) {
            "cerrada" -> "Cerrada"
            "flotante" -> "Flotante"
            "gestor" -> "Gestor"
            else -> ""
        }
        if (destName.isNotEmpty()) {
            lines.add(LineInfo("\u2502  \u2514\u2500 $srcName \u2192 $destName", 0xFFFF0055.toInt()))
        }

        val cmdList = term.commands
        val pathChanges = mutableListOf<Int>()
        var prevPath = cmdList.first().path
        for (i in 1 until cmdList.size) {
            if (cmdList[i].path != prevPath) {
                pathChanges.add(i)
                prevPath = cmdList[i].path
            }
        }

        var groupStart = 0
        for (gIdx in 0..pathChanges.size) {
            val groupEnd = if (gIdx < pathChanges.size) pathChanges[gIdx] else cmdList.size
            val path = cmdList[groupStart].path
            val isLastGroup = gIdx == pathChanges.size
            val pathLevel = if (gIdx == 0) 0 else 1
            val pathIndent = "  ".repeat(pathLevel)
            val pathPrefix = if (isLastGroup) "${pathIndent}\u2514\u2500\u252C\u2500 " else "${pathIndent}\u251C\u2500\u252C\u2500 "
            lines.add(LineInfo("$pathPrefix$path", 0xFF888888.toInt()))

            val cmdIndent = "  ".repeat(pathLevel + 1)
            for (cIdx in groupStart until groupEnd) {
                val cmd = cmdList[cIdx]
                val isLastCmd = cIdx == groupEnd - 1
                val cmdPrefix = if (isLastCmd) "${cmdIndent}\u2514# " else "${cmdIndent}\u251C# "
                val exitStr = if (cmd.status != 0) " \u2717${cmd.status}" else ""
                lines.add(LineInfo("$cmdPrefix${cmd.cmd}$exitStr", 0xFFFFFFFF.toInt()))
            }
            groupStart = groupEnd
        }
    }

    fun rebuild() {
        flatItems.clear()
        val dateFmt = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        val timeFmt = SimpleDateFormat("h:mm a", Locale.US)
        val now = System.currentTimeMillis()
        val nowDate = Date()

        val headerStr = "------ [${dateFmt.format(nowDate)}] [${timeFmt.format(nowDate)}] -------"
        flatItems.add(FlatItem(VIEW_TYPE_DATE_HEADER, line = headerStr, lineColor = 0xFFAAFF00.toInt()))

        for (session in data.sessions) {
            val visibleTerms = session.terminals.filter { it.commands.isNotEmpty() }
            if (visibleTerms.isEmpty()) continue

            val elapsed = now - session.created
            val statusLabel: String
            when {
                elapsed < 3600000 -> { statusLabel = "Ahora" }
                elapsed < 14400000 -> { statusLabel = "Reciente" }
                else -> { statusLabel = "Antiguo" }
            }
            val timeStr = timeFmt.format(Date(session.created))
            val sessionColor = 0xFFFF8800.toInt()

            val sessionIconResId = resolveIconId(visibleTerms.first().iconResId, visibleTerms.first().launchSource)

            val lines = mutableListOf<LineInfo>()

            // Session header line — status at start
            var hdr = " \u250C"
            if (statusLabel.isNotEmpty()) hdr += "[$statusLabel] "
            hdr += "$timeStr"
            lines.add(LineInfo(hdr, sessionColor))

            for (tIdx in visibleTerms.indices) {
                buildTerminalLines(visibleTerms[tIdx], tIdx == visibleTerms.lastIndex, lines)
            }

            // Footer "Lanzar"
            lines.add(LineInfo("   Lanzar", 0xFF00FF00.toInt()))

            if (flatItems.size > 1) {
                flatItems.add(FlatItem(VIEW_TYPE_SPACER))
            }
            flatItems.add(FlatItem(
                VIEW_TYPE_SESSION_CARD, session = session,
                lines = lines, iconResId = sessionIconResId
            ))
        }

        // Crash group — render as a single CardView with all crashed terminals
        val cg = data.crashGroup
        if (cg != null && cg.terminals.any { it.commands.isNotEmpty() }) {
            if (flatItems.size > 1) {
                flatItems.add(FlatItem(VIEW_TYPE_SPACER))
            }

            val lines = mutableListOf<LineInfo>()

            // Crash header
            val crashTime = timeFmt.format(Date())
            lines.add(LineInfo(" \u250C[Crash] [$crashTime]", 0xFFFF0044.toInt()))
            lines.add(LineInfo("\u2502  \u2514\u2500 Motivo: Aplicaci\u00F3n terminada inesperadamente", 0xFFFF0044.toInt()))

            // All terminals from all crashed sessions
            val visibleTerms = cg.terminals.filter { it.commands.isNotEmpty() }
            for (tIdx in visibleTerms.indices) {
                buildTerminalLines(visibleTerms[tIdx], tIdx == visibleTerms.lastIndex, lines)
            }

            // Footer "Lanzar" — restores all crashed terminals
            lines.add(LineInfo("   Lanzar", 0xFF00FF00.toInt()))

            // Build synthetic SessionRecord with ALL crash terminals for restore
            val synthSession = SessionRecord(
                id = "crash_${System.currentTimeMillis()}",
                created = System.currentTimeMillis(),
                terminals = visibleTerms.toMutableList()
            )
            val crashIcon = resolveIconId(
                visibleTerms.firstOrNull()?.iconResId ?: 0,
                visibleTerms.firstOrNull()?.launchSource ?: ""
            )

            flatItems.add(FlatItem(
                VIEW_TYPE_SESSION_CARD, session = synthSession,
                lines = lines, iconResId = crashIcon
            ))
        }

        notifyDataSetChanged()
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.itemView.apply {
            rotationY = -6f
            alpha = 0.85f
            animate()
                .rotationY(0f)
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    override fun getItemCount(): Int = flatItems.size
    override fun getItemViewType(position: Int): Int = flatItems[position].type

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_DATE_HEADER -> DateHeaderHolder(inflater.inflate(R.layout.item_date_header, parent, false))
            VIEW_TYPE_SESSION_CARD -> SessionCardHolder(inflater.inflate(R.layout.item_session_card, parent, false))
            VIEW_TYPE_SPACER -> SpacerHolder(View(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 40)
            })
            else -> throw RuntimeException("Unknown view type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = flatItems[position]
        when (holder) {
            is DateHeaderHolder -> {
                holder.text.text = item.line
                holder.text.setTextColor(item.lineColor)
            }
            is SessionCardHolder -> bindSessionCard(holder, item)
            is SpacerHolder -> {}
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is SessionCardHolder) {
            holder.content.removeAllViews()
        }
    }

    private class WatermarkDrawable(
        private val icon: Drawable,
        private val drawSizePx: Int
    ) : Drawable() {
        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.right
            val cy = b.bottom
            val half = drawSizePx / 2
            icon.setBounds(cx - half, cy - half, cx + half, cy + half)
            icon.draw(canvas)
        }
        override fun getIntrinsicWidth(): Int = -1
        override fun getIntrinsicHeight(): Int = -1
        override fun getMinimumWidth(): Int = 0
        override fun getMinimumHeight(): Int = 0
        override fun setAlpha(alpha: Int) { icon.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { icon.colorFilter = colorFilter }
        override fun getOpacity(): Int = icon.opacity
    }

    private fun bindSessionCard(holder: SessionCardHolder, item: FlatItem) {
        holder.content.removeAllViews()
        val icon = ContextCompat.getDrawable(holder.itemView.context, item.iconResId)
        if (icon != null) {
            icon.mutate()
            icon.alpha = (0.10 * 255).toInt()
            val density = holder.itemView.context.resources.displayMetrics.density
            val drawSizePx = (220 * density).toInt()
            holder.itemView.background = WatermarkDrawable(icon, drawSizePx)
        } else {
            holder.itemView.background = null
        }
        val session = item.session
        val lines = item.lines
        val isLastLineFooter = lines.isNotEmpty() && lines.last().text.contains("Lanzar")

        for (i in lines.indices) {
            val li = lines[i]
            val tv = TextView(holder.content.context)
            tv.text = li.text
            tv.setTextColor(li.color)
            tv.textSize = 10f
            tv.typeface = Typeface.MONOSPACE
            tv.setPadding(2, 0, 8, 0)
            if (isLastLineFooter && i == lines.lastIndex) {
                tv.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.END }
                tv.tag = session
                tv.setOnClickListener { v ->
                    (v.tag as? SessionRecord)?.let { onRestoreSession(it) }
                }
            } else {
                tv.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            holder.content.addView(tv)
        }
    }

    class DateHeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.dateHeaderText)
    }

    class SessionCardHolder(view: View) : RecyclerView.ViewHolder(view) {
        val content: LinearLayout = view.findViewById(R.id.content)
    }

    class SpacerHolder(view: View) : RecyclerView.ViewHolder(view)
}
