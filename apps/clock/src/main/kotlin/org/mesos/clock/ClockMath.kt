package org.mesos.clock

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * An alarm. [days] is a bit set of weekdays (Monday = bit 0 … Sunday = bit 6);
 * 0 means it rings once.
 */
data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Int = 0,
    val enabled: Boolean = true,
    val label: String = "",
    val vibrate: Boolean = true,
) {
    val repeats: Boolean get() = days != 0

    fun ringsOn(day: DayOfWeek): Boolean = days and (1 shl (day.value - 1)) != 0

    companion object {
        const val WEEKDAYS = 0b0011111
        const val WEEKEND = 0b1100000
        const val EVERY_DAY = 0b1111111
    }
}

/** Alarm arithmetic and storage format. Pure Kotlin, unit tested. */
object ClockMath {

    /** The next time [alarm] rings strictly after [now]. */
    fun nextTrigger(alarm: Alarm, now: Long, zone: ZoneId): Long {
        val current = Instant.ofEpochMilli(now).atZone(zone)
        val time = LocalTime.of(alarm.hour, alarm.minute)
        for (offset in 0..7) {
            val date: LocalDate = current.toLocalDate().plusDays(offset.toLong())
            if (alarm.repeats && !alarm.ringsOn(date.dayOfWeek)) continue
            // atZone resolves times that do not exist (clock change) to the valid time after them.
            val candidate: ZonedDateTime = date.atTime(time).atZone(zone)
            val millis = candidate.toInstant().toEpochMilli()
            if (millis > now) return millis
        }
        // Unreachable for valid alarms; ring in a day rather than never.
        return now + 24 * 60 * 60 * 1000L
    }

    /** Hours and minutes until [trigger] (rounded up to the next minute). */
    fun untilTrigger(trigger: Long, now: Long): Pair<Long, Long> {
        val minutes = ((trigger - now) + 59_999) / 60_000
        return minutes / 60 to minutes % 60
    }

    fun toJson(alarms: List<Alarm>): String = JSONArray().apply {
        alarms.forEach { a ->
            put(
                JSONObject()
                    .put("id", a.id).put("hour", a.hour).put("minute", a.minute).put("days", a.days)
                    .put("enabled", a.enabled).put("label", a.label).put("vibrate", a.vibrate),
            )
        }
    }.toString()

    fun fromJson(text: String?): List<Alarm> =
        try {
            val array = JSONArray(text ?: "[]")
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val hour = o.optInt("hour", -1)
                val minute = o.optInt("minute", -1)
                if (hour !in 0..23 || minute !in 0..59 || o.optInt("id", 0) <= 0) return@mapNotNull null
                Alarm(
                    id = o.getInt("id"),
                    hour = hour,
                    minute = minute,
                    days = o.optInt("days", 0) and Alarm.EVERY_DAY,
                    enabled = o.optBoolean("enabled", true),
                    label = o.optString("label"),
                    vibrate = o.optBoolean("vibrate", true),
                )
            }
        } catch (e: JSONException) {
            emptyList()
        }

    /** Formats a stopwatch/timer duration as h:mm:ss or mm:ss.cc. */
    fun formatDuration(millis: Long, withHundredths: Boolean): String {
        val total = millis.coerceAtLeast(0)
        val hours = total / 3_600_000
        val minutes = (total / 60_000) % 60
        val seconds = (total / 1000) % 60
        val hundredths = (total / 10) % 100
        return when {
            hours > 0 -> "%d:%02d:%02d".format(hours, minutes, seconds)
            withHundredths -> "%02d:%02d.%02d".format(minutes, seconds, hundredths)
            else -> "%02d:%02d".format(minutes, seconds)
        }
    }
}
