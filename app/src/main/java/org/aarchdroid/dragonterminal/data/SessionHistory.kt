package org.aarchdroid.dragonterminal.data

import android.content.ContentValues
import android.content.Context
import org.aarchdroid.dragonterminal.data.room.HistoryDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CommandRecord(
    val path: String,
    val cmd: String,
    val status: Int = 0
)

data class TerminalRecord(
    val id: String,
    val created: Long,
    val type: String,
    val commands: MutableList<CommandRecord> = mutableListOf()
)

data class SessionRecord(
    val id: String,
    val created: Long,
    var closedNormally: Boolean? = null,
    var crashReason: String? = null,
    val terminals: MutableList<TerminalRecord> = mutableListOf()
)

data class CrashGroup(
    var sessionCount: Int,
    val terminals: MutableList<TerminalRecord> = mutableListOf()
)

data class SessionHistoryData(
    val date: String,
    var flagActive: Boolean = false,
    val sessions: MutableList<SessionRecord> = mutableListOf(),
    var crashGroup: CrashGroup? = null
)

object SessionHistory {
    private const val PREFS_NAME = "session_history_prefs"
    private const val KEY_FLAG_ACTIVE = "flagActive"
    private const val KEY_CURRENT_DATE = "currentDate"

    private var current: SessionHistoryData? = null
    private var currentSession: SessionRecord? = null
    private var terminalIdCounter = 0
    private var db: HistoryDatabase? = null
    private var prefs: android.content.SharedPreferences? = null

