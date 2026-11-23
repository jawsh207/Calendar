package app.agenda.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import app.agenda.calendar.data.EditScope
import java.time.LocalDate

/** The app's screens. Home holds the four views; the others cover them. */
sealed interface Screen {
    /** The Month, Week, Agenda and Month + agenda views. */
    data object Home : Screen

    /** Finding events by their words. */
    data object Search : Screen

    /** One event's details. [begin] and [end] are the repeat that was tapped, when known. */
    data class Event(val id: Long, val begin: Long?, val end: Long?) : Screen

    /**
     * The editor. With no [id] it makes a new event on [day] (at [hour] when an hour was tapped);
     * otherwise it edits event [id], for the repeats [scope] says.
     */
    data class Edit(
        val id: Long? = null,
        val begin: Long? = null,
        val scope: EditScope = EditScope.All,
        val day: LocalDate? = null,
        val hour: Int? = null,
    ) : Screen

    /** Showing, hiding and making calendars. */
    data object Calendars : Screen

    /** Settings. */
    data object Settings : Screen
}

private fun Screen.encode(): String = when (this) {
    Screen.Home -> "home"
    Screen.Search -> "search"
    Screen.Settings -> "settings"
    Screen.Calendars -> "calendars"
    is Screen.Event -> "event:$id:${begin ?: ""}:${end ?: ""}"
    is Screen.Edit -> "edit:${id ?: ""}:${begin ?: ""}:${scope.name}:${day ?: ""}:${hour ?: ""}"
}

private fun decode(text: String): Screen? {
    val parts = text.split(':')
    return when (parts.first()) {
        "home" -> Screen.Home
        "search" -> Screen.Search
        "settings" -> Screen.Settings
        "calendars" -> Screen.Calendars
        "event" -> parts.getOrNull(1)?.toLongOrNull()?.let { Screen.Event(it, parts.getOrNull(2)?.toLongOrNull(), parts.getOrNull(3)?.toLongOrNull()) }
        "edit" -> Screen.Edit(
            id = parts.getOrNull(1)?.toLongOrNull(),
            begin = parts.getOrNull(2)?.toLongOrNull(),
            scope = EditScope.entries.firstOrNull { it.name == parts.getOrNull(3) } ?: EditScope.All,
            day = parts.getOrNull(4)?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            hour = parts.getOrNull(5)?.toIntOrNull(),
        )
        else -> null
    }
}

/** The back stack, kept across rotation and process death. Always starts with Home. */
@Composable
fun rememberBackStack(): SnapshotStateList<Screen> = rememberSaveable(
    saver = listSaver(
        save = { stack -> stack.map { it.encode() } },
        restore = { saved -> saved.mapNotNull(::decode).ifEmpty { listOf(Screen.Home) }.toMutableStateList() },
    ),
) { listOf<Screen>(Screen.Home).toMutableStateList() }
