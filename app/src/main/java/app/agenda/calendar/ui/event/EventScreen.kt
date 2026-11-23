package app.agenda.calendar.ui.event

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.agenda.calendar.R
import app.agenda.calendar.data.EditScope
import app.agenda.calendar.data.EventDetails
import app.agenda.calendar.data.occurrenceOf
import app.agenda.calendar.data.repeatOf
import app.agenda.calendar.data.textOn
import app.agenda.calendar.ui.CalendarViewModel
import app.agenda.calendar.ui.components.BackTopBar
import app.agenda.calendar.ui.components.EmptyState
import app.agenda.calendar.ui.components.Formats
import app.agenda.calendar.ui.components.ListRow
import app.agenda.calendar.ui.components.SEGMENT_GAP
import app.agenda.calendar.ui.components.reminderText
import app.agenda.calendar.ui.components.rememberFormats
import app.agenda.calendar.ui.components.repeatText
import app.agenda.calendar.ui.components.segmentShape
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.map

/** An event as read: [event] is null once it has been deleted. */
private class Loaded(val event: EventDetails?)

/** One row of the event's details. */
private class Detail(val icon: ImageVector, val title: String, val summary: String? = null, val onClick: (() -> Unit)? = null, val tint: Color? = null)

/**
 * One event's page: its title in its colour, when it is (the repeat that was tapped, when
 * known), how it repeats, where, its reminders, notes and calendar. Edit and Delete are shown
 * when its calendar can be written to; for a repeating event both ask whether it's this one
 * time or all of them. Tapping the place opens it in a maps app.
 */
