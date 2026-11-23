package app.agenda.calendar.ui.components

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.agenda.calendar.R
import app.agenda.calendar.data.MINUTES_PER_DAY
import app.agenda.calendar.data.Occurrence
import app.agenda.calendar.data.ReminderUnit
import app.agenda.calendar.data.Repeat
import app.agenda.calendar.data.reminderParts
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Date and time formats in the phone's language, following its 12- or 24-hour setting (which
 * Java's own localized formats ignore). Made once per screen with [rememberFormats].
 */
class Formats(context: Context, val locale: Locale) {
    private fun pattern(skeleton: String) = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

    /** A clock time: "3:30 PM" or "15:30". */
    val time: DateTimeFormatter = pattern(if (DateFormat.is24HourFormat(context)) "Hm" else "hma")

    /** An hour on the week view's edge: "3 PM" or "15". */
    val hour: DateTimeFormatter = pattern(if (DateFormat.is24HourFormat(context)) "H" else "ha")

    /** A month and year: "November 2026". */
    val monthYear: DateTimeFormatter = pattern("MMMMy")

    /** A month on its own: "November". */
    val month: DateTimeFormatter = pattern("MMMM")

    /** A day in full: "Monday, November 23". */
    val day: DateTimeFormatter = pattern("EEEEMMMMd")

    /** A day in full with its year: "Monday, November 23, 2027". */
    val dayYear: DateTimeFormatter = pattern("EEEEMMMMdy")

    /** A short day: "Nov 23". */
    val shortDay: DateTimeFormatter = pattern("MMMd")

    /** A short day with the weekday: "Mon, Nov 23". */
    val weekdayShortDay: DateTimeFormatter = pattern("EEEMMMd")

    /** A day, with its year only when it isn't [thisYear]. */
    fun day(date: LocalDate, thisYear: Int): String = (if (date.year == thisYear) day else dayYear).format(date)

    /** A minute of the day as a clock time. */
    fun minute(minute: Int): String = time.format(LocalTime.of((minute / 60) % 24, minute % 60))

    /** The first letters of a weekday, as headed in the month grid: "M", "Tu"… kept to [style]. */
    fun weekday(date: LocalDate, style: TextStyle = TextStyle.SHORT): String = date.dayOfWeek.getDisplayName(style, locale)

    /** The title of a month: just the month in [thisYear], month and year otherwise. */
    fun monthTitle(month: YearMonth, thisYear: Int): String =
        (if (month.year == thisYear) this.month else monthYear).format(month.atDay(1))
}

/** The formats for the phone's current language and clock setting. */
@Composable
fun rememberFormats(): Formats {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0] ?: Locale.getDefault()
    return remember(locale, configuration) { Formats(context, locale) }
}

/**
 * What an occurrence's time reads as on [day]: "All day", "9:00 – 10:00 AM", or for one
 * spanning days, when it starts or ends that day.
 */
fun timeText(context: Context, formats: Formats, occurrence: Occurrence, day: LocalDate): String {
    if (occurrence.allDay) return context.getString(R.string.all_day)
    val starts = day == occurrence.startDate
    val ends = day == occurrence.endDate
    return when {
        starts && ends -> if (occurrence.startMinute == occurrence.endMinute) formats.minute(occurrence.startMinute)
        else context.getString(R.string.time_range, formats.minute(occurrence.startMinute), formats.minute(occurrence.endMinute))
        starts -> context.getString(R.string.time_from, formats.minute(occurrence.startMinute))
        ends && occurrence.endMinute < MINUTES_PER_DAY -> context.getString(R.string.time_until, formats.minute(occurrence.endMinute))
        else -> context.getString(R.string.all_day)
    }
}

/** A reminder time as words: "At the start", "10 minutes before", "1 day before". */
fun reminderText(context: Context, minutes: Int): String {
    if (minutes <= 0) return context.getString(R.string.reminder_at_start)
    val (amount, unit) = reminderParts(minutes)
    val plural = when (unit) {
        ReminderUnit.Minutes -> R.plurals.minutes_before
        ReminderUnit.Hours -> R.plurals.hours_before
        ReminderUnit.Days -> R.plurals.days_before
        ReminderUnit.Weeks -> R.plurals.weeks_before
    }
    return context.resources.getQuantityString(plural, amount, amount)
}

/** How an event repeats, in words, for an event starting on [start]. */
fun repeatText(context: Context, formats: Formats, repeat: Repeat, start: LocalDate): String = when (repeat) {
    Repeat.None -> context.getString(R.string.repeat_none)
    Repeat.Daily -> context.getString(R.string.repeat_daily)
    Repeat.Weekly -> context.getString(R.string.repeat_weekly, start.dayOfWeek.getDisplayName(TextStyle.FULL, formats.locale))
    Repeat.Monthly -> context.getString(R.string.repeat_monthly, start.dayOfMonth)
    Repeat.Yearly -> context.getString(R.string.repeat_yearly, formats.shortDay.format(start))
    is Repeat.Custom -> context.getString(R.string.repeat_custom)
}
