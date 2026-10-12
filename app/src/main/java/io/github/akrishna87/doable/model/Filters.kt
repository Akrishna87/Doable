package io.github.akrishna87.doable.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Todoist's filter language, for saved filters and search.
 *
 * Combine with `&` (and), `|` (or), `!` (not) and brackets; a comma makes separate lists, each
 * shown under its own heading: `today | overdue, #Work & p1`.
 *
 * Terms: `today`, `tomorrow`, `yesterday`, `overdue` (or `od`), `no date`, `no time`, `7 days` (or
 * `next 7 days`), `p1` to `p4`, `no priority`, `#Project`, `##Project` (with its sub-projects),
 * `/Section`, `@label`, `no labels`, `recurring`, `subtask`, `deadline`, `no deadline`,
 * `has: comments`, `search: words`, `due before: <date>`, `due after: <date>`, `date: <date>`,
 * `deadline before: <date>`, `created before: <date>`, `created after: <date>`, `all`, and a
 * date on its own (`friday`, `jan 5`, `2026-12-25`). Names can end in `*` to match the start
 * (`@home*`). Only open tasks match; finished ones are left out.
 */
object Filters {
    class Error(message: String) : Exception(message)

    /** A filter, ready to test tasks. Each part of a comma-separated query is one [Group]. */
    data class Group(val text: String, val test: (Task, Data) -> Boolean)

    /** Reads a query; throws [Error] with a readable message if it doesn't make sense. */
    fun compile(query: String, today: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY, zone: ZoneId = ZoneId.systemDefault()): List<Group> =
        splitTop(query, ',').map { it.trim() }.filter { it.isNotEmpty() }.map { part ->
            val p = Parser(part, today, weekStart, zone)
            val test = p.parseOr()
            p.skipSpace()
            if (!p.atEnd()) throw Error("Didn't understand “${part.substring(p.pos).trim()}”")
            Group(part) { t, d -> !t.done && test(t, d) }
        }.ifEmpty { throw Error("The filter is empty") }

    /** The open tasks matching each part of [query], in order, or an error message. */
    fun run(query: String, data: Data, today: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): Result<List<Pair<String, List<Task>>>> =
        runCatching { compile(query, today, weekStart).map { g -> g.text to data.tasks.filter { g.test(it, data) } } }

    /** Splits at [sep] outside brackets. */
    private fun splitTop(s: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var start = 0
        for ((i, c) in s.withIndex()) {
            when (c) {
                '(' -> depth++
                ')' -> depth--
                sep -> if (depth == 0) {
                    out += s.substring(start, i)
                    start = i + 1
                }
            }
        }
        out += s.substring(start)
        return out
    }

    private class Parser(val s: String, val today: LocalDate, val weekStart: DayOfWeek, val zone: ZoneId) {
        var pos = 0

        fun atEnd() = pos >= s.length
        fun skipSpace() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun parseOr(): (Task, Data) -> Boolean {
            var left = parseAnd()
            while (true) {
                skipSpace()
                if (pos < s.length && s[pos] == '|') {
                    pos++
                    val a = left
                    val b = parseAnd()
                    left = { t, d -> a(t, d) || b(t, d) }
                } else {
                    return left
                }
            }
        }

        fun parseAnd(): (Task, Data) -> Boolean {
            var left = parseUnary()
            while (true) {
                skipSpace()
                if (pos < s.length && s[pos] == '&') {
                    pos++
                    val a = left
                    val b = parseUnary()
                    left = { t, d -> a(t, d) && b(t, d) }
                } else {
                    return left
                }
            }
        }

        fun parseUnary(): (Task, Data) -> Boolean {
            skipSpace()
            if (atEnd()) throw Error("Something is missing at the end")
            if (s[pos] == '!') {
                pos++
                val inner = parseUnary()
                return { t, d -> !inner(t, d) }
            }
            if (s[pos] == '(') {
                pos++
                val inner = parseOr()
                skipSpace()
                if (atEnd() || s[pos] != ')') throw Error("A bracket isn't closed")
                pos++
                return inner
            }
            // A term runs to the next operator or bracket.
            val start = pos
            while (pos < s.length && s[pos] !in "&|()") pos++
            val text = s.substring(start, pos).trim()
            if (text.isEmpty()) throw Error("Something is missing before “${s.getOrNull(pos) ?: ""}”")
            return term(text)
        }

