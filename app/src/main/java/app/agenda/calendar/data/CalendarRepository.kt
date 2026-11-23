package app.agenda.calendar.data

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import java.time.LocalDate
import java.time.ZoneId

/** Whether a change to a repeating event is for the one repeat that was opened or the whole series. */
enum class EditScope {
    /** Only the repeat that was opened. */
    One,

    /** Every repeat. */
    All,
}

/**
 * Reads and writes Android's shared calendar storage (the calendar provider), where every
 * calendar app on the phone keeps its calendars and events. Nothing is copied into the app:
 * events written here show up in other calendar apps and widgets, and theirs show up here.
 *
 * All calls block, so they're made off the main thread. They need the calendar permissions;
 * without them the storage refuses and these return nothing.
 */
class CalendarRepository(private val context: Context) {
    private val resolver get() = context.contentResolver

    /** Every calendar on the phone, grouped by account. */
    fun calendars(): List<CalendarInfo> = query(
        Calendars.CONTENT_URI,
        arrayOf(
            Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE,
            Calendars.CALENDAR_COLOR, Calendars.CALENDAR_ACCESS_LEVEL, Calendars.VISIBLE,
        ),
        "${Calendars.DELETED} = 0", null,
        "${Calendars.ACCOUNT_NAME} COLLATE NOCASE, ${Calendars.CALENDAR_DISPLAY_NAME} COLLATE NOCASE",
    ) { c ->
        CalendarInfo(
            id = c.getLong(0),
            name = c.getString(1).orEmpty(),
            accountName = c.getString(2).orEmpty(),
            accountType = c.getString(3).orEmpty(),
            color = c.getInt(4),
            accessLevel = c.getInt(5),
            visible = c.getInt(6) != 0,
        )
    }

    /**
     * The occurrences of events on the days [from] to [until] (inclusive), repeats expanded, in
     * calendars that are shown. Cancelled repeats are left out.
     */
    fun occurrences(from: LocalDate, until: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Occurrence> {
        val (begin, end) = queryWindow(from, until, zone)
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        return readInstances(uri, zone).filter { it.endDate >= from && it.startDate <= until }
    }

    /**
     * Occurrences between [from] and [until] whose title, place or notes contain the words in
     * [text], soonest first. The calendar storage does the matching.
     */
    fun search(text: String, from: LocalDate, until: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Occurrence> {
        if (text.isBlank()) return emptyList()
        val (begin, end) = queryWindow(from, until, zone)
        val uri = Instances.CONTENT_SEARCH_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
            it.appendPath(text.trim())
        }.build()
        return readInstances(uri, zone)
    }

    /**
     * The times to ask the storage for. A day wider on each side than asked, because all-day
     * events are kept in UTC and so can sit a few hours outside the phone's own days; callers
     * trim the extra off by date.
     */
    private fun queryWindow(from: LocalDate, until: LocalDate, zone: ZoneId): Pair<Long, Long> =
        from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() to
            until.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun readInstances(uri: Uri, zone: ZoneId): List<Occurrence> = query(
        uri,
        arrayOf(
            Instances.EVENT_ID, Instances.CALENDAR_ID, Instances.TITLE, Instances.EVENT_LOCATION, Instances.DISPLAY_COLOR,
            Instances.ALL_DAY, Instances.BEGIN, Instances.END, Instances.RRULE, Instances.RDATE,
        ),
        "${Instances.VISIBLE} = 1 AND (${Instances.STATUS} IS NULL OR ${Instances.STATUS} != ${Events.STATUS_CANCELED})",
        null,
        "${Instances.BEGIN} ASC",
    ) { c ->
        occurrenceOf(
            eventId = c.getLong(0),
            calendarId = c.getLong(1),
            title = c.getString(2).orEmpty(),
            location = c.getString(3).orEmpty(),
            color = c.getInt(4),
            allDay = c.getInt(5) != 0,
            begin = c.getLong(6),
            end = c.getLong(7),
            recurring = !c.getString(8).isNullOrBlank() || !c.getString(9).isNullOrBlank(),
            zone = zone,
        )
    }

    /** One event with its reminders, or null when it's gone. */
    fun event(id: Long): EventDetails? {
        val reminders = query(
            Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES, Reminders.METHOD),
            "${Reminders.EVENT_ID} = ?", arrayOf(id.toString()), null,
        ) { c -> c.getInt(0) to c.getInt(1) }
            // Email and text reminders are sent by the account's server; only alerts ring here.
            .filter { (_, method) -> method == Reminders.METHOD_ALERT || method == Reminders.METHOD_DEFAULT }
            .map { it.first }
        return query(
            ContentUris.withAppendedId(Events.CONTENT_URI, id),
            arrayOf(
                Events._ID, Events.CALENDAR_ID, Events.TITLE, Events.DESCRIPTION, Events.EVENT_LOCATION, Events.ALL_DAY,
                Events.DTSTART, Events.DTEND, Events.DURATION, Events.EVENT_TIMEZONE, Events.RRULE, Events.DISPLAY_COLOR,
                Events.ORGANIZER, Events.ORIGINAL_ID,
            ),
            null, null, null,
        ) { c ->
            EventDetails(
                id = c.getLong(0),
                calendarId = c.getLong(1),
                title = c.getString(2).orEmpty(),
                description = c.getString(3).orEmpty(),
                location = c.getString(4).orEmpty(),
                allDay = c.getInt(5) != 0,
                start = c.getLong(6),
                end = if (c.isNull(7)) null else c.getLong(7),
                duration = c.getString(8),
                timeZone = c.getString(9),
                rrule = c.getString(10),
                color = c.getInt(11),
                organizer = c.getString(12),
                reminders = reminders,
                originalId = if (c.isNull(13)) null else c.getLong(13),
            )
        }.firstOrNull()
    }

