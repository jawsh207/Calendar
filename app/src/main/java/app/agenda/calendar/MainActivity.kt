package app.agenda.calendar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import app.agenda.calendar.intents.parseRequest
import app.agenda.calendar.ui.CalendarApp
import app.agenda.calendar.ui.CalendarViewModel
import app.agenda.calendar.ui.Request
import app.agenda.calendar.ui.theme.CalendarTheme
import java.time.LocalDate

/**
 * The app's only screen holder. Everything inside is drawn by [CalendarApp]. It also takes
 * what other apps and the widgets ask for (open an event, show a day, add an event), the
 * launcher shortcuts and the tap on a reminder notification, and passes them on to be handled
 * once the screens are ready.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: CalendarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a fresh start: after rotation the request was already handled.
        if (savedInstanceState == null) handle(intent)
        setContent {
            CalendarTheme {
                // A Surface sets the text and icon colour for the theme; screens drawn outside a
                // Scaffold would otherwise get black text.
                Surface(color = MaterialTheme.colorScheme.background) {
                    CalendarApp(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            ACTION_SEARCH -> return viewModel.request(Request.Search)
            // Read when tapped, not when the shortcut was made, so it's always the day it is now.
            ACTION_TODAY -> return viewModel.request(Request.ShowDate(LocalDate.now()))
        }
        if (intent?.action == ACTION_OPEN_EVENT) {
            val id = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
            if (id > 0) {
                viewModel.request(
                    Request.ShowEvent(id, intent.getLongExtra(EXTRA_BEGIN, -1L).takeIf { it > 0 }, intent.getLongExtra(EXTRA_END, -1L).takeIf { it > 0 }),
                )
            }
            return
        }
        parseRequest(intent)?.let(viewModel::request)
    }

    /** The app's own intents: a reminder notification's tap and the launcher shortcuts. */
    companion object {
        /** The Search launcher shortcut. Named in res/xml/shortcuts.xml too. */
        const val ACTION_SEARCH = "app.agenda.calendar.action.SEARCH"

        /** The Today launcher shortcut: the calendar on today. Named in res/xml/shortcuts.xml too. */
        const val ACTION_TODAY = "app.agenda.calendar.action.TODAY"

        /** Opens the event in [EXTRA_EVENT_ID], at the repeat from [EXTRA_BEGIN] to [EXTRA_END]. */
        const val ACTION_OPEN_EVENT = "app.agenda.calendar.action.OPEN_EVENT"

        /** The event to open. */
        const val EXTRA_EVENT_ID = "event_id"

        /** When the repeat to show starts. */
        const val EXTRA_BEGIN = "begin"

        /** When the repeat to show ends. */
        const val EXTRA_END = "end"
    }
}