        fun term(raw: String): (Task, Data) -> Boolean {
            val t = raw.lowercase().replace(Regex("\\s+"), " ")
            fun date(text: String): LocalDate {
                val words = text.trim().split(" ").filter { it.isNotEmpty() }
                val relative = Regex("([+-]?\\d+) days?").matchEntire(text.trim())
                if (relative != null) return today.plusDays(relative.groupValues[1].toLong())
                val d = QuickAdd.matchDate(words, 0, today, weekStart)
                if (d == null || d.second != words.size) throw Error("“$text” isn't a date")
                return d.first
            }
            fun created(task: Task): LocalDate = Instant.ofEpochMilli(task.createdAt).atZone(zone).toLocalDate()
            fun nameMatch(pattern: String, name: String): Boolean {
                val p = pattern.trim()
                return if (p.endsWith("*")) name.lowercase().replace(" ", "").startsWith(p.dropLast(1).lowercase().replace(" ", ""))
                else name.replace(" ", "").equals(p.replace(" ", ""), true)
            }
            Regex("(?:next )?(\\d+) days?").matchEntire(t)?.let { m ->
                val n = m.groupValues[1].toLong()
                return { task, _ -> task.due != null && !task.due.isBefore(today) && task.due.isBefore(today.plusDays(n)) }
            }
            Regex("p([1-4])").matchEntire(t)?.let { m ->
                val p = m.groupValues[1].toInt()
                return { task, _ -> task.priority == p }
            }
            when {
                t.startsWith("##") -> {
                    val name = raw.trim().substring(2)
                    return { task, d ->
                        val roots = (listOf(Project.Inbox) + d.projects).filter { nameMatch(name, it.name) }.map { it.id }
                        roots.any { task.projectId in d.projectAndChildren(it) }
                    }
                }
                t.startsWith("#") -> {
                    val name = raw.trim().substring(1)
                    return { task, d -> nameMatch(name, d.project(task.projectId).name) }
                }
                t.startsWith("/") -> {
                    val name = raw.trim().substring(1)
                    return { task, d -> d.section(task.sectionId)?.let { nameMatch(name, it.name) } == true }
                }
                t.startsWith("@") -> {
                    val name = raw.trim().substring(1)
                    return { task, _ -> task.labels.any { nameMatch(name, it) } }
                }
                t.startsWith("search:") -> {
                    val q = raw.substringAfter(':').trim()
                    return { task, _ -> task.title.contains(q, true) || task.description.contains(q, true) || task.comments.any { it.text.contains(q, true) } }
                }
                t.startsWith("due before:") -> date(t.substringAfter(':')).let { d -> return { task, _ -> task.due != null && task.due.isBefore(d) } }
                t.startsWith("due after:") -> date(t.substringAfter(':')).let { d -> return { task, _ -> task.due != null && task.due.isAfter(d) } }
                t.startsWith("date before:") -> date(t.substringAfter(':')).let { d -> return { task, _ -> task.due != null && task.due.isBefore(d) } }
                t.startsWith("date after:") -> date(t.substringAfter(':')).let { d -> return { task, _ -> task.due != null && task.due.isAfter(d) } }
                t.startsWith("date:") || t.startsWith("due:") -> date(t.substringAfter(':')).let { d -> return { task, _ -> task.due == d } }
                t.startsWith("deadline before:") -> date(t.substringAfter(':')).let { d -> return { task, _ -> task.deadline != null && task.deadline.isBefore(d) } }
                t.startsWith("deadline after:") -> date(t.substringAfter(':')).let { d -> return { task, _ -> task.deadline != null && task.deadline.isAfter(d) } }
                t.startsWith("deadline:") -> date(t.substringAfter(':')).let { d -> return { task, _ -> task.deadline == d } }
                t.startsWith("created before:") -> date(t.substringAfter(':')).let { d -> return { task, _ -> created(task).isBefore(d) } }
                t.startsWith("created after:") -> date(t.substringAfter(':')).let { d -> return { task, _ -> created(task).isAfter(d) } }
                t.startsWith("created:") -> date(t.substringAfter(':')).let { d -> return { task, _ -> created(task) == d } }
                t.startsWith("has:") -> {
                    val what = t.substringAfter(':').trim()
                    return when (what.removeSuffix("s")) {
                        "comment" -> { task, _ -> task.comments.isNotEmpty() }
                        "attachment" -> { task, _ -> task.comments.any { it.attachment != null } }
                        "reminder" -> { task, _ -> task.reminders.isNotEmpty() }
                        "description" -> { task, _ -> task.description.isNotBlank() }
                        "deadline" -> { task, _ -> task.deadline != null }
                        "duration" -> { task, _ -> task.duration != null }
                        else -> throw Error("“has: $what” isn't something tasks have")
                    }
                }
            }
            return when (t) {
                "all", "everything" -> { _, _ -> true }
                "today" -> { task, _ -> task.due == today }
                "tomorrow" -> { task, _ -> task.due == today.plusDays(1) }
                "yesterday" -> { task, _ -> task.due == today.minusDays(1) }
                "overdue", "od" -> { task, _ -> task.due != null && task.due.isBefore(today) }
                "no date", "no due date" -> { task, _ -> task.due == null }
                "no time" -> { task, _ -> task.due != null && task.time == null }
                "no priority" -> { task, _ -> task.priority == Priority.NONE }
                "no labels", "no label" -> { task, _ -> task.labels.isEmpty() }
                "recurring" -> { task, _ -> task.repeat != null }
                "subtask", "subtasks" -> { task, _ -> task.parentId != null }
                "deadline" -> { task, _ -> task.deadline != null }
                "no deadline" -> { task, _ -> task.deadline == null }
                "no section" -> { task, _ -> task.sectionId == null }
                "this week" -> {
                    val start = Dates.startOfWeek(today, weekStart)
                    return { task, _ -> task.due != null && !task.due.isBefore(start) && task.due.isBefore(start.plusDays(7)) }
                }
                "next week" -> {
                    val start = Dates.startOfWeek(today, weekStart).plusWeeks(1)
                    return { task, _ -> task.due != null && !task.due.isBefore(start) && task.due.isBefore(start.plusDays(7)) }
                }
                else -> {
                    // A date on its own.
                    val d = runCatching { date(t) }.getOrNull() ?: throw Error("“${raw.trim()}” isn't a filter term")
                    return { task, _ -> task.due == d }
                }
            }
        }
    }
}
