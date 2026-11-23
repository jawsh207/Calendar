package app.agenda.calendar.ui.home

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.outlined.CalendarViewDay
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.agenda.calendar.R
import app.agenda.calendar.data.CalendarView
import app.agenda.calendar.data.Occurrence
import app.agenda.calendar.data.monthOfPage
import app.agenda.calendar.data.monthPage
import app.agenda.calendar.data.MONTH_PAGES
import app.agenda.calendar.data.WEEK_PAGES
import app.agenda.calendar.data.weekOfPage
import app.agenda.calendar.data.weekPage
import app.agenda.calendar.ui.CalendarViewModel
import app.agenda.calendar.ui.agenda.AgendaView
import app.agenda.calendar.ui.components.BottomBar
import app.agenda.calendar.ui.components.DatePickDialog
import app.agenda.calendar.ui.components.SearchPill
import app.agenda.calendar.ui.components.TodayIcon
import app.agenda.calendar.ui.components.rememberFormats
import app.agenda.calendar.ui.month.MonthAgendaView
import app.agenda.calendar.ui.month.MonthView
import app.agenda.calendar.ui.week.WeekView
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

/** A bottom tab for each [CalendarView], with its name and icons. */
private enum class Tab(val view: CalendarView, @StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    Month(CalendarView.Month, R.string.tab_month, Icons.Outlined.CalendarViewMonth, Icons.Filled.CalendarViewMonth),
    Week(CalendarView.Week, R.string.tab_week, Icons.Outlined.CalendarViewWeek, Icons.Filled.CalendarViewWeek),
    Agenda(CalendarView.Agenda, R.string.tab_agenda, Icons.Outlined.ViewAgenda, Icons.Filled.ViewAgenda),
    MonthAgenda(CalendarView.MonthAgenda, R.string.tab_month_agenda, Icons.Outlined.CalendarViewDay, Icons.Filled.CalendarViewDay),
}

/**
 * The main screen, laid out like Notes and Contacts: the search bar pinned at the top with
 * Today (a calendar page with today's date in it, as in Google Calendar) and Settings at its end, a heading with the month being looked at (tap it to jump to a
 * date; arrows turn the page), the view itself, the + button for a new event, and the bottom
 * bar switching between Month, Week, Agenda and Month + agenda. The view chosen is remembered.
 */
@Composable
fun HomeScreen(
    viewModel: CalendarViewModel,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onOpen: (Occurrence) -> Unit,
    onNewEvent: (LocalDate, Int?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val todayLabel = stringResource(R.string.today)
    val motion = MaterialTheme.motionScheme
    val formats = rememberFormats()
    val view by viewModel.view.collectAsStateWithLifecycle()
    val firstDayChoice by viewModel.firstDay.collectAsStateWithLifecycle()
    val firstDay = firstDayChoice.day(formats.locale)
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val monthPager = rememberPagerState(initialPage = monthPage(YearMonth.from(selected))) { MONTH_PAGES }
    val weekPager = rememberPagerState(initialPage = weekPage(selected, firstDay)) { WEEK_PAGES }
    val agendaList = rememberLazyListState()
    var picking by remember { mutableStateOf(false) }
    var sheetDay by rememberSaveable { mutableStateOf<LocalDate?>(null) }

    val title = when (view) {
        CalendarView.Month, CalendarView.MonthAgenda -> formats.monthTitle(monthOfPage(monthPager.targetPage), today.year)
        CalendarView.Week -> formats.monthTitle(YearMonth.from(weekOfPage(weekPager.targetPage, firstDay).plusDays(3)), today.year)
        CalendarView.Agenda -> {
            // The day of the first row showing; rows are keyed "kind:date[:…]".
            val first = agendaList.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { item ->
                (item.key as? String)?.split(':')?.getOrNull(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            } ?: selected
            formats.monthTitle(YearMonth.from(first), today.year)
        }
    }
    val turn: (Int) -> Unit = { by ->
        scope.launch {
            when (view) {
                CalendarView.Week -> weekPager.animateScrollToPage(weekPager.currentPage + by)
                else -> monthPager.animateScrollToPage(monthPager.currentPage + by)
            }
        }
    }

    Scaffold(
        topBar = {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
                SearchPill(
                    onClick = onSearch,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    trailing = {
                        Row {
                            IconButton(onClick = viewModel::goToToday, modifier = Modifier.semantics { contentDescription = todayLabel }) {
                                TodayIcon(today)
                            }
                            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings)) }
                        }
                    },
                )
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { picking = true }.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AnimatedContent(
                            targetState = title,
                            transitionSpec = {
                                (fadeIn(motion.defaultEffectsSpec()) + slideInVertically { it / 3 }) togetherWith
                                    (fadeOut(motion.fastEffectsSpec()) + slideOutVertically { -it / 3 })
                            },
                            label = "title",
                        ) { text ->
                            Text(text, style = MaterialTheme.typography.headlineSmall)
                        }
                        Icon(Icons.Rounded.ArrowDropDown, stringResource(R.string.go_to_date))
                    }
                    AnimatedVisibility(view != CalendarView.Agenda, enter = fadeIn() + scaleIn(), exit = fadeOut()) {
                        Row {
                            IconButton(onClick = { turn(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.previous)) }
                            IconButton(onClick = { turn(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.next)) }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNewEvent(selected, null) }) {
                Icon(Icons.Rounded.Add, stringResource(R.string.new_event))
            }
        },
        bottomBar = {
            BottomBar {
                Tab.entries.forEach { tab ->
                    val on = tab.view == view
                    ShortNavigationBarItem(
                        selected = on,
                        onClick = { viewModel.setView(tab.view) },
                        icon = { Icon(if (on) tab.selectedIcon else tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = view,
            transitionSpec = {
                fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.97f) togetherWith
                    fadeOut(motion.fastEffectsSpec())
            },
            label = "view",
            modifier = Modifier.fillMaxSize(),
        ) { shown ->
            when (shown) {
                CalendarView.Month -> MonthView(viewModel, monthPager, firstDay, padding, formats, onDayClick = { sheetDay = it }, onNewEvent = { onNewEvent(it, null) })
                CalendarView.Week -> WeekView(viewModel, weekPager, firstDay, padding, formats, onOpen = onOpen, onNewEvent = onNewEvent)
                CalendarView.Agenda -> AgendaView(viewModel, agendaList, padding, formats, onOpen = onOpen)
                CalendarView.MonthAgenda -> MonthAgendaView(viewModel, monthPager, firstDay, padding, formats, onOpen = onOpen)
            }
        }
    }

    if (picking) {
        DatePickDialog(
            initial = selected,
            onPick = { picking = false; viewModel.select(it) },
            onDismiss = { picking = false },
        )
    }
    sheetDay?.let { day ->
        DaySheet(
            viewModel = viewModel,
            day = day,
            formats = formats,
            onOpen = { sheetDay = null; onOpen(it) },
            onNewEvent = { sheetDay = null; onNewEvent(day, null) },
            onDismiss = { sheetDay = null },
        )
    }
}
