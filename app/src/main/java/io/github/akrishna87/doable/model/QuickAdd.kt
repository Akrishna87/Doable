package io.github.akrishna87.doable.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** What Quick Add understood in a line of text. Fields are null (or empty) when the text didn't mention them. */
data class ParsedTask(
    val title: String,
    val due: LocalDate? = null,
    val time: LocalTime? = null,
    val priority: Int? = null,
    val projectId: String? = null,
    val sectionId: String? = null,
    val labels: List<String> = emptyList(),
    val repeat: RepeatRule? = null,
    val deadline: LocalDate? = null,
    val duration: Int? = null,
    val reminders: List<Reminder> = emptyList(),
    /** The character ranges that were recognised, so the text field can highlight them. */
    val matches: List<IntRange> = emptyList(),
)

/**
 * Reads a task typed in plain words, like Todoist's Quick Add:
 * "Pay rent every 1st 9am p1 #Home /Bills @money {jan 5} !1day for 15min".
 *
 * Dates: today, tomorrow (tmr), weekdays (monday or mon), next week, next friday, this weekend,
 * in 3 days, in 2 weeks, 5 jan, jan 5th, 2026-10-05. Times: 9am, 9:30pm, 21:00, at 5, noon, tonight.
 * Priority: p1 to p4. Project: #Name. Section: /Name. Labels: @anything. Deadline: {date}.
 * Reminders: !30min, !2h, !1day (before the due time), !8am, !tomorrow 8am (at a set time).
 * Duration: for 30min, for 1h, for 1.5 hours. Repeats: daily, every day, every 3 days, every weekday,
 * every other week, every mon, wed, every 15th, every last day, every 2nd tue, every year, and
 * every! 2 weeks (counted from completion).
 */
object QuickAdd {
    private val months = mapOf(
        "jan" to 1, "january" to 1, "feb" to 2, "february" to 2, "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4, "may" to 5, "jun" to 6, "june" to 6, "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8, "sep" to 9, "sept" to 9, "september" to 9, "oct" to 10, "october" to 10,
        "nov" to 11, "november" to 11, "dec" to 12, "december" to 12,
    )

    // "sat", "sun" and "tom" are left out on purpose: they're everyday words and names.
    private val weekdays = mapOf(
        "mon" to DayOfWeek.MONDAY, "monday" to DayOfWeek.MONDAY,
        "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "tuesday" to DayOfWeek.TUESDAY,
        "wed" to DayOfWeek.WEDNESDAY, "weds" to DayOfWeek.WEDNESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "thursday" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY, "friday" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sunday" to DayOfWeek.SUNDAY,
    )

    private val clock = Regex("(\\d{1,2})(?::(\\d{2}))?(am|pm)?")
    private val dayNumber = Regex("(\\d{1,2})(st|nd|rd|th)?")
    private val span = Regex("(\\d+(?:\\.\\d+)?)\\s*(m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days)?")

    private class Tok(val raw: String, val word: String, val range: IntRange)

