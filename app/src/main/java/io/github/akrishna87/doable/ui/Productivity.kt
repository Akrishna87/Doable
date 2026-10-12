package io.github.akrishna87.doable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.akrishna87.doable.model.ActivityKind
import io.github.akrishna87.doable.model.Data
import io.github.akrishna87.doable.model.Dates
import io.github.akrishna87.doable.model.Karma
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Karma, today's and this week's goals, streaks, and the last days and weeks as bars. */
@Composable
fun ProductivityPage(data: Data) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    val s = data.settings
    val p = remember(data.activity, data.tasks, s, today) { Karma.compute(data, today) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar("Productivity", onBack = { nav.pop() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            if (s.vacation) {
                Text("Vacation mode is on: goals and streaks are paused.", Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.primary)
            }
            // Karma and level.
            Text("${p.karma}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { contentDescription = "Karma ${p.karma}" })
            Text("Karma · ${p.level}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            p.nextLevelAt?.let { next ->
                val from = Karma.levels.last { p.karma >= it.first }.first
                LinearProgressIndicator(
                    progress = { (p.karma - from).toFloat() / (next - from).coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    drawStopIndicator = {},
                )
                Text("${next - p.karma} to ${Karma.levels.first { it.first == next }.second}", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Goals.
            Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                GoalRing("Today", p.completedToday, s.dailyGoal)
                GoalRing("This week", p.completedThisWeek, s.weeklyGoal)
            }

            // Streaks.
            HorizontalDivider(Modifier.padding(vertical = 20.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("Daily streak", "${p.dailyStreak} days", "Longest: ${p.longestDailyStreak}", Modifier.weight(1f))
                Stat("Weekly streak", "${p.weeklyStreak} weeks", "Longest: ${p.longestWeeklyStreak}", Modifier.weight(1f))
            }
            Stat("Completed in all", "${p.totalCompleted} tasks", null, Modifier.padding(top = 16.dp))

            // The last seven days, then the last four weeks.
            HorizontalDivider(Modifier.padding(vertical = 20.dp))
            Text("Last 7 days", style = MaterialTheme.typography.titleSmall)
            Bars(p.lastDays.map { it.first.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) to it.second }, s.dailyGoal)
            Text("Last 4 weeks", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleSmall)
            Bars(p.lastWeeks.map { DateTimeFormatter.ofPattern("d MMM").format(it.first) to it.second }, s.weeklyGoal)
            Text(
                "You earn ${Karma.PER_TASK} karma for each task you complete, ${Karma.DAILY_GOAL_BONUS} for each day you reach your daily goal, " +
                    "and ${Karma.WEEKLY_GOAL_BONUS} for each week you reach your weekly goal. Set goals and days off in Settings.",
                Modifier.padding(vertical = 24.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GoalRing(label: String, done: Int, goal: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $done of $goal" }) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { (done.toFloat() / goal.coerceAtLeast(1)).coerceAtMost(1f) },
                modifier = Modifier.size(96.dp),
                strokeWidth = 8.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = if (done >= goal) LocalExtra.current.complete else MaterialTheme.colorScheme.primary,
            )
            Text("$done/$goal", style = MaterialTheme.typography.titleMedium)
        }
        Text(label, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Stat(label: String, value: String, detail: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Simple bars, with a line where the goal is. */
@Composable
private fun Bars(values: List<Pair<String, Int>>, goal: Int) {
    val max = (values.maxOfOrNull { it.second } ?: 0).coerceAtLeast(goal).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(140.dp).padding(top = 12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((label, n) in values) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "$label: $n" }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(n.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier.fillMaxWidth().height((90f * n / max).dp.coerceAtLeast(2.dp)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (n >= goal) LocalExtra.current.complete else MaterialTheme.colorScheme.primary),
                )
                Text(label, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

/** Everything that happened, newest first: tasks added, completed, brought back, commented on and deleted. */
@Composable
fun ActivityPage(data: Data) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    val zone = ZoneId.systemDefault()
    val byDay = remember(data.activity) {
        data.activity.reversed().take(1000).groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
    }
    val is24 = LocalClock.current.is24Hour
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar("Activity", onBack = { nav.pop() })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (byDay.isEmpty()) item { EmptyState(Icons.Rounded.History, "No activity yet", "What you add, complete and change shows up here.") }
            for ((day, list) in byDay) {
                item(key = "h-$day") { SectionHeader(Dates.dayHeading(day, today)) }
                items(list, key = { "${it.at}-${it.taskId}-${it.kind}" }) { a ->
                    val time = Dates.timeFormatter(is24).format(Instant.ofEpochMilli(a.at).atZone(zone).toLocalTime())
                    val exists = data.task(a.taskId) != null
                    ListRow(
                        "You ${a.kind.verb} “${a.title}”",
                        detail = "$time · ${data.project(a.projectId).name}",
                        leading = {
                            Box(
                                Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(
                                    when (a.kind) {
                                        ActivityKind.COMPLETED -> LocalExtra.current.complete
                                        ActivityKind.DELETED -> LocalExtra.current.delete
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                ),
                            )
                        },
                        onClick = if (exists) ({ nav.push(Page.TaskPage(a.taskId)) }) else null,
                    )
                }
            }
        }
    }
}
