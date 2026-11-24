package app.agenda.calendar.ui.month

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.agenda.calendar.R
import app.agenda.calendar.data.Occurrence
import app.agenda.calendar.data.monthGrid
import app.agenda.calendar.data.occurrencesOn
import app.agenda.calendar.ui.components.EventChip
import app.agenda.calendar.ui.components.Formats
import app.agenda.calendar.ui.components.GRID_CORNER
import app.agenda.calendar.ui.components.SEGMENT_GAP
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

/**
 * The row of weekday letters over a month grid, starting on [firstDay]. It sits on the page
 * above the grid's card, spaced like the cells so each letter is over its column.
 */
@Composable
fun WeekdayRow(firstDay: DayOfWeek, formats: Formats, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP)) {
        val start = monthGrid(YearMonth.of(2026, 1), firstDay).first()
        repeat(7) { i ->
            val day = start.plusDays(i.toLong())
            Text(
                formats.weekday(day, TextStyle.NARROW),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The card a calendar grid sits on: rounded like a grouped list and cut into [rows] rows of
 * seven cells in the search bar's colour, with [SEGMENT_GAP] of page showing between them, so
 * the gaps are the lines between the days. The outer cells take the card's rounded corners.
 * [cell] draws the inside of one cell; [rowModifier] sizes each row.
 */
@Composable
fun GridCard(
    rows: Int,
    modifier: Modifier = Modifier,
    rowModifier: ColumnScope.(row: Int) -> Modifier = { Modifier },
    cell: @Composable BoxScope.(row: Int, column: Int) -> Unit,
) {
    val card = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(GRID_CORNER)),
        verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
    ) {
        repeat(rows) { row ->
            Row(rowModifier(this, row).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP)) {
                repeat(7) { column ->
                    Box(Modifier.weight(1f).fillMaxHeight().background(card)) { cell(this, row, column) }
                }
            }
        }
    }
}

/**
 * The day number at the top of a cell: today in a filled primary circle, the picked day on an
 * expressive scalloped shape that springs in, days from the months either side faded.
 */
@Composable
fun DayNumber(day: LocalDate, today: Boolean, selected: Boolean, inMonth: Boolean, size: androidx.compose.ui.unit.Dp = 28.dp) {
    val scheme = MaterialTheme.colorScheme
    val scale by animateFloatAsState(if (selected) 1f else 0.6f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "pick")
    val cookie = MaterialShapes.Cookie4Sided.toShape()
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (selected && !today) {
            Box(
                Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale }.clip(cookie).background(scheme.secondaryContainer),
            )
        }
        if (today) Box(Modifier.fillMaxSize().clip(CircleShape).background(scheme.primary))
        Text(
            day.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (today || selected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                today -> scheme.onPrimary
                selected -> scheme.onSecondaryContainer
                inMonth -> scheme.onSurface
                else -> scheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
        )
    }
}

/**
 * A whole month as six weeks of cells on a [GridCard], each with its events written in as
 * coloured labels (as many as fit, then "+2"). Tapping a day picks it; a long press starts a
 * new event on it.
 */
@Composable
fun MonthGrid(
    month: YearMonth,
    firstDay: DayOfWeek,
    today: LocalDate,
    selected: LocalDate,
    occurrences: List<Occurrence>,
    formats: Formats,
    onDayClick: (LocalDate) -> Unit,
    onDayLongClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weeks = remember(month, firstDay) { monthGrid(month, firstDay).chunked(7) }
    Column(modifier.fillMaxSize()) {
        WeekdayRow(firstDay, formats)
        GridCard(rows = weeks.size, modifier = Modifier.weight(1f), rowModifier = { Modifier.weight(1f) }) { row, column ->
            val day = weeks[row][column]
            DayCell(
                day = day,
                inMonth = YearMonth.from(day) == month,
                today = day == today,
                selected = day == selected,
                occurrences = remember(occurrences, day) { occurrencesOn(occurrences, day) },
                onClick = { onDayClick(day) },
                onLongClick = { onDayLongClick(day) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** One day of the full month grid. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    today: Boolean,
    selected: Boolean,
    occurrences: List<Occurrence>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        // 28dp for the number, then 18dp a label.
        val fits = ((maxHeight - 32.dp) / 18.dp).toInt().coerceAtLeast(0)
        val shown = if (occurrences.size > fits) (fits - 1).coerceAtLeast(0) else occurrences.size
        Column(Modifier.fillMaxSize().padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(2.dp))
            DayNumber(day, today, selected, inMonth)
            Spacer(Modifier.height(2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                occurrences.take(shown).forEach { EventChip(it, faded = !inMonth) }
                val more = occurrences.size - shown
                if (more > 0) {
                    Text(
                        "+$more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * The small month of the Month + agenda view, on a [GridCard] like the full month: day numbers
 * only, with a dot for each of the first three events. Tapping a day picks it and the list
 * below follows.
 */
@Composable
fun CompactMonthGrid(
    month: YearMonth,
    firstDay: DayOfWeek,
    today: LocalDate,
    selected: LocalDate,
    occurrences: List<Occurrence>,
    formats: Formats,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weeks = remember(month, firstDay) { monthGrid(month, firstDay).chunked(7) }
    Column(modifier.fillMaxWidth()) {
        WeekdayRow(firstDay, formats)
        GridCard(rows = weeks.size, rowModifier = { Modifier.height(44.dp) }) { row, column ->
            val day = weeks[row][column]
            val onDay = remember(occurrences, day) { occurrencesOn(occurrences, day) }
            val description = pluralStringResource(R.plurals.event_count, onDay.size, onDay.size)
            Column(
                Modifier.fillMaxSize()
                    .combinedClickable(onClick = { onDayClick(day) })
                    .semantics { contentDescription = "${formats.shortDay.format(day)}, $description" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                DayNumber(day, day == today, day == selected, YearMonth.from(day) == month, size = 32.dp)
                Row(Modifier.height(6.dp).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    onDay.take(3).forEach { o ->
                        Box(Modifier.size(4.dp).clip(CircleShape).background(Color(o.color)))
                    }
                }
            }
        }
    }
}
