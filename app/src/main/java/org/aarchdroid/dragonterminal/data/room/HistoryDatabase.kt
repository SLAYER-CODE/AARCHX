package org.aarchdroid.dragonterminal.data.room

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class HistoryDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE session (
                id TEXT PRIMARY KEY,
                created INTEGER NOT NULL,
                closedNormally INTEGER,
                crashReason TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE terminal (
                id TEXT PRIMARY KEY,
                created INTEGER NOT NULL,
                type TEXT NOT NULL,
                launchSource TEXT DEFAULT '',
                exitDestiny TEXT DEFAULT '',
                iconResId INTEGER DEFAULT 0,
                sessionId TEXT NOT NULL,
                FOREIGN KEY (sessionId) REFERENCES session(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_terminal_sessionId ON terminal(sessionId)")
        db.execSQL(
            """
            CREATE TABLE command (
                uid INTEGER PRIMARY KEY AUTOINCREMENT,
                path TEXT NOT NULL,
                cmd TEXT NOT NULL,
                status INTEGER DEFAULT 0,
                terminalId TEXT NOT NULL,
                ord INTEGER NOT NULL,
                FOREIGN KEY (terminalId) REFERENCES terminal(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_command_terminalId ON command(terminalId)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE terminal ADD COLUMN launchSource TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE terminal ADD COLUMN exitDestiny TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE terminal ADD COLUMN iconResId INTEGER DEFAULT 0")
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    companion object {
        private const val DATABASE_NAME = "session_history.db"
        private const val DATABASE_VERSION = 2

        @Volatile
        private var INSTANCE: HistoryDatabase? = null

        fun getInstance(context: Context): HistoryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HistoryDatabase(context).also { INSTANCE = it }
            }
        }
    }
}