    fun parse(
        input: String,
        today: LocalDate,
        now: LocalTime? = null,
        projects: List<Project> = emptyList(),
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
        sections: List<Section> = emptyList(),
    ): ParsedTask {
        val toks = Regex("\\S+").findAll(input).map {
            val raw = it.value.trimEnd(',', '.', ';')
            Tok(raw, raw.lowercase(), it.range)
        }.toList()
        val used = BooleanArray(toks.size)
        val words = toks.map { it.word }
        fun word(i: Int): String? = words.getOrNull(i)
        fun take(from: Int, count: Int) {
            for (k in from until (from + count).coerceAtMost(toks.size)) used[k] = true
        }

        var due: LocalDate? = null
        var time: LocalTime? = null
        var priority: Int? = null
        var projectId: String? = null
        var sectionName: String? = null
        val labels = mutableListOf<String>()
        var repeat: RepeatRule? = null
        var deadline: LocalDate? = null
        var duration: Int? = null
        val reminders = mutableListOf<Reminder>()
        // Absolute reminders without a date wait for the due date to be known.
        val pendingAt = mutableListOf<Pair<LocalDate?, LocalTime>>()

        var i = 0
        while (i < toks.size) {
            val w = toks[i].word
            val raw = toks[i].raw
            var n = 0

            if (priority == null && w.length == 2 && w[0] == 'p' && w[1] in '1'..'4') {
                priority = w[1].digitToInt()
                n = 1
            } else if (projectId == null && raw.length > 1 && raw.startsWith("#")) {
                val name = raw.substring(1)
                val p = projects.firstOrNull { it.name.replace(" ", "").equals(name, true) }
                if (p != null) {
                    projectId = p.id
                    n = 1
                }
            } else if (sectionName == null && raw.length > 1 && raw.startsWith("/") && sections.any { it.name.replace(" ", "").equals(raw.substring(1), true) }) {
                sectionName = raw.substring(1)
                n = 1
            } else if (raw.length > 1 && raw.startsWith("@")) {
                val label = raw.substring(1)
                if (labels.none { it.equals(label, true) }) labels += label
                n = 1
            } else if (deadline == null && raw.startsWith("{")) {
                // "{jan 5}" or "{ next friday }": up to the closing brace.
                val end = (i until toks.size).firstOrNull { toks[it].raw.endsWith("}") }
                if (end != null) {
                    val inner = words.subList(i, end + 1).joinToString(" ").removePrefix("{").removeSuffix("}").trim()
                        .split(Regex("\\s+")).filter { it.isNotEmpty() }
                    val d = matchDate(inner, 0, today, weekStart)
                    if (d != null && d.second == inner.size) {
                        deadline = d.first
                        n = end - i + 1
                    }
                }
            } else if (raw.length > 1 && raw.startsWith("!") && !raw.startsWith("!!")) {
                // "!30min", "!2h", "!1day" before the due time; "!8am" or "!tomorrow 8am" at a set time.
                val rest = listOf(w.substring(1)) + words.drop(i + 1)
                val before = minutesIn(rest[0], rest.getOrNull(1))
                if (before != null && !rest[0].endsWith("am") && !rest[0].endsWith("pm") && !rest[0].contains(':')) {
                    reminders += Reminder(minutesBefore = before.first)
                    n = before.second
                } else {
                    val d = matchDate(rest, 0, today, weekStart)
                    val tIndex = d?.second ?: 0
                    val t = clockTime(rest.getOrNull(tIndex) ?: "", rest.getOrNull(tIndex + 1), bare = true)
                    if (t != null) {
                        pendingAt += d?.first to t.first
                        n = tIndex + t.second
                    } else if (d != null) {
                        pendingAt += d.first to LocalTime.of(9, 0)
                        n = d.second
                    }
                }
            } else if (duration == null && w == "for") {
                val m = minutesIn(word(i + 1) ?: "", word(i + 2))
                if (m != null && m.first in 1..(24 * 60)) {
                    duration = m.first
                    n = 1 + m.second
                }
            }

            if (n == 0 && repeat == null) {
                val single = when (w) {
                    "daily", "everyday" -> RepeatRule.Daily
                    "weekly" -> RepeatRule.Weekly
                    "biweekly", "fortnightly" -> RepeatRule(Freq.WEEKLY, 2)
                    "monthly" -> RepeatRule.Monthly
                    "yearly", "annually" -> RepeatRule.Yearly
                    else -> null
                }
                if (single != null) {
                    repeat = single
                    n = 1
                } else if (w == "every" || w == "every!" || w == "each") {
                    val r = RepeatRule.parseAfterEvery(words.drop(i + 1), fromCompletion = w == "every!")
                    if (r != null) {
                        repeat = r.first
                        n = 1 + r.second
                    }
                }
            }

            if (n == 0 && due == null) {
                val d = matchDate(words, i, today, weekStart)
                if (d != null) {
                    due = d.first
                    n = d.second
                    if (w == "tonight" && time == null) time = LocalTime.of(19, 0)
                }
            }

            if (n == 0 && time == null) {
                val next = word(i + 1)
                when {
                    w == "noon" || w == "midday" -> { time = LocalTime.NOON; n = 1 }
                    w == "at" && next != null -> {
                        val t = clockTime(next, word(i + 2), bare = true)
                        if (t != null) {
                            time = t.first
                            n = 1 + t.second
                        }
                    }
                    else -> {
                        val t = clockTime(w, next, bare = false)
                        if (t != null) {
                            time = t.first
                            n = t.second
                        }
                    }
                }
            }

            if (n > 0) {
                take(i, n)
                i += n
            } else {
                i++
            }
        }

        if (time != null && due == null) {
            due = if (now != null && !time.isAfter(now)) today.plusDays(1) else today
        }
        val r = repeat
        if (r != null && due == null) due = r.firstOnOrAfter(today)
        for ((d, t) in pendingAt) reminders += Reminder(at = LocalDateTime.of(d ?: due ?: today, t))

        // A section in the chosen project, or (with no #project) in any project, which then becomes the project.
        val section = sectionName?.let { name ->
            sections.filter { it.name.replace(" ", "").equals(name, true) }
                .let { found -> found.firstOrNull { it.projectId == projectId } ?: if (projectId == null) found.firstOrNull() else null }
        }
        if (section != null && projectId == null) projectId = section.projectId

        return ParsedTask(
            title = toks.filterIndexed { k, _ -> !used[k] }.joinToString(" ") { input.substring(it.range) }.trim(),
            due = due,
            time = time,
            priority = priority,
            projectId = projectId,
            sectionId = section?.id,
            labels = labels,
            repeat = repeat,
            deadline = deadline,
            duration = duration,
            reminders = reminders,
            matches = toks.filterIndexed { k, _ -> used[k] }.map { it.range },
        )
    }

