package app.agenda.calendar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.agenda.calendar.R
import app.agenda.calendar.data.FirstDay
import app.agenda.calendar.data.reminderChoices
import app.agenda.calendar.ui.CalendarViewModel
import app.agenda.calendar.ui.components.BackTopBar
import app.agenda.calendar.ui.components.ChoiceDialog
import app.agenda.calendar.ui.components.ListRow
import app.agenda.calendar.ui.components.SEGMENT_GAP
import app.agenda.calendar.ui.components.SectionHeader
import app.agenda.calendar.ui.components.reminderText
import app.agenda.calendar.ui.components.segmentShape

/** The lengths offered for new events, in minutes. */
private val lengths = listOf(30, 60, 90, 120)

/**
 * Settings, in grouped cards as in the other apps: the calendars to show, which day weeks start
 * on, how long new events last and the reminder they get, and whether the app shows reminder
 * notifications (turned off when another calendar app already does).
 */
@Composable
fun SettingsScreen(viewModel: CalendarViewModel, onBack: () -> Unit, onCalendars: () -> Unit) {
    val context = LocalContext.current
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()
    val firstDay by viewModel.firstDay.collectAsStateWithLifecycle()
    val length by viewModel.eventLength.collectAsStateWithLifecycle()
    val reminder by viewModel.defaultReminder.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    var pickingReminder by remember { mutableStateOf(false) }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.settings), onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        ) {
            item { SectionHeader(stringResource(R.string.settings_calendars)) }
            item {
                val shown = calendars.orEmpty().count { it.visible }
                ListRow(
                    icon = Icons.Outlined.CalendarMonth,
                    title = stringResource(R.string.calendars),
                    summary = context.resources.getQuantityString(R.plurals.calendars_shown, shown, shown, calendars.orEmpty().size),
                    shape = segmentShape(0, 1),
                    onClick = onCalendars,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item { SectionHeader(stringResource(R.string.settings_view)) }
            item {
                Segments(
                    title = stringResource(R.string.week_starts),
                    shape = segmentShape(0, 1),
                    choices = FirstDay.entries,
                    selected = firstDay,
                    label = { day ->
                        stringResource(
                            when (day) {
                                FirstDay.Auto -> R.string.first_day_auto
                                FirstDay.Saturday -> R.string.first_day_saturday
                                FirstDay.Sunday -> R.string.first_day_sunday
                                FirstDay.Monday -> R.string.first_day_monday
                            },
                        )
                    },
                    onPick = viewModel::setFirstDay,
                )
            }

            item { SectionHeader(stringResource(R.string.settings_new_events)) }
            item {
                Segments(
                    title = stringResource(R.string.event_length),
                    shape = segmentShape(0, 2),
                    choices = lengths,
                    selected = length.takeIf { it in lengths },
                    label = { minutes ->
                        if (minutes % 60 == 0) stringResource(R.string.length_hours, minutes / 60)
                        else stringResource(R.string.length_minutes, minutes)
                    },
                    onPick = viewModel::setEventLength,
                )
            }
            item {
                ListRow(
                    icon = Icons.Outlined.Notifications,
                    title = stringResource(R.string.default_reminder),
                    summary = if (reminder < 0) stringResource(R.string.no_reminder) else reminderText(context, reminder),
                    shape = segmentShape(1, 2),
                    onClick = { pickingReminder = true },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item { SectionHeader(stringResource(R.string.settings_reminders)) }
            item {
                ListRow(
                    icon = Icons.Outlined.NotificationsActive,
                    title = stringResource(R.string.notifications_title),
                    summary = stringResource(R.string.notifications_summary),
                    shape = segmentShape(0, 1),
                    onClick = { viewModel.setNotifications(!notifications) },
                    trailing = { Switch(checked = notifications, onCheckedChange = viewModel::setNotifications) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item { SectionHeader(stringResource(R.string.settings_about)) }
            item {
                ListRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.about_title),
                    summary = stringResource(R.string.about_body),
                    shape = segmentShape(0, 1),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }

    if (pickingReminder) {
        ChoiceDialog(
            title = stringResource(R.string.default_reminder),
            choices = listOf(-1) + reminderChoices,
            selected = reminder,
            label = { if (it < 0) context.getString(R.string.no_reminder) else reminderText(context, it) },
            onPick = { viewModel.setDefaultReminder(it); pickingReminder = false },
            onDismiss = { pickingReminder = false },
        )
    }
}

/** A card with a title and a row of segmented buttons to pick one of [choices], as Notes' "Sort by". */
@Composable
private fun <T> Segments(
    title: String,
    shape: Shape,
    choices: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                choices.forEachIndexed { index, choice ->
                    SegmentedButton(
                        selected = choice == selected,
                        onClick = { onPick(choice) },
                        shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                        label = { Text(label(choice), maxLines = 1) },
                    )
                }
            }
        }
    }
}
