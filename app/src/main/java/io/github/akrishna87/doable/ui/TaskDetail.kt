package io.github.akrishna87.doable.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.doable.Attachments
import io.github.akrishna87.doable.Store
import io.github.akrishna87.doable.model.ActivityKind
import io.github.akrishna87.doable.model.Attachment
import io.github.akrishna87.doable.model.Comment
import io.github.akrishna87.doable.model.Data
import io.github.akrishna87.doable.model.Dates
import io.github.akrishna87.doable.model.Priority
import io.github.akrishna87.doable.model.Task
import io.github.akrishna87.doable.model.newId
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

/** Everything about one task, each part editable in place. Changes save as you go. */
@Composable
fun TaskDetailPage(id: String, data: Data) {
    val nav = LocalNav.current
    val clock = LocalClock.current
    val actions = LocalActions.current
    val ask = LocalAskNotifications.current
    val context = LocalContext.current
    val task = data.task(id)
    if (task == null) {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    var title by remember(id) { mutableStateOf(TextFieldValue(task.title)) }
    var description by remember(id) { mutableStateOf(TextFieldValue(task.description)) }
    var comment by remember(id) { mutableStateOf(TextFieldValue("")) }
    var pendingFile by remember(id) { mutableStateOf<Attachment?>(null) }
    var photo by remember(id) { mutableStateOf<Attachment?>(null) }

    fun saveText() {
        val cur = Store.data.value.task(id) ?: return
        val t = title.text.trim().ifEmpty { cur.title }
        val d = description.text.trimEnd()
        if (t != cur.title || d != cur.description) Store.saveTask(cur.copy(title = t, description = d))
    }
    fun update(change: (Task) -> Task) {
        val cur = Store.data.value.task(id) ?: return
        val next = change(cur)
        Store.saveTask(next)
        if (next.reminders.isNotEmpty() && cur.reminders.isEmpty() || next.time != null && cur.time == null) ask()
    }
    LaunchedEffect(title.text, description.text) {
        delay(600)
        saveText()
    }
    DisposableEffect(id) { onDispose { saveText() } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val a = Attachments.copy(context, uri)
            if (a == null) actions.message("Couldn't read that file") else pendingFile = a
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val p = photo
        photo = null
        if (p != null) {
            val done = if (ok) Attachments.finished(context, p) else null.also { Attachments.delete(context, p) }
            if (done != null) pendingFile = done
        }
    }

    var dueMenu by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }
    var customRepeat by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    var labelsDialog by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var deadlineMenu by remember { mutableStateOf(false) }
    var pickingDeadline by remember { mutableStateOf(false) }
    var durationMenu by remember { mutableStateOf(false) }
    var addingReminder by remember { mutableStateOf(false) }
    var attachMenu by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        PageBar(
            onBack = { nav.pop() },
            leading = {
                val project = data.project(task.projectId)
                val section = data.section(task.sectionId)
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = task.parentId == null) { nav.moving = task }
                        .semantics { contentDescription = "Project: ${project.name}" + (section?.let { " / ${it.name}" } ?: "") }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ColorDot(project.color)
                    Spacer(Modifier.width(8.dp))
                    Text(project.name + (section?.let { " / ${it.name}" } ?: ""), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (task.parentId == null) Icon(Icons.Rounded.ArrowDropDown, null)
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { moreMenu = true }, Modifier.semantics { contentDescription = "Task options" }) { Icon(Icons.Rounded.MoreVert, null) }
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        if (task.parentId == null) DropdownMenuItem(text = { Text("Move to…") }, onClick = { moreMenu = false; nav.moving = task })
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            onClick = {
                                moreMenu = false
                                val copyId = newId()
                                val subs = Store.taskFamily(id).filter { it.id != id }
                                Store.addTasks(
                                    listOf(task.copy(id = copyId, done = false, doneAt = 0L, createdAt = System.currentTimeMillis(), order = task.order + 1, reminders = task.reminders.map { it.copy(id = newId()) })) +
                                        subs.filter { it.parentId == id }.map { it.copy(id = newId(), parentId = copyId, done = false, doneAt = 0L) },
                                )
                                actions.message("Task duplicated")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Copy as text") },
                            onClick = {
                                moreMenu = false
                                val text = listOf(task.title, task.description).filter { it.isNotBlank() }.joinToString("\n")
                                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(task.title, text))
                                actions.message("Copied")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete task", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                moreMenu = false
                                nav.pop()
                                actions.delete(task)
                            },
                        )
                    }
                }
            },
        )

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(start = 6.dp, end = 16.dp), verticalAlignment = Alignment.Top) {
                PriorityCheck(task, onToggle = { actions.toggle(task) }, size = 24.dp)
                PlainField(
                    value = title,
                    onChange = { title = it },
                    placeholder = "Task name",
                    description = "Task title",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 9.dp),
                )
            }
            PlainField(
                value = description,
                onChange = { description = it },
                placeholder = "Description",
                description = "Description",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 50.dp, end = 16.dp, top = 2.dp),
            )
            HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

            DetailRow(
                Icons.Rounded.CalendarToday,
                task.due?.let { Dates.dayLabel(it, clock.today) } ?: "Due date",
                if (task.due != null) toneColor(Dates.tone(task, clock.now)) else muted,
                onClick = { dueMenu = true },
                onClear = if (task.due != null) ({ update { it.copy(due = null, time = null, repeat = null, duration = null) } }) else null,
            ) {
                DueMenu(dueMenu, { dueMenu = false }, clock.today, Dates.weekStart(data.settings), onPick = { d ->
                    update { if (d == null) it.copy(due = null, time = null, repeat = null, duration = null) else it.copy(due = d) }
                }, onPickDate = { pickingDate = true })
            }
            if (task.due != null) {
                DetailRow(
                    Icons.Rounded.Schedule,
                    task.time?.let { Dates.timeFormatter(clock.is24Hour).format(it) } ?: "Add a time",
                    if (task.time != null) MaterialTheme.colorScheme.onSurface else muted,
                    onClick = { pickingTime = true },
                    onClear = if (task.time != null) ({ update { it.copy(time = null, duration = null) } }) else null,
                )
                if (task.time != null) {
                    DetailRow(
                        Icons.Rounded.Timer,
                        task.duration?.let { "Takes ${Dates.durationLabel(it)}" } ?: "Duration",
                        if (task.duration != null) MaterialTheme.colorScheme.onSurface else muted,
                        onClick = { durationMenu = true },
                        onClear = if (task.duration != null) ({ update { it.copy(duration = null) } }) else null,
                    ) { DurationMenu(durationMenu, task.duration, { durationMenu = false }) { m -> update { it.copy(duration = m) } } }
                }
                DetailRow(
                    Icons.Rounded.Repeat,
                    task.repeat?.label() ?: "Doesn't repeat",
                    if (task.repeat != null) MaterialTheme.colorScheme.onSurface else muted,
                    onClick = { repeatMenu = true },
                ) {
                    RepeatMenu(repeatMenu, task.repeat, { repeatMenu = false }, onCustom = { customRepeat = true }) { r -> update { it.copy(repeat = r) } }
                }
            }
            DetailRow(
                Icons.Rounded.HourglassBottom,
                task.deadline?.let { "Deadline: " + Dates.deadlineLabel(it, clock.today) } ?: "Deadline",
                task.deadline?.let { if (it.isAfter(clock.today)) MaterialTheme.colorScheme.onSurface else LocalExtra.current.overdue } ?: muted,
                onClick = { deadlineMenu = true },
                onClear = if (task.deadline != null) ({ update { it.copy(deadline = null) } }) else null,
            ) {
                DueMenu(deadlineMenu, { deadlineMenu = false }, clock.today, Dates.weekStart(data.settings), onPick = { d -> update { it.copy(deadline = d) } }, onPickDate = { pickingDeadline = true })
            }
            DetailRow(
                Icons.Rounded.Flag,
                Priority.label(task.priority),
                if (task.priority < Priority.NONE) Color(Priority.color(task.priority)) else muted,
                onClick = { priorityMenu = true },
            ) {
                PriorityMenu(priorityMenu, task.priority, { priorityMenu = false }) { p -> update { it.copy(priority = p) } }
            }
            DetailRow(
                Icons.Rounded.Label,
                if (task.labels.isEmpty()) "Labels" else task.labels.joinToString("  ") { "@$it" },
                if (task.labels.isEmpty()) muted else MaterialTheme.colorScheme.onSurface,
                onClick = { labelsDialog = true },
            )

            // Reminders: each one with its own remove button, and a row to add another.
            for (r in task.reminders) {
                val at = r.time(task)
                val text = if (r.at != null) {
                    DateTimeFormatter.ofPattern("EEE d MMM").format(r.at) + " " + Dates.timeFormatter(clock.is24Hour).format(r.at)
                } else {
                    r.label() + (at?.let { " (" + Dates.timeFormatter(clock.is24Hour).format(it) + ")" } ?: "")
                }
                DetailRow(Icons.Rounded.Notifications, text, MaterialTheme.colorScheme.onSurface, onClick = { addingReminder = true },
                    onClear = { update { t -> t.copy(reminders = t.reminders.filter { it.id != r.id }) } })
            }
            DetailRow(Icons.Rounded.Notifications, if (task.reminders.isEmpty()) "Reminders" else "Add another reminder", muted, onClick = { addingReminder = true })

            val parent = data.task(task.parentId)
            if (parent != null) {
                DetailRow(Icons.Rounded.SubdirectoryArrowRight, "Sub-task of “${parent.title}”", MaterialTheme.colorScheme.primary, onClick = {
                    nav.push(Page.TaskPage(parent.id))
                })
            }

            val subs = data.subtasks(id)
            SectionHeader(if (subs.isEmpty()) "Sub-tasks" else "Sub-tasks  ${subs.count { it.done }}/${subs.size}")
            for (s in subs) TaskRow(s, data, showProject = false) { nav.push(Page.TaskPage(s.id)) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { nav.quickAdd = QuickAddDefaults(projectId = task.projectId, sectionId = task.sectionId, parentId = id) }
                    .semantics { contentDescription = "Add sub-task" }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                Text("Add sub-task", Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
            }

            SectionHeader(if (task.comments.isEmpty()) "Comments" else "Comments · ${task.comments.size}")
            for (c in task.comments) CommentRow(c, clock.today, onDelete = {
                Store.deleteComment(id, c.id)
                c.attachment?.let { Attachments.delete(context, it) }
            })

            // The task's own history from the activity log.
            val history = data.activity.filter { it.taskId == id }.takeLast(8)
            Text(
                (listOf("Added ${Dates.shortDate(task.createdAt, clock.today)}") +
                    history.filter { it.kind != ActivityKind.ADDED }.map { "${it.kind.verb.replaceFirstChar { c -> c.uppercase() }} ${Dates.shortDate(it.at, clock.today)}" } +
                    listOfNotNull(if (task.done) "Completed ${Dates.shortDate(task.doneAt, clock.today)}" else null)).distinct().joinToString(" · "),
                Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }

        // Writing a comment, with an optional file or photo.
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        pendingFile?.let { f ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (f.isImage) Icons.Rounded.PhotoCamera else Icons.Rounded.InsertDriveFile, null, Modifier.size(18.dp), tint = muted)
                Text(f.name, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { Attachments.delete(context, f); pendingFile = null }, Modifier.semantics { contentDescription = "Remove attachment" }) { Icon(Icons.Rounded.Close, null) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                IconButton(onClick = { attachMenu = true }, Modifier.semantics { contentDescription = "Attach" }) { Icon(Icons.Rounded.AttachFile, null) }
                DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                    DropdownMenuItem(text = { Text("Photo or file") }, leadingIcon = { Icon(Icons.Rounded.InsertDriveFile, null) }, onClick = {
                        attachMenu = false
                        picker.launch(arrayOf("*/*"))
                    })
                    DropdownMenuItem(text = { Text("Take a photo") }, leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) }, onClick = {
                        attachMenu = false
                        val (a, uri) = Attachments.newPhoto(context)
                        photo = a
                        runCatching { camera.launch(uri) }.onFailure { Attachments.delete(context, a); photo = null; actions.message("No camera app found") }
                    })
                }
            }
            PlainField(
                value = comment,
                onChange = { comment = it },
                placeholder = "Comment",
                description = "Comment",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).heightIn(max = 120.dp).padding(horizontal = 4.dp),
            )
            FilledIconButton(
                onClick = {
                    val text = comment.text.trim()
                    if (text.isNotEmpty() || pendingFile != null) {
                        Store.addComment(id, Comment(text = text, attachment = pendingFile))
                        comment = TextFieldValue("")
                        pendingFile = null
                    }
                },
                enabled = comment.text.isNotBlank() || pendingFile != null,
                modifier = Modifier.semantics { contentDescription = "Add comment" },
            ) { Icon(Icons.AutoMirrored.Rounded.Send, null) }
        }
    }

    if (pickingDate) DatePickDialog(task.due, onPick = { d -> update { it.copy(due = d) } }, onDismiss = { pickingDate = false })
    if (pickingDeadline) DatePickDialog(task.deadline ?: task.due, onPick = { d -> update { it.copy(deadline = d) } }, onDismiss = { pickingDeadline = false })
    if (pickingTime) {
        TimePickDialog(task.time ?: defaultTime(task.due, clock.now), clock.is24Hour, onPick = { t -> update { it.copy(time = t) } }, onDismiss = { pickingTime = false })
    }
    if (customRepeat) RepeatDialog(task.repeat, task.due, onDone = { r -> update { it.copy(repeat = r) } }, onDismiss = { customRepeat = false })
    if (labelsDialog) LabelsDialog(data.labels, task.labels, onDone = { l -> update { it.copy(labels = l) } }, onDismiss = { labelsDialog = false })
    if (addingReminder) ReminderDialog(task, clock.is24Hour, onAdd = { r -> update { it.copy(reminders = it.reminders + r) } }, onDismiss = { addingReminder = false })
}

