package app.agenda.calendar.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings as SystemSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.agenda.calendar.data.EditScope
import app.agenda.calendar.data.Occurrence
import app.agenda.calendar.ui.edit.EditEventScreen
import app.agenda.calendar.ui.event.EventScreen
import app.agenda.calendar.ui.home.HomeScreen
import app.agenda.calendar.ui.search.SearchScreen
import app.agenda.calendar.ui.settings.CalendarsScreen
import app.agenda.calendar.ui.settings.SettingsScreen
import app.agenda.calendar.ui.setup.AccessScreen

/** What's asked for on first run: the two calendar permissions, and notifications for reminders. */
private val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR, Manifest.permission.POST_NOTIFICATIONS)

/**
 * The whole app: asks for calendar access, then shows the screen on top of the back stack.
 * Screens slide and spring in from the side going forward and back out going back, as in Notes.
 * Requests from other apps and from reminder notifications are handled once access is given.
 */
@Composable
fun CalendarApp(viewModel: CalendarViewModel) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
    val access by viewModel.access.collectAsStateWithLifecycle()
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        viewModel.onAccessChanged()
    }
    LaunchedEffect(Unit) { if (!access) launcher.launch(PERMISSIONS) }
    if (!access) {
        // After a second "Don't allow", Android stops showing the prompt; only Settings can grant it then.
        val blocked = asked && activity?.shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR) == false
        AccessScreen(blocked = blocked, onAllow = {
            if (blocked) {
                context.startActivity(
                    Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } else {
                launcher.launch(PERMISSIONS)
            }
        })
        return
    }
    Screens(viewModel)
}

/** The back stack of screens. */
@Composable
private fun Screens(viewModel: CalendarViewModel) {
    val stack = rememberBackStack()
    val open: (Screen) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.size > 1 && stack.last() !is Screen.Edit, onBack = back)
    val openOccurrence: (Occurrence) -> Unit = { open(Screen.Event(it.eventId, it.begin, it.end)) }

    val request by viewModel.request.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        val asked = request ?: return@LaunchedEffect
        viewModel.requestHandled()
        when (asked) {
            is Request.ShowEvent -> open(Screen.Event(asked.id, asked.begin, asked.end))
            is Request.ShowDate -> {
                while (stack.size > 1) stack.removeAt(stack.lastIndex)
                viewModel.select(asked.date)
            }
            is Request.NewEvent -> {
                viewModel.offerDraft(asked.draft)
                open(Screen.Edit())
            }
            is Request.EditEvent -> open(Screen.Edit(id = asked.id, scope = EditScope.All))
        }
    }

    val motion = MaterialTheme.motionScheme
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = stack.last() to stack.size,
            contentKey = { (screen, depth) -> "$depth:${screen::class.simpleName}" },
            transitionSpec = {
                val forward = targetState.second >= initialState.second
                val slide = motion.defaultSpatialSpec<IntOffset>()
                val transform: ContentTransform = if (forward) {
                    (slideInHorizontally(slide) { it / 3 } + fadeIn(motion.defaultEffectsSpec()) +
                        scaleIn(motion.defaultSpatialSpec(), initialScale = 0.96f)) togetherWith
                        (slideOutHorizontally(slide) { -it / 8 } + fadeOut(motion.fastEffectsSpec()))
                } else {
                    (slideInHorizontally(slide) { -it / 8 } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                        (slideOutHorizontally(slide) { it / 3 } + fadeOut(motion.fastEffectsSpec()) +
                            scaleOut(motion.defaultSpatialSpec(), targetScale = 0.96f))
                }
                // The screen being opened draws over the one it covers; going back, the reverse.
                transform.apply { targetContentZIndex = if (forward) 1f else 0f }
            },
            label = "screens",
        ) { (screen, _) ->
            when (screen) {
                Screen.Home -> HomeScreen(
                    viewModel,
                    onSearch = { open(Screen.Search) },
                    onSettings = { open(Screen.Settings) },
                    onOpen = openOccurrence,
                    onNewEvent = { day, hour -> open(Screen.Edit(day = day, hour = hour)) },
                )
                Screen.Search -> SearchScreen(viewModel, onOpen = openOccurrence, onBack = back)
                Screen.Settings -> SettingsScreen(viewModel, onBack = back, onCalendars = { open(Screen.Calendars) })
                Screen.Calendars -> CalendarsScreen(viewModel, onBack = back)
                is Screen.Event -> EventScreen(
                    viewModel, screen.id, screen.begin, screen.end,
                    onBack = back,
                    onEdit = { scope -> open(Screen.Edit(id = screen.id, begin = screen.begin, scope = scope)) },
                )
                is Screen.Edit -> EditEventScreen(viewModel, screen, onClose = back, onSaved = {
                    back()
                    // One repeat changed on its own becomes its own event; the page behind showed the series.
                    if (screen.scope == EditScope.One && stack.last() is Screen.Event) back()
                })
            }
        }
    }
}
