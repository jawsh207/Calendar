package app.agenda.calendar.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The agenda widget's days and the month widget's cells. */
class WidgetLayoutTest {
    private val today = LocalDate.of(2026, 11, 26)

    @Test
    fun agendaAlwaysStartsWithTodayEvenWhenEmpty() {
        val later = timed(today.plusDays(3).atTime(9, 0), today.plusDays(3).atTime(10, 0))
        val days = agendaWidgetDays(listOf(later), today)
        assertEquals(listOf(today, today.plusDays(3)), days.map { it.first })
        assertTrue(days.first().second.isEmpty())
    }

    @Test
    fun agendaLeavesOutDaysBeforeTodayAndPastItsWindow() {
        val yesterday = timed(today.minusDays(1).atTime(9, 0), today.minusDays(1).atTime(10, 0), id = 1)
        val last = timed(today.plusDays(AGENDA_WIDGET_DAYS - 1L).atTime(9, 0), today.plusDays(AGENDA_WIDGET_DAYS - 1L).atTime(10, 0), id = 2)
        val tooFar = timed(today.plusDays(AGENDA_WIDGET_DAYS.toLong()).atTime(9, 0), today.plusDays(AGENDA_WIDGET_DAYS.toLong()).atTime(10, 0), id = 3)
        val days = agendaWidgetDays(listOf(yesterday, last, tooFar), today)
        assertEquals(listOf(today, today.plusDays(AGENDA_WIDGET_DAYS - 1L)), days.map { it.first })
        assertEquals(listOf(2L), days.last().second.map { it.eventId })
    }

    @Test
    fun agendaListsAMultiDayEventOnEachOfItsDaysFromToday() {
        val trip = allDay(today.minusDays(1), today.plusDays(1))
        val days = agendaWidgetDays(listOf(trip), today)
        assertEquals(listOf(today, today.plusDays(1)), days.map { it.first })
    }

    @Test
    fun monthWidgetMovesByWholeMonths() {
        assertEquals(YearMonth.of(2026, 11), monthWidgetMonth(today, 0))
        assertEquals(YearMonth.of(2027, 1), monthWidgetMonth(today, 2))
        assertEquals(YearMonth.of(2026, 10), monthWidgetMonth(today, -1))
    }

    @Test
    fun monthCellsMarkTodayAndTheDaysOutsideTheMonth() {
        val cells = monthWidgetCells(YearMonth.of(2026, 11), DayOfWeek.MONDAY, emptyList(), today)
        assertEquals(42, cells.size)
        assertEquals(LocalDate.of(2026, 10, 26), cells.first().date)
        assertFalse(cells.first().inMonth)
        assertTrue(cells.single { it.date == LocalDate.of(2026, 11, 1) }.inMonth)
        assertEquals(listOf(today), cells.filter { it.today }.map { it.date })
    }

    @Test
    fun monthCellsShowADotPerEventUpToTheLimit() {
        val busy = List(MONTH_WIDGET_DOTS + 2) { i ->
            timed(today.atTime(8 + i, 0), today.atTime(9 + i, 0), id = i.toLong())
        }
        val one = timed(today.plusDays(1).atTime(9, 0), today.plusDays(1).atTime(10, 0), id = 99)
        val cells = monthWidgetCells(YearMonth.of(2026, 11), DayOfWeek.MONDAY, busy + one, today).associateBy { it.date }
        assertEquals(MONTH_WIDGET_DOTS, cells.getValue(today).dots.size)
        assertEquals(1, cells.getValue(today.plusDays(1)).dots.size)
        assertTrue(cells.getValue(today.plusDays(2)).dots.isEmpty())
    }
}
