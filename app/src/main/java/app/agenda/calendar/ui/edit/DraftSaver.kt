package app.agenda.calendar.ui.edit

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import app.agenda.calendar.data.EventDraft
import app.agenda.calendar.data.Repeat
import app.agenda.calendar.data.repeatOf
import app.agenda.calendar.data.ruleOf
import java.time.LocalDate
import java.time.LocalTime

/**
 * Keeps the editor's working copy through rotation and the app being closed in the background,
 * written out as text. The repeat is kept as its rule, which reads back as the same choice.
 */
val DraftSaver: Saver<EventDraft?, Any> = listSaver(
    save = { draft ->
        if (draft == null) emptyList() else listOf(
            draft.id?.toString().orEmpty(),
            draft.calendarId?.toString().orEmpty(),
            draft.title,
            draft.location,
            draft.description,
            draft.allDay.toString(),
            draft.startDate.toString(),
            draft.startTime.toString(),
            draft.endDate.toString(),
            draft.endTime.toString(),
            ruleOf(draft.repeat, draft.startDate).orEmpty(),
            draft.reminders.joinToString(","),
        )
    },
    restore = { stored ->
        val saved = stored.map { it.toString() }
        if (saved.size < 12) null else runCatching {
            val start = LocalDate.parse(saved[6])
            EventDraft(
                id = saved[0].toLongOrNull(),
                calendarId = saved[1].toLongOrNull(),
                title = saved[2],
                location = saved[3],
                description = saved[4],
                allDay = saved[5].toBoolean(),
                startDate = start,
                startTime = LocalTime.parse(saved[7]),
                endDate = LocalDate.parse(saved[8]),
                endTime = LocalTime.parse(saved[9]),
                repeat = saved[10].ifEmpty { null }?.let(::repeatOf) ?: Repeat.None,
                reminders = saved[11].split(',').mapNotNull { it.trim().toIntOrNull() },
            )
        }.getOrNull()
    },
)
