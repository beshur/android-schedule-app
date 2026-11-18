package net.buznik.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

fun DayOfWeek.label(): String = getDisplayName(TextStyle.FULL, Locale.ENGLISH)

/** 1..4 for the nth weekday of the month, -1 when [date] falls in the 5th (last) week. */
fun nthOf(date: LocalDate): Int = ((date.dayOfMonth - 1) / 7 + 1).let { if (it == 5) -1 else it }

fun ordinal(n: Int) = when (n) { 1 -> "first"; 2 -> "second"; 3 -> "third"; 4 -> "fourth"; else -> "last" }

/** The dropdown choices Google Calendar offers for an event on [date]. */
fun presets(date: LocalDate): List<Rule?> = listOf(
    null,
    Rule(Freq.DAILY),
    Rule(Freq.WEEKLY, days = setOf(date.dayOfWeek)),
    Rule(Freq.MONTHLY, days = setOf(date.dayOfWeek), nth = nthOf(date)),
    Rule(Freq.YEARLY),
    Rule(Freq.WEEKLY, days = WEEKDAYS),
)

fun describe(date: LocalDate, rule: Rule?): String {
    if (rule == null) return "Does not repeat"
    fun every(unit: String) = if (rule.interval == 1) null else "Every ${rule.interval} ${unit}s"
    val base = when (rule.freq) {
        Freq.DAILY -> every("day") ?: "Daily"
        Freq.WEEKLY -> if (rule.interval == 1 && rule.days == WEEKDAYS) "Every weekday (Monday to Friday)"
        else (every("week") ?: "Weekly") + " on " +
            rule.days.ifEmpty { setOf(date.dayOfWeek) }.sorted().joinToString(", ") { it.label() }
        Freq.MONTHLY -> (every("month") ?: "Monthly") + " on " +
            if (rule.nth == 0) "day ${date.dayOfMonth}" else "the ${ordinal(rule.nth)} ${rule.days.first().label()}"
        Freq.YEARLY -> (every("year") ?: "Annually") + " on " + date.format(DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH))
    }
    return base + when {
        rule.until != null -> ", until " + rule.until.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))
        rule.count == 1 -> ", once"
        rule.count > 1 -> ", ${rule.count} times"
        else -> ""
    }
}
