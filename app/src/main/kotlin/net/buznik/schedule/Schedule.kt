package net.buznik.schedule

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

/** Subset of RFC 5545 RRULE. For MONTHLY, nth != 0 means "nth weekday" (-1 = last), with the weekday in [days]. */
data class Rule(
    val freq: Freq,
    val interval: Int = 1,
    val days: Set<DayOfWeek> = emptySet(),
    val nth: Int = 0,
    val until: LocalDate? = null,
    val count: Int = 0,
) {
    override fun toString() = buildList {
        add("FREQ=$freq")
        if (interval > 1) add("INTERVAL=$interval")
        if (days.isNotEmpty()) add("BYDAY=" + days.sorted().joinToString(",") { (if (nth != 0) "$nth" else "") + it.code })
        until?.let { add("UNTIL=" + it.format(DateTimeFormatter.BASIC_ISO_DATE)) }
        if (count > 0) add("COUNT=$count")
    }.joinToString(";")

    companion object {
        fun parse(s: String): Rule? {
            if (s.isBlank()) return null
            val parts = s.removePrefix("RRULE:").split(";").associate {
                it.substringBefore("=").uppercase() to it.substringAfter("=").uppercase()
            }
            val freq = Freq.valueOf(requireNotNull(parts["FREQ"]) { "FREQ missing" })
            var nth = 0
            val days = parts["BYDAY"]?.split(",")?.map { token ->
                val prefix = token.dropLast(2)
                if (prefix.isNotEmpty()) nth = prefix.toInt()
                requireNotNull(DayOfWeek.values().firstOrNull { it.code == token.takeLast(2) }) { "bad BYDAY $token" }
            }?.toSet().orEmpty()
            val rule = Rule(
                freq = freq,
                interval = parts["INTERVAL"]?.toInt() ?: 1,
                days = days,
                nth = nth,
                until = parts["UNTIL"]?.let { LocalDate.parse(it.take(8), DateTimeFormatter.BASIC_ISO_DATE) },
                count = parts["COUNT"]?.toInt() ?: 0,
            )
            require(rule.interval >= 1 && rule.count >= 0) { "INTERVAL/COUNT out of range" }
            require(nth in -1..4 && (nth == 0 || (freq == Freq.MONTHLY && days.size == 1))) { "bad BYDAY ordinal" }
            return rule
        }
    }
}

val DayOfWeek.code get() = name.take(2)

fun occurrences(start: LocalDate, rule: Rule?): Sequence<LocalDate> = sequence {
    if (rule == null) {
        yield(start)
        return@sequence
    }
    var produced = 0
    for (k in 0L until 10_000L) {
        val step = k * rule.interval
        val dates = when (rule.freq) {
            Freq.DAILY -> listOf(start.plusDays(step))
            Freq.WEEKLY -> {
                val week = start.with(DayOfWeek.MONDAY).plusWeeks(step)
                rule.days.ifEmpty { setOf(start.dayOfWeek) }.sorted().map { week.with(it) }
            }
            Freq.MONTHLY -> {
                val month = YearMonth.from(start).plusMonths(step)
                listOfNotNull(
                    if (rule.nth == 0) month.atDay(start.dayOfMonth).takeIf { start.dayOfMonth <= month.lengthOfMonth() }
                    else nthWeekday(month, rule.days.first(), rule.nth)
                )
            }
            Freq.YEARLY -> {
                val year = start.year + step.toInt()
                listOfNotNull(MonthDay.from(start).takeIf { it.isValidYear(year) }?.atYear(year))
            }
        }
        for (d in dates) {
            if (d < start) continue
            if (rule.until != null && d > rule.until) return@sequence
            if (rule.count in 1..produced) return@sequence
            produced++
            yield(d)
        }
    }
}

fun nthWeekday(month: YearMonth, day: DayOfWeek, nth: Int): LocalDate? =
    if (nth == -1) month.atEndOfMonth().with(TemporalAdjusters.lastInMonth(day))
    else month.atDay(1).with(TemporalAdjusters.firstInMonth(day)).plusWeeks(nth - 1L)
        .takeIf { YearMonth.from(it) == month }

data class Schedule(
    val id: String,
    val enabled: Boolean = true,
    val app: String = "",
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.of(8, 0),
    val rule: Rule? = null,
) {
    fun next(after: LocalDateTime): LocalDateTime? =
        occurrences(date, rule).map { it.atTime(time) }.firstOrNull { it > after }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("enabled", enabled).put("app", app)
        .put("date", date.toString()).put("time", time.toString()).put("rrule", rule?.toString().orEmpty())

    companion object {
        fun fromJson(o: JSONObject) = Schedule(
            id = o.getString("id"),
            enabled = o.optBoolean("enabled", true),
            app = o.optString("app"),
            date = LocalDate.parse(o.getString("date")),
            time = LocalTime.parse(o.getString("time")),
            rule = Rule.parse(o.optString("rrule")),
        )
    }
}
