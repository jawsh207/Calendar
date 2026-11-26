package app.agenda.calendar.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeping the widgets current. A widget is a picture the launcher keeps, drawn by the app only
 * when asked, so the app has to know when to draw again:
 * - when anything in the calendar storage changes (an edit here, another app, an account
 *   syncing), through [CalendarChangeJob];
 * - at midnight, when "today" moves on, through an alarm;
 * - when the clock, time zone, language or 12/24-hour setting changes, and after the phone
 *   starts or the app is updated, through [WidgetClockReceiver];
 * - when the app is given calendar access or the week's first day changes, from the app.
 */

/** Redraws every Calendar widget on the home screen with the calendar storage as it is now. */
suspend fun refreshWidgets(context: Context) {
    val app = context.applicationContext
    val manager = GlanceAppWidgetManager(app)
    val widgets: List<GlanceAppWidget> = listOf(AgendaWidget(), MonthWidget())
    widgets.forEach { widget ->
        manager.getGlanceIds(widget.javaClass).forEach { id ->
            updateAppWidgetState(app, id) { it[REFRESHED] = System.currentTimeMillis() }
            widget.update(app, id)
        }
    }
}

/** Whether any Calendar widget is on a home screen. */
fun hasWidgets(context: Context): Boolean {
    val manager = AppWidgetManager.getInstance(context)
    return listOf(AgendaWidgetReceiver::class.java, MonthWidgetReceiver::class.java).any {
        manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty()
    }
}

/**
 * Sets up the storage watch and the midnight alarm while there are widgets, and takes both
 * away once the last widget is removed, so the app isn't woken for nothing.
 */
fun keepWidgetsCurrent(context: Context) {
    val app = context.applicationContext
    if (hasWidgets(app)) {
        // Setting the job up again while it runs would stop it mid-refresh, so a watch that's
        // already waiting is left alone.
        if (app.getSystemService(JobScheduler::class.java).getPendingJob(CalendarChangeJob.JOB_ID) == null) {
            CalendarChangeJob.schedule(app)
        }
        scheduleMidnight(app)
    } else {
        app.getSystemService(JobScheduler::class.java).cancel(CalendarChangeJob.JOB_ID)
        app.getSystemService(AlarmManager::class.java).cancel(midnightIntent(app))
    }
}

/**
 * Redraws the widgets now, in the background. For the app's own changes that the storage
 * doesn't announce, such as the day weeks start on.
 */
fun refreshWidgetsSoon(context: Context) {
    val app = context.applicationContext
    if (!hasWidgets(app)) return
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        refreshWidgets(app)
        keepWidgetsCurrent(app)
    }
}

private fun midnightIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
    context, 0,
    Intent(context, WidgetClockReceiver::class.java).setAction(WidgetClockReceiver.ACTION_MIDNIGHT),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

// An inexact alarm needs no permission and doesn't wake the phone; the widgets turn over within
// a few minutes of midnight, or when the screen next comes on.
private fun scheduleMidnight(context: Context) {
    val midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    context.getSystemService(AlarmManager::class.java)
        .setWindow(AlarmManager.RTC, midnight, 10 * 60 * 1000L, midnightIntent(context))
}

/**
 * Wakes when anything in the calendar storage changes and redraws the widgets. Android runs it
 * once per change and then forgets it, so each run sets itself up again for the next change.
 * Changes are gathered for a couple of seconds first, so an account syncing many events
 * redraws once rather than once per event.
 */
class CalendarChangeJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            refreshWidgets(applicationContext)
            jobFinished(params, false)
            // Set up again for the next change, only after finishing: scheduling this job's id
            // while it runs would stop it.
            if (hasWidgets(applicationContext)) schedule(applicationContext)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Setting the job up. */
    companion object {
        /** The job's number; the app has no other jobs. */
        const val JOB_ID = 1

        /** Asks Android to run the job at the next change in the calendar storage. */
        fun schedule(context: Context) {
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, CalendarChangeJob::class.java))
                .addTriggerContentUri(JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .setTriggerContentUpdateDelay(2_000)
                .setTriggerContentMaxDelay(10_000)
                .build()
            context.getSystemService(JobScheduler::class.java).schedule(job)
        }
    }
}

/**
 * Redraws the widgets when the day or the way dates are shown changes: the midnight alarm, the
 * clock or time zone being changed (which is also how Android announces the 12/24-hour
 * setting), the language changing, and the phone starting up or the app being updated, after
 * which the storage watch and the alarm have to be set up again.
 */
class WidgetClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (!hasWidgets(app)) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                refreshWidgets(app)
                keepWidgetsCurrent(app)
            } finally {
                pending.finish()
            }
        }
    }

    /** The midnight alarm's action. */
    companion object {
        /** Sent by the alarm set for midnight. */
        const val ACTION_MIDNIGHT = "app.agenda.calendar.action.WIDGETS_MIDNIGHT"
    }
}
