package io.github.akrishna87.doable.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** How productive you've been, as in Todoist's Karma: points, a level, goals and streaks. */
data class Productivity(
    val karma: Int,
    val level: String,
    /** Karma needed for the next level, or null at the top. */
    val nextLevelAt: Int?,
    val completedToday: Int,
    val completedThisWeek: Int,
    val dailyStreak: Int,
    val longestDailyStreak: Int,
    val weeklyStreak: Int,
    val longestWeeklyStreak: Int,
    /** The last seven days, oldest first, with how many tasks were completed. */
    val lastDays: List<Pair<LocalDate, Int>>,
    /** The last four weeks (by their first day), oldest first. */
    val lastWeeks: List<Pair<LocalDate, Int>>,
    val totalCompleted: Int,
)

object Karma {
    const val PER_TASK = 5
    const val DAILY_GOAL_BONUS = 25
    const val WEEKLY_GOAL_BONUS = 100

    val levels = listOf(
        0 to "Beginner", 500 to "Novice", 2500 to "Intermediate", 5000 to "Professional",
        7500 to "Expert", 10000 to "Master", 20000 to "Grandmaster", 50000 to "Enlightened",
    )

    /** How many tasks were completed each day, from the activity log (and finished tasks it doesn't cover). */
    fun completionsByDay(data: Data, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, Int> {
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val counts = HashMap<LocalDate, Int>()
        val logged = HashSet<String>()
        for (a in data.activity) {
            when (a.kind) {
                ActivityKind.COMPLETED -> {
                    counts.merge(day(a.at), 1, Int::plus)
                    logged += a.taskId
                }
                ActivityKind.UNCOMPLETED -> counts.merge(day(a.at), -1, Int::plus)
                else -> {}
            }
        }
        for (t in data.tasks) if (t.done && t.doneAt > 0 && t.id !in logged) counts.merge(day(t.doneAt), 1, Int::plus)
        return counts.filterValues { it > 0 }
    }

    fun compute(data: Data, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Productivity {
        val s = data.settings
        val ws = Dates.weekStart(s)
        val byDay = completionsByDay(data, zone)
        fun on(d: LocalDate) = byDay[d] ?: 0
        fun weekOf(d: LocalDate) = Dates.startOfWeek(d, ws)
        val byWeek = byDay.entries.groupBy({ weekOf(it.key) }, { it.value }).mapValues { it.value.sum() }

        val goalDays = byDay.count { (d, n) -> n >= s.dailyGoal && d.dayOfWeek !in s.daysOff }
        val goalWeeks = byWeek.count { it.value >= s.weeklyGoal }
        val total = byDay.values.sum()
        val karma = total * PER_TASK + goalDays * DAILY_GOAL_BONUS + goalWeeks * WEEKLY_GOAL_BONUS
        val level = levels.last { karma >= it.first }
        val next = levels.firstOrNull { it.first > karma }?.first

        val first = byDay.keys.minOrNull() ?: today
        val daily = streaks(first, today, step = 1, skip = { it.dayOfWeek in s.daysOff }, met = { on(it) >= s.dailyGoal }, vacation = s.vacation)
        val weekly = streaks(weekOf(first), weekOf(today), step = 7, skip = { false }, met = { (byWeek[it] ?: 0) >= s.weeklyGoal }, vacation = s.vacation)

        return Productivity(
            karma = karma,
            level = level.second,
            nextLevelAt = next,
            completedToday = on(today),
            completedThisWeek = byWeek[weekOf(today)] ?: 0,
            dailyStreak = daily.first,
            longestDailyStreak = daily.second,
            weeklyStreak = weekly.first,
            longestWeeklyStreak = weekly.second,
            lastDays = (6 downTo 0).map { today.minusDays(it.toLong()) }.map { it to on(it) },
            lastWeeks = (3 downTo 0).map { weekOf(today).minusWeeks(it.toLong()) }.map { it to (byWeek[it] ?: 0) },
            totalCompleted = total,
        )
    }

    /**
     * The current and the longest run of periods (days or weeks, [step] days apart) that met the
     * goal, from [from] to [to]. Skipped periods (days off) neither count nor break a run. The
     * current period doesn't break the run while it's still going; in vacation mode nothing breaks it.
     */
    fun streaks(from: LocalDate, to: LocalDate, step: Long, skip: (LocalDate) -> Boolean, met: (LocalDate) -> Boolean, vacation: Boolean): Pair<Int, Int> {
        var run = 0
        var longest = 0
        var d = from
        val periods = ChronoUnit.DAYS.between(from, to) / step
        if (periods > 3660) return 0 to 0
        while (!d.isAfter(to)) {
            when {
                skip(d) -> {}
                met(d) -> {
                    run++
                    longest = maxOf(longest, run)
                }
                d == to || vacation -> {}
                else -> run = 0
            }
            d = d.plusDays(step)
        }
        return run to longest
    }

    /** True on a day off (or on vacation): goals and streaks take the day off too. */
    fun isOff(day: LocalDate, settings: Settings): Boolean = settings.vacation || day.dayOfWeek in settings.daysOff

    val weekdaysInOrder: List<DayOfWeek> = DayOfWeek.entries
}
