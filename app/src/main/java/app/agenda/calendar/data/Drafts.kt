package app.agenda.calendar.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * An event as it's being written in the editor, in days and clock times rather than the
 * milliseconds the calendar storage keeps. For an all-day event the times are ignored and
 * [endDate] is the last day it's on.
 */
data class EventDraft(
    val id: Long? = null,
    val calendarId: Long? = null,
    val title: String = "",
    val location: String = "",
    val description: String = "",
    val allDay: Boolean = false,
    val startDate: LocalDate,
    val startTime: LocalTime,
    val endDate: LocalDate,
    val endTime: LocalTime,
    val repeat: Repeat = Repeat.None,
    val reminders: List<Int> = emptyList(),
) {
    /** When it starts, as a date and time (midnight for an all-day event). */
    val start: LocalDateTime get() = if (allDay) startDate.atStartOfDay() else startDate.atTime(startTime)

    /** When it ends: the end time, or for an all-day event midnight after its last day. */
    val end: LocalDateTime get() = if (allDay) endDate.plusDays(1).atStartOfDay() else endDate.atTime(endTime)

    /** It ends after it starts (or, all day, its last day isn't before its first). */
    val valid: Boolean get() = if (allDay) !endDate.isBefore(startDate) else !end.isBefore(start)

    /** The start and end in milliseconds as stored: UTC midnights for an all-day event, [zone] otherwise. */
    fun millis(zone: ZoneId): Pair<Long, Long> = if (allDay) {
        start.toInstant(ZoneOffset.UTC).toEpochMilli() to end.toInstant(ZoneOffset.UTC).toEpochMilli()
    } else {
        start.atZone(zone).toInstant().toEpochMilli() to end.atZone(zone).toInstant().toEpochMilli()
    }

    /** Moves the start to [date], taking the end along so the event keeps its length. */
    fun withStartDate(date: LocalDate): EventDraft {
        val shift = ChronoUnit.DAYS.between(startDate, date)
        return copy(startDate = date, endDate = endDate.plusDays(shift))
    }

    /** Moves the start to [time], taking the end along so the event keeps its length. */
    fun withStartTime(time: LocalTime): EventDraft {
        val length = ChronoUnit.MINUTES.between(start, end)
        val newEnd = startDate.atTime(time).plusMinutes(length)
        return copy(startTime = time, endDate = newEnd.toLocalDate(), endTime = newEnd.toLocalTime())
    }

    /** Adds [minutes] to the reminders, keeping them in order and without repeats. */
    fun withReminder(minutes: Int): EventDraft = copy(reminders = (reminders + minutes).distinct().sorted())
}

/**
 * A new event's starting point. Tapping an hour gives that hour; otherwise it's the next whole
 * hour when [day] is today, and 9 in the morning on any other day. It lasts [lengthMinutes] and
 * has [reminder] (minutes before, or none when negative).
 */
fun newDraft(
    day: LocalDate,
    hour: Int?,
    now: LocalDateTime,
    lengthMinutes: Int,
    reminder: Int,
    calendarId: Long?,
    allDay: Boolean = false,
): EventDraft {
    val start = when {
        hour != null -> day.atTime(hour.coerceIn(0, 23), 0)
        day == now.toLocalDate() -> now.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        else -> day.atTime(9, 0)
    }
    val end = start.plusMinutes(lengthMinutes.toLong().coerceAtLeast(0))
    return EventDraft(
        calendarId = calendarId,
        allDay = allDay,
        startDate = start.toLocalDate(),
        startTime = start.toLocalTime(),
        endDate = if (allDay) start.toLocalDate() else end.toLocalDate(),
        endTime = end.toLocalTime(),
        reminders = if (reminder >= 0) listOf(reminder) else emptyList(),
    )
}

/**
 * The editor's starting point for an existing event. For a repeating event, [instanceBegin]
 * is the repeat that was opened: editing just that one starts from its own times; editing the
 * whole series ([wholeSeries]) starts from the first.
 */
fun draftOf(event: EventDetails, instanceBegin: Long?, wholeSeries: Boolean, zone: ZoneId): EventDraft {
    val begin = if (event.recurring && !wholeSeries && instanceBegin != null) instanceBegin else event.start
    val finish = begin + event.lengthMillis
    val readZone: ZoneId = if (event.allDay) ZoneOffset.UTC else zone
    val start = Instant.ofEpochMilli(begin).atZone(readZone).toLocalDateTime()
    val end = Instant.ofEpochMilli(finish).atZone(readZone).toLocalDateTime()
    val lastDay = if (event.allDay) end.toLocalDate().minusDays(1).let { if (it.isBefore(start.toLocalDate())) start.toLocalDate() else it } else end.toLocalDate()
    return EventDraft(
        id = event.id,
        calendarId = event.calendarId,
        title = event.title,
        location = event.location,
        description = event.description,
        allDay = event.allDay,
        startDate = start.toLocalDate(),
        startTime = if (event.allDay) LocalTime.of(9, 0) else start.toLocalTime(),
        endDate = lastDay,
        endTime = if (event.allDay) LocalTime.of(10, 0) else end.toLocalTime(),
        repeat = if (wholeSeries || !event.recurring) repeatOf(event.rrule) else Repeat.None,
        reminders = event.reminders.distinct().sorted(),
    )
}

/** The reminder choices offered, in minutes before the event starts. */
val reminderChoices: List<Int> = listOf(0, 5, 10, 15, 30, 60, 120, 1440, 2880, 10080)

/** A unit a reminder time is said in. */
enum class ReminderUnit { Minutes, Hours, Days, Weeks }

/**
 * A reminder time said in the largest unit that divides it exactly: 90 is 90 minutes, 120 is
 * 2 hours, 1440 is 1 day, 10080 is 1 week.
 */
fun reminderParts(minutes: Int): Pair<Int, ReminderUnit> = when {
    minutes >= 10080 && minutes % 10080 == 0 -> minutes / 10080 to ReminderUnit.Weeks
    minutes >= 1440 && minutes % 1440 == 0 -> minutes / 1440 to ReminderUnit.Days
    minutes >= 60 && minutes % 60 == 0 -> minutes / 60 to ReminderUnit.Hours
    else -> minutes to ReminderUnit.Minutes
}

/** Colours offered for the app's own calendars: bright enough on light and dark pages. */
val calendarColors: List<Int> = listOf(
    0xFFD50000, 0xFFE67C73, 0xFFF4511E, 0xFFF6BF26, 0xFF33B679, 0xFF0B8043,
    0xFF039BE5, 0xFF3F51B5, 0xFF7986CB, 0xFF8E24AA, 0xFF795548, 0xFF616161,
).map { it.toInt() }
