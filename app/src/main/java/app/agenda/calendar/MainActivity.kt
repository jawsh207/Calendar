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

/**
 * The app's only screen holder. Everything inside is drawn by [CalendarApp]. It also takes
 * what other apps ask for (open an event, show a day, add an event) and the tap on a reminder
 * notification, and passes them on to be handled once the screens are ready.
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

    /** The intent a reminder notification sends when tapped. */
    companion object {
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
