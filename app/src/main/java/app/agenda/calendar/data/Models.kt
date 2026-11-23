package app.agenda.calendar.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The account type Android's calendar storage uses for calendars that live only on this phone
 * and aren't synced anywhere. The app makes its own calendars with it, so they outlive the app
 * (uninstalling doesn't take their events with it) and every calendar app can see them.
 */
const val LOCAL_ACCOUNT_TYPE = "LOCAL"

/** The account name given to calendars the app makes. Other calendar apps group them under it. */
const val LOCAL_ACCOUNT_NAME = "On this phone"

/** The lowest access level that may add and change events (Android's "contributor"). */
private const val CONTRIBUTOR = 500

/**
 * One calendar from Android's calendar storage: the app's own, another app's, or one synced from
 * an account such as Google or a CalDAV server. [visible] is Android's shared on/off switch,
 * which other calendar apps also respect.
 */
data class CalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val accessLevel: Int,
    val visible: Boolean,
) {
    /** Events can be added to and changed in this calendar. */
    val writable: Boolean get() = accessLevel >= CONTRIBUTOR

    /**
     * Lives only on this phone, so the app may rename, recolour or delete it. Synced calendars
     * belong to their account's app, which would undo such changes.
     */
    val local: Boolean get() = accountType == LOCAL_ACCOUNT_TYPE
}

/**
 * One time an event happens, as shown in the views. A repeating event has one per repeat.
 * [startDate] and [endDate] are the first and last days it's on (inclusive), and
 * [startMinute] and [endMinute] the minute of the day it starts on [startDate] and ends on
 * [endDate]; an event ending at midnight ends at minute 1440 of the day before.
 */
data class Occurrence(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val location: String,
    val color: Int,
    val allDay: Boolean,
    val begin: Long,
    val end: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val recurring: Boolean,
) {
    /** On more than one day. */
    val multiDay: Boolean get() = endDate.isAfter(startDate)

    /** Happens (at least partly) on [day]. */
    fun isOn(day: LocalDate): Boolean = !day.isBefore(startDate) && !day.isAfter(endDate)
}

/**
 * Builds an [Occurrence] from what the calendar storage gives for one instance. All-day events
 * are stored as midnights in UTC whatever the phone's time zone, so their days are read in UTC;
 * timed events are read in [zone].
 */
fun occurrenceOf(
    eventId: Long,
    calendarId: Long,
    title: String,
    location: String,
    color: Int,
    allDay: Boolean,
    begin: Long,
    end: Long,
    recurring: Boolean,
    zone: ZoneId,
): Occurrence {
    val safeEnd = maxOf(begin, end)
    if (allDay) {
        val first = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
        val afterLast = Instant.ofEpochMilli(safeEnd).atZone(ZoneOffset.UTC).toLocalDate()
        val last = if (afterLast.isAfter(first)) afterLast.minusDays(1) else first
        return Occurrence(eventId, calendarId, title, location, color, true, begin, safeEnd, first, last, 0, MINUTES_PER_DAY, recurring)
    }
    val start = Instant.ofEpochMilli(begin).atZone(zone).toLocalDateTime()
    val finish = Instant.ofEpochMilli(safeEnd).atZone(zone).toLocalDateTime()
    val startMinute = start.hour * 60 + start.minute
    var endDate = finish.toLocalDate()
    var endMinute = finish.hour * 60 + finish.minute
    // Ending exactly at midnight means ending with the day before, not starting a new one.
    if (safeEnd > begin && endMinute == 0 && endDate.isAfter(start.toLocalDate())) {
        endDate = endDate.minusDays(1)
        endMinute = MINUTES_PER_DAY
    }
    return Occurrence(
        eventId, calendarId, title, location, color, false, begin, safeEnd,
        start.toLocalDate(), endDate, startMinute, endMinute, recurring,
    )
}

/** Minutes in a day. */
const val MINUTES_PER_DAY = 24 * 60

/**
 * Everything about one event, for its page and for editing it. A repeating event keeps its
 * length in [duration] (an RFC 5545 duration such as "P3600S") instead of an end time.
 */
data class EventDetails(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val description: String,
    val location: String,
    val allDay: Boolean,
    val start: Long,
    val end: Long?,
    val duration: String?,
    val timeZone: String?,
    val rrule: String?,
    val color: Int,
    val organizer: String?,
    val reminders: List<Int>,
    /** Set on a single changed or cancelled repeat of a repeating event: the event it came from. */
    val originalId: Long?,
) {
    /** Repeats. */
    val recurring: Boolean get() = !rrule.isNullOrBlank()

    /** How long each time lasts, in milliseconds. */
    val lengthMillis: Long
        get() = end?.let { it - start } ?: durationMillis(duration) ?: if (allDay) DAY_MILLIS else HOUR_MILLIS
}

/** An hour in milliseconds. */
const val HOUR_MILLIS = 60 * 60 * 1000L

/** A day in milliseconds. */
const val DAY_MILLIS = 24 * HOUR_MILLIS
