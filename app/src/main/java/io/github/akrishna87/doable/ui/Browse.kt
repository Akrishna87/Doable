package io.github.akrishna87.doable.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.akrishna87.doable.Backup
import io.github.akrishna87.doable.Reminders
import io.github.akrishna87.doable.Store
import io.github.akrishna87.doable.model.Accent
import io.github.akrishna87.doable.model.Csv
import io.github.akrishna87.doable.model.Data
import io.github.akrishna87.doable.model.Dates
import io.github.akrishna87.doable.model.Filters
import io.github.akrishna87.doable.model.INBOX_ID
import io.github.akrishna87.doable.model.Project
import io.github.akrishna87.doable.model.ProjectColors
import io.github.akrishna87.doable.model.Reminder
import io.github.akrishna87.doable.model.SwipeAction
import io.github.akrishna87.doable.model.ThemeMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** The Inbox, favourites, projects (with sub-projects), filters and labels, and everything else. */
@Composable
fun BrowseScreen(data: Data, padding: PaddingValues) {
    val nav = LocalNav.current
    var newProject by remember { mutableStateOf(false) }
    val open = data.tasks.filter { !it.done && it.parentId == null }
    val favProjects = data.projects.filter { it.favorite && !it.archived }
    val favLabels = data.labelInfo.filter { it.favorite }
    val favFilters = data.filters.filter { it.favorite }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
    ) {
        item {
            ScreenHeader("Browse") {
                SearchAction()
                CircleAction(Icons.Rounded.Settings, "Settings") { nav.push(Page.SettingsPage) }
            }
        }
        item(key = "inbox") {
            ListRow(
                "Inbox",
                Modifier.cardSegment(0, 1),
                leading = { IconTile(Icons.Rounded.Inbox, Color(ProjectColors.Inbox)) },
                trailing = { Count(open.count { it.projectId == INBOX_ID }) },
                onClick = { nav.push(Page.ProjectPage(INBOX_ID)) },
            )
        }
        if (favProjects.isNotEmpty() || favLabels.isNotEmpty() || favFilters.isNotEmpty()) {
            item { SectionHeader("Favourites") }
            val favs: List<Pair<String, @Composable (Modifier) -> Unit>> =
                favProjects.map { p ->
                    ("fp-" + p.id) to @Composable { m: Modifier ->
                        ListRow(p.name, m, leading = { IconTile(Icons.Rounded.Tag, Color(p.color)) },
                            trailing = { Count(open.count { it.projectId == p.id }) }, onClick = { nav.push(Page.ProjectPage(p.id)) })
                    }
                } + favFilters.map { f ->
                    ("ff-" + f.id) to @Composable { m: Modifier ->
                        ListRow(f.name, m, leading = { IconTile(Icons.Rounded.FilterList, Color(f.color)) }, onClick = { nav.push(Page.FilterPage(f.id)) })
                    }
                } + favLabels.map { l ->
                    ("fl-" + l.name) to @Composable { m: Modifier ->
                        ListRow(l.name, m, leading = { IconTile(Icons.Rounded.Label, Color(l.color)) },
                            trailing = { Count(open.count { t -> t.labels.any { it.equals(l.name, true) } }) }, onClick = { nav.push(Page.LabelPage(l.name)) })
                    }
                }
            cardItems(favs, key = { it.first }) { (_, row), seg -> row(seg) }
        }
        item {
            SectionHeader("My projects") {
                IconButton(onClick = { newProject = true }, Modifier.semantics { contentDescription = "Add project" }) { Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary) }
            }
        }
        val tree = data.projectTree()
        if (tree.isEmpty()) {
            item {
                Text(
                    "Group tasks into projects, like Work or Home. Tap + to add one.",
                    Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        cardItems(tree, key = { "p-" + it.first.id }) { (p, depth), seg ->
            Box(seg) { ProjectRow(p, depth, open.count { it.projectId == p.id }) }
        }
        item { SectionHeader("More") }
        val more = buildList<Triple<String, ImageVector, Pair<Color, () -> Unit>>> {
            add(Triple("Filters & Labels", Icons.Rounded.FilterList, Color(0xFF7C3AED) to { nav.push(Page.FiltersLabels) }))
            add(Triple("Completed", Icons.Rounded.CheckCircle, Color(0xFF0B8463) to { nav.push(Page.Completed) }))
            if (data.settings.karma) add(Triple("Productivity", Icons.Rounded.Insights, Color(0xFFF5A524) to { nav.push(Page.Productivity) }))
            add(Triple("Activity", Icons.Rounded.History, Color(0xFF2563EB) to { nav.push(Page.ActivityLog) }))
            if (data.projects.any { it.archived }) add(Triple("Archived projects", Icons.Rounded.Archive, Color(0xFF6B6D7B) to { nav.push(Page.Archived) }))
            add(Triple("Settings", Icons.Rounded.Settings, Color(0xFF5D5F6E) to { nav.push(Page.SettingsPage) }))
        }
        cardItems(more, key = { "m-" + it.first }) { (label, icon, rest), seg ->
            val count = when (label) {
                "Filters & Labels" -> data.filters.size + data.labels.size
                "Completed" -> data.tasks.count { it.done }
                "Archived projects" -> data.projects.count { it.archived }
                else -> 0
            }
            ListRow(label, seg, leading = { IconTile(icon, rest.first) }, trailing = { Count(count) }, onClick = rest.second)
        }
    }
    if (newProject) {
        var fav by remember { mutableStateOf(false) }
        NameDialog(
            title = "New project",
            confirm = "Add",
            colors = ProjectColors.choices,
            initialColor = NewProjectColor,
            favorite = false,
            onFavorite = { fav = it },
            onSave = { name, color ->
                val p = Project(name = name, color = color, favorite = fav)
                Store.saveProject(p)
                nav.push(Page.ProjectPage(p.id))
            },
            onDismiss = { newProject = false },
        )
    }
}

/** A project in Browse, indented under its parent, with a menu to move it around. */
@Composable
private fun ProjectRow(p: Project, depth: Int, count: Int) {
    val nav = LocalNav.current
    var menu by remember { mutableStateOf(false) }
    Box {
        ListRow(
            p.name,
            modifier = Modifier.padding(start = (depth * 20).dp),
            leading = { IconTile(Icons.Rounded.Tag, Color(p.color)) },
            trailing = {
                if (p.favorite) Icon(Icons.Rounded.Star, "Favourite", Modifier.size(16.dp).padding(end = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Count(count)
            },
            onClick = { nav.push(Page.ProjectPage(p.id)) },
            onLongClick = { menu = true },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; Store.moveProject(p.id, -1) })
            DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; Store.moveProject(p.id, 1) })
            DropdownMenuItem(text = { Text(if (p.favorite) "Remove from favourites" else "Add to favourites") }, onClick = { menu = false; Store.saveProject(p.copy(favorite = !p.favorite)) })
            DropdownMenuItem(text = { Text("Archive") }, onClick = { menu = false; Store.archiveProject(p.id, true) })
        }
    }
}

