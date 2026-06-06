package org.aarchdroid.dragonterminal.ui.term

import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    private val onRestoreSession: (SessionRecord) -> Unit,
    private val onRestoreAll: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SESSION = 0
        private const val VIEW_TYPE_SESSION_FOOTER = 1
        private const val VIEW_TYPE_CRASH_GROUP = 2
        private const val VIEW_TYPE_TERMINAL = 3
        private const val VIEW_TYPE_COMMAND = 4
        private const val VIEW_TYPE_RESTORE_ALL = 5
        private const val MAX_PATH_TAIL = 16
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
        val statusLabel: String = "",
        val statusColor: Int = 0xFF888888.toInt()
    )

    private val flatItems = mutableListOf<FlatItem>()

    init {
        rebuild()
    }

    fun updateData(newData: SessionHistoryData) {
        data = newData
        rebuild()
    }

    private fun shortenPath(path: String): String {
        if (path.length <= 20) return path
        return "(..." + path.takeLast(MAX_PATH_TAIL) + ")"
    }

    fun rebuild() {
        flatItems.clear()
        val timeFmt = SimpleDateFormat("h:mm a", Locale.US)
        val now = System.currentTimeMillis()

        for (session in data.sessions) {
            val visibleTerms = session.terminals.filter { it.commands.isNotEmpty() }
            if (visibleTerms.isEmpty()) continue

            val elapsed = now - session.created
            val statusLabel: String
            val statusColor: Int
            when {
                elapsed < 3600000 -> { statusLabel = "Ahora"; statusColor = 0xFF226622.toInt() }
                elapsed < 14400000 -> { statusLabel = "Reciente"; statusColor = 0xFF664400.toInt() }
                else -> { statusLabel = "Antiguo"; statusColor = 0xFF662222.toInt() }
            }
            val timeStr = timeFmt.format(Date(session.created))
            val isCrash = session.closedNormally == false
            val sessionLabel = if (isCrash) "[$timeStr] *"
                else "[$timeStr] $statusLabel"
            val sessionColor = if (isCrash) 0xFF883333.toInt()
                else 0xFF66DD66.toInt()

            flatItems.add(FlatItem(
                VIEW_TYPE_SESSION, session = session, sessionId = session.id,
                line = sessionLabel, lineColor = sessionColor,
                statusLabel = statusLabel, statusColor = statusColor
            ))

            for (tIdx in visibleTerms.indices) {
                val term = visibleTerms[tIdx]
                val isLanz = term.type == "lanzador"
                val typeLabel = if (isLanz) "Lanzador" else "Terminal"
                val termColor = if (isLanz) 0xFF557755.toInt() else 0xFF338833.toInt()

                flatItems.add(FlatItem(
                    VIEW_TYPE_TERMINAL, terminal = term, sessionId = session.id,
                    line = "-$typeLabel", lineColor = termColor
                ))

                var cdDepth = 0
                for (cmd in term.commands) {
                    val isCd = cmd.cmd.trimStart().startsWith("cd ")
                    val prefix = " ".repeat(2 + cdDepth * 2) + "|-(comando) "
                    val shortPath = shortenPath(cmd.path)
                    val cmdLine = "$prefix${shortPath}# ${cmd.cmd}"

                    flatItems.add(FlatItem(
                        VIEW_TYPE_COMMAND, command = cmd, sessionId = session.id,
                        line = cmdLine, lineColor = 0xFFFFFFFF.toInt()
                    ))

                    if (isCd) cdDepth++
                }
            }

            flatItems.add(FlatItem(
                VIEW_TYPE_SESSION_FOOTER, session = session, sessionId = session.id,
                line = "Relanzar", lineColor = 0xFF338833.toInt()
            ))
        }

        val cg = data.crashGroup
        if (cg != null && cg.terminals.any { it.commands.isNotEmpty() }) {
            val s = if (cg.sessionCount > 1) "s" else ""
            flatItems.add(FlatItem(
                VIEW_TYPE_CRASH_GROUP, crashGroup = cg,
                line = "[Crash ${cg.sessionCount} session$s]", lineColor = 0xFF883333.toInt()
            ))

            val terms = cg.terminals.filter { it.commands.isNotEmpty() }
            for (tIdx in terms.indices) {
                val term = terms[tIdx]
                val isLanz = term.type == "lanzador"
                val typeLabel = if (isLanz) "Lanzador" else "Terminal"
                val termColor = if (isLanz) 0xFF557755.toInt() else 0xFF338833.toInt()

                flatItems.add(FlatItem(VIEW_TYPE_TERMINAL, terminal = term, line = "-$typeLabel", lineColor = termColor))

                var cdDepth = 0
                for (cmd in term.commands) {
                    val isCd = cmd.cmd.trimStart().startsWith("cd ")
                    val prefix = " ".repeat(2 + cdDepth * 2) + "|-(comando) "
                    val shortPath = shortenPath(cmd.path)
                    val cmdLine = "$prefix${shortPath}# ${cmd.cmd}"

                    flatItems.add(FlatItem(VIEW_TYPE_COMMAND, command = cmd, line = cmdLine, lineColor = 0xFFFFFFFF.toInt()))

                    if (isCd) cdDepth++
                }
            }
        }

        val hasAny = flatItems.any { it.type == VIEW_TYPE_SESSION || it.type == VIEW_TYPE_CRASH_GROUP }
        if (hasAny) {
            flatItems.add(FlatItem(VIEW_TYPE_RESTORE_ALL, line = "Restaurar Todo", lineColor = 0xFF338888.toInt()))
        }

        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = flatItems.size
    override fun getItemViewType(position: Int): Int = flatItems[position].type

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SESSION -> SessionHolder(inflater.inflate(R.layout.item_session_header, parent, false))
            VIEW_TYPE_SESSION_FOOTER -> SessionFooterHolder(inflater.inflate(R.layout.item_session_footer, parent, false))
            VIEW_TYPE_CRASH_GROUP -> CrashHolder(inflater.inflate(R.layout.item_crash_group, parent, false))
            VIEW_TYPE_TERMINAL -> TerminalHolder(inflater.inflate(R.layout.item_terminal, parent, false))
            VIEW_TYPE_COMMAND -> CommandHolder(inflater.inflate(R.layout.item_command, parent, false))
            VIEW_TYPE_RESTORE_ALL -> RestoreAllHolder(inflater.inflate(R.layout.item_restore_all, parent, false))
            else -> throw RuntimeException("Unknown view type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = flatItems[position]
        when (holder) {
            is SessionHolder -> bindSession(holder, item)
            is SessionFooterHolder -> bindSessionFooter(holder, item)
            is CrashHolder -> bindCrash(holder, item)
            is TerminalHolder -> bindTerminal(holder, item)
            is CommandHolder -> bindCommand(holder, item)
            is RestoreAllHolder -> bindRestoreAll(holder)
        }
    }

    private fun bindSession(holder: SessionHolder, item: FlatItem) {
        val session = item.session ?: return
        val isCrash = session.closedNormally == false

        holder.time.text = item.line
        holder.time.setTextColor(item.lineColor)

        if (isCrash) {
            holder.reason.visibility = View.VISIBLE
            holder.reason.text = if (session.crashReason != null) session.crashReason else "Crash"
            holder.reason.setTextColor(0xFF883333.toInt())
        } else {
            holder.reason.visibility = View.VISIBLE
            holder.reason.text = item.statusLabel
            holder.reason.setTextColor(item.statusColor)
        }
    }

    private fun bindSessionFooter(holder: SessionFooterHolder, item: FlatItem) {
        holder.text.text = item.line
        holder.text.setTextColor(item.lineColor)
        holder.text.tag = item.session
        holder.text.setOnClickListener { v ->
            (v.tag as? SessionRecord)?.let { onRestoreSession(it) }
        }
    }

    private fun bindCrash(holder: CrashHolder, item: FlatItem) {
        holder.text.text = item.line
        holder.text.setTextColor(item.lineColor)
    }

    private fun bindTerminal(holder: TerminalHolder, item: FlatItem) {
        holder.type.text = item.line
        holder.type.setTextColor(item.lineColor)
    }

    private fun bindCommand(holder: CommandHolder, item: FlatItem) {
        holder.text.text = item.line
        holder.text.setTextColor(item.lineColor)
    }

    private fun bindRestoreAll(holder: RestoreAllHolder) {
        val spannable = SpannableString("Restaurar Todo")
        spannable.setSpan(UnderlineSpan(), 0, spannable.length, 0)
        holder.text.text = spannable
        holder.text.setTextColor(0xFF338888.toInt())
        holder.text.setOnClickListener { onRestoreAll() }
    }

    class SessionHolder(view: View) : RecyclerView.ViewHolder(view) {
        val time: TextView = view.findViewById(R.id.sessionTime)
        val reason: TextView = view.findViewById(R.id.sessionReason)
    }

    class SessionFooterHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.sessionFooterText)
    }

    class CrashHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.crashText)
    }

    class TerminalHolder(view: View) : RecyclerView.ViewHolder(view) {
        val type: TextView = view.findViewById(R.id.terminalType)
    }

    class CommandHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.commandText)
    }

    class RestoreAllHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.restoreAllText)
    }
}