    private fun init(context: Context) {
        if (db == null) {
            db = HistoryDatabase.getInstance(context)
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun ensure(context: Context): SessionHistoryData {
        current?.let { return it }
        return getHistory(context)
    }

    fun getCurrentSession(): SessionRecord? = currentSession

    fun startSession(context: Context): SessionRecord {
        init(context)
        val session = SessionRecord(
            id = java.util.UUID.randomUUID().toString(),
            created = System.currentTimeMillis()
        )
        runCatching {
            val cv = ContentValues().apply {
                put("id", session.id)
                put("created", session.created)
            }
            db?.writableDatabase?.insert("session", null, cv)
            current?.sessions?.add(0, session)
        }
        currentSession = session
        prefs?.edit()?.putBoolean(KEY_FLAG_ACTIVE, true)?.apply()
        return session
    }

    fun startTerminal(context: Context, sessionId: String, type: String = "terminal"): TerminalRecord {
        init(context)
        val term = TerminalRecord(
            id = "term_${++terminalIdCounter}",
            created = System.currentTimeMillis(),
            type = type
        )
        runCatching {
            val cv = ContentValues().apply {
                put("id", term.id)
                put("created", term.created)
                put("type", term.type)
                put("sessionId", sessionId)
            }
            db?.writableDatabase?.insert("terminal", null, cv)
            current?.sessions?.find { it.id == sessionId }?.terminals?.add(term)
        }
        return term
    }

    fun logCommand(context: Context, sessionId: String, terminalId: String, path: String, cmd: String) {
        init(context)
        val record = CommandRecord(path = path, cmd = cmd)
        current?.sessions?.find { it.id == sessionId }?.terminals?.find { it.id == terminalId }?.commands?.let { cmds ->
            cmds.add(record)
            if (cmds.size > 5) cmds.subList(0, cmds.size - 5).clear()
        }
        runCatching {
            var order = 0
            db?.readableDatabase?.rawQuery(
                "SELECT COUNT(*) FROM command WHERE terminalId = ?", arrayOf(terminalId)
            )?.use { cursor ->
                if (cursor.moveToFirst()) order = cursor.getInt(0)
            }
            val cv = ContentValues().apply {
                put("path", path)
                put("cmd", cmd)
                put("status", 0)
                put("terminalId", terminalId)
                put("ord", order)
            }
            db?.writableDatabase?.insert("command", null, cv)
        }
    }

    fun closeSession(context: Context, sessionId: String, crashReason: String? = null) {
        init(context)
        val session = current?.sessions?.find { it.id == sessionId } ?: return
        session.closedNormally = crashReason == null
        session.crashReason = crashReason

        runCatching {
            val cv = ContentValues().apply {
                put("closedNormally", if (crashReason == null) 1 else 0)
                put("crashReason", crashReason)
            }
            db?.writableDatabase?.update("session", cv, "id = ?", arrayOf(sessionId))
        }

        val hasActiveSessions = current?.sessions?.any { s -> s.closedNormally == null && s.id != sessionId } ?: false
        if (!hasActiveSessions && current?.sessions?.all { it.closedNormally != null } == true) {
            prefs?.edit()?.putBoolean(KEY_FLAG_ACTIVE, false)?.apply()
            currentSession = null
        }
    }

    fun getHistory(context: Context): SessionHistoryData {
        init(context)
        val flagActive = prefs?.getBoolean(KEY_FLAG_ACTIVE, false) ?: false
        val data = SessionHistoryData(
            date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
            flagActive = flagActive,
            sessions = mutableListOf(),
            crashGroup = null
        )

        runCatching {
            val readDb = db?.readableDatabase ?: return@runCatching
            readDb.rawQuery("SELECT * FROM session ORDER BY created DESC", null).use { sCursor ->
                while (sCursor.moveToNext()) {
                    val id = sCursor.getString(sCursor.getColumnIndexOrThrow("id"))
                    val created = sCursor.getLong(sCursor.getColumnIndexOrThrow("created"))
                    val closed = if (sCursor.isNull(sCursor.getColumnIndexOrThrow("closedNormally"))) null
                        else sCursor.getInt(sCursor.getColumnIndexOrThrow("closedNormally")) == 1
                    val crash = sCursor.getString(sCursor.getColumnIndexOrThrow("crashReason"))
                    val record = SessionRecord(id = id, created = created, closedNormally = closed, crashReason = crash, terminals = mutableListOf())

                    readDb.rawQuery("SELECT * FROM terminal WHERE sessionId = ? ORDER BY created ASC", arrayOf(id)).use { tCursor ->
                        while (tCursor.moveToNext()) {
                            val tId = tCursor.getString(tCursor.getColumnIndexOrThrow("id"))
                            val tCreated = tCursor.getLong(tCursor.getColumnIndexOrThrow("created"))
                            val tType = tCursor.getString(tCursor.getColumnIndexOrThrow("type"))
                            val tRecord = TerminalRecord(id = tId, created = tCreated, type = tType, commands = mutableListOf())

                            readDb.rawQuery("SELECT * FROM command WHERE terminalId = ? ORDER BY ord ASC", arrayOf(tId)).use { cCursor ->
                                while (cCursor.moveToNext()) {
                                    val path = cCursor.getString(cCursor.getColumnIndexOrThrow("path"))
                                    val cmd = cCursor.getString(cCursor.getColumnIndexOrThrow("cmd"))
                                    val status = cCursor.getInt(cCursor.getColumnIndexOrThrow("status"))
                                    tRecord.commands.add(CommandRecord(path = path, cmd = cmd, status = status))
                                }
                            }
                            if (tRecord.commands.isNotEmpty()) record.terminals.add(tRecord)
                        }
                    }
                    if (record.terminals.isNotEmpty()) data.sessions.add(record)
                }
            }
        }

        // Build crash group from sessions that weren't closed normally
        runCatching {
            val unclosedTerms = mutableListOf<TerminalRecord>()
            var unclosedCount = 0
            for (s in data.sessions) {
                if (s.closedNormally == null) {
                    unclosedCount++
                    s.terminals.filter { it.commands.isNotEmpty() }.forEach { unclosedTerms.add(it) }
                }
            }
            if (unclosedTerms.isNotEmpty()) data.crashGroup = CrashGroup(sessionCount = unclosedCount, terminals = unclosedTerms)
        }

        current = data
        return data
    }

    fun saveNow(context: Context) {
        // SQLite persists immediately — no-op needed
    }

    fun verifyDateAndReset(context: Context) {
        init(context)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val storedDate = prefs?.getString(KEY_CURRENT_DATE, "")
        if (storedDate != today) {
            current = null
            currentSession = null
            runCatching {
                db?.writableDatabase?.execSQL("DELETE FROM command")
                db?.writableDatabase?.execSQL("DELETE FROM terminal")
                db?.writableDatabase?.execSQL("DELETE FROM session")
            }
            prefs?.edit()?.putString(KEY_CURRENT_DATE, today)?.putBoolean(KEY_FLAG_ACTIVE, false)?.apply()
        }
    }
}