@Composable
fun EventScreen(
    viewModel: CalendarViewModel,
    id: Long,
    begin: Long?,
    end: Long?,
    onBack: () -> Unit,
    onEdit: (EditScope) -> Unit,
) {
    val context = LocalContext.current
    val formats = rememberFormats()
    val loaded by remember(id) { viewModel.event(id).map { Loaded(it) } }.collectAsStateWithLifecycle(null)
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val event = loaded?.event
    val calendar = calendars?.firstOrNull { it.id == event?.calendarId }
    val writable = calendar?.writable == true
    var asking by remember { mutableStateOf<Ask?>(null) }

    Scaffold(
        topBar = {
            BackTopBar("", onBack, actions = {
                if (event != null && writable) {
                    IconButton(onClick = { if (event.recurring) asking = Ask.Edit else onEdit(EditScope.All) }) {
                        Icon(Icons.Outlined.Edit, stringResource(R.string.edit))
                    }
                    IconButton(onClick = { asking = if (event.recurring) Ask.DeleteRepeating else Ask.Delete }) {
                        Icon(Icons.Outlined.Delete, stringResource(R.string.delete))
                    }
                }
            })
        },
    ) { padding ->
        when {
            loaded == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            event == null -> EmptyState(
                icon = Icons.Outlined.EventBusy,
                title = stringResource(R.string.event_gone_title),
                body = stringResource(R.string.event_gone_body),
                modifier = Modifier.padding(padding).padding(top = 48.dp),
            )
            else -> {
                val start = begin ?: event.start
                val finish = end ?: (start + event.lengthMillis)
                val details = buildList {
                    add(Detail(Icons.Outlined.Schedule, whenText(context, formats, event, start, finish, today), whenSummary(context, formats, event, start, finish)))
                    if (event.recurring) {
                        val from = Instant.ofEpochMilli(event.start).atZone(if (event.allDay) java.time.ZoneOffset.UTC else ZoneId.systemDefault()).toLocalDate()
                        add(Detail(Icons.Outlined.Repeat, repeatText(context, formats, repeatOf(event.rrule), from)))
                    }
                    if (event.location.isNotBlank()) {
                        add(Detail(Icons.Outlined.Place, event.location, stringResource(R.string.open_in_maps), onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(event.location)))
                            try { context.startActivity(intent) } catch (e: ActivityNotFoundException) { }
                        }))
                    }
                    if (event.reminders.isNotEmpty()) {
                        add(Detail(Icons.Outlined.Notifications, event.reminders.sorted().joinToString("\n") { reminderText(context, it) }))
                    }
                    if (calendar != null) {
                        add(Detail(Icons.Outlined.CalendarToday, calendar.name, accountLabel(context, calendar.accountName, calendar.local), tint = Color(calendar.color)))
                    }
                    event.organizer?.takeIf { it.isNotBlank() && it != calendar?.accountName && calendar?.local != true }?.let {
                        add(Detail(Icons.Outlined.Person, it, stringResource(R.string.organizer)))
                    }
                }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
                ) {
                    item { Header(event) }
                    itemsIndexed(details) { index, d ->
                        ListRow(
                            icon = d.icon, title = d.title, summary = d.summary, shape = segmentShape(index, details.size),
                            onClick = d.onClick, iconTint = d.tint, modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    if (event.description.isNotBlank()) {
                        item { Spacer(Modifier.padding(top = 12.dp)) }
                        item {
                            androidx.compose.material3.Surface(
                                shape = segmentShape(0, 1),
                                color = MaterialTheme.colorScheme.surfaceBright,
                                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                            ) {
                                Row(Modifier.padding(16.dp)) {
                                    Icon(Icons.AutoMirrored.Outlined.Notes, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(16.dp))
                                    SelectionContainer { Text(event.description.trim(), style = MaterialTheme.typography.bodyLarge) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val current = event
    when (asking) {
        Ask.Edit -> ScopeDialog(
            title = stringResource(R.string.edit_repeating_title),
            onPick = { scope -> asking = null; onEdit(scope) },
            onDismiss = { asking = null },
        )
        Ask.DeleteRepeating -> ScopeDialog(
            title = stringResource(R.string.delete_repeating_title),
            onPick = { scope ->
                asking = null
                if (current != null) viewModel.delete(current.id, scope, begin) { onBack() }
            },
            onDismiss = { asking = null },
        )
        Ask.Delete -> AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(stringResource(R.string.delete_event_title)) },
            confirmButton = {
                TextButton(onClick = {
                    asking = null
                    if (current != null) viewModel.delete(current.id, EditScope.All, begin) { onBack() }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { asking = null }) { Text(stringResource(R.string.cancel)) } },
        )
        null -> Unit
    }
}

/** Which question is being asked. */
private enum class Ask { Edit, Delete, DeleteRepeating }

/** "This event" or "All events", for a change to a repeating event. */
@Composable
private fun ScopeDialog(title: String, onPick: (EditScope) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Repeat, null) },
        title = { Text(title) },
        text = { Text(stringResource(R.string.repeating_body)) },
        confirmButton = { TextButton(onClick = { onPick(EditScope.All) }) { Text(stringResource(R.string.all_events)) } },
        dismissButton = { TextButton(onClick = { onPick(EditScope.One) }) { Text(stringResource(R.string.this_event)) } },
    )
}

/** The event's title beside a scalloped badge in its colour. */
@Composable
private fun Header(event: EventDetails) {
    val shape = MaterialShapes.Cookie9Sided.toShape()
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(shape).background(Color(event.color)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Event, null, tint = Color(textOn(event.color)))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            SelectionContainer {
                Text(event.title.ifBlank { stringResource(R.string.no_title) }, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

/** The account a calendar belongs to, as shown under its name. */
fun accountLabel(context: android.content.Context, accountName: String, local: Boolean): String =
    if (local) context.getString(R.string.on_this_phone) else accountName

/** The main line about when: the day (or days). */
private fun whenText(context: android.content.Context, formats: Formats, event: EventDetails, start: Long, end: Long, today: LocalDate): String {
    val o = occurrenceOf(event.id, event.calendarId, event.title, "", 0, event.allDay, start, end, false, ZoneId.systemDefault())
    return if (o.startDate == o.endDate) formats.day(o.startDate, today.year)
    else context.getString(R.string.time_range, formats.shortDay.format(o.startDate), formats.shortDay.format(o.endDate))
}

/** The line under it: the times, or "All day". */
private fun whenSummary(context: android.content.Context, formats: Formats, event: EventDetails, start: Long, end: Long): String {
    val o = occurrenceOf(event.id, event.calendarId, event.title, "", 0, event.allDay, start, end, false, ZoneId.systemDefault())
    return when {
        o.allDay -> context.getString(R.string.all_day)
        o.startDate == o.endDate -> context.getString(R.string.time_range, formats.minute(o.startMinute), formats.minute(o.endMinute))
        else -> context.getString(
            R.string.time_range,
            formats.weekdayShortDay.format(o.startDate) + " " + formats.minute(o.startMinute),
            formats.weekdayShortDay.format(o.endDate) + " " + formats.minute(o.endMinute),
        )
    }
}
