package org.mesos.calendar

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.StateFlow
import org.mesos.core.MesOSApps
import java.time.LocalDate
import java.time.ZoneId

/** What other MesOS components (the Home agenda widget) may read from MesOS Calendar. */
object CalendarFeed {

    fun changes(context: Context): StateFlow<Long> = EventStore.get(context).changes

    /** Occurrences from [from] until the end of the day after [from], at most [limit]. */
    suspend fun upcoming(context: Context, from: Long, limit: Int): List<Occurrence> {
        val zone = ZoneId.systemDefault()
        val today = java.time.Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        val end = CalendarMath.dayEnd(today.plusDays(1), zone)
        return CalendarMath.agenda(EventStore.get(context).all(), from, end, zone)
            .filter { it.end > from }
            .take(limit)
    }

    fun isToday(occurrence: Occurrence): Boolean =
        java.time.Instant.ofEpochMilli(occurrence.start).atZone(ZoneId.systemDefault()).toLocalDate() <= LocalDate.now()

    fun openIntent(context: Context, day: Long? = null): Intent =
        MesOSApps.launchIntent(context, MesOSApps.CALENDAR)
            .apply { if (day != null) putExtra(CalendarActivity.EXTRA_DAY, day) }
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
}
