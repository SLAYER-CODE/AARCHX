package org.aarchdroid.dragonterminal.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CommandRecord(
    val path: String,
    val cmd: String,
    val status: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("path", path)
        put("cmd", cmd)
        put("status", status)
    }

    companion object {
        fun fromJson(obj: JSONObject): CommandRecord =
            CommandRecord(
                path = obj.optString("path", ""),
                cmd = obj.optString("cmd", ""),
                status = obj.optInt("status", 0)
            )
    }
}

data class TerminalRecord(
    val id: String,
    val created: Long,
    val type: String,
    val commands: MutableList<CommandRecord> = mutableListOf()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("created", created)
        put("type", type)
        put("commands", JSONArray().apply {
            commands.forEach { put(it.toJson()) }
        })
    }

    companion object {
        fun fromJson(obj: JSONObject): TerminalRecord =
            TerminalRecord(
                id = obj.optString("id", ""),
                created = obj.optLong("created", System.currentTimeMillis()),
                type = obj.optString("type", "terminal"),
                commands = mutableListOf<CommandRecord>().apply {
                    val arr = obj.optJSONArray("commands") ?: return@apply
                    for (i in 0 until arr.length()) {
                        add(CommandRecord.fromJson(arr.getJSONObject(i)))
                    }
                }
            )
    }
}

data class SessionRecord(
    val id: String,
    val created: Long,
    var closedNormally: Boolean? = null,
    var crashReason: String? = null,
    val terminals: MutableList<TerminalRecord> = mutableListOf()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("created", created)
        closedNormally?.let { put("closedNormally", it) }
        crashReason?.let { put("crashReason", it) }
        put("terminals", JSONArray().apply {
            terminals.forEach { put(it.toJson()) }
        })
    }

    companion object {
        fun fromJson(obj: JSONObject): SessionRecord =
            SessionRecord(
                id = obj.optString("id", ""),
                created = obj.optLong("created", System.currentTimeMillis()),
                closedNormally = if (obj.has("closedNormally")) obj.optBoolean("closedNormally") else null,
                crashReason = obj.optString("crashReason", null),
                terminals = mutableListOf<TerminalRecord>().apply {
                    val arr = obj.optJSONArray("terminals") ?: return@apply
                    for (i in 0 until arr.length()) {
                        add(TerminalRecord.fromJson(arr.getJSONObject(i)))
                    }
                }
            )
    }
}

data class CrashGroup(
    var sessionCount: Int,
    val terminals: MutableList<TerminalRecord> = mutableListOf()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("sessionCount", sessionCount)
        put("terminals", JSONArray().apply {
            terminals.forEach { put(it.toJson()) }
        })
    }

    companion object {
        fun fromJson(obj: JSONObject): CrashGroup =
            CrashGroup(
                sessionCount = obj.optInt("sessionCount", 0),
                terminals = mutableListOf<TerminalRecord>().apply {
                    val arr = obj.optJSONArray("terminals") ?: return@apply
                    for (i in 0 until arr.length()) {
                        add(TerminalRecord.fromJson(arr.getJSONObject(i)))
                    }
                }
            )
    }
}

data class SessionHistoryData(
    val date: String,
    var flagActive: Boolean = false,
    val sessions: MutableList<SessionRecord> = mutableListOf(),
    var crashGroup: CrashGroup? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("date", date)
        put("flagActive", flagActive)
        put("sessions", JSONArray().apply {
            sessions.forEach { put(it.toJson()) }
        })
        crashGroup?.let { put("crashGroup", it.toJson()) }
    }

    companion object {
        fun fromJson(obj: JSONObject): SessionHistoryData =
            SessionHistoryData(
                date = obj.optString("date", ""),
                flagActive = obj.optBoolean("flagActive", false),
                sessions = mutableListOf<SessionRecord>().apply {
                    val arr = obj.optJSONArray("sessions") ?: return@apply
                    for (i in 0 until arr.length()) {
                        add(SessionRecord.fromJson(arr.getJSONObject(i)))
                    }
                },
                crashGroup = if (obj.has("crashGroup")) CrashGroup.fromJson(obj.getJSONObject("crashGroup")) else null
            )
    }
}

