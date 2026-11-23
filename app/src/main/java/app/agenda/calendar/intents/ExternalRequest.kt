package app.agenda.calendar.intents

import android.content.Intent
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import app.agenda.calendar.data.EventDraft
import app.agenda.calendar.data.Repeat
import app.agenda.calendar.data.repeatOf
import app.agenda.calendar.ui.Request
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Reads what another app asked for with the standard calendar intents, the same ones Google
 * Calendar and AOSP Calendar answer:
 * - VIEW content://com.android.calendar/events/<id> (with beginTime/endTime extras): open it;
 * - VIEW content://com.android.calendar/time/<ms>: show that day;
 * - INSERT (with title, beginTime, endTime, allDay, eventLocation, description, rrule extras):
 *   a new event filled in, which the user still has to save;
 * - EDIT content://com.android.calendar/events/<id>: edit it.
 * Anything else is null and the app just opens.
 */
fun parseRequest(intent: Intent?, zone: ZoneId = ZoneId.systemDefault()): Request? {
    intent ?: return null
    val segments = intent.data?.pathSegments.orEmpty()
    val eventId = segments.takeIf { it.firstOrNull() == "events" }?.getOrNull(1)?.toLongOrNull()
    val begin = intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1L).takeIf { it > 0 }
    val end = intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1L).takeIf { it > 0 }
    return when (intent.action) {
        Intent.ACTION_VIEW -> when {
            eventId != null -> Request.ShowEvent(eventId, begin, end)
            segments.firstOrNull() == "time" -> segments.getOrNull(1)?.toLongOrNull()?.let {
                Request.ShowDate(Instant.ofEpochMilli(it).atZone(zone).toLocalDate())
            }
            else -> null
        }
        Intent.ACTION_EDIT -> if (eventId != null) Request.EditEvent(eventId) else Request.NewEvent(draftFrom(intent, begin, end, zone))
        Intent.ACTION_INSERT -> Request.NewEvent(draftFrom(intent, begin, end, zone))
        else -> null
    }
}

/** The event another app filled in, or null when it gave no start time (the editor then picks one). */
private fun draftFrom(intent: Intent, begin: Long?, end: Long?, zone: ZoneId): EventDraft? {
    val allDay = intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
    val title = intent.getStringExtra(Events.TITLE).orEmpty()
    val location = intent.getStringExtra(Events.EVENT_LOCATION).orEmpty()
    val description = intent.getStringExtra(Events.DESCRIPTION).orEmpty()
    val repeat = intent.getStringExtra(Events.RRULE)?.let(::repeatOf) ?: Repeat.None
    if (begin == null) {
        if (title.isEmpty() && location.isEmpty() && description.isEmpty()) return null
        val start = LocalDate.now().atTime(LocalTime.now().hour, 0).plusHours(1)
        return EventDraft(
            title = title, location = location, description = description, allDay = allDay, repeat = repeat,
            startDate = start.toLocalDate(), startTime = start.toLocalTime(),
            endDate = start.plusHours(1).toLocalDate(), endTime = start.plusHours(1).toLocalTime(),
        )
    }
    val finish = end ?: (begin + if (allDay) 24 * 60 * 60 * 1000L else 60 * 60 * 1000L)
    // Apps send an all-day event's day as a UTC midnight, as it's stored.
    val readZone: ZoneId = if (allDay) ZoneOffset.UTC else zone
    val start = Instant.ofEpochMilli(begin).atZone(readZone).toLocalDateTime()
    val stop = Instant.ofEpochMilli(maxOf(begin, finish)).atZone(readZone).toLocalDateTime()
    val lastDay = if (allDay) stop.toLocalDate().minusDays(1).let { if (it.isBefore(start.toLocalDate())) start.toLocalDate() else it } else stop.toLocalDate()
    return EventDraft(
        title = title, location = location, description = description, allDay = allDay, repeat = repeat,
        startDate = start.toLocalDate(), startTime = if (allDay) LocalTime.of(9, 0) else start.toLocalTime(),
        endDate = lastDay, endTime = if (allDay) LocalTime.of(10, 0) else stop.toLocalTime(),
    )
}
