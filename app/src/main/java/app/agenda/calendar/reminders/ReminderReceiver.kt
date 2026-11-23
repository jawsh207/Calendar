package app.agenda.calendar.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.CalendarAlerts
import app.agenda.calendar.MainActivity
import app.agenda.calendar.R
import app.agenda.calendar.data.Settings
import app.agenda.calendar.data.occurrenceOf
import app.agenda.calendar.ui.components.Formats
import app.agenda.calendar.ui.components.timeText
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Shows event reminders. Android's calendar storage keeps the alarms for every event's
 * reminders itself, whichever app made the event, and sends this broadcast when one is due;
 * it's one of the few that still reach apps that aren't running. The receiver then shows a
 * notification for each due reminder and marks it shown, so it isn't shown again.
 *
 * Another calendar app (Google Calendar, say) gets the same broadcast and may show its own
 * notification, which is why Settings can turn these off.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CalendarContract.ACTION_EVENT_REMINDER) return
        // Reading the settings and the storage takes a moment; goAsync keeps the receiver alive for it.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (Settings(context).notifications.first()) showDue(context)
            } finally {
                pending.finish()
            }
        }
    }

    /** Shows every reminder that's due and not yet shown, then marks them shown. */
    private fun showDue(context: Context) {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH),
        )
        val now = System.currentTimeMillis()
        val formats = Formats(context, context.resources.configuration.locales[0])
        val due = runCatching {
            context.contentResolver.query(
                CalendarAlerts.CONTENT_URI,
                arrayOf(
                    CalendarAlerts._ID, CalendarAlerts.EVENT_ID, CalendarAlerts.BEGIN, CalendarAlerts.END,
                    CalendarAlerts.TITLE, CalendarAlerts.EVENT_LOCATION, CalendarAlerts.ALL_DAY,
                ),
                "${CalendarAlerts.STATE} = ? AND ${CalendarAlerts.ALARM_TIME} <= ?",
                arrayOf(CalendarAlerts.STATE_SCHEDULED.toString(), now.toString()),
                "${CalendarAlerts.BEGIN} ASC",
            )?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(Due(c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3), c.getString(4).orEmpty(), c.getString(5).orEmpty(), c.getInt(6) != 0))
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())

        due.forEach { alert ->
            // Long over (the phone was off): mark it, but don't ring for it now.
            if (alert.end >= now) notify(context, manager, formats, alert)
            runCatching {
                context.contentResolver.update(
                    ContentUris.withAppendedId(CalendarAlerts.CONTENT_URI, alert.id),
                    ContentValues().apply { put(CalendarAlerts.STATE, CalendarAlerts.STATE_FIRED) },
                    null, null,
                )
            }
        }
    }

    private fun notify(context: Context, manager: NotificationManager, formats: Formats, alert: Due) {
        val o = occurrenceOf(alert.eventId, 0, alert.title, alert.location, 0, alert.allDay, alert.begin, alert.end, false, ZoneId.systemDefault())
        val text = listOfNotNull(
            timeText(context, formats, o, o.startDate),
            alert.location.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_EVENT)
            .putExtra(MainActivity.EXTRA_EVENT_ID, alert.eventId)
            .putExtra(MainActivity.EXTRA_BEGIN, alert.begin)
            .putExtra(MainActivity.EXTRA_END, alert.end)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val code = (alert.eventId * 31 + alert.begin).hashCode()
        val tap = PendingIntent.getActivity(context, code, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alert.title.ifBlank { context.getString(R.string.no_title) })
            .setContentText(text)
            .setCategory(Notification.CATEGORY_EVENT)
            .setWhen(alert.begin)
            .setShowWhen(!alert.allDay)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        // Without notification access this is refused; there's nowhere else to show it.
        runCatching { manager.notify(code, notification) }
    }

    /** One reminder that's due. */
    private class Due(val id: Long, val eventId: Long, val begin: Long, val end: Long, val title: String, val location: String, val allDay: Boolean)

    private companion object {
        const val CHANNEL = "reminders"
    }
}
