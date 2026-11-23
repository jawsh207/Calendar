package app.agenda.calendar.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.agenda.calendar.R
import app.agenda.calendar.data.Occurrence
import app.agenda.calendar.data.occurrencesOn
import app.agenda.calendar.ui.CalendarViewModel
import app.agenda.calendar.ui.components.EventCard
import app.agenda.calendar.ui.components.Formats
import app.agenda.calendar.ui.components.NothingPlanned
import app.agenda.calendar.ui.components.SEGMENT_GAP
import app.agenda.calendar.ui.components.segmentShape
import app.agenda.calendar.ui.rememberOccurrences
import java.time.LocalDate

/**
 * A day's events, as a sheet from the bottom: what tapping a day in the Month view shows,
 * since the grid only has room for the first few. New event adds one on that day.
 */
@Composable
fun DaySheet(
    viewModel: CalendarViewModel,
    day: LocalDate,
    formats: Formats,
    onOpen: (Occurrence) -> Unit,
    onNewEvent: () -> Unit,
    onDismiss: () -> Unit,
) {
    val today by viewModel.today.collectAsStateWithLifecycle()
    val occurrences by rememberOccurrences(viewModel, day, day)
    val list = occurrencesOn(occurrences.orEmpty(), day)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                formats.day(day, today.year),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            )
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
            ) {
                if (occurrences != null && list.isEmpty()) NothingPlanned()
                list.forEachIndexed { index, o ->
                    EventCard(o, day, segmentShape(index, list.size), formats, onClick = { onOpen(o) })
                }
            }
            Button(onClick = onNewEvent, modifier = Modifier.padding(start = 16.dp, top = 16.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.new_event))
            }
        }
    }
}
