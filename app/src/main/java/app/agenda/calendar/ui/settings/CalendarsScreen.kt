package app.agenda.calendar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.agenda.calendar.R
import app.agenda.calendar.data.CalendarInfo
import app.agenda.calendar.data.calendarColors
import app.agenda.calendar.ui.CalendarViewModel
import app.agenda.calendar.ui.components.BackTopBar
import app.agenda.calendar.ui.components.ColorPalette
import app.agenda.calendar.ui.components.EmptyState
import app.agenda.calendar.ui.components.ListRow
import app.agenda.calendar.ui.components.SEGMENT_GAP
import app.agenda.calendar.ui.components.SectionHeader
import app.agenda.calendar.ui.components.segmentShape
import app.agenda.calendar.ui.event.accountLabel

/**
 * Every calendar on the phone, grouped by account, each with a switch to show or hide it (the
 * same switch other calendar apps use). Calendars on this phone can also be made (+), renamed,
 * recoloured and deleted; synced ones are managed by their account's app.
 */
@Composable
fun CalendarsScreen(viewModel: CalendarViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CalendarInfo?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BackTopBar(stringResource(R.string.calendars), onBack, actions = {
                IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.new_calendar)) }
            })
        },
    ) { padding ->
        val list = calendars
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            list.isEmpty() -> EmptyState(
                icon = Icons.Outlined.EventBusy,
                title = stringResource(R.string.no_calendars_title),
                body = stringResource(R.string.no_calendars_body),
                modifier = Modifier.padding(padding).padding(top = 48.dp),
            )
            else -> {
                // This phone's calendars first, then each account's.
                val groups = list.groupBy { accountLabel(context, it.accountName, it.local) to it.local }
                    .toList().sortedWith(compareBy({ !it.first.second }, { it.first.first.lowercase() }))
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
                ) {
                    groups.forEach { (account, items) ->
                        item(key = "account:${account.first}:${account.second}") { SectionHeader(account.first) }
                        itemsIndexed(items, key = { _, c -> c.id }) { index, calendar ->
                            ListRow(
                                icon = if (calendar.visible) Icons.Rounded.Circle else Icons.Outlined.Circle,
                                iconTint = Color(calendar.color),
                                title = calendar.name,
                                summary = when {
                                    calendar.local -> stringResource(R.string.tap_to_edit)
                                    !calendar.writable -> stringResource(R.string.read_only)
                                    else -> null
                                },
                                shape = segmentShape(index, items.size),
                                onClick = { if (calendar.local) editing = calendar else viewModel.setVisible(calendar, !calendar.visible) },
                                trailing = { Switch(checked = calendar.visible, onCheckedChange = { viewModel.setVisible(calendar, it) }) },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        CalendarDialog(
            title = stringResource(R.string.new_calendar),
            initialName = "",
            initialColor = calendarColors[(calendars.orEmpty().size) % calendarColors.size],
            onSave = { name, color -> viewModel.createCalendar(name, color); creating = false },
            onDelete = null,
            onDismiss = { creating = false },
        )
    }
    editing?.let { calendar ->
        CalendarDialog(
            title = stringResource(R.string.edit_calendar),
            initialName = calendar.name,
            initialColor = calendar.color,
            onSave = { name, color -> viewModel.editCalendar(calendar, name, color); editing = null },
            onDelete = { viewModel.deleteCalendar(calendar); editing = null },
            onDismiss = { editing = null },
        )
    }
}

/** Naming and colouring a calendar on this phone; with [onDelete], deleting it too (asked twice). */
@Composable
private fun CalendarDialog(
    title: String,
    initialName: String,
    initialColor: Int,
    onSave: (String, Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableIntStateOf(initialColor) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_calendar_title, initialName)) },
            text = { Text(stringResource(R.string.delete_calendar_body)) },
            confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.calendar_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                ColorPalette(calendarColors, color, onPick = { color = it }, modifier = Modifier.padding(top = 20.dp))
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(top = 12.dp)) {
                        Text(stringResource(R.string.delete_calendar))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), color) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