    /** Adds [draft] as a new event. Returns its id, or null if the storage refused it. */
    fun insert(draft: EventDraft, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val calendar = draft.calendarId ?: return null
        val uri = resolver.insert(Events.CONTENT_URI, eventValues(draft, zone).apply { put(Events.CALENDAR_ID, calendar) })
        val id = uri?.lastPathSegment?.toLongOrNull() ?: return null
        writeReminders(id, draft.reminders)
        return id
    }

    /**
     * Saves changes to an existing event. With [EditScope.One] on a repeating event, only the
     * repeat starting at [instanceBegin] changes: Android keeps that as a separate "exception"
     * event tied to the series. Returns false if the storage refused.
     */
    fun update(id: Long, draft: EventDraft, scope: EditScope, instanceBegin: Long?, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val event = event(id) ?: return false
        if (event.recurring && scope == EditScope.One && instanceBegin != null) {
            val values = eventValues(draft.copy(repeat = Repeat.None), zone).apply {
                put(Events.ORIGINAL_INSTANCE_TIME, instanceBegin)
                put(Events.ORIGINAL_ALL_DAY, if (event.allDay) 1 else 0)
            }
            val uri = resolver.insert(ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, id), values)
            val exception = uri?.lastPathSegment?.toLongOrNull() ?: return false
            writeReminders(exception, draft.reminders)
            return true
        }
        val values = eventValues(draft, zone)
        if (draft.calendarId != null && draft.calendarId != event.calendarId) values.put(Events.CALENDAR_ID, draft.calendarId)
        val changed = resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), values, null, null) > 0
        if (changed) writeReminders(id, draft.reminders)
        return changed
    }

    /**
     * Deletes an event. With [EditScope.One] on a repeating event only the repeat starting at
     * [instanceBegin] goes: it's marked cancelled, which is how Android skips one repeat.
     * A repeat already changed on its own is cancelled the same way.
     */
    fun delete(id: Long, scope: EditScope, instanceBegin: Long?): Boolean {
        val event = event(id)
        // A repeat that was already changed on its own is its own event; removing that row would
        // bring the original repeat back, so it's cancelled instead.
        if (event?.originalId != null) {
            val values = ContentValues().apply { put(Events.STATUS, Events.STATUS_CANCELED) }
            return resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), values, null, null) > 0
        }
        if (event != null && event.recurring && scope == EditScope.One && instanceBegin != null) {
            val values = ContentValues().apply {
                put(Events.ORIGINAL_INSTANCE_TIME, instanceBegin)
                put(Events.STATUS, Events.STATUS_CANCELED)
            }
            return resolver.insert(ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, id), values) != null
        }
        return resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null) > 0
    }

    /**
     * The columns of an event as the storage keeps them. A repeating event must have a length
     * (DURATION) and no end time; a single one an end time and no length, or the storage refuses it.
     */
    private fun eventValues(draft: EventDraft, zone: ZoneId): ContentValues {
        val (start, end) = draft.millis(zone)
        val rule = ruleOf(draft.repeat, draft.startDate)
        return ContentValues().apply {
            put(Events.TITLE, draft.title.trim())
            put(Events.EVENT_LOCATION, draft.location.trim())
            put(Events.DESCRIPTION, draft.description.trim())
            put(Events.ALL_DAY, if (draft.allDay) 1 else 0)
            put(Events.DTSTART, start)
            // All-day events are kept in UTC so they stay on the same days in every time zone.
            put(Events.EVENT_TIMEZONE, if (draft.allDay) "UTC" else zone.id)
            put(Events.HAS_ALARM, if (draft.reminders.isEmpty()) 0 else 1)
            if (rule == null) {
                put(Events.DTEND, end)
                putNull(Events.DURATION)
                putNull(Events.RRULE)
            } else {
                putNull(Events.DTEND)
                put(Events.DURATION, durationText(end - start, draft.allDay))
                put(Events.RRULE, rule)
            }
        }
    }

    /** Replaces an event's reminders with [minutes], each as an on-phone alert. */
    private fun writeReminders(eventId: Long, minutes: List<Int>) {
        val ops = arrayListOf(
            ContentProviderOperation.newDelete(Reminders.CONTENT_URI)
                .withSelection("${Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()))
                .build(),
        )
        minutes.distinct().forEach { m ->
            ops += ContentProviderOperation.newInsert(Reminders.CONTENT_URI)
                .withValue(Reminders.EVENT_ID, eventId)
                .withValue(Reminders.MINUTES, m)
                .withValue(Reminders.METHOD, Reminders.METHOD_ALERT)
                .build()
        }
        runCatching { resolver.applyBatch(CalendarContract.AUTHORITY, ops) }
    }

    /**
     * Shows or hides a calendar. This is Android's shared switch, so other calendar apps show or
     * hide it too, as they do when it's changed there.
     */
    fun setVisible(id: Long, visible: Boolean) {
        resolver.update(
            ContentUris.withAppendedId(Calendars.CONTENT_URI, id),
            ContentValues().apply { put(Calendars.VISIBLE, if (visible) 1 else 0) },
            null, null,
        )
    }

    /**
     * Makes a new calendar on this phone. It's written as its own "sync adapter", the only way
     * Android allows a calendar to be made, under the shared on-this-phone account type.
     */
    fun createCalendar(name: String, color: Int): Long? {
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT_NAME)
            put(Calendars.ACCOUNT_TYPE, LOCAL_ACCOUNT_TYPE)
            put(Calendars.OWNER_ACCOUNT, LOCAL_ACCOUNT_NAME)
            put(Calendars.NAME, name)
            put(Calendars.CALENDAR_DISPLAY_NAME, name)
            put(Calendars.CALENDAR_COLOR, color)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, ZoneId.systemDefault().id)
        }
        return resolver.insert(Calendars.CONTENT_URI.asOwner(LOCAL_ACCOUNT_NAME), values)?.lastPathSegment?.toLongOrNull()
    }

    /** Renames and recolours a calendar on this phone. */
    fun editCalendar(calendar: CalendarInfo, name: String, color: Int) {
        if (!calendar.local) return
        resolver.update(
            ContentUris.withAppendedId(Calendars.CONTENT_URI, calendar.id).asOwner(calendar.accountName),
            ContentValues().apply {
                put(Calendars.NAME, name)
                put(Calendars.CALENDAR_DISPLAY_NAME, name)
                put(Calendars.CALENDAR_COLOR, color)
            },
            null, null,
        )
    }

    /** Deletes a calendar on this phone, and every event in it. */
    fun deleteCalendar(calendar: CalendarInfo) {
        if (!calendar.local) return
        resolver.delete(ContentUris.withAppendedId(Calendars.CONTENT_URI, calendar.id).asOwner(calendar.accountName), null, null)
    }

    /**
     * Makes a "Personal" calendar on this phone when there's nowhere to put events (no account
     * calendar can be written to), so the first event has a home. Returns its id, or null.
     */
    fun ensureWritableCalendar(name: String, color: Int): Long? {
        val writable = calendars().filter { it.writable }
        return if (writable.isEmpty()) createCalendar(name, color) else writable.first().id
    }

    /** The address with the marks that let the app write as the calendar's own sync adapter. */
    private fun Uri.asOwner(accountName: String): Uri = buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, accountName)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, LOCAL_ACCOUNT_TYPE)
        .build()

    /** Runs a query and reads each row with [read]; nothing when the storage refuses (no permission). */
    private fun <T> query(
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        args: Array<String>?,
        sort: String?,
        read: (Cursor) -> T,
    ): List<T> = runCatching {
        resolver.query(uri, projection, selection, args, sort)?.use { c ->
            buildList { while (c.moveToNext()) add(read(c)) }
        }.orEmpty()
    }.getOrDefault(emptyList())
}
