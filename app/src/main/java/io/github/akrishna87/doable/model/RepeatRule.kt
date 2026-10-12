package io.github.akrishna87.doable.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * How a task repeats, as in Todoist: "every 3 days", "every other week on Mon, Wed",
 * "every month on the 15th", "every last day", "every 2nd Tuesday", "every year", and
 * "every! 2 weeks" (counted from when it was completed). It can stop on a date.
 */
data class RepeatRule(
    val freq: Freq,
    val interval: Int = 1,
    /** WEEKLY: the days it falls on (empty means the due date's day). MONTHLY with [nth]: the one weekday. */
    val weekdays: Set<DayOfWeek> = emptySet(),
    /** MONTHLY: the day of the month, 1 to 31, or 0 for the last day. Null keeps the due date's day. */
    val monthDay: Int? = null,
    /** MONTHLY: which [weekdays] of the month, 1 to 4, or -1 for the last ("every 2nd Tue", "every last Fri"). */
    val nth: Int? = null,
    /** Todoist's "every!": the next date is counted from the day it was completed. */
    val fromCompletion: Boolean = false,
    /** The last day it can fall on. */
    val until: LocalDate? = null,
) {
    /**
     * The next date after [due], or null when the repeat has ended. [completedOn] is used for
     * "every!" rules. Never before [today]: an overdue task moves to its next date from today on.
     */
    fun next(due: LocalDate, today: LocalDate, completedOn: LocalDate = today): LocalDate? {
        var d = step(if (fromCompletion) completedOn else due)
        var guard = 0
        while (d.isBefore(today) && guard++ < 5000) d = step(d)
        return if (until != null && d.isAfter(until)) null else d
    }

    /** The first date on or after [from] that fits the rule, for a new task with a repeat but no date. */
    fun firstOnOrAfter(from: LocalDate): LocalDate = when {
        freq == Freq.WEEKLY && weekdays.isNotEmpty() -> (0L..6L).map { from.plusDays(it) }.first { it.dayOfWeek in weekdays }
        freq == Freq.MONTHLY && nth != null && weekdays.isNotEmpty() -> {
            val here = nthWeekday(from.withDayOfMonth(1), nth, weekdays.first())
            if (here.isBefore(from)) nthWeekday(from.plusMonths(1).withDayOfMonth(1), nth, weekdays.first()) else here
        }
        freq == Freq.MONTHLY && monthDay != null -> {
            val here = dayInMonth(from, monthDay)
            if (here.isBefore(from)) dayInMonth(from.plusMonths(1), monthDay) else here
        }
        else -> from
    }

    private fun step(d: LocalDate): LocalDate = when (freq) {
        Freq.DAILY -> d.plusDays(interval.toLong())
        Freq.WEEKLY -> if (weekdays.isEmpty()) d.plusWeeks(interval.toLong()) else nextWeekday(d)
        Freq.MONTHLY -> {
            val month = d.withDayOfMonth(1).plusMonths(interval.toLong())
            when {
                nth != null && weekdays.isNotEmpty() -> nthWeekday(month, nth, weekdays.first())
                monthDay != null -> dayInMonth(month, monthDay)
                else -> d.plusMonths(interval.toLong())
            }
        }
        Freq.YEARLY -> d.plusYears(interval.toLong())
    }

    /** The next listed weekday after [d], skipping weeks that aren't in the cycle ("every other week"). */
    private fun nextWeekday(d: LocalDate): LocalDate {
        val anchor = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        var c = d.plusDays(1)
        repeat(7 * interval + 7) {
            val week = ChronoUnit.WEEKS.between(anchor, c.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
            if (week % interval == 0L && c.dayOfWeek in weekdays) return c
            c = c.plusDays(1)
        }
        return d.plusWeeks(interval.toLong())
    }

    /** One line for the task: "Every 2 weeks on Mon, Wed", "Every month on the 2nd Tue", "Every 3 days after completion". */
    fun label(locale: Locale = Locale.getDefault()): String {
        val n = interval
        fun unit(one: String) = if (n == 1) one else if (n == 2 && one != "day") "other $one" else "$n ${one}s"
        val days = weekdays.sortedBy { it.value }
        val base = when (freq) {
            Freq.DAILY -> if (n == 1) "Every day" else "Every $n days"
            Freq.WEEKLY -> when {
                days == WORKDAYS.sortedBy { it.value } && n == 1 -> "Every weekday"
                days == WEEKEND.sortedBy { it.value } && n == 1 -> "Every weekend"
                days.isEmpty() -> "Every ${unit("week")}"
                else -> "Every ${unit("week")} on ${days.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }}"
                    .replace("Every week on", "Every")
            }
            Freq.MONTHLY -> when {
                nth != null && days.isNotEmpty() ->
                    "Every ${unit("month")} on the ${ordinal(nth)} ${days.first().getDisplayName(TextStyle.SHORT, locale)}"
                monthDay == 0 -> "Every ${unit("month")} on the last day"
                monthDay != null -> "Every ${unit("month")} on the ${ordinal(monthDay)}"
                else -> "Every ${unit("month")}"
            }
            Freq.YEARLY -> "Every ${unit("year")}"
        }
        return base +
            (if (fromCompletion) " after completion" else "") +
            (until?.let { " until ${java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(it)}" } ?: "")
    }

    fun toJson(): JSONObject = JSONObject()
        .put("freq", freq.name)
        .put("interval", interval)
        .put("weekdays", JSONArray(weekdays.sortedBy { it.value }.map { it.name }))
        .put("monthDay", monthDay ?: -100)
        .put("nth", nth ?: -100)
        .put("fromCompletion", fromCompletion)
        .put("until", until?.toString() ?: "")

    companion object {
        val WORKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        val Daily = RepeatRule(Freq.DAILY)
        val Weekdays = RepeatRule(Freq.WEEKLY, weekdays = WORKDAYS)
        val Weekly = RepeatRule(Freq.WEEKLY)
        val Monthly = RepeatRule(Freq.MONTHLY)
        val Yearly = RepeatRule(Freq.YEARLY)

        /** The one-tap choices in menus. */
        val presets = listOf(Daily, Weekdays, Weekly, Monthly, Yearly)

        fun fromJson(o: JSONObject?): RepeatRule? {
            if (o == null) return null
            val freq = runCatching { Freq.valueOf(o.optString("freq")) }.getOrNull() ?: return null
            val days = o.optJSONArray("weekdays")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
                .mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.toSet()
            return RepeatRule(
                freq = freq,
                interval = o.optInt("interval", 1).coerceIn(1, 999),
                weekdays = days,
                monthDay = o.optInt("monthDay", -100).takeIf { it in 0..31 },
                nth = o.optInt("nth", -100).takeIf { it == -1 || it in 1..5 },
                fromCompletion = o.optBoolean("fromCompletion"),
                until = o.optString("until").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            )
        }

        /** Reads Doable 1's simple repeats. */
        fun fromLegacy(name: String): RepeatRule? = when (name) {
            "DAILY" -> Daily
            "WEEKDAYS" -> Weekdays
            "WEEKLY" -> Weekly
            "MONTHLY" -> Monthly
            "YEARLY" -> Yearly
            else -> null
        }

        fun ordinal(n: Int): String = when {
            n == -1 -> "last"
            n % 100 in 11..13 -> "${n}th"
            n % 10 == 1 -> "${n}st"
            n % 10 == 2 -> "${n}nd"
            n % 10 == 3 -> "${n}rd"
            else -> "${n}th"
        }

        /** The [nth] (or last, -1) [day] in the month of [month]. */
        fun nthWeekday(month: LocalDate, nth: Int, day: DayOfWeek): LocalDate =
            if (nth == -1) month.with(TemporalAdjusters.lastInMonth(day))
            else month.with(TemporalAdjusters.dayOfWeekInMonth(nth.coerceIn(1, 5), day)).let {
                // A 5th weekday that doesn't exist falls back to the last one.
                if (it.month != month.month) month.with(TemporalAdjusters.lastInMonth(day)) else it
            }

        /** Day [day] of [month]'s month, kept inside the month; 0 is the last day. */
        fun dayInMonth(month: LocalDate, day: Int): LocalDate {
            val len = month.lengthOfMonth()
            return month.withDayOfMonth(if (day == 0) len else day.coerceAtMost(len))
        }

        private val ordinals = mapOf("first" to 1, "1st" to 1, "second" to 2, "2nd" to 2, "third" to 3, "3rd" to 3, "fourth" to 4, "4th" to 4, "fifth" to 5, "5th" to 5, "last" to -1)
        private val numberWords = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "other" to 2)
        private val dayNames = mapOf(
            "mon" to DayOfWeek.MONDAY, "monday" to DayOfWeek.MONDAY,
            "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "tuesday" to DayOfWeek.TUESDAY,
            "wed" to DayOfWeek.WEDNESDAY, "weds" to DayOfWeek.WEDNESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
            "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "thursday" to DayOfWeek.THURSDAY,
            "fri" to DayOfWeek.FRIDAY, "friday" to DayOfWeek.FRIDAY,
            "sat" to DayOfWeek.SATURDAY, "saturday" to DayOfWeek.SATURDAY,
            "sun" to DayOfWeek.SUNDAY, "sunday" to DayOfWeek.SUNDAY,
        )

        /** A weekday's name or short name, also in the plural ("mondays"). */
        fun weekday(word: String): DayOfWeek? {
            val w = word.lowercase().trimEnd(',', '.', ';')
            return dayNames[w] ?: dayNames[w.removeSuffix("s")]
        }

        /**
         * Reads the words after "every" (or "every!"): "day", "3 days", "other week", "weekday",
         * "mon, wed", "monday and friday", "15th", "last day", "2nd tue", "month", "year".
         * Returns the rule and how many words it used, or null.
         */
        fun parseAfterEvery(words: List<String>, fromCompletion: Boolean): Pair<RepeatRule, Int>? {
            fun w(i: Int) = words.getOrNull(i)?.lowercase()?.trimEnd(',', '.', ';')
            var i = 0
            var n = 1
            val first = w(0) ?: return null
            val count = first.toIntOrNull() ?: numberWords[first]
            if (count != null && count in 1..999) {
                n = count
                i = 1
            }
            val unit = w(i) ?: return null
            fun rule(r: RepeatRule, used: Int) = r.copy(fromCompletion = fromCompletion) to used
            when (unit.removeSuffix("s")) {
                "day" -> return rule(RepeatRule(Freq.DAILY, n), i + 1)
                "week" -> return rule(RepeatRule(Freq.WEEKLY, n), i + 1)
                "month" -> return rule(RepeatRule(Freq.MONTHLY, n), i + 1)
                "year" -> return rule(RepeatRule(Freq.YEARLY, n), i + 1)
                "weekday", "workday" -> if (i == 0) return rule(Weekdays, 1)
                "weekend" -> if (i == 0) return rule(RepeatRule(Freq.WEEKLY, weekdays = WEEKEND), 1)
            }
            if (i == 0) {
                // "last day", "15th", "2nd tue", "last friday"
                if (unit == "last" && w(1) == "day") return rule(RepeatRule(Freq.MONTHLY, monthDay = 0), 2)
                val ord = ordinals[unit]
                val wd = w(1)?.let { weekday(it) }
                if (ord != null && wd != null) return rule(RepeatRule(Freq.MONTHLY, weekdays = setOf(wd), nth = ord), 2)
                Regex("(\\d{1,2})(st|nd|rd|th)").matchEntire(unit)?.let { m ->
                    val day = m.groupValues[1].toInt()
                    if (day in 1..31) return rule(RepeatRule(Freq.MONTHLY, monthDay = day), 1)
                }
                // A list of weekdays: "mon, wed", "monday and friday", "tue & thu".
                val days = mutableSetOf<DayOfWeek>()
                var j = 0
                while (true) {
                    val word = w(j) ?: break
                    val d = weekday(word)
                    if (d != null) {
                        days += d
                        j++
                    } else if ((word == "and" || word == "&") && days.isNotEmpty() && w(j + 1)?.let { weekday(it) } != null) {
                        j++
                    } else {
                        break
                    }
                }
                if (days.isNotEmpty()) return rule(RepeatRule(Freq.WEEKLY, weekdays = days), j)
            } else if (count != null) {
                // "every 2 mon" isn't a thing, but "every 2 weeks" was handled above.
                return null
            }
            return null
        }
    }
}
