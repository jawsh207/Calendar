package app.agenda.calendar.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** The day a week starting on [firstDay] that holds [date] begins on. */
fun startOfWeek(date: LocalDate, firstDay: DayOfWeek): LocalDate = date.with(TemporalAdjusters.previousOrSame(firstDay))

/**
 * The days shown for [month] in a month grid: always six full weeks starting on [firstDay], so
 * the grid keeps the same height from month to month. Days from the months either side fill
 * the first and last rows.
 */
fun monthGrid(month: YearMonth, firstDay: DayOfWeek): List<LocalDate> {
    val first = startOfWeek(month.atDay(1), firstDay)
    return List(42) { first.plusDays(it.toLong()) }
}

/** The seven days of the week holding [date]. */
fun weekDays(date: LocalDate, firstDay: DayOfWeek): List<LocalDate> {
    val first = startOfWeek(date, firstDay)
    return List(7) { first.plusDays(it.toLong()) }
}

/** The first month the month pagers can show. Page numbers count months and weeks from here. */
val FIRST_MONTH: YearMonth = YearMonth.of(1900, 1)

/** How many months (200 years) the month pagers hold. */
const val MONTH_PAGES = 200 * 12

/** The month pager's page for [month]. */
fun monthPage(month: YearMonth): Int = ChronoUnit.MONTHS.between(FIRST_MONTH, month).toInt().coerceIn(0, MONTH_PAGES - 1)

/** The month on month pager page [page]. */
fun monthOfPage(page: Int): YearMonth = FIRST_MONTH.plusMonths(page.toLong())

/** How many weeks (about 200 years) the week pager holds. */
const val WEEK_PAGES = 10_400

/** The week pager's page for the week holding [date]. */
fun weekPage(date: LocalDate, firstDay: DayOfWeek): Int {
    val origin = startOfWeek(FIRST_MONTH.atDay(1), firstDay)
    return ChronoUnit.WEEKS.between(origin, startOfWeek(date, firstDay)).toInt().coerceIn(0, WEEK_PAGES - 1)
}

/** The first day of week pager page [page]. */
fun weekOfPage(page: Int, firstDay: DayOfWeek): LocalDate =
    startOfWeek(FIRST_MONTH.atDay(1), firstDay).plusWeeks(page.toLong())

/**
 * The occurrences on [day], in the order they're listed: all-day and multi-day ones first
 * (longest first), then the rest by start time, then by title.
 */
fun occurrencesOn(occurrences: List<Occurrence>, day: LocalDate): List<Occurrence> =
    occurrences.filter { it.isOn(day) }.sortedWith(
        compareBy<Occurrence> { !(it.allDay || it.multiDay) }
            .thenByDescending { if (it.allDay || it.multiDay) ChronoUnit.DAYS.between(it.startDate, it.endDate) else 0 }
            .thenBy { it.begin }
            .thenBy { it.title.lowercase() },
    )

/**
 * The days for an agenda list from [from] for [days] days: each day that has something on,
 * with what's on it. The days in [alwaysShow] (today, the picked day) are listed even when
 * nothing is on, so the list can say so.
 */
fun agendaDays(
    occurrences: List<Occurrence>,
    from: LocalDate,
    days: Int,
    alwaysShow: Set<LocalDate> = emptySet(),
): List<Pair<LocalDate, List<Occurrence>>> {
    val until = from.plusDays(days.toLong())
    val byDay = sortedMapOf<LocalDate, MutableList<Occurrence>>()
    occurrences.forEach { occurrence ->
        var day = maxOf(occurrence.startDate, from)
        while (!day.isAfter(occurrence.endDate) && day.isBefore(until)) {
            byDay.getOrPut(day) { mutableListOf() }.add(occurrence)
            day = day.plusDays(1)
        }
    }
    alwaysShow.filter { !it.isBefore(from) && it.isBefore(until) }.forEach { byDay.getOrPut(it) { mutableListOf() } }
    return byDay.map { (day, list) -> day to occurrencesOn(list, day) }
}

/**
 * Where an occurrence goes in a day column of the week view: the minutes it covers on that day
 * and which of [columns] side-by-side columns it takes, so overlapping events sit next to
 * each other instead of on top.
 */
data class Placed(val occurrence: Occurrence, val startMinute: Int, val endMinute: Int, val column: Int, val columns: Int)

/** The minutes [occurrence] covers on [day], or null for an all-day one or one not on that day. */
fun minutesOn(occurrence: Occurrence, day: LocalDate): IntRange? {
    if (occurrence.allDay || !occurrence.isOn(day)) return null
    val start = if (day == occurrence.startDate) occurrence.startMinute else 0
    val end = if (day == occurrence.endDate) occurrence.endMinute else MINUTES_PER_DAY
    return start..maxOf(start, end)
}

/**
 * Lays out the timed occurrences on [day] for a day column. Events that overlap (each drawn at
 * least [minLength] minutes tall, so short ones can still be tapped) form a group that shares
 * the width; each takes the leftmost column free when it starts.
 */
fun layoutDay(occurrences: List<Occurrence>, day: LocalDate, minLength: Int = 20): List<Placed> {
    val segments = occurrences.mapNotNull { o -> minutesOn(o, day)?.let { Triple(o, it.first, it.last) } }
        .sortedWith(compareBy<Triple<Occurrence, Int, Int>> { it.second }.thenByDescending { it.third - it.second })
    val placed = mutableListOf<Placed>()
    var group = mutableListOf<Pair<Triple<Occurrence, Int, Int>, Int>>()
    var groupEnd = -1
    // Bottom of each column in the current group, as drawn.
    val columnEnds = mutableListOf<Int>()
    fun closeGroup() {
        val count = columnEnds.size.coerceAtLeast(1)
        group.forEach { (segment, column) -> placed += Placed(segment.first, segment.second, segment.third, column, count) }
        group = mutableListOf()
        columnEnds.clear()
    }
    segments.forEach { segment ->
        val drawnEnd = maxOf(segment.third, segment.second + minLength)
        if (segment.second >= groupEnd && group.isNotEmpty()) closeGroup()
        val free = columnEnds.indexOfFirst { it <= segment.second }
        val column = if (free >= 0) free else columnEnds.size
        if (column == columnEnds.size) columnEnds += drawnEnd else columnEnds[column] = drawnEnd
        group += segment to column
        groupEnd = if (group.size == 1) drawnEnd else maxOf(groupEnd, drawnEnd)
    }
    closeGroup()
    return placed
}

/**
 * Black or white, whichever reads better on [argb]: used for event titles drawn on the event's
 * own colour. Uses the WCAG relative luminance, with the usual 0.179 crossover.
 */
fun textOn(argb: Int): Int {
    fun channel(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
    val luminance = 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    return if (luminance > 0.179) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
}
