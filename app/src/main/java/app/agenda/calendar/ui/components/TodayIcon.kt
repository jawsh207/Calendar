package app.agenda.calendar.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/**
 * The Today button's icon, as Google Calendar draws it: the outline of a calendar page with
 * today's date written in it, so the button shows what it goes to. Drawn rather than taken
 * from the icon set, which has no icon with a changing number. Follows the button's colour.
 */
@Composable
fun TodayIcon(today: LocalDate, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Box(
        modifier.size(24.dp).drawBehind {
            val stroke = 1.8.dp.toPx()
            val top = 4.dp.toPx()
            val inset = 3.dp.toPx()
            // The page.
            drawRoundRect(
                color = color,
                topLeft = Offset(inset, top),
                size = Size(size.width - inset * 2, size.height - top - 2.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx()),
                style = Stroke(stroke),
            )
            // The two rings at the top.
            val ringTop = 2.dp.toPx()
            val ringBottom = top + 2.5.dp.toPx()
            listOf(size.width * 0.33f, size.width * 0.67f).forEach { x ->
                drawLine(color, Offset(x, ringTop), Offset(x, ringBottom), stroke, cap = StrokeCap.Round)
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            today.dayOfMonth.toString(),
            color = color,
            fontSize = 10.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
