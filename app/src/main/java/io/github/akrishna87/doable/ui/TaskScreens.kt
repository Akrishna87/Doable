package io.github.akrishna87.doable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.doable.Store
import io.github.akrishna87.doable.model.Csv
import io.github.akrishna87.doable.model.Data
import io.github.akrishna87.doable.model.Dates
import io.github.akrishna87.doable.model.Filters
import io.github.akrishna87.doable.model.GroupBy
import io.github.akrishna87.doable.model.INBOX_ID
import io.github.akrishna87.doable.model.Project
import io.github.akrishna87.doable.model.ProjectColors
import io.github.akrishna87.doable.model.ProjectView
import io.github.akrishna87.doable.model.Section
import io.github.akrishna87.doable.model.SortBy
import io.github.akrishna87.doable.model.Task
import io.github.akrishna87.doable.model.ViewOptions
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Overdue tasks, then today's, with how much of the day's list is done. */
@Composable
fun TodayScreen(data: Data, padding: PaddingValues) {
    val nav = LocalNav.current
    val actions = LocalActions.current
    val extra = LocalExtra.current
    val today = LocalClock.current.today
    val options = data.settings.todayOptions
    var viewing by remember { mutableStateOf(false) }
    val overdue = Dates.sorted(Dates.overdue(data.tasks, today), options)
    val due = Dates.sorted(Dates.dueOn(data.tasks, today), options)
    val completed = Dates.completedOn(data.tasks, today)
    val doneToday = completed.size

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
    ) {
        item(key = "header") {
            ScreenHeader("Today", DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()).format(today)) {
                SearchAction()
                CircleAction(Icons.Rounded.Tune, "View options") { viewing = true }
            }
        }
        val total = doneToday + due.size + overdue.size
        if (total > 0) {
            item(key = "progress") { DayProgress(data, doneToday, total, due + overdue, today) }
        }
        if (overdue.isNotEmpty()) {
            item(key = "overdue") {
                SectionHeader("Overdue", color = extra.overdue) {
                    TextButton(onClick = {
                        val before = overdue.toList()
                        Store.rescheduleOverdue(today)
                        actions.offerUndo(if (before.size == 1) "Moved 1 task to today" else "Moved ${before.size} tasks to today") { Store.restoreTasks(before) }
                    }) { Text("Reschedule") }
                }
            }
            cardItems(overdue, key = { it.id }) { t, seg ->
                SwipeTask(t, Modifier.animateItem().then(seg)) { TaskRow(t, data) { nav.push(Page.TaskPage(t.id)) } }
            }
            item(key = "today-header") { SectionHeader(Dates.dayHeading(today, today)) }
        }
        for ((heading, list) in Dates.grouped(due, options.copy(group = if (options.group == GroupBy.DATE) GroupBy.NONE else options.group), data, today)) {
            if (heading.isNotEmpty()) item(key = "g-$heading") { SectionHeader(heading) }
            cardItems(list, key = { "$heading|${it.id}" }) { t, seg ->
                SwipeTask(t, Modifier.animateItem().then(seg)) { TaskRow(t, data, showDue = t.time != null, timeOnly = true) { nav.push(Page.TaskPage(t.id)) } }
            }
        }
        if (overdue.isEmpty() && due.isEmpty()) {
            item(key = "empty") {
                if (doneToday > 0) {
                    EmptyState(
                        Icons.Rounded.TaskAlt,
                        "All done for today",
                        if (doneToday == 1) "You completed 1 task today. Nice work." else "You completed $doneToday tasks today. Nice work.",
                    )
                } else {
                    EmptyState(Icons.Rounded.EventAvailable, "All clear", "Nothing is due today. Tap + to add a task, or plan ahead in Upcoming.")
                }
            }
        }
        if (options.showCompleted && completed.isNotEmpty()) {
            item(key = "done-h") { SectionHeader("Completed today · ${completed.size}") }
            cardItems(completed, key = { "done-" + it.id }) { t, seg -> TaskRow(t, data, seg) { nav.push(Page.TaskPage(t.id)) } }
        }
    }
    if (viewing) {
        ViewOptionsDialog(
            options,
            onChange = { o -> Store.updateSettings { it.copy(todayOptions = o) } },
            onDismiss = { viewing = false },
            groups = listOf(GroupBy.NONE, GroupBy.PRIORITY, GroupBy.PROJECT, GroupBy.LABEL),
        )
    }
}

