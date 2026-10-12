package io.github.akrishna87.doable

import io.github.akrishna87.doable.model.Activity
import io.github.akrishna87.doable.model.ActivityKind
import io.github.akrishna87.doable.model.Comment
import io.github.akrishna87.doable.model.Csv
import io.github.akrishna87.doable.model.Data
import io.github.akrishna87.doable.model.Freq
import io.github.akrishna87.doable.model.Karma
import io.github.akrishna87.doable.model.Project
import io.github.akrishna87.doable.model.ProjectView
import io.github.akrishna87.doable.model.QuickAdd
import io.github.akrishna87.doable.model.RepeatRule
import io.github.akrishna87.doable.model.Section
import io.github.akrishna87.doable.model.Settings
import io.github.akrishna87.doable.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class MoreTest {
    // A Monday.
    private val today = LocalDate.of(2026, 10, 12)

    @Test
    fun quickAddReadsDeadlinesRemindersSectionsAndDurations() {
        val work = Project(id = "w", name = "Work")
        val doing = Section(id = "s", projectId = "w", name = "Doing")
        val p = QuickAdd.parse("Write report tomorrow 9am {friday} !30min !today 8pm /Doing for 1.5h", today, LocalTime.of(10, 0), listOf(work), sections = listOf(doing))
        assertEquals("Write report", p.title)
        assertEquals(today.plusDays(1), p.due)
        assertEquals(LocalTime.of(9, 0), p.time)
        assertEquals(LocalDate.of(2026, 10, 16), p.deadline)
        assertEquals(listOf(30), p.reminders.filter { it.at == null }.map { it.minutesBefore })
        assertEquals(listOf(LocalDateTime.of(today, LocalTime.of(20, 0))), p.reminders.mapNotNull { it.at })
        assertEquals("s", p.sectionId)
        assertEquals("w", p.projectId)
        assertEquals(90, p.duration)
    }

    @Test
    fun quickAddRichRepeats() {
        fun rule(s: String) = QuickAdd.parse(s, today).repeat
        assertEquals(RepeatRule(Freq.DAILY, 3), rule("Water plants every 3 days"))
        assertEquals(RepeatRule(Freq.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)), rule("Swim every mon, thu"))
        assertEquals(RepeatRule(Freq.DAILY, 3, fromCompletion = true), rule("Water plants every! 3 days"))
        assertEquals(RepeatRule(Freq.MONTHLY, monthDay = 1), rule("Pay rent every 1st"))
        assertEquals(RepeatRule(Freq.WEEKLY, 2), rule("Clean every other week"))
        // The first date fits the rule.
        assertEquals(LocalDate.of(2026, 11, 1), QuickAdd.parse("Pay rent every 1st", today).due)
        assertEquals(LocalDate.of(2026, 10, 15), QuickAdd.parse("Swim every thursday", today).due)
        // Words that aren't repeats stay in the title.
        assertEquals("Read every page", QuickAdd.parse("Read every page", today).title)
        assertNull(QuickAdd.parse("Read every page", today).repeat)
    }

    @Test
    fun karmaGoalsAndStreaks() {
        val zone = ZoneOffset.UTC
        fun at(d: LocalDate) = d.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        // Two completions a day for the last five days (Thu to Mon), with a goal of 2.
        val days = (0..4).map { today.minusDays(it.toLong()) }
        val activity = days.flatMap { d -> listOf(Activity(at(d), ActivityKind.COMPLETED, "x", "x", "inbox"), Activity(at(d) + 1, ActivityKind.COMPLETED, "y", "y", "inbox")) }
        val data = Data(activity = activity, settings = Settings(dailyGoal = 2, weeklyGoal = 100, daysOff = emptySet()))
        val p = Karma.compute(data, today, zone)
        assertEquals(10, p.totalCompleted)
        assertEquals(2, p.completedToday)
        assertEquals(5, p.dailyStreak)
        assertEquals(5, p.longestDailyStreak)
        assertEquals(10 * Karma.PER_TASK + 5 * Karma.DAILY_GOAL_BONUS, p.karma)
        assertEquals("Beginner", p.level)
        assertEquals(7, p.lastDays.size)
        assertEquals(listOf(0, 0, 2, 2, 2, 2, 2), p.lastDays.map { it.second })

        // A missed day breaks the streak, unless it's a day off; today not done yet doesn't break it.
        val missed = data.copy(activity = activity.filter { java.time.Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() != today.minusDays(2) })
        assertEquals(2, Karma.compute(missed, today, zone).dailyStreak)
        val off = missed.copy(settings = missed.settings.copy(daysOff = setOf(today.minusDays(2).dayOfWeek)))
        assertEquals(4, Karma.compute(off, today, zone).dailyStreak)
        val notYet = data.copy(activity = activity.filter { java.time.Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() != today })
        assertEquals(4, Karma.compute(notYet, today, zone).dailyStreak)
    }

    @Test
    fun todoistCsvImport() {
        val csv = "TYPE,CONTENT,DESCRIPTION,PRIORITY,INDENT,AUTHOR,RESPONSIBLE,DATE,DATE_LANG,TIMEZONE,DURATION,DURATION_UNIT,DEADLINE,DEADLINE_LANG\r\n" +
            "meta,view_style=board,,,,,,,,,,,,\r\n" +
            "task,Plan the trip @travel,\"Flights, hotel\",4,1,,,every monday 9am,en,,30,minute,2026-11-01,en\r\n" +
            "task,Book flights,,1,2,,,,,,,,,\r\n" +
            "note,Prefer the morning flight,,,,,,,,,,,,\r\n" +
            ",,,,,,,,,,,,,\r\n" +
            "section,Packing,,,,,,,,,,,,\r\n" +
            "task,Passport,,3,1,,,tomorrow,en,,,,,\r\n"
        val r = Csv.import(csv, "Trip", today)
        assertEquals(ProjectView.BOARD, r.project.view)
        assertEquals(listOf("Packing"), r.sections.map { it.name })
        val plan = r.tasks.first { it.title == "Plan the trip" }
        assertEquals(listOf("travel"), plan.labels)
        assertEquals("Flights, hotel", plan.description)
        assertEquals(1, plan.priority)
        assertEquals(RepeatRule(Freq.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY)), plan.repeat)
        assertEquals(LocalTime.of(9, 0), plan.time)
        assertEquals(30, plan.duration)
        assertEquals(LocalDate.of(2026, 11, 1), plan.deadline)
        val flights = r.tasks.first { it.title == "Book flights" }
        assertEquals(plan.id, flights.parentId)
        assertEquals(listOf("Prefer the morning flight"), flights.comments.map { it.text })
        assertEquals(4, flights.priority)
        val passport = r.tasks.first { it.title == "Passport" }
        assertEquals(r.sections.single().id, passport.sectionId)
        assertEquals(2, passport.priority)
        assertEquals(today.plusDays(1), passport.due)
    }

    @Test
    fun csvExportReadsBack() {
        val p = Project(id = "p", name = "Trip")
        val s = Section(id = "s", projectId = "p", name = "Packing")
        val t = Task(id = "t", title = "Plan", projectId = "p", priority = 2, due = today, time = LocalTime.of(9, 0), repeat = RepeatRule(Freq.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)), labels = listOf("go"), comments = listOf(Comment(text = "a, \"quoted\" note")))
        val sub = Task(id = "u", title = "Sub", projectId = "p", parentId = "t")
        val inSection = Task(id = "v", title = "Socks", projectId = "p", sectionId = "s", deadline = today.plusDays(3))
        val data = Data(projects = listOf(p), sections = listOf(s), tasks = listOf(t, sub, inSection))
        val back = Csv.import(Csv.export(p, data), "Trip", today)
        val plan = back.tasks.first { it.title == "Plan" }
        assertEquals(t.repeat, plan.repeat)
        assertEquals(t.time, plan.time)
        assertEquals(listOf("go"), plan.labels)
        assertEquals(2, plan.priority)
        assertEquals(listOf("a, \"quoted\" note"), plan.comments.map { it.text })
        assertEquals(plan.id, back.tasks.first { it.title == "Sub" }.parentId)
        assertEquals(today.plusDays(3), back.tasks.first { it.title == "Socks" }.deadline)
        assertTrue(back.tasks.first { it.title == "Socks" }.sectionId != null)
    }
}