object SessionHistory {
    private const val FILE_NAME = "sessions_today.json"
    private var current: SessionHistoryData? = null
    private var currentSession: SessionRecord? = null
    private var terminalIdCounter = 0

    fun ensure(context: Context): SessionHistoryData {
        current?.let { return it }
        val data = load(context)
        current = data
        return data
    }

    fun getCurrentSession(): SessionRecord? = currentSession

    fun startSession(context: Context): SessionRecord {
        ensure(context)
        val session = SessionRecord(
            id = java.util.UUID.randomUUID().toString(),
            created = System.currentTimeMillis()
        )
        currentSession = session
        current?.sessions?.add(0, session)
        current?.flagActive = true
        save(context)
        return session
    }

    fun startTerminal(context: Context, sessionId: String, type: String = "terminal"): TerminalRecord {
        ensure(context)
        val session = current?.sessions?.find { it.id == sessionId } ?: return TerminalRecord(
            id = "orphan_${++terminalIdCounter}",
            created = System.currentTimeMillis(),
            type = type
        )
        val term = TerminalRecord(
            id = "term_${++terminalIdCounter}",
            created = System.currentTimeMillis(),
            type = type
        )
        session.terminals.add(term)
        save(context)
        return term
    }

    fun logCommand(context: Context, sessionId: String, terminalId: String, path: String, cmd: String) {
        ensure(context)
        val truncatedPath = truncatePath(path, 10)
        val record = CommandRecord(path = truncatedPath, cmd = cmd)
        current?.sessions?.find { it.id == sessionId }?.terminals?.find { it.id == terminalId }?.commands?.let { cmds ->
            cmds.add(record)
            if (cmds.size > 5) {
                cmds.subList(0, cmds.size - 5).clear()
            }
        }
    }

    fun closeTerminal(context: Context, sessionId: String, terminalId: String) {
        ensure(context)
        val session = current?.sessions?.find { it.id == sessionId } ?: return
        val term = session.terminals.find { it.id == terminalId } ?: return
    }

    fun closeSession(context: Context, sessionId: String, crashReason: String? = null) {
        ensure(context)
        val session = current?.sessions?.find { it.id == sessionId } ?: return
        val hasActiveSessions = current?.sessions?.any { s ->
            s.closedNormally == null && s.id != sessionId
        } ?: false

        session.closedNormally = crashReason == null
        session.crashReason = crashReason

        if (!hasActiveSessions && current?.sessions?.all { it.closedNormally != null } == true) {
            current?.flagActive = false
            currentSession = null
        }
        save(context)
    }

    fun getHistory(context: Context): SessionHistoryData {
        return ensure(context)
    }

    private fun load(context: Context): SessionHistoryData {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            return SessionHistoryData(date = today)
        }
        return try {
            val json = JSONObject(file.readText())
            SessionHistoryData.fromJson(json)
        } catch (e: Exception) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            SessionHistoryData(date = today)
        }
    }

    private fun save(context: Context) {
        val data = current ?: return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (data.date != today) {
            current = SessionHistoryData(date = today)
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(current!!.toJson().toString())
            return
        }
        try {
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(data.toJson().toString())
        } catch (_: Exception) {}
    }

    fun saveNow(context: Context) {
        save(context)
    }

    fun verifyDateAndReset(context: Context) {
        val data = ensure(context)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (data.date != today) {
            current = SessionHistoryData(date = today)
            save(context)
        }
    }

    private fun truncatePath(path: String, maxLen: Int): String {
        if (path.length <= maxLen) return path
        return ".." + path.takeLast(maxLen - 2)
    }
}
