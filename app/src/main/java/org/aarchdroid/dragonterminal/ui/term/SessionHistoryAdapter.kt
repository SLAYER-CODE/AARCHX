package org.aarchdroid.dragonterminal.ui.term

import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.UnderlineSpan
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

    fun updateData(newData: SessionHistoryData) {
        data = newData
        rebuild()
    }

    fun rebuild() {
        flatItems.clear()

        for (session in data.sessions) {
            flatItems.add(FlatItem(VIEW_TYPE_SESSION, session = session, sessionId = session.id))
            if (expandedSessions.contains(session.id)) {
                for (term in session.terminals) {
                    flatItems.add(FlatItem(VIEW_TYPE_TERMINAL, terminal = term, sessionId = session.id, indent = 1))
                    for (cmd in term.commands) {
                        flatItems.add(FlatItem(VIEW_TYPE_COMMAND, command = cmd, sessionId = session.id, indent = 2))
                    }
                }
            }
        }

        val cg = data.crashGroup
        if (cg != null && cg.terminals.isNotEmpty()) {
            flatItems.add(FlatItem(VIEW_TYPE_CRASH_GROUP, crashGroup = cg))
            if (expandedCrash) {
                for (term in cg.terminals) {
                    flatItems.add(FlatItem(VIEW_TYPE_TERMINAL, terminal = term, indent = 1))
                    for (cmd in term.commands) {
                        flatItems.add(FlatItem(VIEW_TYPE_COMMAND, command = cmd, indent = 2))
                    }
                }
            }
        }

        val hasAny = data.sessions.isNotEmpty() || (cg != null && cg.terminals.isNotEmpty())
        if (hasAny) {
            flatItems.add(FlatItem(VIEW_TYPE_RESTORE_ALL))
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
        val time = SimpleDateFormat("HH:mm", Locale.US).format(Date(session.created))
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
            if (closedOk) 0xFF005500.toInt()
            else if (isCrash) 0xFF880000.toInt()
            else 0xFF555555.toInt()
        )
        holder.reason.text = if (isCrash && session.crashReason != null) session.crashReason else ""
        holder.reason.visibility = if (isCrash && session.crashReason != null) View.VISIBLE else View.GONE
        holder.reason.setTextColor(0xFF660000.toInt())
        holder.itemView.setOnClickListener { toggleSession(session.id) }
    }

    private fun bindCrash(holder: CrashHolder, item: FlatItem) {
        val cg = item.crashGroup ?: return
        holder.text.text = "⚠ Crash ${cg.sessionCount} sessions"
        holder.text.setTextColor(0xFF660000.toInt())
        holder.reason.text = if (cg.terminals.isNotEmpty()) {
            val last = cg.terminals.last().commands.lastOrNull()
            if (last != null) "Último: ${last.cmd}" else ""
        } else ""
        holder.reason.setTextColor(0xFF553333.toInt())
        holder.itemView.setOnClickListener { toggleCrash() }
    }

    private fun bindTerminal(holder: TerminalHolder, item: FlatItem) {
        val term = item.terminal ?: return
        val isLanz = term.type == "lanzador"
        holder.type.text = "  ▸ ${if (isLanz) "Lanzador" else "Terminal"}"
        holder.type.setTextColor(if (isLanz) 0xFF557755.toInt() else 0xFF006600.toInt())

        val spannable = SpannableString("Relanzar")
        spannable.setSpan(UnderlineSpan(), 0, spannable.length, 0)
        holder.relanzar.text = spannable
        holder.relanzar.setTextColor(0xFF006644.toInt())
        holder.relanzar.tag = term
        holder.relanzar.setOnClickListener { v ->
            (v.tag as? TerminalRecord)?.let { onRestoreTerminal(it) }
        }
    }

    private fun bindCommand(holder: CommandHolder, item: FlatItem) {
        val cmd = item.command ?: return
        holder.text.text = "${cmd.path} · ${cmd.cmd}"
        holder.text.setTextColor(
            when (cmd.status) {
                1 -> 0xFF885555.toInt()
                -1 -> 0xFF886633.toInt()
                else -> 0xFF446644.toInt()
            }
        )
    }

    private fun bindRestoreAll(holder: RestoreAllHolder) {
        val spannable = SpannableString("Restaurar Todo")
        spannable.setSpan(UnderlineSpan(), 0, spannable.length, 0)
        holder.text.text = spannable
        holder.text.setTextColor(0xFF006666.toInt())
        holder.text.setOnClickListener { onRestoreAll() }
    }

    class SessionHolder(view: View) : RecyclerView.ViewHolder(view) {
        val arrow: TextView = view.findViewById(R.id.sessionArrow)
        val time: TextView = view.findViewById(R.id.sessionTime)
        val status: TextView = view.findViewById(R.id.sessionStatus)
        val reason: TextView = view.findViewById(R.id.sessionReason)
    }

    class CrashHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.crashText)
        val reason: TextView = view.findViewById(R.id.crashReason)
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
