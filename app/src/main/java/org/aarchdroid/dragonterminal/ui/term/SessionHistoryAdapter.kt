package org.aarchdroid.dragonterminal.ui.term

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
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
    private val onRestoreSession: (SessionRecord) -> Unit,
    private val onDeleteSession: (SessionRecord) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_DATE_HEADER = 0
        private const val VIEW_TYPE_SESSION_CARD = 1
        private const val VIEW_TYPE_SPACER = 2
    }

    private data class LineInfo(
        val text: String,
        val color: Int,
        val segments: List<Pair<String, Int>>? = null
    )

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
        val termLabel = "$termPrefix$rootLabel  "
        val termCmdCount = "(${term.commands.size} Comandos)"
        val termSegments = listOf(
            termLabel to termColor,
            termCmdCount to 0xFF00CCCC.toInt()
        )
        val termText = termSegments.joinToString("") { it.first }
        lines.add(LineInfo(termText, 0, segments = termSegments))

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

        // Split normal vs crash sessions, group crash by reason
        val normalSessions = mutableListOf<SessionRecord>()
        val crashByReason = mutableMapOf<String, MutableList<SessionRecord>>()

        for (session in data.sessions) {
            if (session.closedNormally == false || session.closedNormally == null) {
                val key = session.crashReason ?: "unknown"
                crashByReason.getOrPut(key) { mutableListOf() }.add(session)
            } else {
                normalSessions.add(session)
            }
        }

        // Render normal sessions — each its own CardView with own Lanzar
        for (session in normalSessions) {
            val visibleTerms = session.terminals.filter { it.commands.isNotEmpty() }
            if (visibleTerms.isEmpty()) continue

            val elapsed = now - session.created
            val statusLabel: String = when {
                elapsed < 600000 -> "Ahora"
                elapsed < 7200000 -> "Reciente"
                else -> "Antiguo"
            }
            val sessionColor = when (statusLabel) {
                "Ahora" -> 0xFF00FF00.toInt()
                "Reciente" -> 0xFFFF8800.toInt()
                else -> 0xFFFF4444.toInt()
            }
            val timeStr = timeFmt.format(Date(session.created))
            val sessionIconResId = resolveIconId(visibleTerms.first().iconResId, visibleTerms.first().launchSource)

            val lines = mutableListOf<LineInfo>()
            val defaultColor = 0xFF888888.toInt()
            val hdrSegments = mutableListOf<Pair<String, Int>>()
            hdrSegments.add(" \u250C[" to defaultColor)
            if (statusLabel.isNotEmpty()) {
                hdrSegments.add(statusLabel to sessionColor)
                hdrSegments.add("] " to defaultColor)
            }
            hdrSegments.add(timeStr to defaultColor)
            val hdrText = hdrSegments.joinToString("") { it.first }
            lines.add(LineInfo(hdrText, 0, segments = hdrSegments))

            for (tIdx in visibleTerms.indices) {
                buildTerminalLines(visibleTerms[tIdx], tIdx == visibleTerms.lastIndex, lines)
            }

            lines.add(LineInfo("   Borrar", 0xFFCC4444.toInt()))
            lines.add(LineInfo("   Lanzar", 0xFF00FF00.toInt()))

            if (flatItems.size > 1) {
                flatItems.add(FlatItem(VIEW_TYPE_SPACER))
            }
            flatItems.add(FlatItem(
                VIEW_TYPE_SESSION_CARD, session = session,
                lines = lines, iconResId = sessionIconResId
            ))
        }

        // Render each crash group — one CardView per reason, one Lanzar per group
        for ((reason, sessions) in crashByReason) {
            val allTerms = sessions.flatMap { it.terminals.filter { t -> t.commands.isNotEmpty() } }
            if (allTerms.isEmpty()) continue

            val sessionIconResId = resolveIconId(
                allTerms.firstOrNull()?.iconResId ?: 0,
                allTerms.firstOrNull()?.launchSource ?: ""
            )

            val lines = mutableListOf<LineInfo>()

            // Crash header with status
            val crashElapsed = now - sessions.minOf { it.created }
            val crashStatusLabel: String = when {
                crashElapsed < 600000 -> "Ahora"
                crashElapsed < 7200000 -> "Reciente"
                else -> "Antiguo"
            }
            val crashStatusColor = when (crashStatusLabel) {
                "Ahora" -> 0xFF00FF00.toInt()
                "Reciente" -> 0xFFFF8800.toInt()
                else -> 0xFFFF4444.toInt()
            }
            val firstTime = timeFmt.format(Date(sessions.minOf { it.created }))
            val crashSegments = listOf(
                " \u250C[Crash]" to 0xFFFF0044.toInt(),
                " [$firstTime]" to 0xFFFF8800.toInt(),
                " $crashStatusLabel" to crashStatusColor
            )
            val crashHdrText = crashSegments.joinToString("") { it.first }
            lines.add(LineInfo(crashHdrText, 0, segments = crashSegments))
            lines.add(LineInfo("\u2502  \u2514\u2500 $reason", 0xFFFF0044.toInt()))

            // All terminals from all sessions in this crash group
            for (tIdx in allTerms.indices) {
                buildTerminalLines(allTerms[tIdx], tIdx == allTerms.lastIndex, lines)
            }

            // One Lanzar button for the whole group
            lines.add(LineInfo("   Borrar", 0xFFCC4444.toInt()))
            lines.add(LineInfo("   Lanzar", 0xFF00FF00.toInt()))

            // Synthetic SessionRecord with all terminals for restore
            val synthSession = SessionRecord(
                id = "crash_${reason.hashCode()}_${System.currentTimeMillis()}",
                created = sessions.minOf { it.created },
                terminals = allTerms.toMutableList()
            )

            if (flatItems.size > 1) {
                flatItems.add(FlatItem(VIEW_TYPE_SPACER))
            }
            flatItems.add(FlatItem(
                VIEW_TYPE_SESSION_CARD, session = synthSession,
                lines = lines, iconResId = sessionIconResId
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
        val session = item.session
        val isCrash = session?.closedNormally == false || session?.closedNormally == null
        holder.leftStrip.setBackgroundColor(
            if (isCrash) 0xFFFF0044.toInt() else 0xFF00FF00.toInt()
        )
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
        val lines = item.lines
        val isLastLineFooter = lines.isNotEmpty() && lines.last().text.contains("Lanzar")

        for (i in lines.indices) {
            val li = lines[i]
            val tv = TextView(holder.content.context)
            if (li.segments != null) {
                val sb = StringBuilder()
                for ((segText, _) in li.segments) sb.append(segText)
                val ss = SpannableString(sb.toString())
                var start = 0
                for ((segText, segColor) in li.segments) {
                    ss.setSpan(ForegroundColorSpan(segColor), start, start + segText.length, 0)
                    start += segText.length
                }
                tv.text = ss
            } else {
                tv.text = li.text
                tv.setTextColor(li.color)
            }
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
            } else if (li.text.contains("Borrar")) {
                tv.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.START }
                tv.tag = session
                tv.setOnClickListener { v ->
                    (v.tag as? SessionRecord)?.let { onDeleteSession(it) }
                }
            } else {
                tv.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
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
        val leftStrip: View = view.findViewById(R.id.left_strip)
    }

    class SpacerHolder(view: View) : RecyclerView.ViewHolder(view)
}
