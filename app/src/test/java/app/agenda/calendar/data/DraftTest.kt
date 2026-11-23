package app.agenda.calendar.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The editor's working copy of an event. */
class DraftTest {
    private val day = LocalDate.of(2026, 11, 23)
    private val now = day.atTime(14, 20)

    @Test
    fun newEventTodayStartsAtTheNextHour() {
        val draft = newDraft(day, null, now, 60, 10, calendarId = 4)
        assertEquals(LocalTime.of(15, 0), draft.startTime)
        assertEquals(LocalTime.of(16, 0), draft.endTime)
        assertEquals(listOf(10), draft.reminders)
        assertEquals(4L, draft.calendarId)
    }

    @Test
    fun newEventAnotherDayStartsAtNine() {
        val draft = newDraft(day.plusDays(2), null, now, 30, -1, calendarId = null)
        assertEquals(LocalTime.of(9, 0), draft.startTime)
        assertEquals(LocalTime.of(9, 30), draft.endTime)
        assertTrue(draft.reminders.isEmpty())
    }

    @Test
    fun lateNewEventRunsPastMidnight() {
        val draft = newDraft(day, 23, now, 90, -1, null)
        assertEquals(day.plusDays(1), draft.endDate)
        assertEquals(LocalTime.of(0, 30), draft.endTime)
    }

    @Test
    fun movingTheStartKeepsTheLength() {
        val draft = newDraft(day, 10, now, 60, -1, null)
        val moved = draft.withStartTime(LocalTime.of(23, 30))
        assertEquals(day.plusDays(1), moved.endDate)
        assertEquals(LocalTime.of(0, 30), moved.endTime)
        val later = draft.withStartDate(day.plusDays(3))
        assertEquals(day.plusDays(3), later.endDate)
    }

    @Test
    fun endBeforeStartIsInvalid() {
        val draft = newDraft(day, 10, now, 60, -1, null)
        assertTrue(draft.valid)
        assertFalse(draft.copy(endTime = LocalTime.of(9, 0)).valid)
        assertTrue(draft.copy(allDay = true, endTime = LocalTime.of(9, 0)).valid)
    }

    @Test
    fun allDayTimesAreUtcMidnights() {
        val draft = newDraft(day, null, now, 60, -1, null, allDay = true).copy(endDate = day.plusDays(1))
        val (start, end) = draft.millis(TEST_ZONE)
        assertEquals(day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(), start)
        assertEquals(day.plusDays(2).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(), end)
    }

    @Test
    fun editingOneRepeatStartsFromThatRepeat() {
        val first = day.atTime(9, 0).atZone(TEST_ZONE).toInstant().toEpochMilli()
        val event = EventDetails(
            id = 7, calendarId = 1, title = "Standup", description = "", location = "", allDay = false,
            start = first, end = null, duration = "P900S", timeZone = TEST_ZONE.id, rrule = "FREQ=DAILY",
            color = 0, organizer = null, reminders = listOf(5, 5, 0), originalId = null,
        )
        val third = first + 2 * DAY_MILLIS
        val one = draftOf(event, third, wholeSeries = false, zone = TEST_ZONE)
        assertEquals(day.plusDays(2), one.startDate)
        assertEquals(LocalTime.of(9, 15), one.endTime)
        assertEquals(Repeat.None, one.repeat)
        assertEquals(listOf(0, 5), one.reminders)
        val all = draftOf(event, third, wholeSeries = true, zone = TEST_ZONE)
        assertEquals(day, all.startDate)
        assertEquals(Repeat.Daily, all.repeat)
    }

    @Test
    fun allDayEventReadsBackItsDays() {
        val start = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val event = EventDetails(
            id = 1, calendarId = 1, title = "Trip", description = "", location = "", allDay = true,
            start = start, end = start + 3 * DAY_MILLIS, duration = null, timeZone = "UTC", rrule = null,
            color = 0, organizer = null, reminders = emptyList(), originalId = null,
        )
        val draft = draftOf(event, null, wholeSeries = true, zone = TEST_ZONE)
        assertEquals(day, draft.startDate)
        assertEquals(day.plusDays(2), draft.endDate)
    }

    @Test
    fun remindersAreSaidInTheLargestUnit() {
        assertEquals(10 to ReminderUnit.Minutes, reminderParts(10))
        assertEquals(90 to ReminderUnit.Minutes, reminderParts(90))
        assertEquals(2 to ReminderUnit.Hours, reminderParts(120))
        assertEquals(1 to ReminderUnit.Days, reminderParts(1440))
        assertEquals(1 to ReminderUnit.Weeks, reminderParts(10080))
        assertEquals(0 to ReminderUnit.Minutes, reminderParts(0))
    }
}
