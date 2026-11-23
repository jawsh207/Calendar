package app.agenda.calendar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.agenda.calendar.R
import app.agenda.calendar.data.Occurrence
import app.agenda.calendar.data.textOn
import java.time.LocalDate

/**
 * An event in a grouped list (Agenda, Month + agenda, search, a day's sheet): a bar in the
 * event's colour, its title, and under it the time and place. Gives under the finger like the
 * other cards.
 */
@Composable
fun EventCard(
    occurrence: Occurrence,
    day: LocalDate,
    shape: Shape,
    formats: Formats,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDay: Boolean = false,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = shape,
        color = scheme.surfaceBright,
        interactionSource = interaction,
        modifier = modifier.pressBounce(interaction, pressedScale = 0.98f).fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).height(40.dp).clip(CircleShape).background(Color(occurrence.color)))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    occurrence.title.ifBlank { stringResource(R.string.no_title) },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val time = timeText(context, formats, occurrence, day)
                val line = listOfNotNull(
                    if (showDay) formats.weekdayShortDay.format(day) else null,
                    time,
                    occurrence.location.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                Text(line, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (occurrence.recurring) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.Repeat, stringResource(R.string.repeats), Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * A small filled label in the event's colour with its title, for the month grid's days and the
 * week view's all-day row. With [faded] (a day outside the month) it's drawn lighter.
 */
@Composable
fun EventChip(occurrence: Occurrence, modifier: Modifier = Modifier, faded: Boolean = false, label: String? = null) {
    val fill = Color(occurrence.color).copy(alpha = if (faded) 0.45f else 1f)
    Box(
        modifier.fillMaxWidth().heightIn(min = 16.dp).clip(RoundedCornerShape(4.dp)).background(fill).padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            label ?: occurrence.title.ifBlank { stringResource(R.string.no_title) },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = Color(textOn(occurrence.color)),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/**
 * The day heading over a day's events in the lists: the weekday and date, in the theme's
 * primary colour for today.
 */
@Composable
fun DayHeading(day: LocalDate, today: LocalDate, formats: Formats, modifier: Modifier = Modifier) {
    val label = when (day) {
        today -> stringResource(R.string.today_named, formats.day(day, today.year))
        today.plusDays(1) -> stringResource(R.string.tomorrow_named, formats.day(day, today.year))
        else -> formats.day(day, today.year)
    }
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 32.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** A line under a day heading saying nothing is on, in a card like the events'. */
@Composable
fun NothingPlanned(modifier: Modifier = Modifier) {
    Surface(shape = segmentShape(0, 1), color = MaterialTheme.colorScheme.surfaceBright, modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.nothing_planned),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}