    /**
     * A date starting at word [i]: today, tomorrow, yesterday, a weekday, next week, next friday,
     * this weekend, in 3 days, 2026-10-05, jan 5 (2027), 5 jan. Returns it and how many words it used.
     */
    fun matchDate(words: List<String>, i: Int, today: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): Pair<LocalDate, Int>? {
        fun word(k: Int) = words.getOrNull(k)?.lowercase()?.trimEnd(',', '.', ';')
        val w = word(i) ?: return null
        val next = word(i + 1)
        return when {
            w == "today" || w == "tod" || w == "tonight" -> today to 1
            w == "tomorrow" || w == "tmr" || w == "tmrw" -> today.plusDays(1) to 1
            w == "yesterday" -> today.minusDays(1) to 1
            weekdays.containsKey(w) -> onOrAfter(today, weekdays.getValue(w)) to 1
            w == "next" && next == "week" -> Dates.startOfWeek(today, weekStart).plusWeeks(1) to 2
            w == "next" && next == "month" -> today.plusMonths(1).withDayOfMonth(1) to 2
            w == "next" && next == "year" -> today.plusYears(1).withDayOfYear(1) to 2
            w == "next" && next != null && weekdays.containsKey(next) ->
                onOrAfter(Dates.startOfWeek(today, weekStart).plusWeeks(1), weekdays.getValue(next)) to 2
            w == "weekend" -> onOrAfter(today, DayOfWeek.SATURDAY) to 1
            w == "this" && next == "weekend" -> onOrAfter(today, DayOfWeek.SATURDAY) to 2
            w == "in" && next != null -> {
                val amount = if (next == "a" || next == "an") 1 else next.toIntOrNull()
                val unit = word(i + 2)?.removeSuffix("s")
                if (amount != null && amount in 1..999) {
                    when (unit) {
                        "day" -> today.plusDays(amount.toLong()) to 3
                        "week" -> today.plusWeeks(amount.toLong()) to 3
                        "month" -> today.plusMonths(amount.toLong()) to 3
                        "year" -> today.plusYears(amount.toLong()) to 3
                        else -> null
                    }
                } else {
                    null
                }
            }
            Regex("\\d{4}-\\d{2}-\\d{2}").matches(w) -> runCatching { LocalDate.parse(w) }.getOrNull()?.let { it to 1 }
            months.containsKey(w) && next != null && dayNumber.matches(next) ->
                monthDay(months.getValue(w), dayNumber.matchEntire(next)!!.groupValues[1].toInt(), word(i + 2), today)?.let { it.first to 2 + it.second }
            dayNumber.matches(w) && next != null && months.containsKey(next) ->
                monthDay(months.getValue(next), dayNumber.matchEntire(w)!!.groupValues[1].toInt(), word(i + 2), today)?.let { it.first to 2 + it.second }
            else -> null
        }
    }

    /** "30min", "2h", "1.5 hours", "1day" (or "30 min" over two words) as minutes, and how many words it used. */
    fun minutesIn(w: String, next: String?): Pair<Int, Int>? {
        val one = span.matchEntire(w)
        val (m, words) = when {
            one != null && one.groupValues[2].isNotEmpty() -> one to 1
            one != null && next != null && Regex("m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days").matches(next) ->
                span.matchEntire(w + next)!! to 2
            else -> return null
        }
        val amount = m.groupValues[1].toDoubleOrNull() ?: return null
        val minutes = when (m.groupValues[2].first()) {
            'm' -> amount
            'h' -> amount * 60
            'd' -> amount * 1440
            else -> return null
        }
        return if (minutes >= 1 && minutes <= 60 * 24 * 30) minutes.toInt() to words else null
    }

    /** The first [day] on or after [from]. */
    fun onOrAfter(from: LocalDate, day: DayOfWeek): LocalDate = from.plusDays(((day.value - from.dayOfWeek.value + 7) % 7).toLong())

    /** A month and day, with an optional year after them. Without a year, a date already past means next year. */
    private fun monthDay(month: Int, day: Int, maybeYear: String?, today: LocalDate): Pair<LocalDate, Int>? {
        val year = maybeYear?.takeIf { it.length == 4 }?.toIntOrNull()
        val date = runCatching { LocalDate.of(year ?: today.year, month, day) }.getOrNull() ?: return null
        if (year != null) return date to 1
        return (if (date.isBefore(today)) date.plusYears(1) else date) to 0
    }

    /**
     * A time: "9am", "9:30pm", "21:00", or "9 am" over two words. With [bare] (after "at") a plain
     * hour counts too; 1 to 7 are taken as the afternoon. Returns the time and how many words it used.
     */
    fun clockTime(w: String, next: String?, bare: Boolean): Pair<LocalTime, Int>? {
        val m = clock.matchEntire(w) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
        var suffix = m.groupValues[3]
        var words = 1
        if (suffix.isEmpty() && (next == "am" || next == "pm")) {
            suffix = next
            words = 2
        }
        val hasMinutes = m.groupValues[2].isNotEmpty()
        when {
            suffix.isNotEmpty() -> {
                if (hour !in 1..12) return null
                hour = when {
                    suffix == "am" && hour == 12 -> 0
                    suffix == "pm" && hour != 12 -> hour + 12
                    else -> hour
                }
            }
            hasMinutes -> if (hour > 23) return null
            bare -> {
                if (hour > 23) return null
                if (hour in 1..7) hour += 12
            }
            else -> return null
        }
        if (minute > 59) return null
        return LocalTime.of(hour, minute) to words
    }
}
