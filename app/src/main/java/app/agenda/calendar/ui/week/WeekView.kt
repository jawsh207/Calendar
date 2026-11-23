package app.agenda.calendar.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.agenda.calendar.R
import app.agenda.calendar.data.MINUTES_PER_DAY
import app.agenda.calendar.data.Occurrence
import app.agenda.calendar.data.layoutDay
import app.agenda.calendar.data.occurrencesOn
import app.agenda.calendar.data.textOn
import app.agenda.calendar.data.weekDays
import app.agenda.calendar.data.weekOfPage
import app.agenda.calendar.data.weekPage
import app.agenda.calendar.ui.CalendarViewModel
import app.agenda.calendar.ui.components.EventChip
import app.agenda.calendar.ui.components.Formats
import app.agenda.calendar.ui.components.timeText
import app.agenda.calendar.ui.month.DayNumber
import app.agenda.calendar.ui.rememberOccurrences
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import kotlinx.coroutines.delay

/** The height of an hour in the time grid. */
private val HOUR = 56.dp

/** The width of the hour labels down the side. */
private val GUTTER = 48.dp

/** How many all-day labels a day shows before "+2". */
private const val ALL_DAY_ROWS = 2

/**
 * The Week view: seven days side by side, one week per page, swiped sideways. All-day events
 * sit in a row along the top; timed ones are blocks in the hour grid, side by side where they
 * overlap. A line marks the time now. Tapping an empty hour starts a new event then; the
 * hour scroll position stays put when changing weeks.
 */
@Composable
fun WeekView(
    viewModel: CalendarViewModel,
    pager: PagerState,
    firstDay: DayOfWeek,
    contentPadding: PaddingValues,
    formats: Formats,
    onOpen: (Occurrence) -> Unit,
    onNewEvent: (LocalDate, Int?) -> Unit,
) {
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    LaunchedEffect(selected, firstDay) {
        val page = weekPage(selected, firstDay)
        if (pager.currentPage != page) pager.animateScrollToPage(page)
    }
    LaunchedEffect(pager.settledPage, firstDay) {
        val start = weekOfPage(pager.settledPage, firstDay)
        val picked = viewModel.selected.value
        if (picked.isBefore(start) || picked.isAfter(start.plusDays(6))) {
            viewModel.select(if (!today.isBefore(start) && !today.isAfter(start.plusDays(6))) today else start)
        }
    }
    // Where the hours are scrolled to, shared by the weeks; -1 until the first week opens.
    val sharedScroll = rememberSaveable { mutableIntStateOf(-1) }
    HorizontalPager(
        state = pager,
        modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()),
        key = { it },
    ) { page ->
        WeekPage(
            viewModel = viewModel,
            start = weekOfPage(page, firstDay),
            firstDay = firstDay,
            today = today,
            selected = selected,
            formats = formats,
            sharedScroll = sharedScroll,
            current = { pager.currentPage == page },
            onOpen = onOpen,
            onNewEvent = onNewEvent,
        )
    }
}

