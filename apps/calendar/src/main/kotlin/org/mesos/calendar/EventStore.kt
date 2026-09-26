package org.mesos.calendar

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** MesOS Calendar events, stored on the device in SQLite. */
class EventStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "mesos_calendar.db", null, 1) {

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val _changes = MutableStateFlow(0L)

    /** Increments after every write. */
    val changes: StateFlow<Long> = _changes.asStateFlow()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE events (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                start_ms INTEGER NOT NULL,
                end_ms INTEGER NOT NULL,
                all_day INTEGER NOT NULL DEFAULT 0,
                location TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                color INTEGER NOT NULL DEFAULT 0,
                reminder INTEGER NOT NULL DEFAULT -1,
                repeat TEXT NOT NULL DEFAULT 'none'
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX events_start ON events(start_ms)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    suspend fun all(): List<Event> = withContext(Dispatchers.IO) {
        readableDatabase.query("events", COLUMNS, null, null, null, null, "start_ms").use { c ->
            buildList { while (c.moveToNext()) add(c.toEvent()) }
        }
    }

    suspend fun get(id: Long): Event? = withContext(Dispatchers.IO) {
        readableDatabase.query("events", COLUMNS, "_id = ?", arrayOf(id.toString()), null, null, null).use { c ->
            if (c.moveToFirst()) c.toEvent() else null
        }
    }

    /** Inserts (id 0) or updates [event]; returns its id. Reschedules reminders. */
    suspend fun save(event: Event): Long = withContext(Dispatchers.IO) {
        val id = mutex.withLock {
            val values = ContentValues().apply {
                put("title", event.title)
                put("start_ms", event.start)
                put("end_ms", event.end)
                put("all_day", if (event.allDay) 1 else 0)
                put("location", event.location)
                put("notes", event.notes)
                put("color", event.color)
                put("reminder", event.reminderMinutes)
                put("repeat", event.repeat.id)
            }
            if (event.id > 0 && writableDatabase.update("events", values, "_id = ?", arrayOf(event.id.toString())) > 0) {
                event.id
            } else {
                writableDatabase.insertOrThrow("events", null, values)
            }
        }
        changed()
        id
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        mutex.withLock { writableDatabase.delete("events", "_id = ?", arrayOf(id.toString())) }
        changed()
    }

    private suspend fun changed() {
        _changes.update { it + 1 }
        ReminderScheduler.reschedule(appContext, all())
    }

    private fun Cursor.toEvent() = Event(
        id = getLong(0),
        title = getString(1),
        start = getLong(2),
        end = getLong(3),
        allDay = getInt(4) == 1,
        location = getString(5),
        notes = getString(6),
        color = getInt(7),
        reminderMinutes = getInt(8),
        repeat = Repeat.fromId(getString(9)),
    )

    companion object {
        private val COLUMNS = arrayOf("_id", "title", "start_ms", "end_ms", "all_day", "location", "notes", "color", "reminder", "repeat")

        @Volatile
        private var instance: EventStore? = null

        fun get(context: Context): EventStore =
            instance ?: synchronized(this) {
                instance ?: EventStore(context).also { instance = it }
            }
    }
}
