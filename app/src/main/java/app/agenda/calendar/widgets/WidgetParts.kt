package app.agenda.calendar.widgets

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.agenda.calendar.MainActivity
import app.agenda.calendar.R
import app.agenda.calendar.data.Settings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.first

/**
 * What the two home screen widgets share: the intents their taps send to the app, reading
 * whether the app may see the calendars, and the widget's outer frame.
 *
 * Every tap opens the app with a request it already answers for other apps (open an event,
 * show a day, add an event), so the widgets need nothing of their own in the app.
 */

/**
 * A number the widgets keep in their own small state and change on every refresh. Glance only
 * reads the events again when something it shows from that state changes, so bumping this is
 * what makes a widget that's already on screen pick up new events.
 */
val REFRESHED = longPreferencesKey("refreshed")

/** Whether the app has been given calendar access yet; without it the widgets ask for it. */
fun hasCalendarAccess(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

/** The phone's language as the widget's context sees it. */
fun widgetLocale(context: Context): Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()

/** The day weeks start on, from the app's settings, as the Month view uses. */
suspend fun widgetFirstDay(context: Context): DayOfWeek = Settings(context).firstDay.first().day(widgetLocale(context))

/** Opens the app on event [eventId]'s page, at the repeat from [begin] to [end] that was tapped. */
fun openEventIntent(context: Context, eventId: Long, begin: Long, end: Long): Intent =
    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(Events.CONTENT_URI, eventId))
        .setClass(context, MainActivity::class.java)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)

/**
 * Opens the app on [day]. Midday is sent rather than midnight so a day whose clocks change at
 * midnight still lands on the right date.
 */
fun openDayIntent(context: Context, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/${day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()}"))
        .setClass(context, MainActivity::class.java)

/** Opens the editor on a new event, as the + button in the app does. */
fun newEventIntent(context: Context): Intent =
    Intent(Intent.ACTION_INSERT, Events.CONTENT_URI).setClass(context, MainActivity::class.java)

/** The modifier for a widget's outer frame: the launcher's widget corners, padding and background. */
@Composable
fun widgetFrame(): GlanceModifier = GlanceModifier
    .fillMaxSize()
    .background(GlanceTheme.colors.widgetBackground)
    .appWidgetBackground()
    .cornerRadius(android.R.dimen.system_app_widget_background_radius)
    .padding(12.dp)

/**
 * What a widget shows before the app has calendar access: a line saying so. Tapping it opens
 * the app, which asks.
 */
@Composable
fun NoAccess(context: Context) {
    Box(
        GlanceModifier.fillMaxSize()
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            context.getString(R.string.widget_no_access),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, textAlign = TextAlign.Center),
            modifier = GlanceModifier.padding(16.dp),
        )
    }
}