/** One week of the Week view. */
@Composable
private fun WeekPage(
    viewModel: CalendarViewModel,
    start: LocalDate,
    firstDay: DayOfWeek,
    today: LocalDate,
    selected: LocalDate,
    formats: Formats,
    sharedScroll: MutableIntState,
    current: () -> Boolean,
    onOpen: (Occurrence) -> Unit,
    onNewEvent: (LocalDate, Int?) -> Unit,
) {
    val days = remember(start, firstDay) { weekDays(start, firstDay) }
    val occurrences by rememberOccurrences(viewModel, days.first(), days.last())
    val list = occurrences.orEmpty()
    val allDay = remember(list, days) { days.map { day -> occurrencesOn(list, day).filter { it.allDay } } }
    val scheme = MaterialTheme.colorScheme
    val hourPx = with(LocalDensity.current) { HOUR.toPx() }
    // Opens at half past seven, so the working day is in view.
    val scroll = rememberScrollState(if (sharedScroll.intValue >= 0) sharedScroll.intValue else (hourPx * 7.5f).toInt())
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.value }.collect { if (current()) sharedScroll.intValue = it }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Box(Modifier.width(GUTTER))
            days.forEach { day ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { viewModel.select(day) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        formats.weekday(day, TextStyle.SHORT),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (day == today) scheme.primary else scheme.onSurfaceVariant,
                    )
                    DayNumber(day, day == today, day == selected, inMonth = true, size = 32.dp)
                }
            }
        }
        if (allDay.any { it.isNotEmpty() }) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text(
                    stringResource(R.string.all_day_short),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(GUTTER).padding(top = 2.dp),
                )
                allDay.forEachIndexed { index, events ->
                    Column(Modifier.weight(1f).padding(horizontal = 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val shown = if (events.size > ALL_DAY_ROWS) ALL_DAY_ROWS - 1 else events.size
                        events.take(shown).forEach { o ->
                            EventChip(o, Modifier.clip(RoundedCornerShape(4.dp)).clickable { onOpen(o) })
                        }
                        if (events.size > shown) {
                            Text(
                                "+${events.size - shown}",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.clickable { viewModel.select(days[index]) }.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = scheme.outlineVariant)
        Row(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll)) {
            HourGutter(formats)
            days.forEach { day ->
                DayColumn(
                    day = day,
                    occurrences = list,
                    today = day == today,
                    formats = formats,
                    onOpen = onOpen,
                    onNewEvent = { hour -> onNewEvent(day, hour) },
                    modifier = Modifier.weight(1f).height(HOUR * 24),
                )
            }
        }
    }
}

/** The hour labels down the side of the time grid. */
@Composable
private fun HourGutter(formats: Formats) {
    Box(Modifier.width(GUTTER).height(HOUR * 24)) {
        for (hour in 1 until 24) {
            Text(
                formats.hour.format(LocalTime.of(hour, 0)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(GUTTER - 8.dp).offset(y = HOUR * hour - 7.dp),
            )
        }
    }
}

/** One day's column of the time grid: hour lines, the day's timed events, and the time now. */
@Composable
private fun DayColumn(
    day: LocalDate,
    occurrences: List<Occurrence>,
    today: Boolean,
    formats: Formats,
    onOpen: (Occurrence) -> Unit,
    onNewEvent: (Int) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val line = scheme.outlineVariant.copy(alpha = 0.6f)
    val placed = remember(occurrences, day) { layoutDay(occurrences, day) }
    val hourPx = with(LocalDensity.current) { HOUR.toPx() }
    val newEvent by rememberUpdatedState(onNewEvent)
    BoxWithConstraints(
        modifier
            .drawBehind {
                drawLine(line, Offset(0f, 0f), Offset(0f, size.height), 1f)
                for (hour in 1 until 24) drawLine(line, Offset(0f, hourPx * hour), Offset(size.width, hourPx * hour), 1f)
            }
            .pointerInput(day) { detectTapGestures(onTap = { offset -> newEvent((offset.y / hourPx).toInt().coerceIn(0, 23)) }) },
    ) {
        val columnWidth: Dp = maxWidth
        placed.forEach { p ->
            val width = columnWidth / p.columns
            val minutes = maxOf(p.endMinute - p.startMinute, 20)
            val fill = Color(p.occurrence.color)
            val text = Color(textOn(p.occurrence.color))
            Column(
                Modifier
                    .offset(x = width * p.column, y = HOUR * p.startMinute / 60f)
                    .width(width)
                    .height(HOUR * minutes / 60f)
                    .padding(1.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(fill)
                    .clickable { onOpen(p.occurrence) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(
                    p.occurrence.title.ifBlank { stringResource(R.string.no_title) },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = text,
                    maxLines = if (minutes >= 45) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (minutes >= 60) {
                    Text(
                        timeText(context, formats, p.occurrence, day),
                        style = MaterialTheme.typography.labelSmall,
                        color = text.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
        if (today) {
            val now by produceState(LocalTime.now()) {
                while (true) {
                    delay(60_000L - (System.currentTimeMillis() % 60_000L))
                    value = LocalTime.now()
                }
            }
            val minute = (now.hour * 60 + now.minute).coerceIn(0, MINUTES_PER_DAY)
            Box(Modifier.offset(y = HOUR * minute / 60f - 1.dp).fillMaxWidth().height(2.dp).background(scheme.primary))
            Box(Modifier.offset(x = (-4).dp, y = HOUR * minute / 60f - 5.dp).size(10.dp).clip(CircleShape).background(scheme.primary))
        }
    }
}