/** Today at a glance: a ring for what's done, what's left and how long it should take, and your streak. */
@Composable
private fun DayProgress(data: Data, done: Int, total: Int, open: List<Task>, today: LocalDate) {
    val left = total - done
    val minutes = open.sumOf { it.duration ?: 0 }
    val karma = if (data.settings.karma) remember(data.activity, data.tasks, today) { io.github.akrishna87.doable.model.Karma.compute(data, today) } else null
    val accent = MaterialTheme.colorScheme.primary
    val on = MaterialTheme.colorScheme.onPrimary
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .softShadow(RoundedCornerShape(24.dp), 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(accent)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressRing(if (total == 0) 0f else done / total.toFloat(), 64.dp, 7.dp, on, on.copy(alpha = 0.25f)) {
            Text("$done/$total", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = on)
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                when {
                    left == 0 -> "Everything's done"
                    left == 1 -> "1 task left today"
                    else -> "$left tasks left today"
                },
                style = MaterialTheme.typography.titleMedium,
                color = on,
            )
            Text(
                if (minutes > 0) "About ${Dates.durationLabel(minutes)} planned" else if (done >= data.settings.dailyGoal) "Daily goal met" else "Goal: ${data.settings.dailyGoal} a day",
                style = MaterialTheme.typography.bodySmall,
                color = on.copy(alpha = 0.9f),
            )
            if (karma != null) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (karma.dailyStreak > 0) {
                        Row(
                            Modifier.clip(CircleShape).background(on.copy(alpha = 0.18f)).padding(horizontal = 9.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(13.dp), tint = on)
                            Text(" ${karma.dailyStreak}-day streak", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = on)
                        }
                    }
                    Text(
                        karma.level,
                        Modifier.clip(CircleShape).background(on.copy(alpha = 0.18f)).padding(horizontal = 9.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = on,
                    )
                }
            }
        }
    }
}

private sealed interface UpRow {
    val key: String

    data object Overdue : UpRow {
        override val key = "overdue"
    }

    data class Day(val day: LocalDate) : UpRow {
        override val key = "day-$day"
    }

    data class Item(val task: Task) : UpRow {
        override val key = "task-${task.id}"
    }
}

/** The next four weeks day by day, with a week strip to jump around; or a month grid. */
@Composable
fun UpcomingScreen(data: Data, padding: PaddingValues) {
    val nav = LocalNav.current
    val extra = LocalExtra.current
    val today = LocalClock.current.today
    val ws = Dates.weekStart(data.settings)
    val firstWeek = Dates.startOfWeek(today, ws)
    var weekStart by remember(ws, today) { mutableStateOf(firstWeek) }
    var selected by remember(today) { mutableStateOf(today) }
    var scrollTo by remember { mutableStateOf<LocalDate?>(null) }
    var picking by remember { mutableStateOf(false) }
    var monthView by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val overdue = Dates.overdue(data.tasks, today)
    val rows: List<UpRow> = buildList {
        if (overdue.isNotEmpty() && weekStart == firstWeek) {
            add(UpRow.Overdue)
            overdue.forEach { add(UpRow.Item(it)) }
        }
        for (k in 0 until 28) {
            val d = weekStart.plusDays(k.toLong())
            if (d.isBefore(today)) continue
            add(UpRow.Day(d))
            Dates.dueOn(data.tasks, d).forEach { add(UpRow.Item(it)) }
        }
    }
    // Where each task sits in its day's card.
    val segments = remember(rows) {
        val out = HashMap<String, Pair<Int, Int>>()
        var run = ArrayList<UpRow.Item>()
        fun flush() { run.forEachIndexed { i, r -> out[r.key] = i to run.size }; run = ArrayList() }
        for (r in rows) if (r is UpRow.Item) run.add(r) else flush()
        flush()
        out
    }
    fun jump(day: LocalDate) {
        val d = if (day.isBefore(today)) today else day
        selected = d
        weekStart = Dates.startOfWeek(d, ws)
        scrollTo = d
    }
    LaunchedEffect(scrollTo, rows.size) {
        val target = scrollTo ?: return@LaunchedEffect
        val i = rows.indexOfFirst { it is UpRow.Day && it.day == target }
        if (i >= 0) listState.animateScrollToItem(i)
        scrollTo = null
    }

    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        ScreenHeader(
            "Upcoming",
            selected.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()) + " " + selected.year,
        ) {
            if (!monthView && weekStart != firstWeek) TextButton(onClick = { jump(today) }) { Text("Today") }
            CircleAction(if (monthView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.CalendarMonth, if (monthView) "Show as list" else "Show as month") { monthView = !monthView }
            if (!monthView) CircleAction(Icons.Rounded.EventAvailable, "Pick a date") { picking = true }
        }
        if (monthView) {
            TaskMonth(data.tasks.filter { !it.done }, data, padding.calculateBottomPadding())
            return@Column
        }
        WeekStrip(weekStart, selected, today, data, ws, onWeek = { weekStart = it }, onDay = { jump(it) })
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 88.dp),
        ) {
            items(rows, key = { it.key }) { row ->
                when (row) {
                    UpRow.Overdue -> SectionHeader("Overdue", color = extra.overdue)
                    is UpRow.Day -> DayHeader(row.day, today) { nav.quickAdd = QuickAddDefaults(due = row.day) }
                    is UpRow.Item -> SwipeTask(row.task, Modifier.animateItem().cardSegment(segments[row.key]?.first ?: 0, segments[row.key]?.second ?: 1)) {
                        val overdueRow = row.task.due?.isBefore(today) == true
                        TaskRow(row.task, data, showDue = row.task.time != null || overdueRow, timeOnly = !overdueRow) {
                            nav.push(Page.TaskPage(row.task.id))
                        }
                    }
                }
            }
        }
    }
    if (picking) DatePickDialog(selected, onPick = { jump(it) }, onDismiss = { picking = false })
}

