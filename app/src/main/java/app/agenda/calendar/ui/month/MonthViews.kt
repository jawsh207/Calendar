package app.agenda.calendar.ui.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.agenda.calendar.data.Occurrence
import app.agenda.calendar.data.agendaDays
import app.agenda.calendar.data.monthGrid
import app.agenda.calendar.data.monthOfPage
import app.agenda.calendar.data.monthPage
import app.agenda.calendar.ui.CalendarViewModel
import app.agenda.calendar.ui.agenda.agendaItems
import app.agenda.calendar.ui.components.Formats
import app.agenda.calendar.ui.components.SEGMENT_GAP
import app.agenda.calendar.ui.rememberOccurrences
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Keeps a month pager and the picked day in step: picking a day in another month (Today, the
 * date picker, another view) turns to that month, and swiping to a month picks a day in it
 * (today if it's there, otherwise the 1st).
 */
@Composable
fun FollowSelection(pager: PagerState, viewModel: CalendarViewModel) {
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    LaunchedEffect(selected) {
        val page = monthPage(YearMonth.from(selected))
        if (pager.currentPage != page) pager.animateScrollToPage(page)
    }
    LaunchedEffect(pager.settledPage) {
        val month = monthOfPage(pager.settledPage)
        if (YearMonth.from(viewModel.selected.value) != month) {
            viewModel.select(if (YearMonth.from(today) == month) today else month.atDay(1))
        }
    }
}

/**
 * The Month view: one month per page, swiped sideways, each day with its events written in.
 * Tapping a day calls [onDayClick] (the home screen shows that day's events); a long press
 * starts a new event on it.
 */
@Composable
fun MonthView(
    viewModel: CalendarViewModel,
    pager: PagerState,
    firstDay: DayOfWeek,
    contentPadding: PaddingValues,
    formats: Formats,
    onDayClick: (LocalDate) -> Unit,
    onNewEvent: (LocalDate) -> Unit,
) {
    FollowSelection(pager, viewModel)
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    HorizontalPager(
        state = pager,
        modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()),
        key = { it },
    ) { page ->
        val month = monthOfPage(page)
        val days = remember(month, firstDay) { monthGrid(month, firstDay) }
        val occurrences by rememberOccurrences(viewModel, days.first(), days.last())
        MonthGrid(
            month = month,
            firstDay = firstDay,
            today = today,
            selected = selected,
            occurrences = occurrences.orEmpty(),
            formats = formats,
            onDayClick = { day -> viewModel.select(day); onDayClick(day) },
            onDayLongClick = onNewEvent,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
    }
}

/** How many days the list under the small month covers, from the picked day. */
private const val LIST_DAYS = 30

/**
 * The Month + agenda view: a small month on top, swiped sideways, and under it what's on from
 * the picked day for the next month, day by day. The picked day is always listed.
 */
@Composable
fun MonthAgendaView(
    viewModel: CalendarViewModel,
    pager: PagerState,
    firstDay: DayOfWeek,
    contentPadding: PaddingValues,
    formats: Formats,
    onOpen: (Occurrence) -> Unit,
) {
    FollowSelection(pager, viewModel)
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(selected) { listState.scrollToItem(0) }
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth(), key = { it }) { page ->
            val month = monthOfPage(page)
            val days = remember(month, firstDay) { monthGrid(month, firstDay) }
            val occurrences by rememberOccurrences(viewModel, days.first(), days.last())
            CompactMonthGrid(
                month, firstDay, today, selected, occurrences.orEmpty(), formats,
                onDayClick = viewModel::select,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        val occurrences by rememberOccurrences(viewModel, selected, selected.plusDays(LIST_DAYS.toLong()))
        val days = remember(occurrences, selected) { agendaDays(occurrences.orEmpty(), selected, LIST_DAYS, alwaysShow = setOf(selected)) }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        ) {
            agendaItems(days, today, formats, onOpen)
        }
    }
}
