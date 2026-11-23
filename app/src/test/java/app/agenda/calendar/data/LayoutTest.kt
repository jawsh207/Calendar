package app.agenda.calendar.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Month grids, week pages, agenda days and placing events side by side. */
class LayoutTest {
    private val day = LocalDate.of(2026, 11, 23) // a Monday

    @Test
    fun monthGridIsSixWeeksFromTheFirstDay() {
        val grid = monthGrid(YearMonth.of(2026, 11), DayOfWeek.SUNDAY)
        assertEquals(42, grid.size)
        assertEquals(LocalDate.of(2026, 11, 1), grid.first()) // November 2026 starts on a Sunday
        assertEquals(DayOfWeek.SUNDAY, grid.first().dayOfWeek)
        val monday = monthGrid(YearMonth.of(2026, 11), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 26), monday.first())
    }

    @Test
    fun weekStartsOnTheChosenDay() {
        assertEquals(day, startOfWeek(day, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 11, 22), startOfWeek(day, DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2026, 11, 21), startOfWeek(day, DayOfWeek.SATURDAY))
        assertEquals(7, weekDays(day, DayOfWeek.MONDAY).size)
    }

    @Test
    fun pagesRoundTrip() {
        val month = YearMonth.of(2026, 11)
        assertEquals(month, monthOfPage(monthPage(month)))
        val page = weekPage(day, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 11, 22), weekOfPage(page, DayOfWeek.SUNDAY))
        assertEquals(page + 1, weekPage(day.plusWeeks(1), DayOfWeek.SUNDAY))
    }

    @Test
    fun allDayEventsComeFirst() {
        val lunch = timed(day.atTime(12, 0), day.atTime(13, 0), "Lunch", 1)
        val breakfast = timed(day.atTime(8, 0), day.atTime(9, 0), "Breakfast", 2)
        val holiday = allDay(day, day, "Holiday", 3)
        assertEquals(listOf("Holiday", "Breakfast", "Lunch"), occurrencesOn(listOf(lunch, breakfast, holiday), day).map { it.title })
    }

    @Test
    fun agendaListsEachDayOfAMultiDayEvent() {
        val trip = allDay(day, day.plusDays(2), "Trip")
        val days = agendaDays(listOf(trip), day, 7)
        assertEquals(listOf(day, day.plusDays(1), day.plusDays(2)), days.map { it.first })
    }

    @Test
    fun agendaKeepsTodayEvenWhenEmpty() {
        val later = timed(day.plusDays(3).atTime(9, 0), day.plusDays(3).atTime(10, 0))
        val days = agendaDays(listOf(later), day, 7, alwaysShow = setOf(day))
        assertEquals(listOf(day, day.plusDays(3)), days.map { it.first })
        assertTrue(days.first().second.isEmpty())
    }

    @Test
    fun agendaSkipsDaysOutsideTheRange() {
        val before = timed(day.minusDays(1).atTime(9, 0), day.minusDays(1).atTime(10, 0))
        assertTrue(agendaDays(listOf(before), day, 7, alwaysShow = setOf(day.minusDays(1))).isEmpty())
    }

    @Test
    fun overlappingEventsSitSideBySide() {
        val a = timed(day.atTime(9, 0), day.atTime(10, 0), "A", 1)
        val b = timed(day.atTime(9, 30), day.atTime(11, 0), "B", 2)
        val c = timed(day.atTime(10, 0), day.atTime(10, 30), "C", 3)
        val d = timed(day.atTime(12, 0), day.atTime(13, 0), "D", 4)
        val placed = layoutDay(listOf(a, b, c, d), day).associateBy { it.occurrence.title }
        assertEquals(0, placed.getValue("A").column)
        assertEquals(1, placed.getValue("B").column)
        // C starts as A ends, so it takes A's column back.
        assertEquals(0, placed.getValue("C").column)
        assertEquals(2, placed.getValue("A").columns)
        assertEquals(1, placed.getValue("D").columns)
    }

    @Test
    fun overnightEventIsSplitAtMidnight() {
        val late = timed(day.atTime(22, 0), day.plusDays(1).atTime(2, 0))
        assertEquals(1320..MINUTES_PER_DAY, minutesOn(late, day))
        assertEquals(0..120, minutesOn(late, day.plusDays(1)))
    }

    @Test
    fun allDayEventsAreNotInTheTimeGrid() {
        assertTrue(layoutDay(listOf(allDay(day, day)), day).isEmpty())
    }

    @Test
    fun textColourContrasts() {
        assertEquals(0xFFFFFFFF.toInt(), textOn(0xFF3F51B5.toInt()))
        assertEquals(0xFF000000.toInt(), textOn(0xFFF6BF26.toInt()))
    }
}