/** A month grid with a dot for each day's tasks, and the picked day's tasks under it. */
@Composable
fun TaskMonth(tasks: List<Task>, data: Data, bottom: androidx.compose.ui.unit.Dp, defaults: QuickAddDefaults = QuickAddDefaults()) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    val ws = Dates.weekStart(data.settings)
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    val grid = remember(month, ws) { Dates.monthGrid(month, ws) }
    val byDay = remember(tasks) { tasks.filter { it.due != null }.groupBy { it.due!! } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }, Modifier.semantics { contentDescription = "Previous month" }) { Icon(Icons.Rounded.ChevronLeft, null) }
            Text(
                DateTimeFormatter.ofPattern(if (month.year == today.year) "MMMM" else "MMMM yyyy", Locale.getDefault()).format(month),
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = { month = month.plusMonths(1) }, Modifier.semantics { contentDescription = "Next month" }) { Icon(Icons.Rounded.ChevronRight, null) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            for (l in Dates.weekdayLetters(ws)) Text(l, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (week in grid) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                for (d in week) {
                    Column(
                        Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(10.dp))
                            .then(if (d != null) Modifier.clickable { selected = d } else Modifier),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (d != null) {
                            val isSel = d == selected
                            Box(
                                Modifier.padding(top = 4.dp).size(28.dp).clip(CircleShape)
                                    .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    d.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (d == today || isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isSel -> MaterialTheme.colorScheme.onPrimary
                                        d == today -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                            val n = byDay[d]?.size ?: 0
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                repeat(n.coerceAtMost(3)) { Box(Modifier.padding(top = 2.dp).size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) }
                            }
                        }
                    }
                }
            }
        }
        val list = Dates.sorted(byDay[selected].orEmpty(), ViewOptions(sort = SortBy.DATE))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = bottom + 88.dp)) {
            item { DayHeader(selected, today) { nav.quickAdd = defaults.copy(due = selected) } }
            if (list.isEmpty()) item { Text("Nothing due", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            cardItems(list, key = { it.id }) { t, seg -> SwipeTask(t, seg) { TaskRow(t, data, showDue = t.time != null, timeOnly = true) { nav.push(Page.TaskPage(t.id)) } } }
        }
    }
}

@Composable
private fun WeekStrip(
    weekStart: LocalDate,
    selected: LocalDate,
    today: LocalDate,
    data: Data,
    ws: java.time.DayOfWeek,
    onWeek: (LocalDate) -> Unit,
    onDay: (LocalDate) -> Unit,
) {
    val busy = remember(data.tasks, weekStart) {
        (0 until 7).map { weekStart.plusDays(it.toLong()) }.filter { d -> data.tasks.any { !it.done && it.due == d } }.toSet()
    }
    val firstWeek = Dates.startOfWeek(today, ws)
    val stripShape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).softShadow(stripShape, 3.dp).clip(stripShape).background(LocalKit.current.card).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onWeek(weekStart.minusWeeks(1)) },
            enabled = weekStart.isAfter(firstWeek),
            modifier = Modifier.semantics { contentDescription = "Previous week" },
        ) { Icon(Icons.Rounded.ChevronLeft, null) }
        Row(Modifier.weight(1f)) {
            for (k in 0 until 7) {
                val d = weekStart.plusDays(k.toLong())
                val isSelected = d == selected
                val past = d.isBefore(today)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable(enabled = !past) { onDay(d) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val fg = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        past -> MaterialTheme.colorScheme.outline
                        d == today -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.labelSmall, color = fg)
                    Text(
                        d.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (d == today || isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = fg,
                    )
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(if (d in busy) (if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary) else Color.Transparent),
                    )
                }
            }
        }
        IconButton(
            onClick = { onWeek(weekStart.plusWeeks(1)) },
            modifier = Modifier.semantics { contentDescription = "Next week" },
        ) { Icon(Icons.Rounded.ChevronRight, null) }
    }
}

