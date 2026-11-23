package app.agenda.calendar.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.AddAlert
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.agenda.calendar.R
import app.agenda.calendar.data.EditScope
import app.agenda.calendar.data.EventDraft
import app.agenda.calendar.data.calendarColors
import app.agenda.calendar.data.Repeat
import app.agenda.calendar.data.draftOf
import app.agenda.calendar.data.newDraft
import app.agenda.calendar.data.reminderChoices
import app.agenda.calendar.data.repeatChoices
import app.agenda.calendar.ui.CalendarViewModel
import app.agenda.calendar.ui.Screen
import app.agenda.calendar.ui.components.ChoiceDialog
import app.agenda.calendar.ui.components.ColorDot
import app.agenda.calendar.ui.components.DatePickDialog
import app.agenda.calendar.ui.components.Formats
import app.agenda.calendar.ui.components.SEGMENT_GAP
import app.agenda.calendar.ui.components.TimePickDialog
import app.agenda.calendar.ui.components.reminderText
import app.agenda.calendar.ui.components.rememberFormats
import app.agenda.calendar.ui.components.repeatText
import app.agenda.calendar.ui.components.segmentShape
import app.agenda.calendar.ui.event.accountLabel
import app.agenda.calendar.ui.settings.CalendarDialog
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Which picker or question is open over the editor. */
private enum class Open { StartDate, StartTime, EndDate, EndTime, Repeat, Calendar, NewCalendar, Reminder, Discard }

/**
 * The editor, for a new event or a change to one. The title is on top; then when (all day or
 * times, how it repeats), which calendar, reminders, place and notes, each in grouped cards.
 * Moving the start takes the end with it. Leaving with changes asks first. Saving writes
 * straight to the calendar storage, where other calendar apps see it too.
 *
 * The calendar row lists every calendar events can be saved to, and New calendar makes one on
 * this phone and picks it. Editing one repeat of a repeating event can't move it to another
 * calendar or make it repeat, so those rows are left out then.
 */
