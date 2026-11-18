package net.buznik.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleTest {
    private val wed = LocalDate.of(2026, 11, 18) // third Wednesday of November 2026

    private fun next(rule: String, after: String, start: LocalDate = wed) =
        Schedule("t", date = start, time = LocalTime.of(8, 0), rule = Rule.parse(rule)).next(LocalDateTime.parse(after))

    @Test fun rruleRoundTrip() {
        listOf("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", "FREQ=MONTHLY;INTERVAL=2;BYDAY=-1FR;COUNT=5", "FREQ=DAILY;UNTIL=20261231")
            .forEach { assertEquals(it, Rule.parse(it).toString()) }
        assertNull(Rule.parse(""))
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsOrdinalOutsideMonthly() {
        Rule.parse("FREQ=WEEKLY;BYDAY=2MO")
    }

    @Test fun oneOff() {
        assertEquals(LocalDateTime.parse("2026-11-18T08:00"), next("", "2026-11-18T07:00"))
        assertNull(next("", "2026-11-18T08:00"))
    }

    @Test fun weekdaysSkipWeekend() =
        assertEquals(LocalDateTime.parse("2026-11-23T08:00"), next("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", "2026-11-20T09:00"))

    @Test fun everyTwoWeeks() =
        assertEquals(LocalDateTime.parse("2026-12-02T08:00"), next("FREQ=WEEKLY;INTERVAL=2;BYDAY=WE", "2026-11-18T09:00"))

    @Test fun monthlyNthWeekday() =
        assertEquals(LocalDateTime.parse("2026-12-16T08:00"), next("FREQ=MONTHLY;BYDAY=3WE", "2026-11-18T09:00"))

    @Test fun monthlyLastWeekday() =
        assertEquals(LocalDateTime.parse("2026-12-25T08:00"), next("FREQ=MONTHLY;BYDAY=-1FR", "2026-11-28T09:00", LocalDate.of(2026, 11, 27)))

    @Test fun monthlyDay31SkipsShortMonths() =
        assertEquals(LocalDateTime.parse("2026-12-31T08:00"), next("FREQ=MONTHLY", "2026-10-31T09:00", LocalDate.of(2026, 10, 31)))

    @Test fun yearlyLeapDay() =
        assertEquals(LocalDateTime.parse("2032-02-29T08:00"), next("FREQ=YEARLY", "2028-02-29T09:00", LocalDate.of(2028, 2, 29)))

    @Test fun countEnds() {
        assertEquals(LocalDateTime.parse("2026-11-20T08:00"), next("FREQ=DAILY;COUNT=3", "2026-11-19T09:00"))
        assertNull(next("FREQ=DAILY;COUNT=3", "2026-11-20T09:00"))
    }

    @Test fun untilIsInclusive() {
        assertEquals(LocalDateTime.parse("2026-11-20T08:00"), next("FREQ=DAILY;UNTIL=20261120", "2026-11-19T09:00"))
        assertNull(next("FREQ=DAILY;UNTIL=20261120", "2026-11-20T09:00"))
    }

    @Test fun presetsMatchGoogleCalendar() {
        assertEquals(
            listOf(
                "Does not repeat", "Daily", "Weekly on Wednesday", "Monthly on the third Wednesday",
                "Annually on November 18", "Every weekday (Monday to Friday)",
            ),
            presets(wed).map { describe(wed, it) },
        )
    }

    @Test fun describesCustom() {
        assertEquals("Every 2 weeks on Monday, Friday, 5 times", describe(wed, Rule(Freq.WEEKLY, 2, setOf(FRIDAY, MONDAY), count = 5)))
        assertEquals("Monthly on the last Friday", describe(wed, Rule.parse("FREQ=MONTHLY;BYDAY=-1FR")))
    }
}
