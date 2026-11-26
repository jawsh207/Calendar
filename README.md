# Calendar

A calendar app for Android 16 and later. It keeps nothing to itself: events are read from and
written to Android's shared calendar storage (the calendar provider), the same place every
calendar app and widget on the phone uses.

- **One calendar for every app.** Events made here show up in Google Calendar, Etar, widgets
  and anything else that reads the phone's calendars, and theirs show up here. Calendars synced
  by your accounts (Google, DAVx⁵, Exchange…) appear and stay in sync through their own apps.
- **Four views.** Month, Week, Agenda, and Planner (a month on top with the picked day's
  list underneath). The view you used last opens next time.
- **No account needed.** Calendars made in the app live on the phone, under "On this phone",
  and stay there if the app is removed.
- **Modern look.** Jetpack Compose and Material 3 Expressive: wallpaper colours, Google Sans
  Flex, grouped cards, spring animations. It shares its look with
  [Notes](https://github.com/jawsh207/Notes), [Contacts](https://github.com/jawsh207/Contacts)
  and [Dialtone](https://github.com/jawsh207/dialtone): the search bar at the top with Settings
  at its end, and the bottom bar for switching views.

## Using it

On first launch the app asks for calendar access (read and write) and to show notifications
for reminders. If there's no calendar it can write to, it makes a "Personal" calendar on the
phone.

- **Top of the screen:** the search bar; at its end, **Today** (a calendar page showing
  today's date, as in Google Calendar) and **Settings**. Under it the
  month being looked at: tap it to jump to any date, or use the arrows to turn the page.
- **Month:** six weeks of days with their events written in. Swipe sideways for other months.
  Tap a day to see all its events in a sheet; long-press a day to add an event on it.
- **Week:** seven days hour by hour, overlapping events side by side, all-day events along the
  top, and a line at the time now. Swipe for other weeks; tap an empty hour to add an event
  then.
- **Agenda:** what's on, day by day, from the picked day on. Today is always listed. It reads
  further ahead as you scroll; **Show earlier** reaches back a month at a time.
- **Planner:** a small month (dots under days with events) and the list from the picked
  day for the next 30 days.
- **Events:** tap one to see when, how it repeats, where (tap to open in a maps app),
  reminders, notes and calendar. Edit and Delete are there when its calendar can be written
  to. For a repeating event both ask whether it's this one time or all of them.
- **New event (+):** title, all day or times (moving the start takes the end along), repeat
  (daily, weekly, monthly, yearly), calendar, reminders, place and notes. The calendar
  choice lists every calendar you can add events to, with **New calendar** to make one on
  the phone there and then. Repeat rules made in
  other apps that don't fit those choices are kept as they are.
- **Search:** finds events by words in their title, place or notes, from a year ago to two
  years ahead.
- **Calendars** (in Settings): every calendar on the phone grouped by account, each with a
  switch to show or hide it. This switch is Android's shared one, so other calendar apps follow
  it too. Calendars on this phone can be made, renamed, recoloured and deleted here; synced ones
  are managed by their account's app.
- **Settings:** the day weeks start on, how long new events last, their default reminder, and
  whether the app shows reminder notifications.

### Widgets and shortcuts

Two home screen widgets, both in the wallpaper's colours and resizable:

- **Agenda:** today's date with a **+** for a new event, then the next 30 days that have
  something on, grouped by day as in the Agenda view. Today is always listed. Tap an event to
  open it, or a day's heading or the date to open the app on that day.
- **Month:** the month on a rounded grid like the Month view, today circled and a dot for each
  event on a day (up to three). The arrows turn the month; tap a day to open the app on it.
  Tapping the month's name opens the app, or comes back to this month from another one.

They follow the app's week start, and redraw when any event or calendar changes (here, in
another app or from a sync), at midnight, and when the clock, time zone or language changes.

Long-press the app's icon for **New event**, **Search** and **Today** (opens the calendar on
today). They can be dragged onto the home screen as icons of their own.

### Reminders

Android's calendar storage keeps the alarms for every event's reminders, whichever app made the
event, and tells calendar apps when one is due. Calendar then shows a notification; tapping it
opens the event. If another calendar app (Google Calendar, say) is installed it may remind you
too: turn **Show reminders** off in Settings to leave it to that app.

### Other apps

Calendar answers the standard calendar requests other apps send, so it can be picked as the
phone's calendar app:

- opening an event (`content://com.android.calendar/events/<id>`),
- opening the calendar at a time (`content://com.android.calendar/time/<ms>`),
- adding an event (`ACTION_INSERT`, with the title, times, place and notes filled in; it's only
  saved once you tap Save),
- editing an event (`ACTION_EDIT`).

## Permissions

| Permission | Why |
| --- | --- |
| Read and write calendars | Everything: events and calendars live in Android's calendar storage |
| Notifications | Event reminders |
| Run at startup | Sets the widgets' updates up again after the phone restarts (it shows no prompt) |

The app has no internet permission.

## Building

GitHub Actions builds two kinds of APK, each signed with its own key:

| | When | App | Artifact |
| --- | --- | --- | --- |
| **Test build** | every push to `main`, PRs, manual runs | "Calendar Dev" (`app.agenda.calendar.dev`) | `calendar-dev-apk` on the run page |
| **Release** | Actions → **Build release** → Run workflow; or pushing to the `release` branch | "Calendar" (`app.agenda.calendar`) | attached to a GitHub Release |

A release is tagged with the version name on line 2 of `version.txt` (line 1 is the version
code, which must go up with every release), and its notes come from that version's section of
`CHANGELOG.md`. Each run's page shows the APK's signing certificate SHA-256.

To build locally (JDK 21 and the Android SDK required):

```sh
./gradlew :app:assembleDev         # test build
./gradlew :app:assembleRelease     # release
./gradlew :app:testDebugUnitTest   # unit tests
```

Every CI build runs the unit tests (`app/src/test`) and `scripts/check-docs.py` first, and
stops if either fails. Before publishing a release, CI opens the release APK on an Android 16
emulator and stops if it crashes.

For what each part of the app does and which file it's in, see
[docs/HOW-IT-WORKS.md](docs/HOW-IT-WORKS.md).

### Signing keys

To make both keys and upload them, run this once on your own computer (it needs a JDK for
`keytool` and the GitHub CLI, signed in with `gh auth login`):

```sh
scripts/make-signing-keys.sh            # keystores go in ~/keys/calendar
```

It asks for a password for each key, writes the keystores outside the repository, sets the four
secrets below and prints each certificate's SHA-256. Back up the keystores and passwords: an app
signed with a lost release key can't be updated over, only reinstalled.

The secrets, also settable by hand (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `DEV_KEYSTORE_BASE64` | base64 of the dev keystore (alias `calendar-dev`) |
| `DEV_KEYSTORE_PASSWORD` | its password (store and key) |
| `RELEASE_KEYSTORE_BASE64` | base64 of the release keystore (alias `calendar`) |
| `RELEASE_KEYSTORE_PASSWORD` | its password (store and key) |

Without the dev key, test builds are signed with a throwaway key (the run says so) and won't
install over each other. A release refuses to build without its key.

## License

Apache License 2.0 (see `LICENSE`). Third-party parts are listed in `NOTICE`.
