package org.mesos.calendar

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

/** How an event repeats. */
enum class Repeat(val id: String) {
    NONE("none"),
    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    YEARLY("yearly");

    companion object {
        fun fromId(id: String?): Repeat = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/** A MesOS calendar event. Times are epoch milliseconds; all-day events use local midnights. */
data class Event(
    val id: Long,
    val title: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean = false,
    val location: String = "",
    val notes: String = "",
    val color: Int = 0,
    /** Minutes before the start to remind, or [NO_REMINDER]. */
    val reminderMinutes: Int = NO_REMINDER,
    val repeat: Repeat = Repeat.NONE,
) {
    companion object {
        const val NO_REMINDER = -1
    }
}

/** One concrete occurrence of a (possibly repeating) event. */
data class Occurrence(val event: Event, val start: Long, val end: Long)

/** Calendar arithmetic. Pure Kotlin (java.time), unit tested. */
object CalendarMath {

    /** Occurrences of [event] overlapping [rangeStart, rangeEnd), in time order. */
    fun occurrences(event: Event, rangeStart: Long, rangeEnd: Long, zone: ZoneId): List<Occurrence> {
        val duration = (event.end - event.start).coerceAtLeast(0)
        if (event.repeat == Repeat.NONE) {
            return if (overlaps(event.start, event.start + duration, rangeStart, rangeEnd)) {
                listOf(Occurrence(event, event.start, event.start + duration))
            } else {
                emptyList()
            }
        }
        val first = ZonedDateTime.ofInstant(Instant.ofEpochMilli(event.start), zone)
        val result = mutableListOf<Occurrence>()
        // Jump close to the range instead of walking from the first occurrence.
        var index = when (event.repeat) {
            Repeat.DAILY -> ChronoUnit.DAYS.between(first, ZonedDateTime.ofInstant(Instant.ofEpochMilli(rangeStart - duration), zone)).coerceAtLeast(0)
            Repeat.WEEKLY -> ChronoUnit.WEEKS.between(first, ZonedDateTime.ofInstant(Instant.ofEpochMilli(rangeStart - duration), zone)).coerceAtLeast(0)
            else -> 0L
        } - 1
        index = index.coerceAtLeast(0)
        var guard = 0
        while (guard++ < MAX_STEPS) {
            val candidate = step(first, event.repeat, index)
            index++
            if (candidate == null) continue
            val start = candidate.toInstant().toEpochMilli()
            if (start >= rangeEnd) break
            if (overlaps(start, start + duration, rangeStart, rangeEnd)) result += Occurrence(event, start, start + duration)
        }
        return result
    }

    /** All occurrences of [events] in the range, sorted by start (all-day first on the same day). */
    fun agenda(events: List<Event>, rangeStart: Long, rangeEnd: Long, zone: ZoneId): List<Occurrence> =
        events.flatMap { occurrences(it, rangeStart, rangeEnd, zone) }
            .sortedWith(compareBy<Occurrence> { it.start }.thenByDescending { it.event.allDay }.thenBy { it.event.title })

    /** The next reminder after [now]: (occurrence, time the reminder fires). */
    fun nextReminder(events: List<Event>, now: Long, zone: ZoneId): Pair<Occurrence, Long>? {
        val horizon = now + REMINDER_HORIZON_MS
        return events.filter { it.reminderMinutes >= 0 }
            .flatMap { event ->
                val lead = event.reminderMinutes * 60_000L
                occurrences(event, now + lead, horizon + lead, zone)
                    .map { it to (it.start - lead) }
                    .filter { (_, at) -> at > now }
            }
            .minByOrNull { it.second }
    }

    /** The 42 days (6 weeks) of the month grid containing [month], starting on the locale's first weekday. */
    fun monthGrid(month: LocalDate, locale: Locale): List<LocalDate> {
        val firstOfMonth = month.withDayOfMonth(1)
        val firstDay: DayOfWeek = WeekFields.of(locale).firstDayOfWeek
        val shift = (firstOfMonth.dayOfWeek.value - firstDay.value + 7) % 7
        val start = firstOfMonth.minusDays(shift.toLong())
        return List(42) { start.plusDays(it.toLong()) }
    }

    fun dayStart(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun dayEnd(date: LocalDate, zone: ZoneId): Long = dayStart(date.plusDays(1), zone)

    private fun step(first: ZonedDateTime, repeat: Repeat, index: Long): ZonedDateTime? = when (repeat) {
        Repeat.NONE -> if (index == 0L) first else null
        Repeat.DAILY -> first.plusDays(index)
        Repeat.WEEKLY -> first.plusWeeks(index)
        // A monthly event on the 31st skips shorter months; a yearly one on 29 Feb skips non-leap years.
        Repeat.MONTHLY -> first.plusMonths(index).takeIf { it.dayOfMonth == first.dayOfMonth }
        Repeat.YEARLY -> first.plusYears(index).takeIf { it.dayOfMonth == first.dayOfMonth }
    }

    private fun overlaps(start: Long, end: Long, rangeStart: Long, rangeEnd: Long): Boolean =
        if (end == start) start in rangeStart until rangeEnd else start < rangeEnd && end > rangeStart

    private const val MAX_STEPS = 5000
    private const val REMINDER_HORIZON_MS = 400L * 24 * 60 * 60 * 1000
}
