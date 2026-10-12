package io.github.akrishna87.doable

import io.github.akrishna87.doable.model.Data
import io.github.akrishna87.doable.model.INBOX_ID
import io.github.akrishna87.doable.model.Project
import io.github.akrishna87.doable.model.Repeat
import io.github.akrishna87.doable.model.Settings
import io.github.akrishna87.doable.model.Task
import io.github.akrishna87.doable.model.ThemeMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ModelTest {
    @Test
    fun everythingSurvivesSavingAndLoading() {
        val work = Project(name = "Work", color = 0xFF299438.toInt())
        val parent = Task(title = "Launch", projectId = work.id, priority = 1, due = LocalDate.of(2026, 10, 9), time = LocalTime.of(9, 30), repeat = Repeat.WEEKLY, labels = listOf("deep"))
        val data = Data(
            tasks = listOf(parent, Task(title = "Draft", parentId = parent.id, reminder = false, done = true, doneAt = 9)),
            projects = listOf(work),
            settings = Settings(theme = ThemeMode.DARK, weekStartsSunday = true, reminders = false, askedNotifications = true),
        )
        assertEquals(data, Data.fromJson(JSONObject(data.toJson().toString())))
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