/** Archived projects: bring them back, or delete them for good. */
@Composable
fun ArchivedPage(data: Data) {
    val nav = LocalNav.current
    var deleting by remember { mutableStateOf<Project?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar("Archived projects", onBack = { nav.pop() })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            val archived = data.projects.filter { it.archived }.sortedBy { it.name.lowercase() }
            if (archived.isEmpty()) item { EmptyState(Icons.Rounded.Archive, "Nothing archived", "Archived projects are kept here, out of the way.") }
            items(archived, key = { it.id }) { p ->
                ListRow(
                    p.name,
                    leading = { Text("#", style = MaterialTheme.typography.titleLarge, color = Color(p.color)) },
                    detail = "${data.tasks.count { it.projectId == p.id && !it.done }} open tasks",
                    trailing = {
                        IconButton(onClick = { Store.archiveProject(p.id, false) }, Modifier.semantics { contentDescription = "Unarchive ${p.name}" }) { Icon(Icons.Rounded.Unarchive, null) }
                        IconButton(onClick = { deleting = p }, Modifier.semantics { contentDescription = "Delete ${p.name}" }) { Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.error) }
                    },
                    onClick = { nav.push(Page.ProjectPage(p.id)) },
                )
            }
        }
    }
    deleting?.let { p ->
        ConfirmDialog("Delete project?", "“${p.name}” and its tasks are deleted for good.", "Delete", onConfirm = { Store.deleteProject(p.id) }, onDismiss = { deleting = null })
    }
}

