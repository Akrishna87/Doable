package io.github.akrishna87.doable.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Projects as CSV in Todoist's template format, so a project can be exported from Todoist and
 * imported here (and the other way round). Sections, tasks, sub-tasks (INDENT), priorities,
 * dates (in words, like "every monday 9am"), durations, deadlines, labels in the task name and
 * comments ("note" rows) come across.
 */
object Csv {
    val HEADER = listOf("TYPE", "CONTENT", "DESCRIPTION", "PRIORITY", "INDENT", "AUTHOR", "RESPONSIBLE", "DATE", "DATE_LANG", "TIMEZONE", "DURATION", "DURATION_UNIT", "DEADLINE", "DEADLINE_LANG")

    /** What an import made: a new project, its sections and its tasks. */
    data class Imported(val project: Project, val sections: List<Section>, val tasks: List<Task>)

    /** Reads a Todoist CSV into a new project called [name]. Throws IllegalArgumentException if it isn't one. */
    fun import(text: String, name: String, today: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): Imported {
        val rows = parse(text).filter { r -> r.any { it.isNotBlank() } }
        require(rows.isNotEmpty()) { "The file is empty" }
        val header = rows.first().map { it.trim().uppercase() }
        require("TYPE" in header && "CONTENT" in header) { "This isn't a Todoist CSV (it needs TYPE and CONTENT columns)" }
        fun col(row: List<String>, name: String) = header.indexOf(name).let { if (it < 0) "" else row.getOrElse(it) { "" } }

        var view = ProjectView.LIST
        val project0 = Project(name = name)
        val sections = mutableListOf<Section>()
        val tasks = mutableListOf<Task>()
        var section: Section? = null
        // The last task at each indent, to hang sub-tasks under.
        val stack = mutableListOf<Task>()
        var order = 0L
        for (row in rows.drop(1)) {
            val type = col(row, "TYPE").trim().lowercase()
            val content = col(row, "CONTENT")
            when (type) {
                "meta" -> if (content.contains("view_style=board")) view = ProjectView.BOARD
                "section" -> {
                    section = Section(projectId = project0.id, name = content.trim(), order = ++order)
                    sections += section!!
                    stack.clear()
                }
                "task" -> {
                    val indent = col(row, "INDENT").trim().toIntOrNull()?.coerceAtLeast(1) ?: 1
                    while (stack.size >= indent) stack.removeAt(stack.lastIndex)
                    val parent = stack.lastOrNull()
                    // Labels are written into the name as @label.
                    val labels = Regex("(?<=^|\\s)@([\\p{L}\\p{N}_\\-./]+)").findAll(content).map { it.groupValues[1] }.toList()
                    val title = content.replace(Regex("(?<=^|\\s)@[\\p{L}\\p{N}_\\-./]+"), "").replace(Regex("\\s+"), " ").trim()
                    val date = col(row, "DATE").trim()
                    val when_ = if (date.isEmpty()) null else QuickAdd.parse(date, today, null, weekStart = weekStart)
                    val csvPriority = col(row, "PRIORITY").trim().toIntOrNull()
                    val durationAmount = col(row, "DURATION").trim().toIntOrNull()
                    val duration = durationAmount?.let { if (col(row, "DURATION_UNIT").trim().lowercase().startsWith("day")) it * 1440 else it }
                    val deadline = col(row, "DEADLINE").trim().takeIf { it.isNotEmpty() }?.let { d ->
                        val words = d.split(Regex("\\s+"))
                        QuickAdd.matchDate(words, 0, today, weekStart)?.first
                    }
                    val t = Task(
                        title = title.ifEmpty { content.trim() },
                        description = col(row, "DESCRIPTION").trim(),
                        projectId = project0.id,
                        sectionId = section?.id,
                        labels = labels,
                        // Todoist's CSV counts priority the other way: 4 is the most urgent.
                        priority = csvPriority?.let { (5 - it).coerceIn(Priority.P1, Priority.NONE) } ?: Priority.NONE,
                        due = when_?.due,
                        time = when_?.time,
                        repeat = when_?.repeat,
                        duration = duration?.takeIf { it > 0 },
                        deadline = deadline,
                        parentId = parent?.id,
                        order = ++order,
                        createdAt = System.currentTimeMillis() + order,
                    )
                    tasks += t
                    stack += t
                }
                "note", "comment" -> {
                    val last = stack.lastOrNull() ?: continue
                    val i = tasks.indexOfFirst { it.id == last.id }
                    val updated = tasks[i].copy(comments = tasks[i].comments + Comment(text = content.trim()))
                    tasks[i] = updated
                    stack[stack.lastIndex] = updated
                }
            }
        }
        return Imported(project0.copy(view = view), sections, tasks)
    }

