package org.aarchdroid.dragonterminal.ui.term

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.aarchdroid.R
import org.aarchdroid.dragonterminal.data.CommandRecord
import org.aarchdroid.dragonterminal.data.CrashGroup
import org.aarchdroid.dragonterminal.data.SessionHistoryData
import org.aarchdroid.dragonterminal.data.SessionRecord
import org.aarchdroid.dragonterminal.data.TerminalRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionHistoryAdapter(
    private var data: SessionHistoryData,
    private val onRestoreSession: (SessionRecord) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_DATE_HEADER = 0
        private const val VIEW_TYPE_SESSION = 1
        private const val VIEW_TYPE_SESSION_FOOTER = 2
        private const val VIEW_TYPE_CRASH_GROUP = 3
        private const val VIEW_TYPE_TERMINAL = 4
        private const val VIEW_TYPE_COMMAND = 5
        private const val VIEW_TYPE_PATH = 6
        private const val VIEW_TYPE_SPACER = 7
    }

    private data class FlatItem(
        val type: Int,
        val session: SessionRecord? = null,
        val crashGroup: CrashGroup? = null,
        val terminal: TerminalRecord? = null,
        val command: CommandRecord? = null,
        val sessionId: String? = null,
        val line: String = "",
        val lineColor: Int = 0xFF888888.toInt(),
        val grayText: String = "",
        val statusColor: Int = 0xFF888888.toInt(),
        val extraText: String = "",
        val extraColor: Int = 0xFF888888.toInt()
    )

    private val flatItems = mutableListOf<FlatItem>()

    init {
        rebuild()
    }

    fun updateData(newData: SessionHistoryData) {
        data = newData
        rebuild()
    }

    fun rebuild() {
        flatItems.clear()
        val dateFmt = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        val timeFmt = SimpleDateFormat("h:mm a", Locale.US)
        val now = System.currentTimeMillis()
        val nowDate = Date()

        // Date header as first item
        val headerStr = "------ [${dateFmt.format(nowDate)}] [${timeFmt.format(nowDate)}] -------"
        flatItems.add(FlatItem(VIEW_TYPE_DATE_HEADER, line = headerStr, lineColor = 0xFFAAFF00.toInt()))

        for (session in data.sessions) {
            val visibleTerms = session.terminals.filter { it.commands.isNotEmpty() }
            if (visibleTerms.isEmpty()) continue

            val elapsed = now - session.created
            val statusLabel: String
            val statusColor: Int
            when {
                elapsed < 3600000 -> { statusLabel = "Ahora"; statusColor = 0xFF00FF00.toInt() }
                elapsed < 14400000 -> { statusLabel = "Reciente"; statusColor = 0xFFFF8800.toInt() }
                else -> { statusLabel = "Antiguo"; statusColor = 0xFF992222.toInt() }
            }
            val timeStr = timeFmt.format(Date(session.created))
            val isCrash = session.closedNormally == false
            val sessionColor = if (isCrash) 0xFF883333.toInt()
                else 0xFFFF8800.toInt()
            val cmdCount = visibleTerms.sumOf { it.commands.size }
            val cmdCountStr = "(${cmdCount}cmd)"

            if (flatItems.size > 1) {
                flatItems.add(FlatItem(VIEW_TYPE_SPACER))
            }

            flatItems.add(FlatItem(
                VIEW_TYPE_SESSION, session = session, sessionId = session.id,
                line = " \u250C[$timeStr]", lineColor = sessionColor,
                grayText = statusLabel, statusColor = statusColor,
                extraText = cmdCountStr, extraColor = 0xFF55AAAA.toInt()
            ))

            for (tIdx in visibleTerms.indices) {
                val term = visibleTerms[tIdx]
                val isLastTerm = tIdx == visibleTerms.lastIndex
                val isLanz = term.type == "lanzador"
                val termColor = if (isLanz) 0xFF557755.toInt() else 0xFF338833.toInt()
                val termPrefix = if (isLastTerm) "\u2514\u2500 Terminal@root" else "\u251C\u2500 Terminal@root"
                val termCmdCount = "(${term.commands.size}cmd)"
                flatItems.add(FlatItem(
                    VIEW_TYPE_TERMINAL, terminal = term, sessionId = session.id,
                    line = "$termPrefix", lineColor = termColor,
                    extraText = termCmdCount, extraColor = 0xFF55AAAA.toInt()
                ))

                // Group consecutive commands by path, render as tree
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
                    flatItems.add(FlatItem(
                        VIEW_TYPE_PATH, sessionId = session.id,
                        line = "$pathPrefix$path"
                    ))

                    val cmdIndent = "  ".repeat(pathLevel + 1)
                    for (cIdx in groupStart until groupEnd) {
                        val cmd = cmdList[cIdx]
                        val isLastCmd = cIdx == groupEnd - 1
                        val cmdPrefix = if (isLastCmd) "${cmdIndent}\u2514# " else "${cmdIndent}\u251C# "
                        val exitStr = if (cmd.status != 0) "\u2717${cmd.status}" else ""
                        flatItems.add(FlatItem(
                            VIEW_TYPE_COMMAND, command = cmd, sessionId = session.id,
                            line = "$cmdPrefix${cmd.cmd}", lineColor = 0xFFFFFFFF.toInt(),
                            extraText = exitStr, extraColor = 0xFFFF3333.toInt()
                        ))
                    }
                    groupStart = groupEnd
                }
            }

            flatItems.add(FlatItem(
                VIEW_TYPE_SESSION_FOOTER, session = session, sessionId = session.id,
                line = "   Lanzar", lineColor = 0xFF00FF00.toInt()
            ))
        }

        val cg = data.crashGroup
        if (cg != null && cg.terminals.any { it.commands.isNotEmpty() }) {
            val s = if (cg.sessionCount > 1) "s" else ""
            if (flatItems.size > 1) {
                flatItems.add(FlatItem(VIEW_TYPE_SPACER))
            }
            flatItems.add(FlatItem(
                VIEW_TYPE_CRASH_GROUP, crashGroup = cg,
                line = "   [Crash ${cg.sessionCount} session$s]", lineColor = 0xFF883333.toInt()
            ))

            val terms = cg.terminals.filter { it.commands.isNotEmpty() }
            for (tIdx in terms.indices) {
                val term = terms[tIdx]
                val isLastTerm = tIdx == terms.lastIndex
                val isLanz = term.type == "lanzador"
                val termColor = if (isLanz) 0xFF557755.toInt() else 0xFF338833.toInt()
                val termPrefix = if (isLastTerm) "\u2514\u2500 Terminal@root" else "\u251C\u2500 Terminal@root"
                flatItems.add(FlatItem(VIEW_TYPE_TERMINAL, terminal = term, line = "$termPrefix", lineColor = termColor, extraText = "(${term.commands.size}cmd)", extraColor = 0xFF55AAAA.toInt()))
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
                    flatItems.add(FlatItem(VIEW_TYPE_PATH, line = "$pathPrefix$path"))
                    val cmdIndent = "  ".repeat(pathLevel + 1)
                    for (cIdx in groupStart until groupEnd) {
                        val cmd = cmdList[cIdx]
                        val isLastCmd = cIdx == groupEnd - 1
                        val cmdPrefix = if (isLastCmd) "${cmdIndent}\u2514# " else "${cmdIndent}\u251C# "
                        val exitStr = if (cmd.status != 0) "\u2717${cmd.status}" else ""
                        flatItems.add(FlatItem(VIEW_TYPE_COMMAND, command = cmd, line = "$cmdPrefix${cmd.cmd}", lineColor = 0xFFFFFFFF.toInt(), extraText = exitStr, extraColor = 0xFFFF3333.toInt()))
                    }
                    groupStart = groupEnd
                }
            }
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
            VIEW_TYPE_SESSION -> SessionHolder(inflater.inflate(R.layout.item_session_header, parent, false))
            VIEW_TYPE_SESSION_FOOTER -> SessionFooterHolder(inflater.inflate(R.layout.item_session_footer, parent, false))
            VIEW_TYPE_CRASH_GROUP -> CrashHolder(inflater.inflate(R.layout.item_crash_group, parent, false))
            VIEW_TYPE_TERMINAL -> TerminalHolder(inflater.inflate(R.layout.item_terminal, parent, false))
            VIEW_TYPE_COMMAND -> CommandHolder(inflater.inflate(R.layout.item_command, parent, false))
            VIEW_TYPE_PATH -> PathHolder(inflater.inflate(R.layout.item_path, parent, false))
            VIEW_TYPE_SPACER -> SpacerHolder(inflater.inflate(R.layout.item_spacer, parent, false))
            else -> throw RuntimeException("Unknown view type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = flatItems[position]
        when (holder) {
            is DateHeaderHolder -> bindDateHeader(holder, item)
            is SessionHolder -> bindSession(holder, item)
            is SessionFooterHolder -> bindSessionFooter(holder, item)
            is CrashHolder -> bindCrash(holder, item)
            is TerminalHolder -> bindTerminal(holder, item)
            is CommandHolder -> bindCommand(holder, item)
            is PathHolder -> bindPath(holder, item)
            is SpacerHolder -> {} // no-op
        }
    }

    private fun bindDateHeader(holder: DateHeaderHolder, item: FlatItem) {
        holder.text.text = item.line
        holder.text.setTextColor(item.lineColor)
    }

    private fun bindSession(holder: SessionHolder, item: FlatItem) {
        val session = item.session ?: return
        val isCrash = session.closedNormally == false

        holder.time.text = item.line
        holder.time.setTextColor(item.lineColor)

        holder.status.text = item.grayText
        holder.status.setTextColor(item.statusColor)
        holder.status.visibility = if (isCrash || item.grayText.isEmpty()) View.GONE else View.VISIBLE

        if (isCrash) {
            holder.reason.visibility = View.VISIBLE
            holder.reason.text = if (session.crashReason != null) session.crashReason else "Crash"
            holder.reason.setTextColor(0xFF883333.toInt())
            holder.itemView.setBackgroundResource(R.drawable.crash_bg)
        } else {
            holder.reason.visibility = View.VISIBLE
            holder.reason.text = item.extraText
            holder.reason.setTextColor(item.extraColor)
            holder.itemView.setBackgroundResource(R.drawable.session_bg)
        }
    }

    private fun bindSessionFooter(holder: SessionFooterHolder, item: FlatItem) {
        holder.text.text = item.line
        holder.text.setTextColor(item.lineColor)
        holder.text.apply {
            post {
                val w = width.coerceAtLeast(1)
                val h = height.coerceAtLeast(1)
                paint.shader = android.graphics.LinearGradient(
                    w.toFloat(), h.toFloat(), 0f, 0f,
                    0xFF00AA00.toInt(), 0xFF002200.toInt(),
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
        }
        holder.icon.setColorFilter(0xFF00AA00.toInt())
        holder.text.tag = item.session
        holder.text.setOnClickListener { v ->
            (v.tag as? SessionRecord)?.let { onRestoreSession(it) }
        }
        holder.itemView.setBackgroundResource(R.drawable.lanzar_bg)
    }

    private fun bindCrash(holder: CrashHolder, item: FlatItem) {
        holder.text.text = item.line
        holder.text.setTextColor(item.lineColor)
        holder.itemView.setBackgroundResource(R.drawable.crash_bg)
    }

    private fun bindTerminal(holder: TerminalHolder, item: FlatItem) {
        holder.type.text = item.line
        holder.type.setTextColor(item.lineColor)
        if (item.extraText.isNotEmpty()) {
            holder.extra.visibility = View.VISIBLE
            holder.extra.text = item.extraText
            holder.extra.setTextColor(item.extraColor)
        } else {
            holder.extra.visibility = View.GONE
        }
        holder.itemView.setBackgroundResource(R.drawable.session_bg)
    }

    private fun bindCommand(holder: CommandHolder, item: FlatItem) {
        holder.commandText.text = item.line
        holder.commandText.setTextColor(item.lineColor)
        if (item.extraText.isNotEmpty()) {
            holder.grayPath.visibility = View.VISIBLE
            holder.grayPath.text = item.extraText
            holder.grayPath.setTextColor(item.extraColor)
        } else {
            holder.grayPath.visibility = View.GONE
        }
        holder.itemView.setBackgroundResource(R.drawable.session_bg)
    }

    private fun bindPath(holder: PathHolder, item: FlatItem) {
        holder.pathText.text = item.line
        holder.pathText.setTextColor(0xFF888888.toInt())
        holder.itemView.setBackgroundResource(R.drawable.session_bg)
    }

    class DateHeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.dateHeaderText)
    }

    class SessionHolder(view: View) : RecyclerView.ViewHolder(view) {
        val time: TextView = view.findViewById(R.id.sessionTime)
        val status: TextView = view.findViewById(R.id.sessionStatus)
        val reason: TextView = view.findViewById(R.id.sessionReason)
    }

    class SessionFooterHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.sessionFooterText)
        val icon: ImageView = view.findViewById(R.id.sessionFooterIcon)
    }

    class CrashHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.crashText)
    }

    class TerminalHolder(view: View) : RecyclerView.ViewHolder(view) {
        val type: TextView = view.findViewById(R.id.terminalType)
        val extra: TextView = view.findViewById(R.id.terminalExtra)
    }

    class CommandHolder(view: View) : RecyclerView.ViewHolder(view) {
        val commandText: TextView = view.findViewById(R.id.commandText)
        val grayPath: TextView = view.findViewById(R.id.grayPath)
    }

    class PathHolder(view: View) : RecyclerView.ViewHolder(view) {
        val pathText: TextView = view.findViewById(R.id.pathText)
    }

    class SpacerHolder(view: View) : RecyclerView.ViewHolder(view)
}
