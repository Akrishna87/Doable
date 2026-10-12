package io.github.akrishna87.doable

import io.github.akrishna87.doable.model.Dates
import io.github.akrishna87.doable.model.DueTone
import io.github.akrishna87.doable.model.Reminder
import io.github.akrishna87.doable.model.RepeatRule
import io.github.akrishna87.doable.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

class DatesTest {
    private val today = LocalDate.of(2026, 10, 3) // a Saturday
    private val now = LocalDateTime.of(today, LocalTime.of(15, 36))

    @Test
    fun dayLabels() {
        assertEquals("Today", Dates.dayLabel(today, today, Locale.ENGLISH))
        assertEquals("Tomorrow", Dates.dayLabel(today.plusDays(1), today, Locale.ENGLISH))
        assertEquals("Yesterday", Dates.dayLabel(today.minusDays(1), today, Locale.ENGLISH))
        assertEquals("Wednesday", Dates.dayLabel(today.plusDays(4), today, Locale.ENGLISH))
        assertEquals("20 Oct", Dates.dayLabel(LocalDate.of(2026, 10, 20), today, Locale.ENGLISH))
        assertEquals("5 Jan 2027", Dates.dayLabel(LocalDate.of(2027, 1, 5), today, Locale.ENGLISH))
        assertEquals("Today · Sat 3 Oct", Dates.dayHeading(today, today, Locale.ENGLISH))
    }

    @Test
    fun dueLabelsIncludeTheTime() {
        val t = Task(title = "x", due = today, time = LocalTime.of(9, 0))
        assertEquals("Today 09:00", Dates.dueLabel(t, today, true, Locale.ENGLISH))
        assertEquals("Today 9:00 AM", Dates.dueLabel(t, today, false, Locale.ENGLISH))
        assertNull(Dates.dueLabel(Task(title = "x"), today, true))
    }

    @Test
    fun overdueAndTones() {
        assertTrue(Dates.isOverdue(Task(title = "x", due = today.minusDays(1)), now))
        assertTrue(Dates.isOverdue(Task(title = "x", due = today, time = LocalTime.of(9, 0)), now))
        assertFalse(Dates.isOverdue(Task(title = "x", due = today), now))
        assertFalse(Dates.isOverdue(Task(title = "x", due = today.minusDays(1), done = true), now))
        assertEquals(DueTone.TODAY, Dates.tone(Task(title = "x", due = today), now))
        assertEquals(DueTone.TOMORROW, Dates.tone(Task(title = "x", due = today.plusDays(1)), now))
        assertEquals(DueTone.THIS_WEEK, Dates.tone(Task(title = "x", due = today.plusDays(5)), now))
        assertEquals(DueTone.LATER, Dates.tone(Task(title = "x", due = today.plusDays(9)), now))
    }

    @Test
    fun repeatingTasksMoveOn() {
        assertEquals(today.plusDays(1), RepeatRule.Daily.next(today, today))
        // An overdue daily task comes back today, not in the past.
        assertEquals(today, RepeatRule.Daily.next(today.minusDays(3), today))
        // Friday's weekday task skips the weekend.
        assertEquals(LocalDate.of(2026, 10, 5), RepeatRule.Weekdays.next(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 2)))
        assertEquals(today.plusWeeks(1), RepeatRule.Weekly.next(today, today))
        assertEquals(LocalDate.of(2026, 2, 28), RepeatRule.Monthly.next(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 31)))
        assertEquals(LocalDate.of(2027, 10, 3), RepeatRule.Yearly.next(today, today))
    }

    @Test
    fun ordering() {
        val a = Task(title = "undated p1", priority = 1, createdAt = 1)
        val b = Task(title = "today untimed p4", due = today, createdAt = 2)
        val c = Task(title = "today 9am", due = today, time = LocalTime.of(9, 0), createdAt = 3)
        val d = Task(title = "today untimed p1", due = today, priority = 1, createdAt = 4)
        val e = Task(title = "yesterday", due = today.minusDays(1), createdAt = 5)
        assertEquals(listOf("yesterday", "today 9am", "today untimed p1", "today untimed p4", "undated p1"), listOf(a, b, c, d, e).sortedWith(Dates.taskOrder).map { it.title })
        assertEquals(listOf("yesterday"), Dates.overdue(listOf(a, b, c, d, e), today).map { it.title })
        assertEquals(3, Dates.dueOn(listOf(a, b, c, d, e), today).size)
    }

    @Test
    fun weeks() {
        assertEquals(LocalDate.of(2026, 9, 28), Dates.startOfWeek(today, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 9, 27), Dates.startOfWeek(today, DayOfWeek.SUNDAY))
        val grid = Dates.monthGrid(java.time.YearMonth.of(2026, 10), DayOfWeek.MONDAY)
        assertEquals(1, grid[0][3]?.dayOfMonth)
    }

    @Test
    fun reminders() {
        val nine = LocalTime.of(9, 0)
        val atDue = Reminder(id = "a", minutesBefore = 0)
        val early = Reminder(id = "b", minutesBefore = 30)
        val fixed = Reminder(id = "c", at = LocalDateTime.of(today.minusDays(1), LocalTime.of(20, 0)))
        val task = Task(title = "x", due = today, time = nine, reminders = listOf(atDue, early, fixed))
        assertEquals(
            listOf("a" to LocalDateTime.of(today, nine), "b" to LocalDateTime.of(today, LocalTime.of(8, 30)), "c" to LocalDateTime.of(today.minusDays(1), LocalTime.of(20, 0))),
            Dates.reminderTimes(task),
        )
        // Without a time, only the fixed one can go off; a finished task has none.
        assertEquals(listOf("c"), Dates.reminderTimes(task.copy(time = null)).map { it.first })
        assertEquals(emptyList<Pair<String, LocalDateTime>>(), Dates.reminderTimes(task.copy(done = true)))
    }
}
