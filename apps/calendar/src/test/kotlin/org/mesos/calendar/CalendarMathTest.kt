package org.mesos.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class CalendarMathTest {

    private val zone = ZoneId.of("Europe/Istanbul")
    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun event(start: Long, hours: Int = 1, repeat: Repeat = Repeat.NONE, reminder: Int = Event.NO_REMINDER) =
        Event(id = 1, title = "E", start = start, end = start + hours * 3_600_000L, repeat = repeat, reminderMinutes = reminder)

    @Test
    fun singleEventInsideAndOutsideRange() {
        val e = event(at(2026, 9, 26, 19))
        assertEquals(1, CalendarMath.occurrences(e, at(2026, 9, 26), at(2026, 9, 27), zone).size)
        assertTrue(CalendarMath.occurrences(e, at(2026, 9, 27), at(2026, 9, 28), zone).isEmpty())
    }

    @Test
    fun eventSpanningMidnightAppearsOnBothDays() {
        val e = event(at(2026, 9, 26, 23), hours = 2)
        assertEquals(1, CalendarMath.occurrences(e, at(2026, 9, 27), at(2026, 9, 28), zone).size)
    }

    @Test
    fun dailyRepeatsEveryDayFromStart() {
        val e = event(at(2026, 9, 1, 8), repeat = Repeat.DAILY)
        val list = CalendarMath.occurrences(e, at(2026, 9, 10), at(2026, 9, 13), zone)
        assertEquals(listOf(at(2026, 9, 10, 8), at(2026, 9, 11, 8), at(2026, 9, 12, 8)), list.map { it.start })
        assertTrue(CalendarMath.occurrences(e, at(2026, 8, 1), at(2026, 9, 1), zone).isEmpty())
    }

    @Test
    fun weeklyKeepsWeekday() {
        val e = event(at(2026, 9, 7, 10), repeat = Repeat.WEEKLY) // Monday
        val list = CalendarMath.occurrences(e, at(2026, 10, 1), at(2026, 11, 1), zone)
        assertEquals(4, list.size)
        assertTrue(list.all { java.time.Instant.ofEpochMilli(it.start).atZone(zone).dayOfWeek == DayOfWeek.MONDAY })
    }

    @Test
    fun monthlyOn31stSkipsShortMonths() {
        val e = event(at(2026, 1, 31, 9), repeat = Repeat.MONTHLY)
        val list = CalendarMath.occurrences(e, at(2026, 1, 1), at(2026, 6, 1), zone)
        assertEquals(listOf(at(2026, 1, 31, 9), at(2026, 3, 31, 9), at(2026, 5, 31, 9)), list.map { it.start })
    }

    @Test
    fun yearlyOnLeapDaySkipsOtherYears() {
        val e = event(at(2024, 2, 29, 12), repeat = Repeat.YEARLY)
        val list = CalendarMath.occurrences(e, at(2024, 1, 1), at(2029, 1, 1), zone)
        assertEquals(listOf(at(2024, 2, 29, 12), at(2028, 2, 29, 12)), list.map { it.start })
    }

    @Test
    fun agendaSortsByStartWithAllDayFirst() {
        val allDay = Event(2, "All day", at(2026, 9, 26), at(2026, 9, 27), allDay = true)
        val morning = Event(3, "Morning", at(2026, 9, 26, 9), at(2026, 9, 26, 10))
        val midnight = Event(4, "Midnight", at(2026, 9, 26), at(2026, 9, 26, 1))
        val agenda = CalendarMath.agenda(listOf(morning, midnight, allDay), at(2026, 9, 26), at(2026, 9, 27), zone)
        assertEquals(listOf("All day", "Midnight", "Morning"), agenda.map { it.event.title })
    }

    @Test
    fun nextReminderPicksEarliestFutureReminder() {
        val now = at(2026, 9, 26, 12)
        val soon = event(at(2026, 9, 26, 14), reminder = 30).copy(id = 10)
        val later = event(at(2026, 9, 26, 13), reminder = 0).copy(id = 11)
        val past = event(at(2026, 9, 26, 12, 10), reminder = 15).copy(id = 12) // reminder was at 11:55
        val none = event(at(2026, 9, 26, 12, 5)).copy(id = 13)
        val next = CalendarMath.nextReminder(listOf(soon, later, past, none), now, zone)!!
        assertEquals(11L, next.first.event.id)
        assertEquals(at(2026, 9, 26, 13), next.second)
    }

    @Test
    fun nextReminderOfRepeatingEvent() {
        val now = at(2026, 9, 26, 9)
        val daily = event(at(2026, 9, 1, 8), repeat = Repeat.DAILY, reminder = 10)
        val next = CalendarMath.nextReminder(listOf(daily), now, zone)!!
        assertEquals(at(2026, 9, 27, 7, 50), next.second)
        assertNull(CalendarMath.nextReminder(emptyList(), now, zone))
    }

    @Test
    fun monthGridStartsOnLocaleFirstDay() {
        val tr = CalendarMath.monthGrid(LocalDate.of(2026, 9, 15), Locale.forLanguageTag("tr-TR"))
        assertEquals(42, tr.size)
        assertEquals(DayOfWeek.MONDAY, tr.first().dayOfWeek)
        assertTrue(tr.contains(LocalDate.of(2026, 9, 1)))
        val us = CalendarMath.monthGrid(LocalDate.of(2026, 9, 15), Locale.US)
        assertEquals(DayOfWeek.SUNDAY, us.first().dayOfWeek)
    }
}
