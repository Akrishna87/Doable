package io.github.akrishna87.doable

import io.github.akrishna87.doable.model.Comment
import io.github.akrishna87.doable.model.Data
import io.github.akrishna87.doable.model.Filters
import io.github.akrishna87.doable.model.Project
import io.github.akrishna87.doable.model.RepeatRule
import io.github.akrishna87.doable.model.Section
import io.github.akrishna87.doable.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class FiltersTest {
    // A Monday.
    private val today = LocalDate.of(2026, 10, 12)
    private val work = Project(id = "w", name = "Work")
    private val client = Project(id = "c", name = "Client A", parentId = "w")
    private val home = Project(id = "h", name = "Home")
    private val doing = Section(id = "s", projectId = "w", name = "Doing")
    private val data = Data(
        projects = listOf(work, client, home),
        sections = listOf(doing),
        tasks = listOf(
            Task(id = "1", title = "Report", projectId = "w", sectionId = "s", priority = 1, due = today, labels = listOf("deep")),
            Task(id = "2", title = "Call client", projectId = "c", due = today.minusDays(2), time = LocalTime.of(9, 0)),
            Task(id = "3", title = "Groceries", projectId = "h", due = today.plusDays(3), labels = listOf("errands", "home-run")),
            Task(id = "4", title = "Someday", priority = 4, comments = listOf(Comment(text = "maybe the blue one"))),
            Task(id = "5", title = "Gym", due = today.plusDays(10), repeat = RepeatRule.Weekly, deadline = today.plusDays(20)),
            Task(id = "6", title = "Done one", due = today, done = true),
            Task(id = "7", title = "Step", parentId = "1", projectId = "w", due = today.plusDays(1)),
        ),
    )

    private fun ids(q: String) = Filters.run(q, data, today).getOrThrow().flatMap { g -> g.second.map { it.id } }.sorted()

    @Test
    fun dates() {
        assertEquals(listOf("1"), ids("today"))
        assertEquals(listOf("2"), ids("overdue"))
        assertEquals(listOf("1", "2"), ids("today | overdue"))
        assertEquals(listOf("4"), ids("no date"))
        assertEquals(listOf("1", "3", "7"), ids("7 days"))
        assertEquals(listOf("7"), ids("tomorrow"))
        assertEquals(listOf("3"), ids("due after: tomorrow & due before: next week"))
        assertEquals(listOf("3"), ids("thursday"))
    }

    @Test
    fun projectsSectionsLabelsAndPriorities() {
        assertEquals(listOf("1", "7"), ids("#Work"))
        assertEquals(listOf("1", "2", "7"), ids("##Work"))
        assertEquals(listOf("1"), ids("/Doing"))
        assertEquals(listOf("1"), ids("@deep"))
        assertEquals(listOf("3"), ids("@home*"))
        assertEquals(listOf("1"), ids("p1"))
        assertEquals(listOf("1"), ids("#Work & p1"))
        assertEquals(listOf("2", "3", "4", "5", "7"), ids("!p1"))
        assertEquals(listOf("7"), ids("subtask"))
        assertEquals(listOf("5"), ids("recurring"))
        assertEquals(listOf("5"), ids("deadline"))
    }

    @Test
    fun searchAndBracketsAndLists() {
        assertEquals(listOf("4"), ids("search: blue"))
        assertEquals(listOf("4"), ids("has: comments"))
        assertEquals(listOf("2", "3"), ids("(#Home | ##Client A) & !today"))
        val groups = Filters.run("today, #Home", data, today).getOrThrow()
        assertEquals(listOf("today", "#Home"), groups.map { it.first })
        assertEquals(listOf(listOf("1"), listOf("3")), groups.map { g -> g.second.map { it.id } })
    }

    @Test
    fun mistakesAreExplained() {
        assertTrue(Filters.run("today &", data, today).isFailure)
        assertTrue(Filters.run("(today", data, today).isFailure)
        assertEquals("“purple” isn't a filter term", Filters.run("purple", data, today).exceptionOrNull()?.message)
    }
}