    /** Writes a project (its sections, open tasks, sub-tasks and comments) as a Todoist CSV. */
    fun export(project: Project, data: Data): String {
        val out = StringBuilder()
        fun row(vararg cells: String) {
            val full = HEADER.indices.map { cells.getOrElse(it) { "" } }
            out.append(full.joinToString(",") { quote(it) }).append("\r\n")
        }
        row(*HEADER.toTypedArray())
        row("meta", "view_style=${if (project.view == ProjectView.BOARD) "board" else "list"}")
        val open = data.tasks.filter { it.projectId == project.id && !it.done }
        fun writeTask(t: Task, indent: Int) {
            val name = (listOf(t.title) + t.labels.map { "@$it" }).joinToString(" ")
            row(
                "task", name, t.description, (5 - t.priority).toString(), indent.toString(), "", "",
                dateText(t), "en", "", t.duration?.toString() ?: "", if (t.duration != null) "minute" else "",
                t.deadline?.toString() ?: "", if (t.deadline != null) "en" else "",
            )
            for (c in t.comments) row("note", c.text)
            for (s in open.filter { it.parentId == t.id }.sortedBy { it.order }) if (indent < 4) writeTask(s, indent + 1)
        }
        for (t in open.filter { it.parentId == null && it.sectionId == null }.sortedBy { it.order }) writeTask(t, 1)
        for (s in data.sectionsOf(project.id)) {
            row()
            row("section", s.name)
            for (t in open.filter { it.parentId == null && it.sectionId == s.id }.sortedBy { it.order }) writeTask(t, 1)
        }
        return out.toString()
    }

    /** A task's date as words Todoist (and Doable) read back: "2026-10-05 09:00", "every monday". */
    fun dateText(t: Task): String {
        val due = t.due ?: return ""
        val time = t.time?.let { " " + DateTimeFormatter.ofPattern("HH:mm").format(it) } ?: ""
        val r = t.repeat ?: return due.toString() + time
        val every = if (r.fromCompletion) "every!" else "every"
        val what = when (r.freq) {
            Freq.DAILY -> if (r.interval == 1) "day" else "${r.interval} days"
            Freq.WEEKLY -> when {
                r.weekdays == RepeatRule.WORKDAYS && r.interval == 1 -> "weekday"
                r.weekdays.isNotEmpty() && r.interval == 1 -> r.weekdays.sortedBy { it.value }.joinToString(", ") { it.name.lowercase() }
                else -> if (r.interval == 1) "week" else "${r.interval} weeks"
            }
            Freq.MONTHLY -> when {
                r.nth != null && r.weekdays.isNotEmpty() -> "${RepeatRule.ordinal(r.nth)} ${r.weekdays.first().name.lowercase()}"
                r.monthDay == 0 -> "last day"
                r.monthDay != null -> RepeatRule.ordinal(r.monthDay)
                else -> if (r.interval == 1) "month" else "${r.interval} months"
            }
            Freq.YEARLY -> if (r.interval == 1) "year" else "${r.interval} years"
        }
        return "$every $what$time"
    }

    fun quote(s: String): String = if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** RFC 4180 CSV: quoted fields can hold commas, quotes ("") and line breaks. */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        val s = text.removePrefix("﻿")
        while (i < s.length) {
            val c = s[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        quoted = false
                    }
                } else {
                    cell.append(c)
                }
            } else {
                when (c) {
                    '"' -> quoted = true
                    ',' -> { row += cell.toString(); cell.clear() }
                    '\r' -> {}
                    '\n' -> { row += cell.toString(); cell.clear(); rows += row; row = mutableListOf() }
                    else -> cell.append(c)
                }
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            rows += row
        }
        return rows
    }
}