/** Searches task titles, descriptions, comments and labels; a query in filter language shows filter results too. */
@Composable
fun SearchScreen(data: Data, padding: PaddingValues) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var saving by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val q = query.text.trim()
    val tasks = if (q.isEmpty()) emptyList() else data.tasks.filter { t ->
        t.title.contains(q, true) || t.description.contains(q, true) || t.comments.any { it.text.contains(q, true) } ||
            t.labels.any { it.contains(q.removePrefix("@"), true) }
    }.sortedWith(compareBy<io.github.akrishna87.doable.model.Task>({ it.done }).then(Dates.taskOrder))
    // Only worth showing when the words look like filter language.
    val asFilter = if (q.isEmpty() || !Regex("[&|!#@/()]|^p[1-4]$|today|overdue|days?\\b|no date|:").containsMatchIn(q)) null
    else Filters.run(q, data, today, Dates.weekStart(data.settings)).getOrNull()
    val projects = if (q.isEmpty()) emptyList() else data.projects.filter { it.name.contains(q, true) }
    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            PlainField(
                value = query,
                onChange = { query = it },
                placeholder = "Tasks, projects, or a filter like p1 & today",
                description = "Search",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(start = 12.dp).focusRequester(focus),
                singleLine = true,
            )
            if (query.text.isNotEmpty()) {
                IconButton(onClick = { query = TextFieldValue("") }, Modifier.semantics { contentDescription = "Clear search" }) { Icon(Icons.Rounded.Close, null) }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 32.dp)) {
            if (q.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.Search, "Search everything",
                        "Find tasks by title, description, comment or @label. Filter language works too: try “today | overdue” or “#Work & p1”.",
                    )
                }
            } else if (tasks.isEmpty() && projects.isEmpty() && asFilter.isNullOrEmpty()) {
                item { EmptyState(Icons.Rounded.Search, "No results", "Nothing matches “$q”.") }
            }
            if (projects.isNotEmpty()) {
                item { SectionHeader("Projects · ${projects.size}") }
                items(projects, key = { "p-" + it.id }) { p ->
                    ListRow(p.name, leading = { Text("#", style = MaterialTheme.typography.titleLarge, color = Color(p.color)) }, onClick = { nav.push(Page.ProjectPage(p.id)) })
                }
            }
            if (tasks.isNotEmpty()) {
                item { SectionHeader("Tasks · ${tasks.size}") }
                items(tasks, key = { "t-" + it.id }) { t -> TaskRow(t, data) { nav.push(Page.TaskPage(t.id)) } }
            }
            if (!asFilter.isNullOrEmpty()) {
                item {
                    SectionHeader("As a filter") {
                        TextButton(onClick = { saving = true }) { Text("Save filter") }
                    }
                }
                for ((gi, g) in asFilter.withIndex()) {
                    if (asFilter.size > 1) item(key = "fq-$gi") { Text(g.first, Modifier.padding(start = 20.dp, top = 8.dp), style = MaterialTheme.typography.labelLarge) }
                    items(g.second, key = { "f-$gi-" + it.id }) { t -> TaskRow(t, data) { nav.push(Page.TaskPage(t.id)) } }
                }
            }
        }
    }
    if (saving) FilterDialog(null, onDismiss = { saving = false }, initialQuery = q)
}

