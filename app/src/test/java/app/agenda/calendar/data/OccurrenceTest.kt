package app.agenda.calendar.data

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Turning the calendar storage's milliseconds into days and minutes. */
class OccurrenceTest {
    private val day = LocalDate.of(2026, 11, 23)

    @Test
    fun timedEventKeepsItsMinutes() {
        val o = timed(day.atTime(9, 30), day.atTime(10, 45))
        assertEquals(day, o.startDate)
        assertEquals(day, o.endDate)
        assertEquals(570, o.startMinute)
        assertEquals(645, o.endMinute)
        assertFalse(o.multiDay)
    }

    @Test
    fun endingAtMidnightEndsTheDayBefore() {
        val o = timed(day.atTime(22, 0), day.plusDays(1).atStartOfDay())
        assertEquals(day, o.endDate)
        assertEquals(MINUTES_PER_DAY, o.endMinute)
        assertFalse(o.multiDay)
    }

    @Test
    fun overnightEventCoversBothDays() {
        val o = timed(day.atTime(22, 0), day.plusDays(1).atTime(2, 0))
        assertTrue(o.multiDay)
        assertTrue(o.isOn(day))
        assertTrue(o.isOn(day.plusDays(1)))
        assertFalse(o.isOn(day.plusDays(2)))
    }

    @Test
    fun allDayEventIsReadInUtc() {
        // New York is behind UTC, so a local reading would put this on the day before.
        val o = allDay(day, day)
        assertEquals(day, o.startDate)
        assertEquals(day, o.endDate)
    }

    @Test
    fun multiDayAllDayEventEndsOnItsLastDay() {
        val o = allDay(day, day.plusDays(2))
        assertEquals(day.plusDays(2), o.endDate)
        assertTrue(o.multiDay)
    }

    @Test
    fun zeroLengthEventStaysOnItsDay() {
        val at = LocalDateTime.of(2026, 11, 23, 0, 0)
        val o = timed(at, at)
        assertEquals(day, o.endDate)
        assertEquals(0, o.endMinute)
    }
}
