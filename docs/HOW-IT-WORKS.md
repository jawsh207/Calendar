# How Calendar works

A plain-language guide to the app: what each part does and where it lives in the code. No
programming knowledge needed. File paths are inside `app/src/main/java/app/agenda/calendar/`.

## The big picture

Calendar doesn't keep its own copy of anything. Android has one shared place for calendars,
the **calendar storage** (its "calendar provider"), which every calendar app, widget and
account on the phone reads and writes. The app is a window onto it: the views are what's in
it, saving an event writes it there, and deleting one removes it there. So an event made here
appears straight away in any other calendar app, and the reverse.

Each calendar in the storage belongs to an **account**. Calendars from Google, a CalDAV app
such as DAVx⁵, or Exchange belong to those accounts, and their apps keep them in sync with the
internet; the app just reads and writes their events like any other. Calendars made in the app
go under Android's own "on this phone" account type, which never syncs anywhere and which
Android keeps even if the app is removed. If there's no calendar the app can write to when
access is first given, it makes one called "Personal".

Which calendars are shown is Android's shared switch too, so hiding a calendar here hides it
in other calendar apps that respect the switch, and the other way round.

The app keeps only a small settings file for itself: the view last used, the day weeks start
on, how long new events last and their default reminder, whether to show reminder
notifications, and the calendar the last new event went into.

The storage notices every change, whoever makes it, and tells apps that are watching. While
the app is open it watches, and every view reads again whenever anything changes (an edit
here, another app, an account syncing).

## Times, days and repeats

The storage keeps times as milliseconds. Two things about that shape the app:

- **All-day events** are kept as midnights in UTC whatever the phone's time zone, so that a
  birthday stays on the same day when you travel. The app reads and writes their days in UTC;
  everything else is read in the phone's time zone.
- **Repeating events** are kept once, with a rule (every week on Monday, every month on the
  23rd…) and a length instead of an end time. The storage works out each time it happens.
  The editor offers daily, weekly, monthly and yearly; a rule made in another app that isn't
  one of those (every other week, until a date) is shown as a custom repeat and kept as it is
  unless you pick something else.
- **Changing one repeat** ("This event") makes a separate event tied to the series, which is
  how Android records a single changed time. **Deleting one repeat** marks that time
  cancelled, which is how Android skips it.

## Screens

| Screen | What it's for | File |
| --- | --- | --- |
| First run | A welcome with three short points, and the button that asks for calendar access (or opens the app's system settings once Android won't ask again) | `ui/setup/AccessScreen.kt` |
| Home | The search bar with Today and Settings, the month heading with the date picker and arrows, the + button and the bottom bar switching views | `ui/home/HomeScreen.kt` |
| Month view | Six weeks of days with their events written in; tap a day for its sheet, long-press to add an event | `ui/month/MonthViews.kt`, `ui/month/MonthGrid.kt` |
| A day's sheet | All of one day's events, and New event, from the Month view | `ui/home/DaySheet.kt` |
| Week view | Seven days hour by hour, all-day row, the line at the time now; tap an hour to add an event | `ui/week/WeekView.kt` |
| Agenda view | What's on, day by day, from the picked day; Show earlier and Show later | `ui/agenda/AgendaList.kt` |
| Planner view | A small month with dots, and the list from the picked day | `ui/month/MonthViews.kt`, `ui/month/MonthGrid.kt` |
| Event | One event's details; Edit and Delete, asking "this event or all" for repeating ones | `ui/event/EventScreen.kt` |
| Editor | Making or changing an event: title, all day, start and end, repeat, calendar, reminders, place, notes | `ui/edit/EditEventScreen.kt` |
| Search | Finding events by words in their title, place or notes | `ui/search/SearchScreen.kt` |
| Settings | Calendars, week start, new event length and reminder, reminder notifications | `ui/settings/SettingsScreen.kt` |
| Calendars | Every calendar by account with its show/hide switch; making, renaming, recolouring and deleting calendars on this phone | `ui/settings/CalendarsScreen.kt` |

## Features

- **Views.** Month, Week, Agenda and Planner, one per bottom tab. They all follow one
  picked day, so switching views keeps your place; Today and the date picker move it.
  Swiping to another month or week picks a day in it (today if it's there, otherwise the
  first).
- **Week start.** Follows the phone's region unless set to Saturday, Sunday or Monday in
  Settings. It changes the month grids and the weeks.
- **Overlapping events** in the Week view sit side by side, each taking the leftmost free
  column, and short ones are drawn tall enough to tap. An event past midnight shows in both
  days.
- **New events** start at the hour tapped in the Week view; otherwise at the next whole hour
  today, or 9 in the morning on another day. They last as long as set in Settings, get its
  default reminder, and go into the calendar the last new event went into.
- **Leaving the editor** with changes asks whether to discard them.
- **Reminders.** The storage sets the alarms for every event's reminders and tells calendar
  apps when one is due, even when they aren't running. The app then shows a notification for
  each reminder due and marks it shown; tapping it opens the event. Reminders for events
  already over (the phone was off) are marked without ringing.
- **Requests from other apps.** Opening an event, opening the calendar at a time, adding an
  event (filled in by the other app, saved only when you tap Save) and editing an event, with
  the same requests Google Calendar answers.

## Jobs and where they're done

| Job | File |
| --- | --- |
| Reading calendars and events, expanding repeats for a range of days, search, adding, changing and deleting events (and single repeats), reminders, showing and hiding calendars, making, editing and deleting calendars on this phone | `data/CalendarRepository.kt` |
| What a calendar, an occurrence and an event are; reading the storage's times into days and minutes | `data/Models.kt` |
| Month grids, week pages, the order of a day's events, agenda days, laying out overlapping events, readable text on an event's colour | `data/Layout.kt` |
| Repeat choices and rules, reading and writing event lengths | `data/Recurrence.kt` |
| The editor's working copy: new event times, moving the start, all-day times, starting from an existing event, reminder choices, calendar colours | `data/Drafts.kt` |
| Keeping the editor's working copy through rotation | `ui/edit/DraftSaver.kt` |
| Settings | `data/Settings.kt` |
| Watching the storage, the picked day and today, the settings, saving and deleting | `ui/CalendarViewModel.kt` |
| Reading a range of days' events for a view and keeping it up to date | `ui/Occurrences.kt` |
| Asking for access, the back stack and screen animations, handling requests | `ui/CalendarApp.kt`, `ui/Navigation.kt` |
| Requests from other apps and reminder taps | `MainActivity.kt`, `intents/ExternalRequest.kt` |
| Reminder notifications | `reminders/ReminderReceiver.kt` |
| Dates and times in the phone's language and clock style, event times, reminder and repeat wording | `ui/components/Format.kt` |
| Event cards, coloured labels, day headings | `ui/components/Events.kt` |
| Date, time and choice dialogs, colour swatches | `ui/components/Pickers.kt` |
| Hearing about changes in the storage | `util/ContentChanges.kt` |
| The look: theme, font, search bar, top and bottom bars, grouped cards, empty states, press animation | `ui/theme/Theme.kt`, `ui/components/SearchPill.kt`, `ui/components/TopBars.kt`, `ui/components/BottomBar.kt`, `ui/components/Rows.kt`, `ui/components/Lists.kt`, `ui/components/Motion.kt` |

## Permissions

- **Read and write calendars:** everything lives in the calendar storage, so the app can do
  nothing without them. It asks on first run.
- **Notifications:** for reminders. Asked for on first run with calendar access.

The app has no internet access of its own; synced calendars reach the internet through their
accounts' apps.
