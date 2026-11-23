package app.agenda.calendar.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.agenda.calendar.R
import app.agenda.calendar.ui.components.ListRow
import app.agenda.calendar.ui.components.SEGMENT_GAP
import app.agenda.calendar.ui.components.segmentShape
import kotlinx.coroutines.delay

/**
 * The first screen, until calendar access is given: a welcome in the same style as Notes',
 * explaining in three short points that events live in Android's shared calendar storage,
 * with the button that asks for access kept at the bottom within reach of the thumb. When
 * Android won't ask again ([blocked]), the button opens the app's system settings instead.
 */
@Composable
fun AccessScreen(blocked: Boolean, onAllow: () -> Unit) {
    Scaffold(
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Appear(order = 4) {
                    Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).heightIn(min = 56.dp)) {
                        Icon(Icons.Outlined.EventAvailable, null, Modifier.padding(end = 8.dp))
                        Text(
                            stringResource(if (blocked) R.string.open_app_settings else R.string.allow_access),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                if (blocked) {
                    Text(
                        stringResource(R.string.access_blocked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            Appear(order = 0) { Badge() }
            Spacer(Modifier.height(32.dp))
            Appear(order = 1) {
                Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(12.dp))
            Appear(order = 2) {
                Text(
                    stringResource(R.string.setup_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 480.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
            Appear(order = 3) {
                Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP)) {
                    val points = listOf(
                        Triple(Icons.Outlined.Share, R.string.setup_point_shared, R.string.setup_point_shared_body),
                        Triple(Icons.Outlined.CalendarMonth, R.string.setup_point_views, R.string.setup_point_views_body),
                        Triple(Icons.Outlined.CloudOff, R.string.setup_point_private, R.string.setup_point_private_body),
                    )
                    points.forEachIndexed { index, (icon, title, body) ->
                        ListRow(icon = icon, title = stringResource(title), summary = stringResource(body), shape = segmentShape(index, points.size))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The big icon at the top, on a slowly turning scalloped shape. */
@Composable
private fun Badge() {
    val turn by rememberInfiniteTransition(label = "badge").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart),
        label = "turn",
    )
    val shape = MaterialShapes.Cookie9Sided.toShape()
    Box(Modifier.size(144.dp), contentAlignment = Alignment.Center) {
        // Only the shape turns; the icon stays upright.
        Box(Modifier.size(144.dp).graphicsLayer { rotationZ = turn }.clip(shape).background(MaterialTheme.colorScheme.primaryContainer))
        Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** Springs [content] in shortly after the screen opens, later for a higher [order], so the screen builds up top to bottom. */
@Composable
private fun Appear(order: Int, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        delay(order * 80L)
        state.targetState = true
    }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(300)) +
            slideInVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) { it / 3 } +
            scaleIn(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow), initialScale = 0.92f),
    ) {
        content()
    }
}
