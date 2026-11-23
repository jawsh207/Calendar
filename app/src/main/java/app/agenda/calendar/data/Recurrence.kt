package app.agenda.calendar.data

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * How an event repeats, in the simple choices the editor offers. Rules written by other apps
 * that don't fit one of them (every other week, until a date, the last Friday) are [Custom] and
 * kept exactly as they are unless the user picks something else.
 */
sealed interface Repeat {
    /** Happens once. */
    data object None : Repeat

    /** Every day. */
    data object Daily : Repeat

    /** Every week, on the day it starts. */
    data object Weekly : Repeat

    /** Every month, on the date it starts. */
    data object Monthly : Repeat

    /** Every year, on the date it starts. */
    data object Yearly : Repeat

    /** A rule from another app, kept as written. */
    data class Custom(val rule: String) : Repeat
}

/** The choices the editor lists, in order. */
val repeatChoices: List<Repeat> = listOf(Repeat.None, Repeat.Daily, Repeat.Weekly, Repeat.Monthly, Repeat.Yearly)

/** The two-letter day names RFC 5545 rules use. */
private val ruleDays = mapOf(
    DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE", DayOfWeek.THURSDAY to "TH",
    DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU",
)

/** Splits a rule such as "FREQ=WEEKLY;BYDAY=MO" into its upper-cased parts. */
private fun ruleParts(rule: String): Map<String, String> = rule.removePrefix("RRULE:").split(';')
    .mapNotNull { part ->
        val at = part.indexOf('=')
        if (at <= 0) null else part.substring(0, at).trim().uppercase() to part.substring(at + 1).trim().uppercase()
    }.toMap()

/**
 * Reads a stored repeat rule as one of the simple choices when it is one. A rule that names
 * the start's own day ("FREQ=WEEKLY;BYDAY=MO", as Google Calendar writes) still counts as
 * plain weekly; anything with a count, an end date, a gap or several days is [Repeat.Custom].
 */
fun repeatOf(rule: String?): Repeat {
    if (rule.isNullOrBlank()) return Repeat.None
    val parts = ruleParts(rule)
    val extra = parts.keys - setOf("FREQ", "WKST", "INTERVAL", "BYDAY", "BYMONTHDAY", "BYMONTH")
    val interval = parts["INTERVAL"]
    if (extra.isNotEmpty() || (interval != null && interval != "1")) return Repeat.Custom(rule)
    val byDay = parts["BYDAY"]
    val byMonthDay = parts["BYMONTHDAY"]
    val byMonth = parts["BYMONTH"]
    // One plain day name, not a list or "1MO".
    val singleDay = byDay == null || byDay in ruleDays.values
    val single = { value: String? -> value == null || (value.toIntOrNull() ?: -1) > 0 }
    return when (parts["FREQ"]) {
        "DAILY" -> if (byDay == null && byMonthDay == null && byMonth == null) Repeat.Daily else Repeat.Custom(rule)
        "WEEKLY" -> if (singleDay && byMonthDay == null && byMonth == null) Repeat.Weekly else Repeat.Custom(rule)
        "MONTHLY" -> if (byDay == null && single(byMonthDay) && byMonth == null) Repeat.Monthly else Repeat.Custom(rule)
        "YEARLY" -> if (byDay == null && single(byMonthDay) && single(byMonth)) Repeat.Yearly else Repeat.Custom(rule)
        else -> Repeat.Custom(rule)
    }
}

/** The rule to store for [repeat] on an event starting on [start]; null for one that doesn't repeat. */
fun ruleOf(repeat: Repeat, start: LocalDate): String? = when (repeat) {
    Repeat.None -> null
    Repeat.Daily -> "FREQ=DAILY"
    Repeat.Weekly -> "FREQ=WEEKLY;BYDAY=${ruleDays.getValue(start.dayOfWeek)}"
    Repeat.Monthly -> "FREQ=MONTHLY;BYMONTHDAY=${start.dayOfMonth}"
    Repeat.Yearly -> "FREQ=YEARLY;BYMONTH=${start.monthValue};BYMONTHDAY=${start.dayOfMonth}"
    is Repeat.Custom -> repeat.rule
}

private val durationPattern = Regex("""^([+-])?P(?:(\d+)W)?(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?$""")

/**
 * Reads an RFC 5545 duration ("PT1H30M", "P1D", "P2W") as milliseconds. Android itself writes
 * "P3600S", seconds without the T, so that's accepted too. Null when it can't be read.
 */
fun durationMillis(text: String?): Long? {
    val match = durationPattern.matchEntire(text?.trim()?.uppercase() ?: return null) ?: return null
    val (sign, weeks, days, hours, minutes, seconds) = match.destructured
    if (listOf(weeks, days, hours, minutes, seconds).all { it.isEmpty() }) return null
    val number = { part: String -> part.toLongOrNull() ?: 0L }
    val total = (((number(weeks) * 7 + number(days)) * 24 + number(hours)) * 60 + number(minutes)) * 60 + number(seconds)
    return total * 1000 * if (sign == "-") -1 else 1
}

/**
 * The duration to store for a repeating event lasting [millis]: whole days for an all-day event
 * ("P1D"), seconds otherwise ("P3600S"), as Android's own calendar writes them.
 */
fun durationText(millis: Long, allDay: Boolean): String = if (allDay) {
    "P${maxOf(1L, (millis + DAY_MILLIS / 2) / DAY_MILLIS)}D"
} else {
    "P${maxOf(0L, millis / 1000)}S"
}