/** A comment: its text, its file (a picture shows small; tap to open), and when it was written. */
@Composable
private fun CommentRow(c: Comment, today: java.time.LocalDate, onDelete: () -> Unit) {
    val context = LocalContext.current
    val actions = LocalActions.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                if (c.text.isNotBlank()) Text(c.text, style = MaterialTheme.typography.bodyMedium)
                c.attachment?.let { a ->
                    val thumb = remember(a.file) { if (a.isImage) Attachments.thumbnail(context, a)?.asImageBitmap() else null }
                    if (thumb != null) {
                        Image(
                            thumb, a.name,
                            Modifier.padding(top = 6.dp).size(180.dp).clip(RoundedCornerShape(10.dp)).clickable { if (!Attachments.open(context, a)) actions.message("No app can open this") },
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Row(
                            Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .clickable { if (!Attachments.open(context, a)) actions.message("No app can open this") }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.InsertDriveFile, null, Modifier.size(20.dp))
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(a.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(Attachments.sizeLabel(a.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Text(Dates.shortDate(c.createdAt, today), Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }, Modifier.size(36.dp).semantics { contentDescription = "Comment options" }) { Icon(Icons.Rounded.MoreVert, null, Modifier.size(18.dp)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Delete comment", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    text: String,
    tint: Color,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
    menu: @Composable () -> Unit = {},
) {
    Box {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 8.dp).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = tint)
            Text(
                text,
                Modifier.weight(1f).padding(start = 20.dp, top = 12.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = tint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (onClear != null) {
                IconButton(onClick = onClear, Modifier.semantics { contentDescription = "Clear $text" }) { Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        menu()
    }
}
