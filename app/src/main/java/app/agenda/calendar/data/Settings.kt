package app.agenda.calendar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The four ways of looking at the calendar, one per bottom tab, in order. */
enum class CalendarView {
    /** A month of days, each with its events written in. */
    Month,

    /** Seven days side by side, hour by hour. */
    Week,

    /** A list of what's coming up, day by day. */
    Agenda,

    /** A small month on top with the picked day's list underneath. */
    MonthAgenda,
}

/** Which day weeks start on. [Auto] follows the phone's language and region. */
enum class FirstDay {
    /** Whatever the phone's region uses. */
    Auto,

    /** Saturday. */
    Saturday,

    /** Sunday. */
    Sunday,

    /** Monday. */
    Monday,
    ;

    /** The day itself, reading the phone's region for [Auto]. */
    fun day(locale: Locale = Locale.getDefault()): DayOfWeek = when (this) {
        Auto -> WeekFields.of(locale).firstDayOfWeek
        Saturday -> DayOfWeek.SATURDAY
        Sunday -> DayOfWeek.SUNDAY
        Monday -> DayOfWeek.MONDAY
    }
}

/**
 * One settings file for the whole app. DataStore must only have one instance per file, and the
 * view model and the reminder receiver both read it, so the instance lives here.
 */
private object SettingsFile {
    @Volatile private var store: DataStore<Preferences>? = null

    fun get(context: Context): DataStore<Preferences> = store ?: synchronized(this) {
        store ?: PreferenceDataStoreFactory.create {
            context.applicationContext.preferencesDataStoreFile("settings")
        }.also { store = it }
    }
}

/**
 * The app's own settings, kept on the phone. The events and calendars themselves are in
 * Android's calendar storage, not here; even which calendars are shown is kept there, so other
 * calendar apps agree.
 */
class Settings(context: Context) {
    private val store = SettingsFile.get(context)

    /** The view last shown, opened again next time. */
    val view: Flow<CalendarView> = store.data.map { prefs ->
        CalendarView.entries.firstOrNull { it.name == prefs[VIEW] } ?: CalendarView.Month
    }

    /** Remembers the view being shown. */
    suspend fun setView(view: CalendarView) {
        store.edit { it[VIEW] = view.name }
    }

    /** The day weeks start on. */
    val firstDay: Flow<FirstDay> = store.data.map { prefs ->
        FirstDay.entries.firstOrNull { it.name == prefs[FIRST_DAY] } ?: FirstDay.Auto
    }

    /** Changes the day weeks start on. */
    suspend fun setFirstDay(day: FirstDay) {
        store.edit { it[FIRST_DAY] = day.name }
    }

    /** How long a new event lasts, in minutes. */
    val eventLength: Flow<Int> = store.data.map { it[EVENT_LENGTH] ?: 60 }

    /** Changes how long new events last. */
    suspend fun setEventLength(minutes: Int) {
        store.edit { it[EVENT_LENGTH] = minutes }
    }

    /** The reminder new events get, in minutes before; negative for none. */
    val defaultReminder: Flow<Int> = store.data.map { it[DEFAULT_REMINDER] ?: 10 }

    /** Changes the reminder new events get. */
    suspend fun setDefaultReminder(minutes: Int) {
        store.edit { it[DEFAULT_REMINDER] = minutes }
    }

    /**
     * Whether the app shows reminder notifications. Off for people who keep another calendar app
     * that already does, so they aren't reminded twice.
     */
    val notifications: Flow<Boolean> = store.data.map { it[NOTIFICATIONS] ?: true }

    /** Turns reminder notifications on or off. */
    suspend fun setNotifications(on: Boolean) {
        store.edit { it[NOTIFICATIONS] = on }
    }

    /** The calendar the last new event went into, offered first for the next one. */
    val lastCalendar: Flow<Long?> = store.data.map { it[LAST_CALENDAR] }

    /** Remembers the calendar a new event went into. */
    suspend fun setLastCalendar(id: Long) {
        store.edit { it[LAST_CALENDAR] = id }
    }

    private companion object {
        val VIEW = stringPreferencesKey("view")
        val FIRST_DAY = stringPreferencesKey("first_day")
        val EVENT_LENGTH = intPreferencesKey("event_length")
        val DEFAULT_REMINDER = intPreferencesKey("default_reminder")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val LAST_CALENDAR = longPreferencesKey("last_calendar")
    }
}