/** Theme and colours, start page, dates, swipes, reminders, productivity goals, backups and the version. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsPage(data: Data) {
    val nav = LocalNav.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val s = data.settings
    var refresh by remember { mutableIntStateOf(0) }
    var restoring by remember { mutableStateOf<Data?>(null) }
    var startMenu by remember { mutableStateOf(false) }
    var reminderMenu by remember { mutableStateOf(false) }
    var pickingSummary by remember { mutableStateOf(false) }
    var goalMenu by remember { mutableStateOf(false) }
    var weekGoalMenu by remember { mutableStateOf(false) }
    var rightMenu by remember { mutableStateOf(false) }
    var leftMenu by remember { mutableStateOf(false) }
    val canNotify = remember(refresh) { Reminders.canNotify(context) }
    val canExact = remember(refresh) { Reminders.canScheduleExact(context) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) actions.message(if (Backup.export(context, uri)) "Backup saved" else "Couldn't save the backup")
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val d = Backup.read(context, uri)
            if (d == null) actions.message("That file isn't a Doable backup") else restoring = d
        }
    }
    val csvImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val result = runCatching {
                val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')?.ifBlank { null } ?: "Imported"
                Csv.import(text, name, LocalDate.now(), Dates.weekStart(Store.data.value.settings))
            }
            result.onSuccess { r ->
                Store.import(r)
                actions.message("Imported ${r.tasks.size} tasks into “${r.project.name}”")
                nav.push(Page.ProjectPage(r.project.id))
            }.onFailure { actions.message(it.message ?: "Couldn't read that file") }
        }
    }
    fun startLabel(start: String): String = when {
        start == "today" -> "Today"
        start == "upcoming" -> "Upcoming"
        start == "inbox" -> "Inbox"
        start.startsWith("project:") -> data.projects.firstOrNull { it.id == start.removePrefix("project:") }?.name ?: "Today"
        start.startsWith("filter:") -> data.filter(start.removePrefix("filter:"))?.name ?: "Today"
        start.startsWith("label:") -> "@" + start.removePrefix("label:")
        else -> "Today"
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar("Settings", onBack = { nav.pop() })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { SectionHeader("Theme") }
            card {
            for (mode in ThemeMode.entries) row {
                ListRow(
                    mode.label,
                    leading = { RadioButton(selected = s.theme == mode, onClick = null) },
                    onClick = { Store.updateSettings { it.copy(theme = mode) } },
                    modifier = Modifier.semantics { contentDescription = "Theme: ${mode.label}" },
                )
            }
            row {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("Colour", style = MaterialTheme.typography.labelLarge)
                    androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                        for (a in Accent.entries) {
                            Column(
                                Modifier.clip(MaterialTheme.shapes.small).clickable { Store.updateSettings { it.copy(accent = a) } }
                                    .semantics { contentDescription = "Colour: ${a.label}" }.padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(a.light)), contentAlignment = Alignment.Center) {
                                    if (a == s.accent) Icon(Icons.Rounded.CheckCircle, null, tint = Color.White)
                                }
                                Text(a.label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            }
            item { SectionHeader("General") }
            card {
            row {
                Box {
                    ListRow("Start page", detail = startLabel(s.startPage), onClick = { startMenu = true })
                    DropdownMenu(expanded = startMenu, onDismissRequest = { startMenu = false }) {
                        val choices = listOf("today", "upcoming", "inbox") + data.filters.map { "filter:${it.id}" } + data.projectTree().map { "project:${it.first.id}" }
                        for (c in choices) DropdownMenuItem(text = { Text(startLabel(c)) }, onClick = { startMenu = false; Store.updateSettings { it.copy(startPage = c) } })
                    }
                }
            }
            row {
                ListRow(
                    "Week starts on Sunday",
                    trailing = { Switch(checked = s.weekStartsSunday, onCheckedChange = { on -> Store.updateSettings { it.copy(weekStartsSunday = on) } }) },
                    onClick = { Store.updateSettings { it.copy(weekStartsSunday = !it.weekStartsSunday) } },
                )
            }
            row {
                ListRow(
                    "Smart date recognition",
                    detail = "Read dates, #projects and @labels typed into a task",
                    trailing = { Switch(checked = s.smartDates, onCheckedChange = { on -> Store.updateSettings { it.copy(smartDates = on) } }) },
                    onClick = { Store.updateSettings { it.copy(smartDates = !it.smartDates) } },
                )
            }
            row {
                Box {
                    ListRow("Swipe right", detail = s.swipeRight.label, onClick = { rightMenu = true })
                    DropdownMenu(expanded = rightMenu, onDismissRequest = { rightMenu = false }) {
                        for (a in SwipeAction.entries) DropdownMenuItem(text = { Text(a.label) }, onClick = { rightMenu = false; Store.updateSettings { it.copy(swipeRight = a) } })
                    }
                }
            }
            row {
                Box {
                    ListRow("Swipe left", detail = s.swipeLeft.label, onClick = { leftMenu = true })
                    DropdownMenu(expanded = leftMenu, onDismissRequest = { leftMenu = false }) {
                        for (a in SwipeAction.entries) DropdownMenuItem(text = { Text(a.label) }, onClick = { leftMenu = false; Store.updateSettings { it.copy(swipeLeft = a) } })
                    }
                }
            }

            }
            item { SectionHeader("Reminders") }
            card {
            row {
                ListRow(
                    "Reminders",
                    detail = "Notifications at the times you set on tasks",
                    trailing = { Switch(checked = s.reminders, onCheckedChange = { on -> Store.updateSettings { it.copy(reminders = on) } }) },
                    onClick = { Store.updateSettings { it.copy(reminders = !it.reminders) } },
                )
            }
            row {
                Box {
                    ListRow("Automatic reminder", detail = (s.autoReminder?.let { Reminder(minutesBefore = it).label() } ?: "None") + " · for tasks given a time", onClick = { reminderMenu = true })
                    DropdownMenu(expanded = reminderMenu, onDismissRequest = { reminderMenu = false }) {
                        DropdownMenuItem(text = { Text("None") }, onClick = { reminderMenu = false; Store.updateSettings { it.copy(autoReminder = null) } })
                        for (m in Reminder.presets) DropdownMenuItem(text = { Text(Reminder(minutesBefore = m).label()) }, onClick = { reminderMenu = false; Store.updateSettings { it.copy(autoReminder = m) } })
                    }
                }
            }
            row {
                ListRow(
                    "Daily summary",
                    detail = s.dailySummary?.let { "Each day at " + Dates.timeFormatter(LocalClock.current.is24Hour).format(it) } ?: "Off · a morning list of the day's tasks",
                    trailing = {
                        Switch(checked = s.dailySummary != null, onCheckedChange = { on -> Store.updateSettings { it.copy(dailySummary = if (on) LocalTime.of(8, 0) else null) } })
                    },
                    onClick = { pickingSummary = true },
                )
            }
            if (s.reminders && !canNotify) {
                row {
                    ListRow(
                        "Notifications are off",
                        detail = "Tap to allow them, or reminders can't appear",
                        trailing = { Text("Allow", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) },
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33 && !s.askedNotifications) {
                                Store.updateSettings { it.copy(askedNotifications = true) }
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                context.startActivity(
                                    Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
                                )
                            }
                        },
                    )
                }
            }
            if (s.reminders && !canExact && Build.VERSION.SDK_INT >= 31) {
                row {
                    ListRow(
                        "Reminders may be a few minutes late",
                        detail = "Tap to allow exact alarms for on-time reminders",
                        trailing = { Text("Allow", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) },
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                            }
                        },
                    )
                }
            }

            }
            item { SectionHeader("Productivity") }
            card {
            row {
                ListRow(
                    "Karma",
                    detail = "Points, goals and streaks for completing tasks",
                    trailing = { Switch(checked = s.karma, onCheckedChange = { on -> Store.updateSettings { it.copy(karma = on) } }) },
                    onClick = { Store.updateSettings { it.copy(karma = !it.karma) } },
                )
            }
            if (s.karma) {
                row {
                    Box {
                        ListRow("Daily goal", detail = "${s.dailyGoal} tasks", onClick = { goalMenu = true })
                        DropdownMenu(expanded = goalMenu, onDismissRequest = { goalMenu = false }) {
                            for (n in listOf(1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20)) DropdownMenuItem(text = { Text("$n tasks") }, onClick = { goalMenu = false; Store.updateSettings { it.copy(dailyGoal = n) } })
                        }
                    }
                }
                row {
                    Box {
                        ListRow("Weekly goal", detail = "${s.weeklyGoal} tasks", onClick = { weekGoalMenu = true })
                        DropdownMenu(expanded = weekGoalMenu, onDismissRequest = { weekGoalMenu = false }) {
                            for (n in listOf(5, 10, 15, 20, 25, 30, 40, 50, 75, 100)) DropdownMenuItem(text = { Text("$n tasks") }, onClick = { weekGoalMenu = false; Store.updateSettings { it.copy(weeklyGoal = n) } })
                        }
                    }
                }
                row {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text("Days off", style = MaterialTheme.typography.bodyLarge)
                        Text("Streaks don't break on these days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.padding(top = 4.dp)) {
                            for (d in DayOfWeek.entries) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Checkbox(
                                        checked = d in s.daysOff,
                                        onCheckedChange = { on -> Store.updateSettings { it.copy(daysOff = if (on) it.daysOff + d else it.daysOff - d) } },
                                        modifier = Modifier.semantics { contentDescription = "Day off: " + d.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
                                    )
                                    Text(d.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                row {
                    ListRow(
                        "Vacation mode",
                        detail = "Pause goals and streaks",
                        trailing = { Switch(checked = s.vacation, onCheckedChange = { on -> Store.updateSettings { it.copy(vacation = on) } }) },
                        onClick = { Store.updateSettings { it.copy(vacation = !it.vacation) } },
                    )
                }
            }

            }
            item { SectionHeader("Backup and import") }
            card {
            row {
                ListRow(
                    "Export backup",
                    detail = "Save every task and project to a file",
                    onClick = { exporter.launch("doable-backup-${LocalDate.now()}.json") },
                )
            }
            row {
                ListRow(
                    "Restore from backup",
                    detail = "Replace everything with a backup file",
                    onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                )
            }
            row {
                ListRow(
                    "Import a Todoist template (CSV)",
                    leading = { Icon(Icons.Rounded.UploadFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    detail = "Adds it as a new project",
                    onClick = { csvImporter.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream", "*/*")) },
                )
            }
            }
            item { SectionHeader("About") }
            card {
            row {
                val version = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
                }
                ListRow("Doable", detail = "Version $version · Your tasks, on your phone only")
            }
            }
        }
    }
    if (pickingSummary) {
        TimePickDialog(s.dailySummary ?: LocalTime.of(8, 0), LocalClock.current.is24Hour, onPick = { t -> Store.updateSettings { it.copy(dailySummary = t) } }, onDismiss = { pickingSummary = false })
    }
    restoring?.let { backup ->
        ConfirmDialog(
            "Restore this backup?",
            "It has ${backup.tasks.size} tasks and ${backup.projects.size} projects. Everything in Doable now is replaced.",
            "Restore",
            onConfirm = {
                Store.replaceAll(backup)
                actions.message("Backup restored")
            },
            onDismiss = { restoring = null },
        )
    }
}
