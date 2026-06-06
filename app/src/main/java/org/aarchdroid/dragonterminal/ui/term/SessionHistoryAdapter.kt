package org.aarchdroid.dragonterminal.ui.term

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
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
    private val data: SessionHistoryData,
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

    private data class FlatItem(
        val type: Int,
        val session: SessionRecord? = null,
        val crashGroup: CrashGroup? = null,
        val terminal: TerminalRecord? = null,
        val command: CommandRecord? = null,
        val sessionId: String? = null,
        val indent: Int = 0
    )

    private val flatItems = mutableListOf<FlatItem>()

    init {
        rebuild()
    }

    fun rebuild() {
        flatItems.clear()
        val timeFmt = SimpleDateFormat("HH:mm", Locale.US)

        // Sessions
        for (session in data.sessions) {
            flatItems.add(
                FlatItem(
                    type = VIEW_TYPE_SESSION,
                    session = session,
                    sessionId = session.id,
                    indent = 0
                )
            )
            if (expandedSessions.contains(session.id)) {
                for (term in session.terminals) {
                    flatItems.add(
                        FlatItem(
                            type = VIEW_TYPE_TERMINAL,
                            terminal = term,
                            sessionId = session.id,
                            indent = 1
                        )
                    )
                    for (cmd in term.commands) {
                        flatItems.add(
                            FlatItem(
                                type = VIEW_TYPE_COMMAND,
                                command = cmd,
                                sessionId = session.id,
                                indent = 2
                            )
                        )
                    }
                }
            }
        }

        // Crash group
        val cg = data.crashGroup
        if (cg != null && cg.terminals.isNotEmpty()) {
            flatItems.add(
                FlatItem(
                    type = VIEW_TYPE_CRASH_GROUP,
                    crashGroup = cg,
                    indent = 0
                )
            )
            if (expandedCrash) {
                for (term in cg.terminals) {
                    flatItems.add(
                        FlatItem(
                            type = VIEW_TYPE_TERMINAL,
                            terminal = term,
                            indent = 1
                        )
                    )
                    for (cmd in term.commands) {
                        flatItems.add(
                            FlatItem(
                                type = VIEW_TYPE_COMMAND,
                                command = cmd,
                                indent = 2
                            )
                        )
                    }
                }
            }
        }

        // Restore all button — only if there are sessions or crash terminals
        val hasAny = data.sessions.isNotEmpty() || (cg != null && cg.terminals.isNotEmpty())
        if (hasAny) {
            flatItems.add(
                FlatItem(
                    type = VIEW_TYPE_RESTORE_ALL,
                    indent = 0
                )
            )
        }

        notifyDataSetChanged()
    }

    fun toggleSession(sessionId: String) {
        if (expandedSessions.contains(sessionId)) {
            expandedSessions.remove(sessionId)
        } else {
            expandedSessions.add(sessionId)
        }
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
            VIEW_TYPE_SESSION -> SessionHolder(
                inflater.inflate(R.layout.item_session_header, parent, false)
            )
            VIEW_TYPE_CRASH_GROUP -> CrashHolder(
                inflater.inflate(R.layout.item_crash_group, parent, false)
            )
            VIEW_TYPE_TERMINAL -> TerminalHolder(
                inflater.inflate(R.layout.item_terminal, parent, false)
            )
            VIEW_TYPE_COMMAND -> CommandHolder(
                inflater.inflate(R.layout.item_command, parent, false)
            )
            VIEW_TYPE_RESTORE_ALL -> RestoreAllHolder(
                inflater.inflate(R.layout.item_restore_all, parent, false)
            )
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
        val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
        val time = timeFmt.format(Date(session.created))

        val closedOk = session.closedNormally == true
        val isCrash = session.closedNormally == false

        holder.arrow.text = if (expandedSessions.contains(session.id)) "▼" else "▸"
        holder.time.text = "Hoy $time — ./"
        holder.status.text = when {
            closedOk -> "✔"
            isCrash -> "✘"
            else -> "?"
        }
        holder.status.setTextColor(
            ContextCompat.getColor(
                holder.itemView.context,
                if (closedOk) R.color.colorAccent else if (isCrash) R.color.colorred else R.color.tool_title_gray
            )
        )
        holder.reason.text = if (isCrash && session.crashReason != null) session.crashReason else ""
        holder.reason.visibility = if (isCrash && session.crashReason != null) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { toggleSession(session.id) }
    }

    private fun bindCrash(holder: CrashHolder, item: FlatItem) {
        val cg = item.crashGroup ?: return
        holder.text.text = "⚠ Crash ${cg.sessionCount} sessions"
        holder.reason.text = if (cg.terminals.isNotEmpty()) {
            val lastTerm = cg.terminals.last()
            if (lastTerm.commands.isNotEmpty()) {
                val lastCmd = lastTerm.commands.last()
                "Último: ${lastCmd.cmd}"
            } else ""
        } else ""
        holder.itemView.setOnClickListener { toggleCrash() }
    }

    private fun bindTerminal(holder: TerminalHolder, item: FlatItem) {
        val term = item.terminal ?: return
        val isLanzador = term.type == "lanzador"
        holder.type.text = "  ${"  ".repeat(item.indent)}▸ ${if (isLanzador) "Lanzador" else "Terminal"} ${term.type}"
        holder.type.setTextColor(
            ContextCompat.getColor(
                holder.itemView.context,
                if (isLanzador) R.color.tool_desc_gray else R.color.colorAccent
            )
        )
        holder.relanzar.tag = term
        holder.relanzar.setOnClickListener { v ->
            val t = v.tag as? TerminalRecord ?: return@setOnClickListener
            onRestoreTerminal(t)
        }
    }

    private fun bindCommand(holder: CommandHolder, item: FlatItem) {
        val cmd = item.command ?: return
        val prefix = "${"  ".repeat(item.indent)}"
        holder.text.text = "${prefix}${cmd.path} · ${cmd.cmd}"
        holder.text.setTextColor(
            when (cmd.status) {
                1 -> ContextCompat.getColor(holder.itemView.context, R.color.colorred)
                -1 -> 0xFFFF6600.toInt()
                else -> ContextCompat.getColor(holder.itemView.context, R.color.colorAccent)
            }
        )
    }

    private fun bindRestoreAll(holder: RestoreAllHolder) {
        holder.text.setOnClickListener { onRestoreAll() }
    }

    class SessionHolder(view: View) : RecyclerView.ViewHolder(view) {
        val arrow: TextView = view.findViewById(R.id.sessionArrow)
        val time: TextView = view.findViewById(R.id.sessionTime)
        val status: TextView = view.findViewById(R.id.sessionStatus)
        val reason: TextView = view.findViewById(R.id.sessionReason)
    }

    class CrashHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.crashIcon)
        val text: TextView = view.findViewById(R.id.crashText)
        val reason: TextView = view.findViewById(R.id.crashReason)
    }

    class TerminalHolder(view: View) : RecyclerView.ViewHolder(view) {
        val arrow: TextView = view.findViewById(R.id.terminalArrow)
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
