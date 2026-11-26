package app.agenda.calendar.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * What the home screen widgets show, worked out without a phone so it can be tested: the
 * agenda widget's days and the month widget's cells. The widgets themselves only draw these.
 */

/** How many days ahead, today included, the agenda widget lists. */
const val AGENDA_WIDGET_DAYS = 30

/** The most event dots a day in the month widget shows; more than this still shows this many. */
const val MONTH_WIDGET_DOTS = 3

/**
 * The agenda widget's list: each day from [today] for [AGENDA_WIDGET_DAYS] days that has
 * something on, with what's on it in the app's order. Today is always there, even when it's
 * empty, so the widget can say nothing is planned rather than starting on a later day without
 * saying so.
 */
fun agendaWidgetDays(occurrences: List<Occurrence>, today: LocalDate): List<Pair<LocalDate, List<Occurrence>>> =
    agendaDays(occurrences, today, AGENDA_WIDGET_DAYS, alwaysShow = setOf(today))

/**
 * One day in the month widget's grid. [inMonth] is false for the days from the months either
 * side that fill the first and last rows. [dots] holds the colours of its first few events, in
 * the order the app lists them.
 */
data class MonthWidgetCell(val date: LocalDate, val inMonth: Boolean, val today: Boolean, val dots: List<Int>)

/** The month the month widget shows: this month moved on by [offset] months (back when negative). */
fun monthWidgetMonth(today: LocalDate, offset: Int): YearMonth = YearMonth.from(today).plusMonths(offset.toLong())

/**
 * The 42 cells (six weeks starting on [firstDay]) of the month widget for [month], the same
 * days as the app's Month view. Each gets a dot per event on it, up to [MONTH_WIDGET_DOTS].
 */
fun monthWidgetCells(
    month: YearMonth,
    firstDay: DayOfWeek,
    occurrences: List<Occurrence>,
    today: LocalDate,
): List<MonthWidgetCell> = monthGrid(month, firstDay).map { day ->
    MonthWidgetCell(
        date = day,
        inMonth = YearMonth.from(day) == month,
        today = day == today,
        dots = occurrencesOn(occurrences, day).take(MONTH_WIDGET_DOTS).map { it.color },
    )
}
