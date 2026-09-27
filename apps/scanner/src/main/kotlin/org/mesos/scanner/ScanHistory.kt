package org.mesos.scanner

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A code scanned earlier. */
data class ScanRecord(val text: String, val format: String, val time: Long)

/** The last scans, kept on the device only. */
internal class ScanHistory private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("mesos_scanner", Context.MODE_PRIVATE)
    private val _records = MutableStateFlow(read())
    val records: StateFlow<List<ScanRecord>> = _records.asStateFlow()

    fun add(code: DecodedCode, time: Long = System.currentTimeMillis()) {
        val record = ScanRecord(code.text, code.format.name, time)
        val updated = (listOf(record) + _records.value.filterNot { it.text == code.text }).take(MAX)
        write(updated)
    }

    fun remove(record: ScanRecord) = write(_records.value - record)

    fun clear() = write(emptyList())

    private fun write(records: List<ScanRecord>) {
        val array = JSONArray()
        records.forEach { array.put(JSONObject().put("text", it.text).put("format", it.format).put("time", it.time)) }
        prefs.edit().putString(KEY, array.toString()).apply()
        _records.value = records
    }

    private fun read(): List<ScanRecord> =
        try {
            val array = JSONArray(prefs.getString(KEY, null) ?: return emptyList())
            List(array.length()) { i ->
                val item = array.getJSONObject(i)
                ScanRecord(item.getString("text"), item.optString("format"), item.optLong("time"))
            }
        } catch (e: JSONException) {
            emptyList()
        }

    companion object {
        private const val KEY = "history"
        private const val MAX = 30

        @Volatile
        private var instance: ScanHistory? = null

        fun get(context: Context): ScanHistory =
            instance ?: synchronized(this) {
                instance ?: ScanHistory(context.applicationContext).also { instance = it }
            }
    }
}
