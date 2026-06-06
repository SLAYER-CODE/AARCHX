package org.aarchdroid.dragonterminal.ui.term

import android.graphics.Typeface
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
    private val onRestoreTerminal: (TerminalRecord) -> Unit,
    private val onRestoreAll: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SESSION = 0
        private const val VIEW_TYPE_CRASH_GROUP = 1
        private const val VIEW_TYPE_TERMINAL = 2
        private const val VIEW_TYPE_COMMAND = 3
        private const val VIEW_TYPE_RESTORE_ALL = 4
    }

    private var expandedSessions = mutableSetOf<String>()
    private var expandedCrash = false
    private var activeTerminals = mutableSetOf<String>() // terminal IDs with relanzar available

    private data class FlatItem(
        val type: Int,
        val session: SessionRecord? = null,
        val crashGroup: CrashGroup? = null,
        val terminal: TerminalRecord? = null,
        val command: CommandRecord? = null,
        val sessionId: String? = null,
        val line: String = "",
        val timeColor: Int = 0xFF888888.toInt()
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
        val timeFmt = SimpleDateFormat("h:mm a", Locale.US)
        val now = System.currentTimeMillis()

        for (sIdx in data.sessions.indices) {
            val session = data.sessions[sIdx]
            val elapsed = now - session.created
            val timeColor = when {
                elapsed < 3600000 -> 0xFF88FF88.toInt()
                elapsed < 14400000 -> 0xFFFF8800.toInt()
                else -> 0xFFFF6666.toInt()
            }
            val timeStr = timeFmt.format(Date(session.created))

            flatItems.add(FlatItem(
                VIEW_TYPE_SESSION, session = session, sessionId = session.id,
                line = "  $timeStr",
                timeColor = timeColor
            ))

            if (expandedSessions.contains(session.id)) {
                val terms = session.terminals
                for (tIdx in terms.indices) {
                    val term = terms[tIdx]
                    val isLastTerm = tIdx == terms.lastIndex
                    val termLine = if (isLastTerm) "  ╚═ " else "  ╠═ "

                    flatItems.add(FlatItem(
                        VIEW_TYPE_TERMINAL, terminal = term, sessionId = session.id,
                        line = termLine
                    ))

                    val cmds = term.commands
                    for (cIdx in cmds.indices) {
                        val cmd = cmds[cIdx]
                        val isLastCmd = cIdx == cmds.lastIndex
                        val cmdLine = when {
                            isLastTerm && isLastCmd -> "      "
                            else -> if (isLastTerm) "     " else "  ║  "
                        }
                        flatItems.add(FlatItem(
                            VIEW_TYPE_COMMAND, command = cmd, sessionId = session.id,
                            line = cmdLine
                        ))
                    }
                }
            }
        }

        val cg = data.crashGroup
        if (cg != null && cg.terminals.isNotEmpty()) {
            val s = if (cg.sessionCount > 1) "s" else ""
            flatItems.add(FlatItem(
                VIEW_TYPE_CRASH_GROUP, crashGroup = cg,
                line = "  ╔═ Crash ${cg.sessionCount} session$s"
            ))

            if (expandedCrash) {
                val terms = cg.terminals
                for (tIdx in terms.indices) {
                    val term = terms[tIdx]
                    val isLastTerm = tIdx == terms.lastIndex
                    val termLine = if (isLastTerm) "  ╚═ " else "  ╠═ "

                    flatItems.add(FlatItem(
                        VIEW_TYPE_TERMINAL, terminal = term,
                        line = termLine
                    ))

                    val cmds = term.commands
                    for (cIdx in cmds.indices) {
                        val cmd = cmds[cIdx]
                        val cmdLine = if (isLastTerm) "     " else "  ║  "
                        flatItems.add(FlatItem(
                            VIEW_TYPE_COMMAND, command = cmd,
                            line = cmdLine
                        ))
                    }
                }
            }
        }

        val hasAny = data.sessions.isNotEmpty() || (cg != null && cg.terminals.isNotEmpty())
        if (hasAny) {
            flatItems.add(FlatItem(VIEW_TYPE_RESTORE_ALL, line = "  ╚═ Restaurar Todo"))
        }

        notifyDataSetChanged()
    }

    fun toggleSession(sessionId: String) {
        if (expandedSessions.contains(sessionId)) expandedSessions.remove(sessionId)
        else expandedSessions.add(sessionId)
        rebuild()
    }

    fun toggleCrash() {
        expandedCrash = !expandedCrash
        rebuild()
    }

    override fun getItemCount(): Int = flatItems.size
    override fun getItemViewType(position: Int): Int = flatItems[position].type

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SESSION -> SessionHolder(inflater.inflate(R.layout.item_session_header, parent, false))
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
        holder.time.setTextColor(item.timeColor)

        if (isCrash) {
            holder.reason.visibility = View.VISIBLE
            holder.reason.text = if (session.crashReason != null) session.crashReason else "Crash"
            holder.reason.setTextColor(0xFFFF6666.toInt())
        } else {
            holder.reason.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { toggleSession(session.id) }
    }

    private fun bindCrash(holder: CrashHolder, item: FlatItem) {
        holder.text.text = item.line
        holder.text.setTextColor(0xFFFF6666.toInt())
        holder.itemView.setOnClickListener { toggleCrash() }
    }

    private fun bindTerminal(holder: TerminalHolder, item: FlatItem) {
        val term = item.terminal ?: return
        val isLanz = term.type == "lanzador"
        val typeLabel = if (isLanz) "Lanzador" else "Terminal"
        holder.type.text = "${item.line}$typeLabel"
        holder.type.setTextColor(if (isLanz) 0xFF88AA88.toInt() else 0xFF88FF88.toInt())

        val spannable = SpannableString("Relanzar")
        spannable.setSpan(UnderlineSpan(), 0, spannable.length, 0)
        holder.relanzar.text = spannable
        holder.relanzar.setTextColor(0xFF88FF88.toInt())
        holder.relanzar.tag = term
        holder.relanzar.setOnClickListener { v ->
            (v.tag as? TerminalRecord)?.let { onRestoreTerminal(it) }
        }
    }

    private fun bindCommand(holder: CommandHolder, item: FlatItem) {
        val cmd = item.command ?: return
        holder.text.text = cmd.cmd
        holder.text.setTextColor(
            when (cmd.status) {
                1 -> 0xFFFF6666.toInt()
                -1 -> 0xFFFFAA44.toInt()
                else -> 0xFFAAAAAA.toInt()
            }
        )
    }

    private fun bindRestoreAll(holder: RestoreAllHolder) {
        holder.text.text = "  ╚═ Restaurar Todo"
        holder.text.setTextColor(0xFF88FFFF.toInt())
        holder.text.setOnClickListener { onRestoreAll() }
    }

    class SessionHolder(view: View) : RecyclerView.ViewHolder(view) {
        val time: TextView = view.findViewById(R.id.sessionTime)
        val reason: TextView = view.findViewById(R.id.sessionReason)
    }

    class CrashHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.crashText)
    }

    class TerminalHolder(view: View) : RecyclerView.ViewHolder(view) {
        val type: TextView = view.findViewById(R.id.terminalType)
        val relanzar: TextView = view.findViewById(R.id.terminalRelanzar)
    }

    class CommandHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.commandText)
    }

    class RestoreAllHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.restoreAllText)
    }
}
