package org.aarchdroid.dragonterminal.data

import android.content.ContentValues
import android.content.Context
import android.util.Log
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
    val launchSource: String = "",
    var exitDestiny: String = "",
    val iconResId: Int = 0,
    val commands: MutableList<CommandRecord> = mutableListOf()
)

data class SessionRecord(
    val id: String,
    val created: Long,
    var closedNormally: Boolean? = null,
    var crashReason: String? = null,
    val terminals: MutableList<TerminalRecord> = mutableListOf()
)

data class SessionHistoryData(
    val date: String,
    var flagActive: Boolean = false,
    val sessions: MutableList<SessionRecord> = mutableListOf()
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
            // Restore terminalIdCounter from DB to avoid PK conflicts after process restart
            runCatching {
                db?.readableDatabase?.rawQuery("SELECT MAX(id) FROM terminal", null)?.use { c ->
                    if (c.moveToFirst()) {
                        val last = c.getString(0)
                        if (last?.startsWith("term_") == true) {
                            val n = last.removePrefix("term_").toIntOrNull()
                            if (n != null && n >= terminalIdCounter) terminalIdCounter = n
                        }
                    }
                }
            }
        }
    }

    fun ensure(context: Context): SessionHistoryData {
        current?.let { return it }
        return getHistory(context)
    }

    fun getCurrentSession(): SessionRecord? = currentSession

    fun startSession(context: Context): SessionRecord {
        init(context)
        Log.d("SessionHistory", "startSession() called, current=null? ${current == null}, prefs=$prefs")
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
        Log.d("SessionHistory", "startSession -> id=${session.id}, flagActive set to true")
        return session
    }

    fun startTerminal(context: Context, sessionId: String, type: String = "terminal",
                      launchSource: String = "", iconResId: Int = 0): TerminalRecord {
        init(context)
        val term = TerminalRecord(
            id = "term_${++terminalIdCounter}",
            created = System.currentTimeMillis(),
            type = type,
            launchSource = launchSource,
            iconResId = iconResId
        )
        runCatching {
            val cv = ContentValues().apply {
                put("id", term.id)
                put("created", term.created)
                put("type", term.type)
                put("launchSource", term.launchSource)
                put("exitDestiny", term.exitDestiny)
                put("iconResId", term.iconResId)
                put("sessionId", sessionId)
            }
            db?.writableDatabase?.insert("terminal", null, cv)
            current?.sessions?.find { it.id == sessionId }?.terminals?.add(term)
        }
        Log.d("SessionHistory", "startTerminal -> id=${term.id}, sessionId=$sessionId, launchSource=${term.launchSource}")
        return term
    }

    fun updateTerminalDestiny(context: Context, terminalId: String, exitDestiny: String) {
        init(context)
        Log.d("SessionHistory", "updateTerminalDestiny -> terminalId=$terminalId, exitDestiny=$exitDestiny")
        runCatching {
            val cv = ContentValues().apply {
                put("exitDestiny", exitDestiny)
            }
            db?.writableDatabase?.update("terminal", cv, "id = ?", arrayOf(terminalId))
        }
        for (s in current?.sessions.orEmpty()) {
            for (t in s.terminals) {
                if (t.id == terminalId) {
                    (t as TerminalRecord).exitDestiny = exitDestiny
                    return
                }
            }
        }
    }

    fun logCommand(context: Context, sessionId: String, terminalId: String, path: String, cmd: String) {
        Log.d("SessionHistory", "logCommand -> sessionId=$sessionId, terminalId=$terminalId, cmd=$cmd")
        val trimmed = cmd.trim()
        if (trimmed == "ls" || trimmed == "cd" || trimmed.startsWith("ls ") || trimmed.startsWith("cd ")) {
            return
        }

        init(context)
        val limit = org.aarchdroid.dragonterminal.frontend.config.NeoPreference.getCommandLimit()
        val unlimited = limit == 0
        val actualLimit = if (unlimited) Int.MAX_VALUE else limit
        val record = CommandRecord(path = path, cmd = cmd)
        current?.sessions?.find { it.id == sessionId }?.terminals?.find { it.id == terminalId }?.commands?.let { cmds ->
            cmds.add(record)
            if (!unlimited && cmds.size > actualLimit) cmds.subList(0, cmds.size - actualLimit).clear()
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
            if (!unlimited) {
                db?.writableDatabase?.execSQL(
                    "DELETE FROM command WHERE terminalId = ? AND uid NOT IN (SELECT uid FROM command WHERE terminalId = ? ORDER BY uid DESC LIMIT $actualLimit)",
                    arrayOf(terminalId, terminalId)
                )
            }
        }
    }

    fun closeSession(context: Context, sessionId: String, crashReason: String? = null) {
        init(context)
        Log.d("SessionHistory", "closeSession -> sessionId=$sessionId, crashReason=$crashReason, current=null? ${current == null}")

        // Always write to DB first, regardless of in-memory cache state
        runCatching {
            val cv = ContentValues().apply {
                put("closedNormally", if (crashReason == null) 1 else 0)
                put("crashReason", crashReason)
            }
            db?.writableDatabase?.update("session", cv, "id = ?", arrayOf(sessionId))
        }

        // Update in-memory cache if available
        val session = current?.sessions?.find { it.id == sessionId }
        Log.d("SessionHistory", "closeSession -> found in cache? ${session != null}")
        if (session != null) {
            session.closedNormally = crashReason == null
            session.crashReason = crashReason

            val hasActiveSessions = current?.sessions?.any { s -> s.closedNormally == null && s.id != sessionId } ?: false
            if (!hasActiveSessions && current?.sessions?.all { it.closedNormally != null } == true) {
                prefs?.edit()?.putBoolean(KEY_FLAG_ACTIVE, false)?.apply()
                currentSession = null
            }
        }
    }

    fun getHistory(context: Context): SessionHistoryData {
        init(context)
        val flagActive = prefs?.getBoolean(KEY_FLAG_ACTIVE, false) ?: false
        val data = SessionHistoryData(
            date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
            flagActive = flagActive,
            sessions = mutableListOf()
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
                            val tLaunchSource = if (tCursor.isNull(tCursor.getColumnIndexOrThrow("launchSource"))) "" else tCursor.getString(tCursor.getColumnIndexOrThrow("launchSource"))
                            val tExitDestiny = if (tCursor.isNull(tCursor.getColumnIndexOrThrow("exitDestiny"))) "" else tCursor.getString(tCursor.getColumnIndexOrThrow("exitDestiny"))
                            val tIconResId = if (tCursor.isNull(tCursor.getColumnIndexOrThrow("iconResId"))) 0 else tCursor.getInt(tCursor.getColumnIndexOrThrow("iconResId"))
                            val tRecord = TerminalRecord(id = tId, created = tCreated, type = tType,
                                launchSource = tLaunchSource, exitDestiny = tExitDestiny,
                                iconResId = tIconResId, commands = mutableListOf())

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

        current = data
        Log.d("SessionHistory", "getHistory -> loaded ${data.sessions.size} sessions, flagActive=$flagActive")
        return data
    }

    fun deleteSession(context: Context, sessionId: String) {
        init(context)
        current?.sessions?.removeAll { it.id == sessionId }
        runCatching {
            val readDb = db?.readableDatabase ?: return@runCatching
            readDb.rawQuery("SELECT id FROM terminal WHERE sessionId = ?", arrayOf(sessionId)).use { c ->
                while (c.moveToNext()) {
                    val tid = c.getString(0)
                    db?.writableDatabase?.delete("command", "terminalId = ?", arrayOf(tid))
                }
            }
            db?.writableDatabase?.delete("terminal", "sessionId = ?", arrayOf(sessionId))
            db?.writableDatabase?.delete("session", "id = ?", arrayOf(sessionId))
        }
    }

    fun saveNow(context: Context) {
        // SQLite persists immediately — no-op needed
    }

    fun clearAll(context: Context) {
        init(context)
        current = null
        currentSession = null
        runCatching {
            db?.writableDatabase?.execSQL("DELETE FROM command")
            db?.writableDatabase?.execSQL("DELETE FROM terminal")
            db?.writableDatabase?.execSQL("DELETE FROM session")
        }
        prefs?.edit()?.putBoolean(KEY_FLAG_ACTIVE, false)?.apply()
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
