package org.mesos.launcher.home

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Which apps the user opens, kept on the device only: the recent list drives the
 * drawer suggestions and launch counts break ties in search.
 */
class AppUsage private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _recent = MutableStateFlow(readRecent())

    /** App keys, most recently opened first. */
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    private val _counts = MutableStateFlow(readCounts())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    fun recordLaunch(key: String) {
        val recent = (listOf(key) + _recent.value.filter { it != key }).take(MAX_RECENT)
        val counts = _counts.value.toMutableMap().apply { this[key] = (this[key] ?: 0) + 1 }
            .entries.sortedByDescending { it.value }.take(MAX_COUNTS).associate { it.key to it.value }
        _recent.value = recent
        _counts.value = counts
        prefs.edit()
            .putString(KEY_RECENT, JSONArray(recent).toString())
            .putString(KEY_COUNTS, JSONObject(counts).toString())
            .apply()
    }

    fun forget(keys: Set<String>) {
        if (keys.isEmpty()) return
        _recent.value = _recent.value.filter { it !in keys }
        _counts.value = _counts.value.filterKeys { it !in keys }
        prefs.edit()
            .putString(KEY_RECENT, JSONArray(_recent.value).toString())
            .putString(KEY_COUNTS, JSONObject(_counts.value).toString())
            .apply()
    }

    private fun readRecent(): List<String> =
        try {
            val array = JSONArray(prefs.getString(KEY_RECENT, "[]"))
            (0 until array.length()).map { array.getString(it) }
        } catch (e: JSONException) {
            emptyList()
        }

    private fun readCounts(): Map<String, Int> =
        try {
            val json = JSONObject(prefs.getString(KEY_COUNTS, "{}") ?: "{}")
            json.keys().asSequence().associateWith { json.optInt(it) }
        } catch (e: JSONException) {
            emptyMap()
        }

    companion object {
        private const val FILE_NAME = "mesos_launcher_usage"
        private const val KEY_RECENT = "recent"
        private const val KEY_COUNTS = "counts"
        private const val MAX_RECENT = 12
        private const val MAX_COUNTS = 200

        @Volatile
        private var instance: AppUsage? = null

        fun get(context: Context): AppUsage =
            instance ?: synchronized(this) {
                instance ?: AppUsage(context).also { instance = it }
            }
    }
}