@Composable
private fun DayHeader(day: LocalDate, today: LocalDate, onAdd: () -> Unit) {
    val heading = Dates.dayHeading(day, today)
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                heading,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = onAdd, Modifier.semantics { contentDescription = "Add task for $heading" }) {
                Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** A project's (or the Inbox's) tasks, as a list with sections, a board, or a calendar. */
@Composable
fun ProjectPage(id: String, data: Data) {
    val nav = LocalNav.current
    val context = LocalContext.current
    val actions = LocalActions.current
    val project = if (id == INBOX_ID) Project.Inbox.copy(options = data.settings.inboxOptions, view = data.settings.inboxView) else data.projects.firstOrNull { it.id == id }
    if (project == null) {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf(false) }
    var addingSection by remember { mutableStateOf(false) }
    var addingSub by remember { mutableStateOf(false) }
    var reorder by remember { mutableStateOf(false) }
    val options = project.options
    val sections = data.sectionsOf(id)
    val inProject = data.tasks.filter { it.projectId == id }
    val open = inProject.filter { !it.done }
    val done = inProject.filter { it.done && it.parentId == null }.sortedByDescending { it.doneAt }
    fun saveOptions(o: ViewOptions) = if (id == INBOX_ID) Store.updateSettings { it.copy(inboxOptions = o) } else Store.saveProject(project.copy(options = o))
    fun saveView(v: ProjectView) = if (id == INBOX_ID) Store.updateSettings { it.copy(inboxView = v) } else Store.saveProject(project.copy(view = v))
    val exporter = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(Csv.export(project, Store.data.value).toByteArray()) }
            }.isSuccess
            actions.message(if (ok) "Template saved" else "Couldn't save the file")
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                PageBar(
                    onBack = { nav.pop() },
                    leading = {
                        if (id == INBOX_ID) Icon(Icons.Rounded.Inbox, null, tint = Color(ProjectColors.Inbox)) else ColorDot(project.color, 12.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(project.name, Modifier.weight(10f, fill = false), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    actions = {
                        if (reorder) {
                            TextButton(onClick = { reorder = false }) { Text("Done") }
                        } else {
                            IconButton(onClick = { viewing = true }, Modifier.semantics { contentDescription = "View options" }) { Icon(Icons.Rounded.Tune, null) }
                        }
                        Box {
                            IconButton(onClick = { menu = true }, Modifier.semantics { contentDescription = "Project options" }) { Icon(Icons.Rounded.MoreVert, null) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (id != INBOX_ID) DropdownMenuItem(text = { Text("Edit project") }, onClick = { menu = false; editing = true })
                                DropdownMenuItem(text = { Text("Add section") }, onClick = { menu = false; addingSection = true })
                                if (id != INBOX_ID) DropdownMenuItem(text = { Text("Add sub-project") }, onClick = { menu = false; addingSub = true })
                                if (project.view == ProjectView.LIST) {
                                    DropdownMenuItem(text = { Text("Reorder tasks") }, onClick = {
                                        menu = false
                                        saveOptions(options.copy(sort = SortBy.MANUAL, group = GroupBy.NONE))
                                        reorder = true
                                    })
                                }
                                if (id != INBOX_ID) {
                                    DropdownMenuItem(text = { Text(if (project.favorite) "Remove from favourites" else "Add to favourites") }, onClick = {
                                        menu = false
                                        Store.saveProject(project.copy(favorite = !project.favorite))
                                    })
                                }
                                DropdownMenuItem(text = { Text("Export as a template (CSV)") }, onClick = {
                                    menu = false
                                    exporter.launch(project.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "") + ".csv")
                                })
                                if (id != INBOX_ID) {
                                    DropdownMenuItem(text = { Text("Archive") }, onClick = {
                                        menu = false
                                        Store.archiveProject(id, true)
                                        nav.pop()
                                        actions.offerUndo("${project.name} archived") { Store.archiveProject(id, false) }
                                    })
                                    DropdownMenuItem(text = { Text("Delete project", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleting = true })
                                }
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = { if (!reorder) Fab("Add task") { nav.quickAdd = QuickAddDefaults(projectId = id) } },
    ) { padding ->
        when (project.view) {
            ProjectView.BOARD -> Board(project, sections, open, data, padding)
            ProjectView.CALENDAR -> Box(Modifier.padding(top = padding.calculateTopPadding())) {
                TaskMonth(open, data, padding.calculateBottomPadding(), QuickAddDefaults(projectId = id))
            }
            ProjectView.LIST -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
            ) {
                val topLevel = open.filter { it.parentId == null }
                if (open.isEmpty() && sections.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            Icons.Rounded.TaskAlt,
                            if (id == INBOX_ID) "Your Inbox is empty" else "No tasks yet",
                            if (id == INBOX_ID) "Tasks without a project land here. Tap + to add one." else "Tap + to add the first task to ${project.name}.",
                        )
                    }
                }
                val bySection = options.sort == SortBy.MANUAL || options.group == GroupBy.NONE
                if (bySection && options.group == GroupBy.NONE) {
                    val loose = Dates.sorted(topLevel.filter { it.sectionId == null }, options)
                    taskTree(loose, open, data, reorder)
                    for (s in sections) {
                        val inSection = Dates.sorted(topLevel.filter { it.sectionId == s.id }, options)
                        item(key = "s-${s.id}") { SectionRow(s, inSection.size, data, reorder) }
                        if (!s.collapsed) taskTree(inSection, open, data, reorder)
                    }
                    item(key = "add-section") {
                        Row(
                            Modifier.fillMaxWidth().clickable { addingSection = true }.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                            Text("Add section", Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else {
                    for ((heading, list) in Dates.grouped(Dates.sorted(topLevel, options), options, data, LocalDate.now())) {
                        if (heading.isNotEmpty()) item(key = "g-$heading") { SectionHeader(heading) }
                        taskTree(list, open, data, false, heading)
                    }
                }
                if (options.showCompleted && done.isNotEmpty()) {
                    item(key = "done-h") { SectionHeader("Completed · ${done.size}") }
                    cardItems(done, key = { "done-" + it.id }) { t, seg -> TaskRow(t, data, seg, showProject = false) { nav.push(Page.TaskPage(t.id)) } }
                }
            }
        }
    }
    if (viewing) {
        ViewOptionsDialog(options, onChange = { saveOptions(it) }, onDismiss = { viewing = false }, view = project.view, onView = { saveView(it) },
            groups = listOf(GroupBy.NONE, GroupBy.DATE, GroupBy.PRIORITY, GroupBy.LABEL))
    }
    if (addingSection) {
        NameDialog(title = "Add section", confirm = "Add", onSave = { name, _ -> Store.saveSection(Section(projectId = id, name = name)) }, onDismiss = { addingSection = false })
    }
    if (addingSub) {
        NameDialog(
            title = "Add sub-project", confirm = "Add", colors = ProjectColors.choices, initialColor = project.color,
            onSave = { name, color -> Store.saveProject(Project(name = name, color = color, parentId = id)) }, onDismiss = { addingSub = false },
        )
    }
    if (editing) {
        NameDialog(
            title = "Edit project",
            initial = project.name,
            colors = ProjectColors.choices,
            initialColor = project.color,
            favorite = project.favorite,
            onSave = { name, color -> Store.saveProject(Store.data.value.projects.first { it.id == id }.copy(name = name, color = color)) },
            onFavorite = { fav -> Store.saveProject(Store.data.value.projects.first { it.id == id }.copy(favorite = fav)) },
            onDismiss = { editing = false },
        )
    }
    if (deleting) {
        val subs = data.projectAndChildren(id).size - 1
        ConfirmDialog(
            title = "Delete project?",
            text = "This deletes “${project.name}”" + (if (subs > 0) ", its $subs sub-projects" else "") + " and all their tasks. It can't be undone. To keep them, archive the project instead.",
            confirm = "Delete",
            onConfirm = {
                Store.deleteProject(project.id)
                nav.pop()
            },
            onDismiss = { deleting = false },
        )
    }
}

/** Top-level tasks, each followed by its sub-tasks (indented, open ones only), with move arrows in reorder mode. */
/** [group] keeps keys apart when a task shows in more than one group (grouped by label). */
private fun LazyListScope.taskTree(list: List<Task>, open: List<Task>, data: Data, reorder: Boolean, group: String = "") {
    // Each task followed by its sub-tasks, all in one card.
    val rows = list.withIndex().flatMap { (i, t) ->
        listOf(Triple(t, false, i)) + if (reorder) emptyList() else open.filter { it.parentId == t.id }.sortedWith(compareBy({ it.order }, { it.createdAt })).map { Triple(it, true, i) }
    }
    rows.forEachIndexed { j, (t, sub, i) ->
        item(key = "t-$group-${t.id}") {
            val nav = LocalNav.current
            val seg = Modifier.cardSegment(j, rows.size, inset = if (sub) 76.dp else 52.dp)
            when {
                reorder -> Box(seg) {
                    ReorderRow(t, data, canUp = i > 0, canDown = i < list.lastIndex,
                        onUp = { Store.placeTask(t.id, list[i - 1].id, list) },
                        onDown = { Store.placeTask(t.id, list.getOrNull(i + 2)?.id, list) })
                }
                sub -> SwipeTask(t, Modifier.animateItem().then(seg)) { TaskRow(t, data, Modifier.padding(start = 24.dp), showProject = false) { nav.push(Page.TaskPage(t.id)) } }
                else -> SwipeTask(t, Modifier.animateItem().then(seg)) { TaskRow(t, data, showProject = false) { nav.push(Page.TaskPage(t.id)) } }
            }
        }
    }
}

@Composable
private fun ReorderRow(t: Task, data: Data, canUp: Boolean, canDown: Boolean, onUp: () -> Unit, onDown: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { TaskRow(t, data, showProject = false) {} }
        IconButton(onClick = onUp, enabled = canUp, modifier = Modifier.semantics { contentDescription = "Move ${t.title} up" }) { Icon(Icons.Rounded.KeyboardArrowUp, null) }
        IconButton(onClick = onDown, enabled = canDown, modifier = Modifier.semantics { contentDescription = "Move ${t.title} down" }) { Icon(Icons.Rounded.KeyboardArrowDown, null) }
    }
}

/** A section's heading: collapse, its count, and a menu to add, rename, move or delete. */
@Composable
private fun SectionRow(s: Section, count: Int, data: Data, reorder: Boolean) {
    val nav = LocalNav.current
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { Store.saveSection(s.copy(collapsed = !s.collapsed)) }.padding(start = 14.dp, end = 6.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (s.collapsed) Icons.Rounded.ChevronRight else Icons.Rounded.ExpandMore, if (s.collapsed) "Expand ${s.name}" else "Collapse ${s.name}")
            Text(s.name, Modifier.weight(1f).padding(start = 6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text(count.toString(), Modifier.padding(end = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (reorder) {
                IconButton(onClick = { Store.moveSection(s.id, -1) }, Modifier.semantics { contentDescription = "Move section ${s.name} up" }) { Icon(Icons.Rounded.ExpandLess, null) }
                IconButton(onClick = { Store.moveSection(s.id, 1) }, Modifier.semantics { contentDescription = "Move section ${s.name} down" }) { Icon(Icons.Rounded.ExpandMore, null) }
            } else {
                Box {
                    IconButton(onClick = { menu = true }, Modifier.semantics { contentDescription = "Section ${s.name} options" }) { Icon(Icons.Rounded.MoreVert, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Add task") }, onClick = { menu = false; nav.quickAdd = QuickAddDefaults(projectId = s.projectId, sectionId = s.id) })
                        DropdownMenuItem(text = { Text("Rename section") }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; Store.moveSection(s.id, -1) })
                        DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; Store.moveSection(s.id, 1) })
                        DropdownMenuItem(text = { Text("Archive section") }, onClick = { menu = false; Store.saveSection(s.copy(archived = true)) })
                        DropdownMenuItem(text = { Text("Delete section", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleting = true })
                    }
                }
            }
        }
    }
    if (renaming) NameDialog(title = "Rename section", initial = s.name, onSave = { name, _ -> Store.saveSection(s.copy(name = name)) }, onDismiss = { renaming = false })
    if (deleting) {
        val n = data.tasks.count { it.sectionId == s.id }
        ConfirmDialog("Delete section?", "“${s.name}” and its $n tasks are deleted.", "Delete", onConfirm = { Store.deleteSection(s.id) }, onDismiss = { deleting = false })
    }
}

/** Board view: a column per section (and one for tasks without a section), side by side. */
@Composable
private fun Board(project: Project, sections: List<Section>, open: List<Task>, data: Data, padding: PaddingValues) {
    val topLevel = open.filter { it.parentId == null }
    val loose = Dates.sorted(topLevel.filter { it.sectionId == null }, project.options)
    var addingSection by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxSize().padding(top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding()).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
    ) {
        if (loose.isNotEmpty() || sections.isEmpty()) {
            BoardColumn("(No section)", null, project.id, loose, open, data)
        }
        for (s in sections) BoardColumn(s.name, s, project.id, Dates.sorted(topLevel.filter { it.sectionId == s.id }, project.options), open, data)
        Box(Modifier.width(220.dp).padding(8.dp)) {
            TextButton(onClick = { addingSection = true }) {
                Icon(Icons.Rounded.Add, null)
                Text("Add section", Modifier.padding(start = 8.dp))
            }
        }
    }
    if (addingSection) {
        NameDialog(title = "Add section", confirm = "Add", onSave = { name, _ -> Store.saveSection(Section(projectId = project.id, name = name)) }, onDismiss = { addingSection = false })
    }
}

@Composable
private fun BoardColumn(title: String, section: Section?, projectId: String, tasks: List<Task>, open: List<Task>, data: Data) {
    val nav = LocalNav.current
    Column(
        Modifier
            .width(290.dp)
            .fillMaxHeight()
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(LocalKit.current.chip),
    ) {
        if (section != null) {
            SectionRow(section, tasks.size, data, reorder = false)
        } else {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(tasks.size.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (section?.collapsed != true) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                for (t in tasks) {
                    Surface(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).softShadow(RoundedCornerShape(16.dp), 2.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = LocalKit.current.card,
                    ) {
                        val subs = open.count { it.parentId == t.id }
                        Column {
                            TaskRow(t, data, showProject = false) { nav.push(Page.TaskPage(t.id)) }
                            if (subs > 0) Text("$subs sub-tasks", Modifier.padding(start = 48.dp, bottom = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            TextButton(onClick = { nav.quickAdd = QuickAddDefaults(projectId = projectId, sectionId = section?.id) }, Modifier.padding(4.dp).semantics { contentDescription = "Add task to $title" }) {
                Icon(Icons.Rounded.Add, null)
                Text("Add task", Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Tasks with a label. */
@Composable
fun LabelPage(name: String, data: Data) {
    val nav = LocalNav.current
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val info = data.labelInfo(name)
    val open = data.tasks.filter { t -> !t.done && t.labels.any { it.equals(name, true) } }
    val done = data.tasks.filter { t -> t.done && t.labels.any { it.equals(name, true) } }.sortedByDescending { it.doneAt }
    TaskListPage(
        title = "@$name",
        icon = { Icon(Icons.Rounded.Label, null, tint = Color(info.color)) },
        open = open,
        done = done,
        data = data,
        options = info.options,
        onOptions = { o -> Store.saveLabel(info.copy(options = o)) },
        defaults = QuickAddDefaults(labels = listOf(name)),
        emptyTitle = "Nothing labelled @$name",
        emptyMessage = "Tasks with this label show up here.",
    ) {
        Box {
            IconButton(onClick = { menu = true }, Modifier.semantics { contentDescription = "Label options" }) { Icon(Icons.Rounded.MoreVert, null) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit label") }, onClick = { menu = false; editing = true })
                DropdownMenuItem(text = { Text(if (info.favorite) "Remove from favourites" else "Add to favourites") }, onClick = { menu = false; Store.saveLabel(info.copy(favorite = !info.favorite)) })
                DropdownMenuItem(text = { Text("Delete label", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleting = true })
            }
        }
    }
    if (editing) {
        NameDialog(
            title = "Edit label",
            initial = name,
            colors = ProjectColors.choices,
            initialColor = info.color,
            favorite = info.favorite,
            onSave = { new, color ->
                val clean = new.removePrefix("@").replace(" ", "-")
                if (!clean.equals(name, true)) Store.renameLabel(name, clean)
                Store.saveLabel(Store.data.value.labelInfo(clean).copy(name = clean, color = color))
                if (clean != name) {
                    nav.pop()
                    nav.push(Page.LabelPage(clean))
                }
            },
            onFavorite = { fav -> Store.saveLabel(Store.data.value.labelInfo(name).copy(favorite = fav)) },
            onDismiss = { editing = false },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = "Delete label?",
            text = "@$name is taken off every task. The tasks themselves stay.",
            confirm = "Delete",
            onConfirm = {
                Store.deleteLabel(name)
                nav.pop()
            },
            onDismiss = { deleting = false },
        )
    }
}

/** A saved filter's tasks, one list per comma-separated part of its query. */
@Composable
fun FilterPage(id: String, data: Data) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    val f = data.filter(id)
    if (f == null) {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf(false) }
    val result = Filters.run(f.query, data, today, Dates.weekStart(data.settings))
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                PageBar(
                    onBack = { nav.pop() },
                    leading = {
                        Icon(Icons.Rounded.FilterList, null, tint = Color(f.color))
                        Spacer(Modifier.width(10.dp))
                        Text(f.name, Modifier.weight(10f, fill = false), style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    },
                    actions = {
                        IconButton(onClick = { viewing = true }, Modifier.semantics { contentDescription = "View options" }) { Icon(Icons.Rounded.Tune, null) }
                        Box {
                            IconButton(onClick = { menu = true }, Modifier.semantics { contentDescription = "Filter options" }) { Icon(Icons.Rounded.MoreVert, null) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Edit filter") }, onClick = { menu = false; editing = true })
                                DropdownMenuItem(text = { Text(if (f.favorite) "Remove from favourites" else "Add to favourites") }, onClick = { menu = false; Store.saveFilter(f.copy(favorite = !f.favorite)) })
                                DropdownMenuItem(text = { Text("Delete filter", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; Store.deleteFilter(f.id); nav.pop() })
                            }
                        }
                    },
                )
                Text(f.query, Modifier.padding(start = 20.dp, bottom = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        floatingActionButton = { Fab("Add task") { nav.quickAdd = QuickAddDefaults(due = today) } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp)) {
            val groups = result.getOrNull()
            if (groups == null) {
                item { EmptyState(Icons.Rounded.FilterList, "This filter has a mistake", result.exceptionOrNull()?.message ?: "") }
            } else {
                val many = groups.size > 1
                for ((gi, g) in groups.withIndex()) {
                    val (query, tasks) = g
                    if (many) item(key = "q-$gi") { SectionHeader("$query · ${tasks.size}") }
                    for ((heading, list) in Dates.grouped(Dates.sorted(tasks, f.options), f.options, data, today)) {
                        if (heading.isNotEmpty()) item(key = "g-$gi-$heading") { SectionHeader(heading) }
                        cardItems(list, key = { "t-$gi-$heading-${it.id}" }) { t, seg -> SwipeTask(t, seg) { TaskRow(t, data) { nav.push(Page.TaskPage(t.id)) } } }
                    }
                    if (tasks.isEmpty() && !many) item { EmptyState(Icons.Rounded.TaskAlt, "Nothing matches", "No open tasks match “${f.query}”.") }
                }
            }
        }
    }
    if (viewing) ViewOptionsDialog(f.options, onChange = { Store.saveFilter(f.copy(options = it)) }, onDismiss = { viewing = false })
    if (editing) FilterDialog(f, onDismiss = { editing = false })
}

@Composable
private fun TaskListPage(
    title: String,
    icon: @Composable () -> Unit,
    open: List<Task>,
    done: List<Task>,
    data: Data,
    options: ViewOptions,
    onOptions: (ViewOptions) -> Unit,
    defaults: QuickAddDefaults,
    emptyTitle: String,
    emptyMessage: String,
    actions: @Composable RowScope.() -> Unit,
) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    var viewing by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                PageBar(
                    onBack = { nav.pop() },
                    leading = {
                        icon()
                        Spacer(Modifier.width(10.dp))
                        Text(title, Modifier.weight(10f, fill = false), style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    },
                    actions = {
                        IconButton(onClick = { viewing = true }, Modifier.semantics { contentDescription = "View options" }) { Icon(Icons.Rounded.Tune, null) }
                        actions()
                    },
                )
            }
        },
        floatingActionButton = { Fab("Add task") { nav.quickAdd = defaults } },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
        ) {
            if (open.isEmpty()) item(key = "empty") { EmptyState(Icons.Rounded.TaskAlt, emptyTitle, emptyMessage) }
            for ((heading, list) in Dates.grouped(Dates.sorted(open, options), options, data, today)) {
                if (heading.isNotEmpty()) item(key = "g-$heading") { SectionHeader(heading) }
                cardItems(list, key = { "$heading|${it.id}" }) { t, seg ->
                    SwipeTask(t, Modifier.animateItem().then(seg)) { TaskRow(t, data) { nav.push(Page.TaskPage(t.id)) } }
                }
            }
            if (options.showCompleted && done.isNotEmpty()) {
                item(key = "done-h") { SectionHeader("Completed · ${done.size}") }
                cardItems(done, key = { "done-" + it.id }) { t, seg -> TaskRow(t, data, seg) { nav.push(Page.TaskPage(t.id)) } }
            }
        }
    }
    if (viewing) ViewOptionsDialog(options, onChange = onOptions, onDismiss = { viewing = false })
}

/** Finished tasks by the day they were finished. Tap a circle to bring a task back. */
@Composable
fun CompletedPage(data: Data) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    var clearing by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val byDay = data.tasks.filter { it.done }
        .sortedByDescending { it.doneAt }
        .groupBy { Instant.ofEpochMilli(it.doneAt).atZone(zone).toLocalDate() }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar("Completed", onBack = { nav.pop() }) {
            if (byDay.isNotEmpty()) TextButton(onClick = { clearing = true }) { Text("Clear all") }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (byDay.isEmpty()) item { EmptyState(Icons.Rounded.TaskAlt, "Nothing completed yet", "Tasks you complete are kept here.") }
            for ((day, tasks) in byDay) {
                item(key = "h-$day") { SectionHeader(Dates.dayHeading(day, today)) }
                cardItems(tasks, key = { it.id }) { t, seg -> TaskRow(t, data, seg, showDue = false) { nav.push(Page.TaskPage(t.id)) } }
            }
        }
    }
    if (clearing) {
        ConfirmDialog(
            title = "Clear completed tasks?",
            text = "Every completed task is deleted for good.",
            confirm = "Clear",
            onConfirm = { Store.clearCompleted() },
            onDismiss = { clearing = false },
        )
    }
}
