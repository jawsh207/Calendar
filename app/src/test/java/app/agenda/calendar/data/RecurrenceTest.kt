package app.agenda.calendar.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading and writing repeat rules and durations. */
class RecurrenceTest {
    private val monday = LocalDate.of(2026, 11, 23)

    @Test
    fun simpleRulesAreRecognised() {
        assertEquals(Repeat.None, repeatOf(null))
        assertEquals(Repeat.Daily, repeatOf("FREQ=DAILY"))
        assertEquals(Repeat.Weekly, repeatOf("FREQ=WEEKLY;BYDAY=MO"))
        assertEquals(Repeat.Weekly, repeatOf("FREQ=WEEKLY;WKST=SU"))
        assertEquals(Repeat.Monthly, repeatOf("FREQ=MONTHLY;BYMONTHDAY=23"))
        assertEquals(Repeat.Yearly, repeatOf("FREQ=YEARLY;BYMONTH=11;BYMONTHDAY=23"))
        assertEquals(Repeat.Daily, repeatOf("freq=daily;interval=1"))
    }

    @Test
    fun otherRulesAreKeptAsWritten() {
        listOf(
            "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO",
            "FREQ=WEEKLY;BYDAY=MO,WE,FR",
            "FREQ=DAILY;COUNT=5",
            "FREQ=MONTHLY;BYDAY=-1FR",
            "FREQ=YEARLY;UNTIL=20300101T000000Z",
        ).forEach { rule -> assertEquals(Repeat.Custom(rule), repeatOf(rule)) }
    }

    @Test
    fun rulesAreWrittenForTheStartDay() {
        assertNull(ruleOf(Repeat.None, monday))
        assertEquals("FREQ=WEEKLY;BYDAY=MO", ruleOf(Repeat.Weekly, monday))
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=23", ruleOf(Repeat.Monthly, monday))
        assertEquals("FREQ=YEARLY;BYMONTH=11;BYMONTHDAY=23", ruleOf(Repeat.Yearly, monday))
        assertEquals("FREQ=DAILY;COUNT=3", ruleOf(Repeat.Custom("FREQ=DAILY;COUNT=3"), monday))
    }

    @Test
    fun writtenRulesReadBack() {
        repeatChoices.forEach { choice -> assertEquals(choice, repeatOf(ruleOf(choice, monday))) }
    }

    @Test
    fun durationsAreRead() {
        assertEquals(HOUR_MILLIS, durationMillis("P3600S"))
        assertEquals(HOUR_MILLIS, durationMillis("PT1H"))
        assertEquals(90 * 60 * 1000L, durationMillis("PT1H30M"))
        assertEquals(DAY_MILLIS, durationMillis("P1D"))
        assertEquals(14 * DAY_MILLIS, durationMillis("P2W"))
        assertEquals(-15 * 60 * 1000L, durationMillis("-PT15M"))
        assertNull(durationMillis("P"))
        assertNull(durationMillis("one hour"))
        assertNull(durationMillis(null))
    }

    @Test
    fun durationsAreWritten() {
        assertEquals("P3600S", durationText(HOUR_MILLIS, allDay = false))
        assertEquals("P2D", durationText(2 * DAY_MILLIS, allDay = true))
        // A day that's an hour short or long across a clock change still counts as one.
        assertEquals("P1D", durationText(DAY_MILLIS - HOUR_MILLIS, allDay = true))
        assertTrue(durationMillis(durationText(5400_000, false)) == 5400_000L)
    }
}
