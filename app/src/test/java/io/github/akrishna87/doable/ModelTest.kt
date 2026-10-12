package io.github.akrishna87.doable

import io.github.akrishna87.doable.model.Data
import io.github.akrishna87.doable.model.INBOX_ID
import io.github.akrishna87.doable.model.Project
import io.github.akrishna87.doable.model.Settings
import io.github.akrishna87.doable.model.Task
import io.github.akrishna87.doable.model.ThemeMode
import io.github.akrishna87.doable.model.Accent
import io.github.akrishna87.doable.model.Activity
import io.github.akrishna87.doable.model.ActivityKind
import io.github.akrishna87.doable.model.Attachment
import io.github.akrishna87.doable.model.Comment
import io.github.akrishna87.doable.model.Filter
import io.github.akrishna87.doable.model.Freq
import io.github.akrishna87.doable.model.GroupBy
import io.github.akrishna87.doable.model.LabelInfo
import io.github.akrishna87.doable.model.ProjectView
import io.github.akrishna87.doable.model.Reminder
import io.github.akrishna87.doable.model.RepeatRule
import io.github.akrishna87.doable.model.Section
import io.github.akrishna87.doable.model.SortBy
import io.github.akrishna87.doable.model.SwipeAction
import io.github.akrishna87.doable.model.ViewOptions
import java.time.DayOfWeek
import java.time.LocalDateTime
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ModelTest {
    @Test
    fun everythingSurvivesSavingAndLoading() {
        val work = Project(name = "Work", color = 0xFF299438.toInt(), favorite = true, view = ProjectView.BOARD, options = ViewOptions(SortBy.PRIORITY, true, GroupBy.LABEL, true))
        val client = Project(name = "Client", parentId = work.id, archived = true)
        val doing = Section(projectId = work.id, name = "Doing", order = 2, collapsed = true)
        val parent = Task(
            title = "Launch", projectId = work.id, sectionId = doing.id, priority = 1, due = LocalDate.of(2026, 10, 9), time = LocalTime.of(9, 30),
            repeat = RepeatRule(Freq.WEEKLY, 2, setOf(DayOfWeek.MONDAY)), labels = listOf("deep"), duration = 45, deadline = LocalDate.of(2026, 10, 30),
            reminders = listOf(Reminder(minutesBefore = 30), Reminder(at = LocalDateTime.of(2026, 10, 8, 20, 0))),
            comments = listOf(Comment(text = "See the brief", attachment = Attachment("a/brief.pdf", "brief.pdf", "application/pdf", 1234), createdAt = 5)),
            order = 7,
        )
        val data = Data(
            tasks = listOf(parent, Task(title = "Draft", parentId = parent.id, projectId = work.id, done = true, doneAt = 9)),
            projects = listOf(work, client),
            sections = listOf(doing),
            labelInfo = listOf(LabelInfo("deep", 0xFFD1453B.toInt(), favorite = true)),
            filters = listOf(Filter(name = "Urgent", query = "p1 & today", favorite = true)),
            activity = listOf(Activity(10, ActivityKind.COMPLETED, parent.id, "Launch", work.id)),
            settings = Settings(
                theme = ThemeMode.DARK, accent = Accent.KALE, weekStartsSunday = true, reminders = false, autoReminder = null, askedNotifications = true,
                startPage = "upcoming", dailySummary = LocalTime.of(8, 0), karma = false, dailyGoal = 3, weeklyGoal = 15,
                daysOff = setOf(DayOfWeek.SUNDAY), vacation = true, smartDates = false, swipeRight = SwipeAction.SCHEDULE, swipeLeft = SwipeAction.DELETE,
            ),
        )
        assertEquals(data, Data.fromJson(JSONObject(data.toJson().toString())))
    }

    @Test
    fun doableOneFilesCarryOver() {
        // Doable 1: simple repeats and one "remind me at the due time" switch.
        val v1 = """{"version":1,"tasks":[
            {"id":"a","title":"Gym","due":"2026-10-05","time":"18:00","repeat":"WEEKDAYS"},
            {"id":"b","title":"Quiet","due":"2026-10-05","time":"18:00","reminder":false},
            {"id":"c","title":"No time","due":"2026-10-05"}]}"""
        val d = Data.fromJson(JSONObject(v1))
        assertEquals(RepeatRule.Weekdays, d.task("a")!!.repeat)
        assertEquals(listOf(0), d.task("a")!!.reminders.map { it.minutesBefore })
        assertTrue(d.task("b")!!.reminders.isEmpty())
        assertTrue(d.task("c")!!.reminders.isEmpty())
    }

    @Test
    fun projectTreeAndSections() {
        val a = Project(id = "a", name = "A", order = 1)
        val a1 = Project(id = "a1", name = "A1", parentId = "a", order = 1)
        val a2 = Project(id = "a2", name = "A2", parentId = "a", order = 2, archived = true)
        val b = Project(id = "b", name = "B", order = 2)
        val d = Data(projects = listOf(b, a2, a1, a), sections = listOf(Section(projectId = "a", name = "x", order = 2), Section(projectId = "a", name = "y", order = 1)))
        assertEquals(listOf("a" to 0, "a1" to 1, "b" to 0), d.projectTree().map { it.first.id to it.second })
        assertEquals(listOf("a", "a1", "a2", "b"), d.projectTree(includeArchived = true).map { it.first.id })
        assertEquals(setOf("a", "a1", "a2"), d.projectAndChildren("a"))
        assertEquals(listOf("y", "x"), d.sectionsOf("a").map { it.name })
    }

    @Test
    fun daybookBackupsRestoreTheirTasks() {
        // A Daybook backup has notes too; Doable takes the tasks and projects and leaves the rest.
        val daybook = """
            {"version":2,
             "tasks":[{"id":"t1","title":"Buy milk","projectId":"p1","due":"2026-10-04","noteId":"n1","priority":2}],
             "projects":[{"id":"p1","name":"Home","color":-14575885,"createdAt":5}],
             "notes":[{"id":"n1","title":"Shopping","body":"text"}],
             "notebooks":[{"id":"notebook-default","name":"My notebook"}],
             "settings":{"theme":"DARK","weekStartsSunday":true,"noteSort":"TITLE"}}
        """.trimIndent()
        val d = Data.fromJson(JSONObject(daybook))
        val t = d.tasks.single()
        assertEquals("Buy milk", t.title)
        assertEquals("p1", t.projectId)
        assertEquals(LocalDate.of(2026, 10, 4), t.due)
        assertEquals(2, t.priority)
        assertEquals("Home", d.projects.single().name)
        assertEquals(ThemeMode.DARK, d.settings.theme)
        assertTrue(d.settings.weekStartsSunday)
    }

    @Test
    fun orphansFindAHome() {
        val json = """{"tasks":[{"id":"t","title":"x","projectId":"gone"}]}"""
        val d = Data.fromJson(JSONObject(json))
        assertEquals(INBOX_ID, d.tasks.single().projectId)
    }

    @Test
    fun labelsAreCollected() {
        val d = Data(tasks = listOf(Task(title = "a", labels = listOf("Home", "errands")), Task(title = "b", labels = listOf("home"))))
        assertEquals(listOf("errands", "Home"), d.labels)
    }

    @Test
    fun firstRunTeachesTheBasics() {
        val d = Data.firstRun()
        assertEquals(3, d.tasks.size)
        assertTrue(d.tasks.all { it.projectId == INBOX_ID && it.due == null })
    }
}
