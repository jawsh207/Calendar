package app.agenda.calendar.ui.agenda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.agenda.calendar.R
import app.agenda.calendar.data.Occurrence
import app.agenda.calendar.data.agendaDays
import app.agenda.calendar.ui.CalendarViewModel
import app.agenda.calendar.ui.components.DayHeading
import app.agenda.calendar.ui.components.EventCard
import app.agenda.calendar.ui.components.Formats
import app.agenda.calendar.ui.components.NothingPlanned
import app.agenda.calendar.ui.components.SEGMENT_GAP
import app.agenda.calendar.ui.components.segmentShape
import app.agenda.calendar.ui.rememberOccurrences
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Adds day-by-day agenda rows to a list: each day's heading, then its events as one group of
 * cards, or "Nothing planned" for a day listed with nothing on it.
 */
fun LazyListScope.agendaItems(
    days: List<Pair<LocalDate, List<Occurrence>>>,
    today: LocalDate,
    formats: Formats,
    onOpen: (Occurrence) -> Unit,
) {
    days.forEach { (day, list) ->
        item(key = "day:$day", contentType = "heading") { DayHeading(day, today, formats, Modifier.animateItem()) }
        if (list.isEmpty()) {
            item(key = "none:$day", contentType = "none") { NothingPlanned(Modifier.padding(horizontal = 16.dp).animateItem()) }
        } else {
            itemsIndexed(list, key = { _, o -> "event:$day:${o.eventId}:${o.begin}" }, contentType = { _, _ -> "event" }) { index, o ->
                EventCard(o, day, segmentShape(index, list.size), formats, onClick = { onOpen(o) }, modifier = Modifier.padding(horizontal = 16.dp).animateItem())
            }
        }
    }
}

/** How many days the agenda reads at first, and adds each time the end is reached. */
private const val AGENDA_STEP = 90

/**
 * The Agenda view: what's on from the picked day onwards, day by day, with today always listed.
 * It reads further ahead as it's scrolled to the end (or with "Show later"), and "Show earlier"
 * at the top reaches back a month at a time. Picking a day elsewhere (Today, the date picker) starts it there.
 */
@Composable
fun AgendaView(
    viewModel: CalendarViewModel,
    state: LazyListState,
    contentPadding: PaddingValues,
    formats: Formats,
    onOpen: (Occurrence) -> Unit,
) {
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    var start by rememberSaveable { mutableStateOf(selected) }
    var span by rememberSaveable { mutableIntStateOf(AGENDA_STEP) }
    // A day picked elsewhere starts the list there; the first run keeps the saved place.
    var seen by rememberSaveable { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected != seen) {
            seen = selected
            start = selected
            span = AGENDA_STEP
            state.scrollToItem(0)
        }
    }
    val occurrences by rememberOccurrences(viewModel, start, start.plusDays(span.toLong()))
    val days = remember(occurrences, start, span, today, selected) {
        agendaDays(occurrences.orEmpty(), start, span, alwaysShow = setOf(today, selected))
    }
    val nearEnd by remember { derivedStateOf { state.layoutInfo.visibleItemsInfo.lastOrNull()?.let { it.index >= state.layoutInfo.totalItemsCount - 4 } ?: false } }
    LaunchedEffect(nearEnd, occurrences != null) { if (nearEnd && occurrences != null) span += AGENDA_STEP }

    if (occurrences == null) {
        Box(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding() + 96.dp), contentAlignment = Alignment.TopCenter) { LoadingIndicator() }
        return
    }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
    ) {
        item(key = "earlier", contentType = "earlier") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = {
                    val earlier = start.minusMonths(1)
                    span += ChronoUnit.DAYS.between(earlier, start).toInt()
                    start = earlier
                }) {
                    Icon(Icons.Rounded.ExpandLess, null, Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.show_earlier))
                }
            }
        }
        agendaItems(days, today, formats, onOpen)
        item(key = "later", contentType = "later") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = { span += AGENDA_STEP }) {
                    Icon(Icons.Rounded.ExpandMore, null, Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.show_later))
                }
            }
        }
    }
}
