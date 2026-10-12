package io.github.akrishna87.doable

import io.github.akrishna87.doable.model.Freq
import io.github.akrishna87.doable.model.RepeatRule
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.util.Locale

class RepeatRuleTest {
    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)
    private fun parse(s: String, fromCompletion: Boolean = false) = RepeatRule.parseAfterEvery(s.split(" "), fromCompletion)?.first

    @Test
    fun simpleSteps() {
        assertEquals(d(10, 15), RepeatRule(Freq.DAILY, 3).next(d(10, 12), today = d(10, 12)))
        assertEquals(d(10, 26), RepeatRule(Freq.WEEKLY, 2).next(d(10, 12), today = d(10, 12)))
        assertEquals(d(11, 12), RepeatRule.Monthly.next(d(10, 12), today = d(10, 12)))
        assertEquals(d(10, 12, 2027), RepeatRule.Yearly.next(d(10, 12), today = d(10, 12)))
    }

    @Test
    fun overdueTasksMoveToTheNextDateFromToday() {
        // Due last Monday, completed today (Thursday 15 Oct): the next daily date is today.
        assertEquals(d(10, 15), RepeatRule.Daily.next(d(10, 12), today = d(10, 15)))
        // "every!" counts from completion instead.
        assertEquals(d(10, 18), RepeatRule(Freq.DAILY, 3, fromCompletion = true).next(d(10, 12), today = d(10, 15), completedOn = d(10, 15)))
    }

    @Test
    fun chosenWeekdays() {
        val monWed = RepeatRule(Freq.WEEKLY, weekdays = setOf(MONDAY, WEDNESDAY))
        // 12 Oct 2026 is a Monday.
        assertEquals(d(10, 14), monWed.next(d(10, 12), today = d(10, 12)))
        assertEquals(d(10, 19), monWed.next(d(10, 14), today = d(10, 14)))
        // Every other week: Mon, Wed this week, then the week after next.
        val other = monWed.copy(interval = 2)
        assertEquals(d(10, 14), other.next(d(10, 12), today = d(10, 12)))
        assertEquals(d(10, 26), other.next(d(10, 14), today = d(10, 14)))
        assertEquals(d(10, 13), RepeatRule.Weekdays.next(d(10, 12), today = d(10, 12)))
        assertEquals(d(10, 19), RepeatRule.Weekdays.next(d(10, 16), today = d(10, 16)))
    }

    @Test
    fun monthlyDaysAndNthWeekdays() {
        val last = RepeatRule(Freq.MONTHLY, monthDay = 0)
        assertEquals(d(2, 28), last.next(d(1, 31), today = d(1, 31)))
        assertEquals(d(3, 31), last.next(d(2, 28), today = d(2, 28)))
        val on31 = RepeatRule(Freq.MONTHLY, monthDay = 31)
        assertEquals(d(2, 28), on31.next(d(1, 31), today = d(1, 31)))
        assertEquals(d(3, 31), on31.next(d(2, 28), today = d(2, 28)))
        val secondTue = RepeatRule(Freq.MONTHLY, weekdays = setOf(TUESDAY), nth = 2)
        assertEquals(d(11, 10), secondTue.next(d(10, 13), today = d(10, 13)))
        val lastFri = RepeatRule(Freq.MONTHLY, weekdays = setOf(FRIDAY), nth = -1)
        assertEquals(d(11, 27), lastFri.next(d(10, 30), today = d(10, 30)))
    }

    @Test
    fun repeatsEnd() {
        val r = RepeatRule(Freq.WEEKLY, until = d(10, 20))
        assertEquals(d(10, 19), r.next(d(10, 12), today = d(10, 12)))
        assertNull(r.next(d(10, 19), today = d(10, 19)))
    }

    @Test
    fun firstDates() {
        assertEquals(d(10, 14), RepeatRule(Freq.WEEKLY, weekdays = setOf(WEDNESDAY)).firstOnOrAfter(d(10, 12)))
        assertEquals(d(11, 1), RepeatRule(Freq.MONTHLY, monthDay = 1).firstOnOrAfter(d(10, 12)))
        assertEquals(d(10, 13), RepeatRule(Freq.MONTHLY, weekdays = setOf(TUESDAY), nth = 2).firstOnOrAfter(d(10, 12)))
    }

    @Test
    fun readingWords() {
        assertEquals(RepeatRule(Freq.DAILY, 3), parse("3 days"))
        assertEquals(RepeatRule(Freq.WEEKLY, 2), parse("other week"))
        assertEquals(RepeatRule.Weekdays, parse("weekday"))
        assertEquals(RepeatRule(Freq.WEEKLY, weekdays = setOf(MONDAY, WEDNESDAY)), parse("mon, wed"))
        assertEquals(RepeatRule(Freq.WEEKLY, weekdays = setOf(MONDAY, FRIDAY)), parse("monday and friday"))
        assertEquals(RepeatRule(Freq.MONTHLY, monthDay = 15), parse("15th"))
        assertEquals(RepeatRule(Freq.MONTHLY, monthDay = 0), parse("last day"))
        assertEquals(RepeatRule(Freq.MONTHLY, weekdays = setOf(TUESDAY), nth = 2), parse("2nd tue"))
        assertEquals(RepeatRule(Freq.YEARLY), parse("year"))
        assertEquals(RepeatRule(Freq.WEEKLY, 2, fromCompletion = true), parse("2 weeks", fromCompletion = true))
        assertNull(parse("blue moon"))
    }

    @Test
    fun labels() {
        assertEquals("Every day", RepeatRule.Daily.label(Locale.ENGLISH))
        assertEquals("Every 3 days after completion", RepeatRule(Freq.DAILY, 3, fromCompletion = true).label(Locale.ENGLISH))
        assertEquals("Every weekday", RepeatRule.Weekdays.label(Locale.ENGLISH))
        assertEquals("Every Mon, Wed", RepeatRule(Freq.WEEKLY, weekdays = setOf(WEDNESDAY, MONDAY)).label(Locale.ENGLISH))
        assertEquals("Every other week on Mon", RepeatRule(Freq.WEEKLY, 2, weekdays = setOf(MONDAY)).label(Locale.ENGLISH))
        assertEquals("Every month on the 2nd Tue", RepeatRule(Freq.MONTHLY, weekdays = setOf(TUESDAY), nth = 2).label(Locale.ENGLISH))
        assertEquals("Every month on the last day", RepeatRule(Freq.MONTHLY, monthDay = 0).label(Locale.ENGLISH))
        assertEquals("Every year until 1 Jan 2030", RepeatRule(Freq.YEARLY, until = LocalDate.of(2030, 1, 1)).label(Locale.ENGLISH))
    }

    @Test
    fun savesAndLoads() {
        val r = RepeatRule(Freq.MONTHLY, 2, setOf(TUESDAY), nth = -1, fromCompletion = true, until = d(12, 31))
        assertEquals(r, RepeatRule.fromJson(JSONObject(r.toJson().toString())))
        val m = RepeatRule(Freq.MONTHLY, monthDay = 0)
        assertEquals(m, RepeatRule.fromJson(JSONObject(m.toJson().toString())))
    }
}
