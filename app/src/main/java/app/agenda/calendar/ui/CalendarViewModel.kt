package app.agenda.calendar.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.agenda.calendar.R
import app.agenda.calendar.data.CalendarInfo
import app.agenda.calendar.data.CalendarRepository
import app.agenda.calendar.data.CalendarView
import app.agenda.calendar.data.EditScope
import app.agenda.calendar.data.EventDetails
import app.agenda.calendar.data.EventDraft
import app.agenda.calendar.data.FirstDay
import app.agenda.calendar.data.Occurrence
import app.agenda.calendar.data.Settings
import app.agenda.calendar.data.calendarColors
import app.agenda.calendar.util.contentChanges
import app.agenda.calendar.widgets.keepWidgetsCurrent
import app.agenda.calendar.widgets.refreshWidgetsSoon
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What another app, a widget, a launcher shortcut or a reminder notification asked the app to
 * show. Kept until the screens
 * are ready (calendar access granted) and then handled once.
 */
sealed interface Request {
    /** Open one event, at the repeat starting at [begin] when known. */
    data class ShowEvent(val id: Long, val begin: Long?, val end: Long?) : Request

    /** Show the calendar on [date]. */
    data class ShowDate(val date: LocalDate) : Request

    /** Start a new event from [draft], filled in by the asking app. */
    data class NewEvent(val draft: EventDraft?) : Request

    /** Edit event [id]. */
    data class EditEvent(val id: Long) : Request

    /** Open search, from the launcher's Search shortcut. */
    data object Search : Request
}

