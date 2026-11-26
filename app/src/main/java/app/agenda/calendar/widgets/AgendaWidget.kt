package app.agenda.calendar.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.agenda.calendar.R
import app.agenda.calendar.data.AGENDA_WIDGET_DAYS
import app.agenda.calendar.data.CalendarRepository
import app.agenda.calendar.data.Occurrence
import app.agenda.calendar.data.agendaWidgetDays
import app.agenda.calendar.ui.components.Formats
import app.agenda.calendar.ui.components.timeText
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Agenda widget: today's date with a + for a new event, and the next 30 days that have
 * something on, as in the app's Agenda view. Tapping an event opens it, tapping a day's
 * heading or the date opens the app on that day.
 */
class AgendaWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = load(context)
        provideContent {
            // Read again whenever a refresh bumps REFRESHED, while the widget is already showing.
            val stamp = currentState(REFRESHED)
            val shown by produceState(first, stamp) { value = load(context) }
            GlanceTheme { Agenda(shown) }
        }
    }

    /** What the widget shows: today and the days with something on, or null without calendar access. */
    private class Shown(val today: LocalDate, val days: List<Pair<LocalDate, List<Occurrence>>>?)

    private suspend fun load(context: Context): Shown = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        if (!hasCalendarAccess(context)) return@withContext Shown(today, null)
        val occurrences = CalendarRepository(context).occurrences(today, today.plusDays(AGENDA_WIDGET_DAYS - 1L))
        Shown(today, agendaWidgetDays(occurrences, today))
    }

    /** One line of the list: a day's heading, one of its events, or the line saying it's empty. */
    private sealed interface Line {
        val key: Long

        class Heading(override val key: Long, val day: LocalDate) : Line
        class Event(override val key: Long, val day: LocalDate, val occurrence: Occurrence, val index: Int, val count: Int) : Line
        class Empty(override val key: Long, val day: LocalDate) : Line
    }

    // Each event is its own list line: Glance draws only the first ten things in a column, so a
    // busy day can't be one item holding all its events.
    private fun lines(days: List<Pair<LocalDate, List<Occurrence>>>): List<Line> {
        val lines = mutableListOf<Line>()
        days.forEach { (day, list) ->
            lines += Line.Heading(lines.size.toLong(), day)
            if (list.isEmpty()) lines += Line.Empty(lines.size.toLong(), day)
            list.forEachIndexed { i, occurrence -> lines += Line.Event(lines.size.toLong(), day, occurrence, i, list.size) }
        }
        return lines
    }

    @Composable
    private fun Agenda(shown: Shown) {
        val context = LocalContext.current
        val formats = Formats(context, widgetLocale(context))
        Column(widgetFrame()) {
            Row(GlanceModifier.fillMaxWidth().padding(start = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(openDayIntent(context, shown.today)))) {
                    Text(
                        formats.weekday(shown.today, java.time.format.TextStyle.FULL),
                        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                    Text(
                        formats.shortDay.format(shown.today),
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 22.sp),
                        maxLines = 1,
                    )
                }
                Box(
                    GlanceModifier.size(48.dp).cornerRadius(16.dp).background(GlanceTheme.colors.primaryContainer)
                        .clickable(actionStartActivity(newEventIntent(context))),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        ImageProvider(R.drawable.ic_add),
                        contentDescription = context.getString(R.string.new_event),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                        modifier = GlanceModifier.size(24.dp),
                    )
                }
            }
            val days = shown.days
            if (days == null) {
                NoAccess(context)
            } else {
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(lines(days), itemId = { it.key }) { line ->
                        when (line) {
                            is Line.Heading -> DayHeading(line.day, shown.today, formats)
                            is Line.Event -> EventLine(line, formats)
                            is Line.Empty -> EmptyLine()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DayHeading(day: LocalDate, today: LocalDate, formats: Formats) {
        val context = LocalContext.current
        val label = when (day) {
            today -> context.getString(R.string.today_named, formats.day(day, today.year))
            today.plusDays(1) -> context.getString(R.string.tomorrow_named, formats.day(day, today.year))
            else -> formats.day(day, today.year)
        }
        // 16dp in from the cards' edge, over the colour bars, as the app's headings sit.
        Text(
            label,
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            modifier = GlanceModifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 6.dp)
                .clickable(actionStartActivity(openDayIntent(context, day))),
        )
    }

    @Composable
    private fun EventLine(line: Line.Event, formats: Formats) {
        val context = LocalContext.current
        val occurrence = line.occurrence
        val segment = when {
            line.count == 1 -> R.drawable.widget_segment_single
            line.index == 0 -> R.drawable.widget_segment_top
            line.index == line.count - 1 -> R.drawable.widget_segment_bottom
            else -> R.drawable.widget_segment_middle
        }
        val time = timeText(context, formats, occurrence, line.day)
        val detail = listOfNotNull(time, occurrence.location.takeIf { it.isNotBlank() }).joinToString(" · ")
        Column(GlanceModifier.fillMaxWidth()) {
            if (line.index > 0) Spacer(GlanceModifier.height(2.dp))
            Row(
                GlanceModifier.fillMaxWidth()
                    .background(ImageProvider(segment), colorFilter = ColorFilter.tint(GlanceTheme.colors.surface))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clickable(actionStartActivity(openEventIntent(context, occurrence.eventId, occurrence.begin, occurrence.end))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    GlanceModifier.width(5.dp).height(36.dp).cornerRadius(3.dp)
                        .background(ColorProvider(day = Color(occurrence.color), night = Color(occurrence.color))),
                ) {}
                Spacer(GlanceModifier.width(14.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(
                        occurrence.title.ifBlank { context.getString(R.string.no_title) },
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp),
                        maxLines = 1,
                    )
                    Text(
                        detail,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }

    @Composable
    private fun EmptyLine() {
        val context = LocalContext.current
        Text(
            context.getString(R.string.nothing_planned),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
            modifier = GlanceModifier.fillMaxWidth()
                .background(ImageProvider(R.drawable.widget_segment_single), colorFilter = ColorFilter.tint(GlanceTheme.colors.surface))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

/**
 * Android's entry point for the Agenda widget, named in the manifest. Besides drawing, each
 * update makes sure the widgets are kept current (see [keepWidgetsCurrent]).
 */
class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        keepWidgetsCurrent(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        keepWidgetsCurrent(context)
    }
}