@Composable
fun EditEventScreen(viewModel: CalendarViewModel, screen: Screen.Edit, onClose: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val formats = rememberFormats()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()
    val writable = calendars.orEmpty().filter { it.writable }
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf<EventDraft?>(null) }
    var original by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf<EventDraft?>(null) }
    var open by remember { mutableStateOf<Open?>(null) }
    var saving by remember { mutableStateOf(false) }
    val onlyOne = screen.id != null && screen.scope == EditScope.One

    LaunchedEffect(Unit) {
        if (draft != null) return@LaunchedEffect
        val all = viewModel.calendars.filterNotNull().first()
        val usable = all.filter { it.writable && it.visible }.ifEmpty { all.filter { it.writable } }
        val preferred = viewModel.lastCalendar.value?.takeIf { id -> usable.any { it.id == id } } ?: usable.firstOrNull()?.id
        val start = if (screen.id == null) {
            val incoming = viewModel.takeDraft()
            incoming?.copy(calendarId = incoming.calendarId?.takeIf { id -> usable.any { it.id == id } } ?: preferred)
                ?: newDraft(
                    day = screen.day ?: viewModel.selected.value,
                    hour = screen.hour,
                    now = LocalDateTime.now(),
                    lengthMinutes = viewModel.eventLength.value,
                    reminder = viewModel.defaultReminder.value,
                    calendarId = preferred,
                )
        } else {
            val event = viewModel.loadEvent(screen.id)
            if (event == null) {
                onClose()
                return@LaunchedEffect
            }
            draftOf(event, screen.begin, wholeSeries = screen.scope == EditScope.All, zone = ZoneId.systemDefault())
        }
        draft = start
        original = start
    }
    val changed = draft != null && draft != original
    val close: () -> Unit = {
        if (changed) {
            open = Open.Discard
        } else {
            onClose()
        }
    }
    BackHandler(onBack = close)

    val saveFailed = stringResource(R.string.save_failed)
    val save: () -> Unit = save@{
        val current = draft ?: return@save
        saving = true
        viewModel.save(current, screen.scope, screen.begin) { ok ->
            saving = false
            if (ok) onSaved() else scope.launch { snackbar.showSnackbar(saveFailed) }
        }
    }

    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding().height(64.dp).padding(start = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = close) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) }
                Text(
                    stringResource(if (screen.id == null) R.string.new_event else R.string.edit_event),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                Button(onClick = save, enabled = draft?.valid == true && !saving) { Text(stringResource(R.string.save)) }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = draft
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }
        val update: (EventDraft) -> Unit = { draft = it }
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        ) {
            TitleField(current.title, focus = screen.id == null) { update(current.copy(title = it)) }
            Spacer(Modifier.height(8.dp))

            // When.
            val whenRows = if (onlyOne) 3 else 4
            Card(segmentShape(0, whenRows)) {
                RowContent(Icons.Outlined.Schedule, stringResource(R.string.all_day), onClick = { update(current.copy(allDay = !current.allDay)) }) {
                    Switch(checked = current.allDay, onCheckedChange = { update(current.copy(allDay = it)) })
                }
            }
            Card(segmentShape(1, whenRows)) {
                TimeRow(stringResource(R.string.starts), formats, current.startDate, current.startTime.takeUnless { current.allDay },
                    onDate = { open = Open.StartDate }, onTime = { open = Open.StartTime })
            }
            Card(segmentShape(2, whenRows)) {
                TimeRow(stringResource(R.string.ends), formats, current.endDate, current.endTime.takeUnless { current.allDay },
                    onDate = { open = Open.EndDate }, onTime = { open = Open.EndTime }, error = !current.valid)
            }
            if (!onlyOne) {
                Card(segmentShape(3, whenRows)) {
                    RowContent(Icons.Outlined.Repeat, repeatText(context, formats, current.repeat, current.startDate), onClick = { open = Open.Repeat })
                }
            }
            if (!current.valid) {
                Text(
                    stringResource(R.string.ends_before_start),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 32.dp, top = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))

            // Calendar.
            if (!onlyOne) {
                val chosen = calendars.orEmpty().firstOrNull { it.id == current.calendarId }
                Card(segmentShape(0, 1)) {
                    RowContent(
                        Icons.Outlined.CalendarToday,
                        chosen?.name ?: stringResource(R.string.personal),
                        summary = chosen?.let { accountLabel(context, it.accountName, it.local) } ?: stringResource(R.string.on_this_phone),
                        iconTint = chosen?.let { Color(it.color) },
                        onClick = { open = Open.Calendar },
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // Reminders.
            val reminderRows = current.reminders.size + 1
            current.reminders.forEachIndexed { index, minutes ->
                Card(segmentShape(index, reminderRows)) {
                    RowContent(Icons.Outlined.Notifications, reminderText(context, minutes)) {
                        IconButton(onClick = { update(current.copy(reminders = current.reminders - minutes)) }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.remove_reminder))
                        }
                    }
                }
            }
            Card(segmentShape(reminderRows - 1, reminderRows)) {
                RowContent(Icons.Outlined.AddAlert, stringResource(R.string.add_reminder), onClick = { open = Open.Reminder })
            }
            Spacer(Modifier.height(12.dp))

            // Place and notes.
            Card(segmentShape(0, 2)) {
                FieldRow(Icons.Outlined.Place, current.location, stringResource(R.string.add_location), singleLine = true) {
                    update(current.copy(location = it))
                }
            }
            Card(segmentShape(1, 2)) {
                FieldRow(Icons.AutoMirrored.Outlined.Notes, current.description, stringResource(R.string.add_notes), singleLine = false) {
                    update(current.copy(description = it))
                }
            }
        }

        when (open) {
            Open.StartDate -> DatePickDialog(current.startDate, onPick = { update(current.withStartDate(it)); open = null }, onDismiss = { open = null })
            Open.StartTime -> TimePickDialog(current.startTime, onPick = { update(current.withStartTime(it)); open = null }, onDismiss = { open = null })
            Open.EndDate -> DatePickDialog(current.endDate, onPick = { update(current.copy(endDate = it)); open = null }, onDismiss = { open = null })
            Open.EndTime -> TimePickDialog(current.endTime, onPick = { update(current.copy(endTime = it)); open = null }, onDismiss = { open = null })
            Open.Repeat -> {
                val choices = repeatChoices + listOfNotNull(original?.repeat?.takeIf { it is Repeat.Custom })
                ChoiceDialog(
                    title = stringResource(R.string.repeat),
                    choices = choices,
                    selected = current.repeat,
                    label = { repeatText(context, formats, it, current.startDate) },
                    onPick = { update(current.copy(repeat = it)); open = null },
                    onDismiss = { open = null },
                )
            }
            Open.Calendar -> ChoiceDialog(
                title = stringResource(R.string.calendar),
                choices = writable,
                selected = writable.firstOrNull { it.id == current.calendarId },
                label = { "${it.name} · ${accountLabel(context, it.accountName, it.local)}" },
                onPick = { update(current.copy(calendarId = it.id)); open = null },
                onDismiss = { open = null },
                leading = { ColorDot(it.color) },
                footer = {
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { open = Open.NewCalendar }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, null, Modifier.padding(horizontal = 12.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.new_calendar), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                },
            )
            Open.NewCalendar -> CalendarDialog(
                title = stringResource(R.string.new_calendar),
                initialName = "",
                initialColor = calendarColors[calendars.orEmpty().size % calendarColors.size],
                onSave = { name, color ->
                    open = null
                    viewModel.createCalendar(name, color) { id -> if (id != null) draft = draft?.copy(calendarId = id) }
                },
                onDelete = null,
                onDismiss = { open = Open.Calendar },
            )
            Open.Reminder -> ChoiceDialog(
                title = stringResource(R.string.add_reminder),
                choices = reminderChoices.filter { it !in current.reminders },
                selected = null,
                label = { reminderText(context, it) },
                onPick = { update(current.withReminder(it)); open = null },
                onDismiss = { open = null },
            )
            Open.Discard -> AlertDialog(
                onDismissRequest = { open = null },
                title = { Text(stringResource(R.string.discard_title)) },
                confirmButton = { TextButton(onClick = { open = null; onClose() }) { Text(stringResource(R.string.discard)) } },
                dismissButton = { TextButton(onClick = { open = null }) { Text(stringResource(R.string.keep_editing)) } },
            )
            null -> Unit
        }
    }
}

