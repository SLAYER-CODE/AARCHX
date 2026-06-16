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

    @Volatile private var current: SessionHistoryData? = null
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
        if (trimmed == "ls" || trimmed == "cd" || trimmed == "clear" ||
            trimmed.startsWith("ls ") || trimmed.startsWith("cd ")) {
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

        // Auto-clear old sessions when closing the 2nd+ terminal of a new day
        runCatching {
            val readDb = db?.readableDatabase ?: return@runCatching
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val todayStart = cal.timeInMillis

            readDb.rawQuery(
                "SELECT COUNT(*) FROM terminal WHERE created >= ?",
                arrayOf(todayStart.toString())
            )?.use { c ->
                if (c.moveToFirst() && c.getInt(0) >= 2) {
                    readDb.rawQuery(
                        "SELECT COUNT(*) FROM session WHERE created < ?",
                        arrayOf(todayStart.toString())
                    )?.use { c2 ->
                        if (c2.moveToFirst() && c2.getInt(0) > 0) {
                            Log.d("SessionHistory", "closeSession: auto-clearing sessions from before today")
                            db?.writableDatabase?.execSQL(
                                "DELETE FROM command WHERE terminalId IN (SELECT id FROM terminal WHERE sessionId IN (SELECT id FROM session WHERE created < ?))",
                                arrayOf(todayStart.toString())
                            )
                            db?.writableDatabase?.execSQL(
                                "DELETE FROM terminal WHERE sessionId IN (SELECT id FROM session WHERE created < ?)",
                                arrayOf(todayStart.toString())
                            )
                            db?.writableDatabase?.execSQL(
                                "DELETE FROM session WHERE created < ?",
                                arrayOf(todayStart.toString())
                            )
                            current?.sessions?.removeAll { it.created < todayStart }
                        }
                    }
                }
            }
        }

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

    fun getHistoryPage(context: Context, offset: Int = 0, limit: Int = 4): List<SessionRecord> {
        init(context)
        val sessionMap = mutableMapOf<String, SessionRecord>()
        val terminalMap = mutableMapOf<String, TerminalRecord>()
        val pages = mutableListOf<SessionRecord>()

        runCatching {
            val readDb = db?.readableDatabase ?: return@runCatching
            readDb.rawQuery("""
                SELECT s.id s_id, s.created s_created, s.closedNormally, s.crashReason,
                       t.id t_id, t.created t_created, t.type, t.launchSource,
                       t.exitDestiny, t.iconResId,
                       c.path, c.cmd, c.status, c.ord
                FROM (
                    SELECT * FROM session ORDER BY created DESC LIMIT ? OFFSET ?
                ) s
                LEFT JOIN terminal t ON t.sessionId = s.id
                LEFT JOIN command c ON c.terminalId = t.id
                ORDER BY s.created DESC, t.created ASC, c.ord ASC
            """, arrayOf(limit.toString(), offset.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    val sId = cursor.getString(cursor.getColumnIndexOrThrow("s_id")) ?: continue
                    val session = sessionMap.getOrPut(sId) {
                        SessionRecord(
                            id = sId,
                            created = cursor.getLong(cursor.getColumnIndexOrThrow("s_created")),
                            closedNormally = if (cursor.isNull(cursor.getColumnIndexOrThrow("closedNormally"))) null
                                else cursor.getInt(cursor.getColumnIndexOrThrow("closedNormally")) == 1,
                            crashReason = cursor.getString(cursor.getColumnIndexOrThrow("crashReason")),
                            terminals = mutableListOf()
                        ).also { pages.add(it) }
                    }

                    val tId = cursor.getString(cursor.getColumnIndexOrThrow("t_id")) ?: continue
                    val tKey = "${sId}_${tId}"
                    val terminal = terminalMap.getOrPut(tKey) {
                        TerminalRecord(
                            id = tId,
                            created = cursor.getLong(cursor.getColumnIndexOrThrow("t_created")),
                            type = cursor.getString(cursor.getColumnIndexOrThrow("type")) ?: "",
                            launchSource = cursor.getString(cursor.getColumnIndexOrThrow("launchSource")) ?: "",
                            exitDestiny = cursor.getString(cursor.getColumnIndexOrThrow("exitDestiny")) ?: "",
                            iconResId = cursor.getInt(cursor.getColumnIndexOrThrow("iconResId")),
                            commands = mutableListOf()
                        ).also { session.terminals.add(it) }
                    }

                    val path = cursor.getString(cursor.getColumnIndexOrThrow("path"))
                    if (path != null) {
                        terminal.commands.add(CommandRecord(
                            path = path,
                            cmd = cursor.getString(cursor.getColumnIndexOrThrow("cmd")) ?: "",
                            status = cursor.getInt(cursor.getColumnIndexOrThrow("status"))
                        ))
                    }
                }
            }
        }

        // Remove terminals with no commands and sessions with no terminals (same as original logic)
        for (session in pages) {
            session.terminals.removeAll { it.commands.isEmpty() }
        }
        pages.removeAll { it.terminals.isEmpty() }
        return pages
    }

    fun getHistoryCount(context: Context): Int {
        current?.let {
            Log.d("SessionHistory", "getHistoryCount -> from cache: ${it.sessions.size}")
            return it.sessions.size
        }
        init(context)
        val count = runCatching {
            db?.readableDatabase?.rawQuery("""
                SELECT COUNT(*) FROM session s
                WHERE EXISTS (
                  SELECT 1 FROM command c
                  JOIN terminal t ON c.terminalId = t.id
                  WHERE t.sessionId = s.id
                )
            """.trimIndent(), null)?.use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
        }.getOrNull() ?: 0
        Log.d("SessionHistory", "getHistoryCount -> from SQL: $count")
        return count
    }

    fun getHistory(context: Context): SessionHistoryData {
        init(context)
        val flagActive = prefs?.getBoolean(KEY_FLAG_ACTIVE, false) ?: false
        val all = getHistoryPage(context, 0, Int.MAX_VALUE)
        val data = SessionHistoryData(
            date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
            flagActive = flagActive,
            sessions = all.toMutableList()
        )
        current = data
        Log.d("SessionHistory", "getHistory -> loaded ${data.sessions.size} sessions, flagActive=$flagActive")
        return data
    }

    fun deleteSession(context: Context, sessionId: String) {
        init(context)
        Log.d("SessionHistory", "deleteSession: sessionId=$sessionId, current=null? ${current == null}, db=null? ${db == null}")
        val removed = current?.sessions?.removeAll { it.id == sessionId }
        Log.d("SessionHistory", "deleteSession: removed from cache=$removed, remaining=${current?.sessions?.size}")
        runCatching {
            val writeDb = db?.writableDatabase
            if (writeDb == null) {
                Log.e("SessionHistory", "deleteSession: writableDatabase is null!")
                return@runCatching
            }
            val readDb = db?.readableDatabase ?: run {
                Log.e("SessionHistory", "deleteSession: readableDatabase is null!")
                return@runCatching
            }
            readDb.rawQuery("SELECT id FROM terminal WHERE sessionId = ?", arrayOf(sessionId)).use { c ->
                var terminalCount = 0
                while (c.moveToNext()) {
                    val tid = c.getString(0)
                    val cmdDeleted = writeDb.delete("command", "terminalId = ?", arrayOf(tid))
                    Log.d("SessionHistory", "deleteSession: deleted terminal=$tid commands=$cmdDeleted")
                    terminalCount++
                }
                Log.d("SessionHistory", "deleteSession: found $terminalCount terminals for sessionId=$sessionId")
            }
            val termDeleted = writeDb.delete("terminal", "sessionId = ?", arrayOf(sessionId))
            val sessDeleted = writeDb.delete("session", "id = ?", arrayOf(sessionId))
            Log.d("SessionHistory", "deleteSession: terminals_deleted=$termDeleted session_deleted=$sessDeleted")
        }.onFailure { e ->
            Log.e("SessionHistory", "deleteSession: DB error", e)
        }
    }

    fun hasUnclosedSessions(context: Context): Boolean {
        init(context)
        return runCatching {
            db?.readableDatabase?.rawQuery("SELECT COUNT(*) FROM session WHERE closedNormally IS NULL", null)?.use {
                it.moveToFirst() && it.getInt(0) > 0
            }
        }.getOrNull() ?: false
    }

    fun saveNow(context: Context) {
        // SQLite persists immediately — no-op needed
    }

    fun clearAll(context: Context) {
        init(context)
        Log.d("SessionHistory", "clearAll: db=null? ${db == null}, writable=null? ${db?.writableDatabase == null}")
        current = null
        currentSession = null
        runCatching {
            val writeDb = db?.writableDatabase
            if (writeDb == null) {
                Log.e("SessionHistory", "clearAll: writableDatabase is null, cannot delete!")
                return@runCatching
            }
            writeDb.execSQL("DELETE FROM command")
            writeDb.execSQL("DELETE FROM terminal")
            writeDb.execSQL("DELETE FROM session")
            Log.d("SessionHistory", "clearAll: DB tables truncated successfully")
        }.onFailure { e ->
            Log.e("SessionHistory", "clearAll: DB error", e)
        }
        prefs?.edit()?.putBoolean(KEY_FLAG_ACTIVE, false)?.apply()
        val afterCount = getHistoryCount(context)
        Log.d("SessionHistory", "clearAll: done, getHistoryCount=$afterCount")
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
