package app.agenda.calendar.data

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** The time zone the tests run in, so they don't depend on the computer's. */
val TEST_ZONE: ZoneId = ZoneId.of("America/New_York")

/** A timed test occurrence from [start] to [end], in [TEST_ZONE]. */
fun timed(start: LocalDateTime, end: LocalDateTime, title: String = "Event", id: Long = 1): Occurrence = occurrenceOf(
    eventId = id, calendarId = 1, title = title, location = "", color = 0,
    allDay = false,
    begin = start.atZone(TEST_ZONE).toInstant().toEpochMilli(),
    end = end.atZone(TEST_ZONE).toInstant().toEpochMilli(),
    recurring = false, zone = TEST_ZONE,
)

/** An all-day test occurrence covering the days from [first] to [last], stored in UTC as Android does. */
fun allDay(first: java.time.LocalDate, last: java.time.LocalDate, title: String = "All day", id: Long = 1): Occurrence = occurrenceOf(
    eventId = id, calendarId = 1, title = title, location = "", color = 0,
    allDay = true,
    begin = first.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    end = last.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    recurring = false, zone = TEST_ZONE,
)
