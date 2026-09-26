package org.mesos.browser

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.mesos.core.log.MesOSLog

data class Bookmark(val url: String, val title: String, val created: Long)

data class Visit(val id: Long, val url: String, val title: String, val time: Long)

/** Bookmarks and history, kept in a private database on this device. */
internal class BrowserStore private constructor(context: Context) :
    SQLiteOpenHelper(context, "mesos_browser.db", null, 1) {

    private val prefs = context.getSharedPreferences("mesos_browser", Context.MODE_PRIVATE)

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _engine = MutableStateFlow(SearchEngine.fromId(prefs.getString(KEY_ENGINE, null)))
    val engine: StateFlow<SearchEngine> = _engine.asStateFlow()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE bookmarks (url TEXT PRIMARY KEY, title TEXT NOT NULL, created INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL, title TEXT NOT NULL, time INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX history_time ON history(time)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** Loads bookmarks; call once off the main thread. */
    fun load() {
        _bookmarks.value = queryBookmarks()
    }

    fun setEngine(engine: SearchEngine) {
        prefs.edit().putString(KEY_ENGINE, engine.id).apply()
        _engine.value = engine
    }

    fun isBookmarked(url: String): Boolean = _bookmarks.value.any { it.url == url }

    fun toggleBookmark(url: String, title: String) {
        guarded {
            if (isBookmarked(url)) {
                writableDatabase.delete("bookmarks", "url = ?", arrayOf(url))
            } else {
                writableDatabase.insertWithOnConflict(
                    "bookmarks",
                    null,
                    ContentValues().apply {
                        put("url", url)
                        put("title", title.ifBlank { UrlPolicy.displayHost(url) })
                        put("created", System.currentTimeMillis())
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
        }
        _bookmarks.value = queryBookmarks()
    }

    fun removeBookmark(bookmark: Bookmark) {
        guarded { writableDatabase.delete("bookmarks", "url = ?", arrayOf(bookmark.url)) }
        _bookmarks.value = queryBookmarks()
    }

    fun recordVisit(url: String, title: String) {
        if (UrlPolicy.classify(url) != LinkAction.LOAD) return
        guarded {
            val db = writableDatabase
            // One entry per page and minute is enough; update the title of a repeat visit.
            val recent = System.currentTimeMillis() - 60_000
            val updated = db.update(
                "history",
                ContentValues().apply {
                    put("title", title)
                    put("time", System.currentTimeMillis())
                },
                "url = ? AND time > ?",
                arrayOf(url, recent.toString()),
            )
            if (updated == 0) {
                db.insert(
                    "history",
                    null,
                    ContentValues().apply {
                        put("url", url)
                        put("title", title)
                        put("time", System.currentTimeMillis())
                    },
                )
                db.execSQL("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY time DESC LIMIT $MAX_HISTORY)")
            }
        }
    }

    fun history(limit: Int = MAX_HISTORY): List<Visit> {
        val result = mutableListOf<Visit>()
        guarded {
            readableDatabase.query("history", arrayOf("id", "url", "title", "time"), null, null, null, null, "time DESC", limit.toString()).use { c ->
                while (c.moveToNext()) result += Visit(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))
            }
        }
        return result
    }

    fun deleteVisit(visit: Visit) {
        guarded { writableDatabase.delete("history", "id = ?", arrayOf(visit.id.toString())) }
    }

    fun clearHistory() {
        guarded { writableDatabase.delete("history", null, null) }
    }

    /** Pages whose address or title contains [query], for address bar suggestions. */
    fun suggestions(query: String, limit: Int = 6): List<Visit> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val like = "%" + q.replace("%", "").replace("_", "") + "%"
        val result = mutableListOf<Visit>()
        guarded {
            readableDatabase.rawQuery(
                "SELECT MAX(id), url, title, MAX(time) AS t FROM history WHERE url LIKE ? OR title LIKE ? GROUP BY url ORDER BY COUNT(*) DESC, t DESC LIMIT $limit",
                arrayOf(like, like),
            ).use { c ->
                while (c.moveToNext()) result += Visit(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))
            }
        }
        return result
    }

    /** Addresses of the open tabs, restored after MesOS was closed by Android. */
    var openTabs: List<String>
        get() = try {
            val array = JSONArray(prefs.getString(KEY_TABS, "[]"))
            List(array.length()) { array.getString(it) }
        } catch (e: JSONException) {
            emptyList()
        }
        set(value) {
            prefs.edit().putString(KEY_TABS, JSONArray(value.take(MAX_TABS)).toString()).apply()
        }

    private fun queryBookmarks(): List<Bookmark> {
        val result = mutableListOf<Bookmark>()
        guarded {
            readableDatabase.query("bookmarks", arrayOf("url", "title", "created"), null, null, null, null, "created DESC").use { c ->
                while (c.moveToNext()) result += Bookmark(c.getString(0), c.getString(1), c.getLong(2))
            }
        }
        return result
    }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: SQLiteException) {
            MesOSLog.w(MesOSLog.SYSTEM, "Browser database error", e)
        }
    }

    companion object {
        private const val KEY_ENGINE = "search_engine"
        private const val KEY_TABS = "open_tabs"
        private const val MAX_HISTORY = 500
        const val MAX_TABS = 20

        @Volatile
        private var instance: BrowserStore? = null

        fun get(context: Context): BrowserStore =
            instance ?: synchronized(this) {
                instance ?: BrowserStore(context.applicationContext).also { instance = it }
            }
    }
}
