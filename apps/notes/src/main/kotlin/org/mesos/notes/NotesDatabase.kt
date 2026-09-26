package org.mesos.notes

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Note(val id: Long, val title: String, val body: String, val updatedAt: Long)

/** Private SQLite store for MesOS Notes. Call from a background thread. */
internal class NotesDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, "mesos_notes.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE notes (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT NOT NULL, " +
                "body TEXT NOT NULL, " +
                "updated_at INTEGER NOT NULL)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun list(query: String): List<Note> {
        val q = query.trim()
        var selection: String? = null
        var args: Array<String>? = null
        if (q.isNotEmpty()) {
            val pattern = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            selection = "title LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\'"
            args = arrayOf(pattern, pattern)
        }
        readableDatabase.query("notes", COLUMNS, selection, args, null, null, "updated_at DESC").use { c ->
            return buildList { while (c.moveToNext()) add(c.toNote()) }
        }
    }

    fun get(id: Long): Note? =
        readableDatabase.query("notes", COLUMNS, "_id = ?", arrayOf(id.toString()), null, null, null).use { c ->
            if (c.moveToFirst()) c.toNote() else null
        }

    /** Inserts a new note when [id] is null, otherwise updates it. Returns the note id. */
    fun save(id: Long?, title: String, body: String): Long {
        val values = ContentValues().apply {
            put("title", title)
            put("body", body)
            put("updated_at", System.currentTimeMillis())
        }
        return if (id == null) {
            writableDatabase.insertOrThrow("notes", null, values)
        } else {
            writableDatabase.update("notes", values, "_id = ?", arrayOf(id.toString()))
            id
        }
    }

    fun delete(id: Long) {
        writableDatabase.delete("notes", "_id = ?", arrayOf(id.toString()))
    }

    private fun Cursor.toNote() = Note(
        id = getLong(0),
        title = getString(1),
        body = getString(2),
        updatedAt = getLong(3),
    )

    companion object {
        private val COLUMNS = arrayOf("_id", "title", "body", "updated_at")

        @Volatile
        private var instance: NotesDatabase? = null

        fun get(context: Context): NotesDatabase =
            instance ?: synchronized(this) {
                instance ?: NotesDatabase(context.applicationContext).also { instance = it }
            }
    }
}
