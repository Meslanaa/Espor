package org.mesos.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ClockMathTest {

    private val zone = ZoneId.of("Europe/Istanbul")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun oneShotLaterToday() {
        val now = at(2026, 9, 26, 6, 0) // Saturday
        assertEquals(at(2026, 9, 26, 7, 30), ClockMath.nextTrigger(Alarm(1, 7, 30), now, zone))
    }

    @Test
    fun oneShotTomorrowWhenTimePassed() {
        val now = at(2026, 9, 26, 8, 0)
        assertEquals(at(2026, 9, 27, 7, 30), ClockMath.nextTrigger(Alarm(1, 7, 30), now, zone))
    }

    @Test
    fun exactlyNowRingsNextTime() {
        val now = at(2026, 9, 26, 7, 30)
        assertEquals(at(2026, 9, 27, 7, 30), ClockMath.nextTrigger(Alarm(1, 7, 30), now, zone))
    }

    @Test
    fun weekdaysSkipWeekend() {
        val now = at(2026, 9, 26, 6, 0) // Saturday
        val next = ClockMath.nextTrigger(Alarm(1, 7, 0, days = Alarm.WEEKDAYS), now, zone)
        assertEquals(at(2026, 9, 28, 7, 0), next) // Monday
        assertEquals(DayOfWeek.MONDAY, Instant.ofEpochMilli(next).atZone(zone).dayOfWeek)
    }

    @Test
    fun weekendOnly() {
        val now = at(2026, 9, 27, 10, 0) // Sunday after the alarm
        assertEquals(at(2026, 10, 3, 9, 0), ClockMath.nextTrigger(Alarm(1, 9, 0, days = Alarm.WEEKEND), now, zone))
    }

    @Test
    fun ringsOnChecksBits() {
        val alarm = Alarm(1, 7, 0, days = (1 shl 0) or (1 shl 6))
        assertTrue(alarm.ringsOn(DayOfWeek.MONDAY))
        assertTrue(alarm.ringsOn(DayOfWeek.SUNDAY))
        assertTrue(!alarm.ringsOn(DayOfWeek.WEDNESDAY))
    }

    @Test
    fun daylightSavingGapMovesForward() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 29 March 2026: clocks jump from 02:00 to 03:00 in Berlin.
        val now = LocalDateTime.of(2026, 3, 29, 1, 0).atZone(berlin).toInstant().toEpochMilli()
        val next = ClockMath.nextTrigger(Alarm(1, 2, 30), now, berlin)
        assertEquals(LocalDateTime.of(2026, 3, 29, 3, 30), Instant.ofEpochMilli(next).atZone(berlin).toLocalDateTime())
    }

    @Test
    fun untilTriggerRoundsUp() {
        assertEquals(7L to 12L, ClockMath.untilTrigger(1_000_000 + (7 * 60 + 11) * 60_000L + 1, 1_000_000))
        assertEquals(0L to 1L, ClockMath.untilTrigger(1_000_500, 1_000_000))
    }

    @Test
    fun jsonRoundTripAndValidation() {
        val alarms = listOf(Alarm(1, 7, 30, Alarm.WEEKDAYS, true, "Work", false), Alarm(2, 22, 0))
        assertEquals(alarms, ClockMath.fromJson(ClockMath.toJson(alarms)))
        assertEquals(emptyList<Alarm>(), ClockMath.fromJson("""[{"id":3,"hour":25,"minute":0},{"id":0,"hour":1,"minute":1}]"""))
        assertEquals(emptyList<Alarm>(), ClockMath.fromJson("garbage"))
    }

    @Test
    fun formatsDurations() {
        assertEquals("01:05.30", ClockMath.formatDuration(65_300, withHundredths = true))
        assertEquals("01:05", ClockMath.formatDuration(65_300, withHundredths = false))
        assertEquals("1:00:05", ClockMath.formatDuration(3_605_000, withHundredths = true))
        assertEquals("00:00", ClockMath.formatDuration(-5, withHundredths = false))
    }
}