/** A grouped card, 16dp in from the screen edges. */
@Composable
private fun Card(shape: androidx.compose.ui.graphics.Shape, content: @Composable () -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { content() }
}

/** The inside of a card row: icon, text and an optional control, tappable when [onClick] is given. */
@Composable
private fun RowContent(
    icon: ImageVector,
    title: String,
    summary: String? = null,
    iconTint: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val base = Modifier.fillMaxWidth()
    Row(
        (if (onClick != null) base.then(Modifier.clickableRow(onClick)) else base).padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

/** Makes a whole row tappable. */
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

/** "Starts" or "Ends" with the day and (unless all day) the time as tappable pills. */
@Composable
private fun TimeRow(
    label: String,
    formats: Formats,
    date: java.time.LocalDate,
    time: java.time.LocalTime?,
    onDate: () -> Unit,
    onTime: () -> Unit,
    error: Boolean = false,
) {
    Row(Modifier.fillMaxWidth().padding(start = 56.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Pill(formats.weekdayShortDay.format(date), error, onDate)
        if (time != null) {
            Spacer(Modifier.width(8.dp))
            Pill(formats.time.format(time), error, onTime)
        }
    }
}

/** A rounded, tonal button showing a day or a time. */
@Composable
private fun Pill(text: String, error: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = CircleShape, color = if (error) scheme.errorContainer else scheme.secondaryContainer) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (error) scheme.onErrorContainer else scheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/** The big title field at the top. A new event's title has the keyboard up straight away. */
@Composable
private fun TitleField(value: String, focus: Boolean, onChange: (String) -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (focus) runCatching { requester.requestFocus() } }
    Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().focusRequester(requester),
            decorationBox = { field ->
                Box {
                    if (value.isEmpty()) {
                        Text(stringResource(R.string.add_title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    field()
                }
            },
        )
    }
}

/** A card row holding a text field, with an icon and a hint when it's empty. */
@Composable
private fun FieldRow(icon: ImageVector, value: String, hint: String, singleLine: Boolean, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        val style: TextStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            textStyle = style,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f).padding(top = 2.dp),
            decorationBox = { field ->
                Box {
                    if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    field()
                }
            },
        )
    }
}