/**
 * Holds everything the screens show: the calendars, the settings, the picked day, and the
 * events for whatever days a view asks for. Anything in the calendar storage changing (an edit
 * here, another app, an account syncing) makes every list read again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = CalendarRepository(app)
    private val settings = Settings(app)

    private val _access = MutableStateFlow(hasAccess())

    /** Whether the app may read and write calendars. */
    val access: StateFlow<Boolean> = _access.asStateFlow()

    private var changeCount = 0

    /**
     * Counts changes in the calendar storage; every list re-reads when it moves. It also moves
     * when access is granted, since the storage refused everything before that.
     */
    private val changes: StateFlow<Int> = _access.flatMapLatest { granted ->
        if (!granted) emptyFlow() else contentChanges(app, CalendarContract.CONTENT_URI).map { ++changeCount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Every calendar, or null while loading. */
    val calendars: StateFlow<List<CalendarInfo>?> = changes
        .map { withContext(Dispatchers.IO) { repository.calendars() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The view on show. */
    val view: StateFlow<CalendarView> = settings.view.stateIn(viewModelScope, SharingStarted.Eagerly, CalendarView.Month)

    /** The day weeks start on, as chosen. */
    val firstDay: StateFlow<FirstDay> = settings.firstDay.stateIn(viewModelScope, SharingStarted.Eagerly, FirstDay.Auto)

    /** How long new events last, in minutes. */
    val eventLength: StateFlow<Int> = settings.eventLength.stateIn(viewModelScope, SharingStarted.Eagerly, 60)

    /** The reminder new events get (minutes before; negative for none). */
    val defaultReminder: StateFlow<Int> = settings.defaultReminder.stateIn(viewModelScope, SharingStarted.Eagerly, 10)

    /** Whether reminder notifications are shown. */
    val notifications: StateFlow<Boolean> = settings.notifications.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** The calendar the last new event went into. */
    val lastCalendar: StateFlow<Long?> = settings.lastCalendar.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _today = MutableStateFlow(LocalDate.now())

    /** Today's date, checked again each time the app comes back, so it moves on after midnight. */
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    private val _selected = MutableStateFlow(LocalDate.now())

    /** The day picked in the views. Every view follows it, so switching views keeps the place. */
    val selected: StateFlow<LocalDate> = _selected.asStateFlow()

    private val _request = MutableStateFlow<Request?>(null)

    /** Something asked for from outside, not yet handled. */
    val request: StateFlow<Request?> = _request.asStateFlow()

    /** Called when calendar access may have changed (after the permission prompt); redraws the widgets to match. */
    fun onAccessChanged() {
        _access.value = hasAccess()
        // Widgets placed before access was given are still asking for it.
        refreshWidgetsSoon(getApplication())
        if (_access.value) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.ensureWritableCalendar(getApplication<Application>().getString(R.string.personal), calendarColors[6])
            }
        }
    }

    /**
     * Called each time the app comes to the front: picks up a new day and permission changes,
     * and makes sure any widgets are being kept current.
     */
    fun onForeground() {
        val now = LocalDate.now()
        if (now != _today.value) {
            // The picked day follows "today" over midnight if it was on it.
            if (_selected.value == _today.value) _selected.value = now
            _today.value = now
        }
        if (hasAccess() != _access.value) onAccessChanged()
        keepWidgetsCurrent(getApplication())
    }

    private fun hasAccess(): Boolean {
        val app = getApplication<Application>()
        return listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            .all { app.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    /** Picks a day. */
    fun select(date: LocalDate) {
        _selected.value = date
    }

    /** Goes to today. */
    fun goToToday() {
        _today.value = LocalDate.now()
        _selected.value = _today.value
    }

    /** Remembers which view is on show. */
    fun setView(view: CalendarView) {
        viewModelScope.launch { settings.setView(view) }
    }

    /** The occurrences on the days [from] to [until], read again whenever the calendar storage changes. */
    fun occurrences(from: LocalDate, until: LocalDate): Flow<List<Occurrence>> =
        changes.map { repository.occurrences(from, until) }.flowOn(Dispatchers.IO)

    /** Searches events from a year back to two years ahead. */
    suspend fun search(text: String): List<Occurrence> = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        repository.search(text, today.minusYears(1), today.plusYears(2))
    }

    /** One event, read again whenever the calendar storage changes; null once it's gone. */
    fun event(id: Long): Flow<EventDetails?> = changes.map { repository.event(id) }.flowOn(Dispatchers.IO)

    /** Reads one event once. */
    suspend fun loadEvent(id: Long): EventDetails? = withContext(Dispatchers.IO) { repository.event(id) }

    /**
     * Saves [draft]: a new event when it has no id, otherwise changes to it ([scope] and
     * [instanceBegin] say which repeats). Calls [done] with whether it worked.
     */
    fun save(draft: EventDraft, scope: EditScope, instanceBegin: Long?, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val id = draft.id
                if (id == null) {
                    val calendar = draft.calendarId
                        ?: repository.ensureWritableCalendar(getApplication<Application>().getString(R.string.personal), calendarColors[6])
                    repository.insert(draft.copy(calendarId = calendar)) != null
                } else {
                    repository.update(id, draft, scope, instanceBegin)
                }
            }
            if (ok && draft.id == null) draft.calendarId?.let { settings.setLastCalendar(it) }
            done(ok)
        }
    }

    /** Deletes an event, or one repeat of it. */
    fun delete(id: Long, scope: EditScope, instanceBegin: Long?, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            done(withContext(Dispatchers.IO) { runCatching { repository.delete(id, scope, instanceBegin) }.getOrDefault(false) })
        }
    }

    /** Shows or hides a calendar. */
    fun setVisible(calendar: CalendarInfo, visible: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { repository.setVisible(calendar.id, visible) } }
    }

    /** Makes a new calendar on this phone, then calls [done] with its id (null if it couldn't be made). */
    fun createCalendar(name: String, color: Int, done: (Long?) -> Unit = {}) {
        viewModelScope.launch {
            done(withContext(Dispatchers.IO) { runCatching { repository.createCalendar(name, color) }.getOrNull() })
        }
    }

    /** Renames and recolours a calendar on this phone. */
    fun editCalendar(calendar: CalendarInfo, name: String, color: Int) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { repository.editCalendar(calendar, name, color) } }
    }

    /** Deletes a calendar on this phone and its events. */
    fun deleteCalendar(calendar: CalendarInfo) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { repository.deleteCalendar(calendar) } }
    }

    /** Changes the day weeks start on, in the app and the Month widget. */
    fun setFirstDay(day: FirstDay) {
        viewModelScope.launch {
            settings.setFirstDay(day)
            refreshWidgetsSoon(getApplication())
        }
    }

    /** Changes how long new events last. */
    fun setEventLength(minutes: Int) {
        viewModelScope.launch { settings.setEventLength(minutes) }
    }

    /** Changes the reminder new events get. */
    fun setDefaultReminder(minutes: Int) {
        viewModelScope.launch { settings.setDefaultReminder(minutes) }
    }

    /** Turns reminder notifications on or off. */
    fun setNotifications(on: Boolean) {
        viewModelScope.launch { settings.setNotifications(on) }
    }

    private var incomingDraft: EventDraft? = null

    /** Keeps an event another app filled in, for the editor to start from. */
    fun offerDraft(draft: EventDraft?) {
        incomingDraft = draft
    }

    /** The event another app filled in, once; null when there's none. */
    fun takeDraft(): EventDraft? = incomingDraft.also { incomingDraft = null }

    /** Records something asked for from outside, to handle once the screens are ready. */
    fun request(request: Request) {
        _request.value = request
    }

    /** Marks the outside request as handled. */
    fun requestHandled() {
        _request.value = null
    }
}
