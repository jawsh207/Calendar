package app.agenda.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.agenda.calendar.data.Occurrence
import java.time.LocalDate

/**
 * The occurrences on the days [from] to [until], kept up to date with the calendar storage.
 * Null until first read. When the days change, the old list stays on screen until the new one
 * is read, so a list that grows doesn't blink empty.
 */
@Composable
fun rememberOccurrences(viewModel: CalendarViewModel, from: LocalDate, until: LocalDate): State<List<Occurrence>?> {
    val state = remember { mutableStateOf<List<Occurrence>?>(null) }
    LaunchedEffect(from, until) {
        viewModel.occurrences(from, until).collect { state.value = it }
    }
    return state
}
