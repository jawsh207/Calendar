package app.agenda.calendar.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import app.agenda.calendar.R
import app.agenda.calendar.data.CalendarRepository
import app.agenda.calendar.data.MonthWidgetCell
import app.agenda.calendar.data.monthGrid
import app.agenda.calendar.data.monthWidgetCells
import app.agenda.calendar.data.monthWidgetMonth
import app.agenda.calendar.ui.components.Formats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Which month the Month widget shows, kept in each widget's own state as months away from
 * this one, so a widget left on next month stays a month ahead as time passes.
 */
val MONTH_OFFSET = intPreferencesKey("month_offset")

/**
 * The Month widget: a month of days on a rounded card cut into cells, like the app's Month
 * view, with a dot for each event (up to three) and today circled. The arrows move between
 * months; tapping a day opens the app on it. Tapping the month's name opens the app, or comes
 * back to this month when another month is showing.
 */
class MonthWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val stamp = currentState(REFRESHED)
            val offset = currentState(MONTH_OFFSET) ?: 0
            // Starts empty for a moment the first time; afterwards the last month stays up
            // while the next one is read.
            val shown by produceState<Shown?>(null, stamp, offset) { value = load(context, offset) }
            GlanceTheme {
                val month = shown
                if (month == null) Column(widgetFrame()) {} else Month(month, offset)
            }
        }
    }

    /** The month shown, its cells, and today; [cells] is null without calendar access. */
    private class Shown(val month: YearMonth, val today: LocalDate, val firstDay: DayOfWeek, val cells: List<MonthWidgetCell>?)

    private suspend fun load(context: Context, offset: Int): Shown = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val month = monthWidgetMonth(today, offset)
        val firstDay = widgetFirstDay(context)
        if (!hasCalendarAccess(context)) return@withContext Shown(month, today, firstDay, null)
        val grid = monthGrid(month, firstDay)
        val occurrences = CalendarRepository(context).occurrences(grid.first(), grid.last())
        Shown(month, today, firstDay, monthWidgetCells(month, firstDay, occurrences, today))
    }

    @Composable
    private fun Month(shown: Shown, offset: Int) {
        val context = LocalContext.current
        val formats = Formats(context, widgetLocale(context))
        Column(widgetFrame()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val titleTap = if (offset == 0) {
                    actionStartActivity(openDayIntent(context, shown.today))
                } else {
                    actionRunCallback<ChangeMonth>(actionParametersOf(ChangeMonth.STEP to 0))
                }
                Text(
                    formats.monthTitle(shown.month, shown.today.year),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight().padding(start = 8.dp, top = 6.dp, bottom = 6.dp).clickable(titleTap),
                )
                Arrow(R.drawable.ic_previous, context.getString(R.string.previous), -1)
                Arrow(R.drawable.ic_next, context.getString(R.string.next), 1)
            }
            val cells = shown.cells
            if (cells == null) {
                NoAccess(context)
            } else {
                WeekdayLetters(cells.take(7).map { it.date }, formats)
                // The card: its corners clip the outer cells, and the 1dp around each cell
                // leaves the page showing as the lines between days.
                Column(GlanceModifier.fillMaxSize().cornerRadius(20.dp)) {
                    cells.chunked(7).forEach { week ->
                        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                            week.forEach { Cell(it) }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Arrow(icon: Int, label: String, step: Int) {
        Box(
            GlanceModifier.size(40.dp).cornerRadius(20.dp)
                .clickable(actionRunCallback<ChangeMonth>(actionParametersOf(ChangeMonth.STEP to step))),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                ImageProvider(icon),
                contentDescription = label,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.size(24.dp),
            )
        }
    }

    @Composable
    private fun ColumnScope.WeekdayLetters(days: List<LocalDate>, formats: Formats) {
        Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
            days.forEach { day ->
                Text(
                    formats.weekday(day, java.time.format.TextStyle.NARROW),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center),
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
    }

    @Composable
    private fun RowScope.Cell(cell: MonthWidgetCell) {
        val context = LocalContext.current
        Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(1.dp)) {
            Column(
                GlanceModifier.fillMaxSize().background(GlanceTheme.colors.surface).padding(top = 3.dp)
                    .clickable(actionStartActivity(openDayIntent(context, cell.date))),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val numberColor = when {
                    cell.today -> GlanceTheme.colors.onPrimary
                    cell.inMonth -> GlanceTheme.colors.onSurface
                    else -> GlanceTheme.colors.outline
                }
                Box(
                    if (cell.today) {
                        GlanceModifier.size(24.dp).cornerRadius(12.dp).background(GlanceTheme.colors.primary)
                    } else {
                        GlanceModifier.size(24.dp)
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        cell.date.dayOfMonth.toString(),
                        style = TextStyle(
                            color = numberColor,
                            fontSize = 13.sp,
                            fontWeight = if (cell.today) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
                Spacer(GlanceModifier.height(2.dp))
                Row {
                    cell.dots.forEachIndexed { i, argb ->
                        if (i > 0) Spacer(GlanceModifier.width(3.dp))
                        Box(
                            GlanceModifier.size(5.dp).cornerRadius(3.dp)
                                .background(ColorProvider(day = Color(argb), night = Color(argb))),
                        ) {}
                    }
                }
            }
        }
    }
}

/**
 * The Month widget's arrows and title: moves the widget [STEP] months on (back when negative),
 * or back to this month when the step is 0, then redraws it.
 */
class ChangeMonth : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val step = parameters[STEP] ?: 0
        updateAppWidgetState(context, glanceId) { state ->
            state[MONTH_OFFSET] = if (step == 0) 0 else (state[MONTH_OFFSET] ?: 0) + step
        }
        MonthWidget().update(context, glanceId)
    }

    /** The key for how many months to move. */
    companion object {
        /** How many months to move; 0 for back to this month. */
        val STEP = ActionParameters.Key<Int>("step")
    }
}

/**
 * Android's entry point for the Month widget, named in the manifest. Besides drawing, each
 * update makes sure the widgets are kept current (see [keepWidgetsCurrent]).
 */
class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        keepWidgetsCurrent(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        keepWidgetsCurrent(context)
    }
}
