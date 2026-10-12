package io.github.akrishna87.doable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NextWeek
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.akrishna87.doable.model.Data
import io.github.akrishna87.doable.model.Dates
import io.github.akrishna87.doable.model.Freq
import io.github.akrishna87.doable.model.GroupBy
import io.github.akrishna87.doable.model.INBOX_ID
import io.github.akrishna87.doable.model.Priority
import io.github.akrishna87.doable.model.Project
import io.github.akrishna87.doable.model.ProjectColors
import io.github.akrishna87.doable.model.QuickAdd
import io.github.akrishna87.doable.model.Reminder
import io.github.akrishna87.doable.model.RepeatRule
import io.github.akrishna87.doable.model.SortBy
import io.github.akrishna87.doable.model.Task
import io.github.akrishna87.doable.model.ViewOptions
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(initial: LocalTime, is24Hour: Boolean, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(LocalTime.of(state.hour, state.minute))
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

/** Today, Tomorrow, This weekend, Next week, Pick a date, or No date. */
@Composable
fun DueMenu(expanded: Boolean, onDismiss: () -> Unit, today: LocalDate, weekStart: DayOfWeek, onPick: (LocalDate?) -> Unit, onPickDate: () -> Unit) {
    val f = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val weekend = QuickAdd.onOrAfter(today.plusDays(1), DayOfWeek.SATURDAY)
    val nextWeek = Dates.startOfWeek(today, weekStart).plusWeeks(1)
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DateItem("Today", f.format(today), Icons.Rounded.Today) { onPick(today); onDismiss() }
        DateItem("Tomorrow", f.format(today.plusDays(1)), Icons.Rounded.LightMode) { onPick(today.plusDays(1)); onDismiss() }
        DateItem("This weekend", f.format(weekend), Icons.Rounded.Weekend) { onPick(weekend); onDismiss() }
        DateItem("Next week", f.format(nextWeek), Icons.Rounded.NextWeek) { onPick(nextWeek); onDismiss() }
        DateItem("Pick a date…", "", Icons.Rounded.CalendarMonth) { onDismiss(); onPickDate() }
        DateItem("No date", "", Icons.Rounded.Block) { onPick(null); onDismiss() }
    }
}

@Composable
private fun DateItem(label: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = { Icon(icon, null) },
        trailingIcon = { if (detail.isNotEmpty()) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}

/** Picking a new date for a task: the quick choices, a calendar, or no date. */
@Composable
fun ScheduleDialog(task: Task, data: Data, onDismiss: () -> Unit, onPick: (LocalDate?) -> Unit) {
    val today = LocalDate.now()
    val ws = Dates.weekStart(data.settings)
    var calendar by remember { mutableStateOf(false) }
    val f = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    val weekend = QuickAdd.onOrAfter(today.plusDays(1), DayOfWeek.SATURDAY)
    val nextWeek = Dates.startOfWeek(today, ws).plusWeeks(1)
    if (calendar) {
        DatePickDialog(task.due ?: today, onPick = { onPick(it) }, onDismiss = onDismiss)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule") },
        text = {
            Column {
                Text(task.title, Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                @Composable
                fun choice(label: String, day: LocalDate?, icon: androidx.compose.ui.graphics.vector.ImageVector) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(day); onDismiss() }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(label, Modifier.weight(1f).padding(start = 16.dp))
                        if (day != null) Text(f.format(day), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
                choice("Today", today, Icons.Rounded.Today)
                choice("Tomorrow", today.plusDays(1), Icons.Rounded.LightMode)
                choice("This weekend", weekend, Icons.Rounded.Weekend)
                choice("Next week", nextWeek, Icons.Rounded.NextWeek)
                if (task.due != null) choice("No date", null, Icons.Rounded.Block)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { calendar = true }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Pick a date…", Modifier.padding(start = 16.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PriorityMenu(expanded: Boolean, current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        for (p in Priority.P1..Priority.NONE) {
            DropdownMenuItem(
                text = { Text(Priority.label(p)) },
                onClick = { onPick(p); onDismiss() },
                leadingIcon = {
                    Icon(if (p == Priority.NONE) Icons.Outlined.Flag else Icons.Rounded.Flag, null, tint = Color(Priority.color(p)))
                },
                trailingIcon = { if (p == current) Icon(Icons.Rounded.Check, null) },
            )
        }
    }
}

/** Projects as a tree (sub-projects indented). */
@Composable
fun ProjectMenu(expanded: Boolean, data: Data, current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        val rows = listOf(Project.Inbox to 0) + data.projectTree()
        for ((p, depth) in rows) {
            DropdownMenuItem(
                text = { Text(p.name, Modifier.padding(start = (depth * 16).dp)) },
                onClick = { onPick(p.id); onDismiss() },
                leadingIcon = { ColorDot(p.color) },
                trailingIcon = { if (p.id == current) Icon(Icons.Rounded.Check, null) },
            )
        }
    }
}

/** Moving a task: every project, and the sections inside each. */
@Composable
fun MoveDialog(task: Task, data: Data, onDismiss: () -> Unit, onPick: (projectId: String, sectionId: String?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to…") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                val rows = listOf(Project.Inbox to 0) + data.projectTree()
                for ((p, depth) in rows) {
                    val here = task.projectId == p.id && task.sectionId == null
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(p.id, null); onDismiss() }
                            .padding(start = (depth * 16).dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (p.id == INBOX_ID) Icon(Icons.Rounded.Inbox, null, Modifier.size(18.dp), tint = Color(p.color)) else ColorDot(p.color, 12.dp)
                        Text(p.name, Modifier.weight(1f).padding(start = 12.dp))
                        if (here) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                    }
                    for (s in data.sectionsOf(p.id)) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(p.id, s.id); onDismiss() }
                                .semantics { contentDescription = "${p.name} / ${s.name}" }
                                .padding(start = (depth * 16 + 30).dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("/ ${s.name}", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (task.sectionId == s.id) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The common repeats, "Custom…" for the rest, and "Doesn't repeat". */
@Composable
fun RepeatMenu(expanded: Boolean, current: RepeatRule?, onDismiss: () -> Unit, onCustom: () -> Unit, onPick: (RepeatRule?) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Doesn't repeat") }, onClick = { onPick(null); onDismiss() }, trailingIcon = { if (current == null) Icon(Icons.Rounded.Check, null) })
        for (r in RepeatRule.presets) {
            DropdownMenuItem(
                text = { Text(r.label()) },
                onClick = { onPick(r); onDismiss() },
                trailingIcon = { if (r == current) Icon(Icons.Rounded.Check, null) },
            )
        }
        DropdownMenuItem(text = { Text("Custom…") }, onClick = { onDismiss(); onCustom() })
    }
}

/**
 * A repeat made by hand, like Todoist's: every N days, weeks (on chosen days), months (on a day,
 * or the 2nd Tuesday) or years; from the due date or from completion; until a date.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatDialog(initial: RepeatRule?, due: LocalDate?, onDone: (RepeatRule) -> Unit, onDismiss: () -> Unit) {
    val anchor = due ?: LocalDate.now()
    var freq by remember { mutableStateOf(initial?.freq ?: Freq.WEEKLY) }
    var interval by remember { mutableStateOf((initial?.interval ?: 1).toString()) }
    var days by remember { mutableStateOf(initial?.takeIf { it.freq == Freq.WEEKLY }?.weekdays?.takeIf { it.isNotEmpty() } ?: setOf(anchor.dayOfWeek)) }
    // Monthly: 0 = on the same day, 1 = on the nth weekday, 2 = on the last day.
    var monthMode by remember { mutableIntStateOf(when { initial?.nth != null -> 1; initial?.monthDay == 0 -> 2; else -> 0 }) }
    var fromCompletion by remember { mutableStateOf(initial?.fromCompletion ?: false) }
    var until by remember { mutableStateOf(initial?.until) }
    var pickingUntil by remember { mutableStateOf(false) }
    val nth = ((anchor.dayOfMonth - 1) / 7 + 1).let { if (anchor.plusWeeks(1).month != anchor.month) -1 else it }

    fun build(): RepeatRule {
        val n = interval.toIntOrNull()?.coerceIn(1, 999) ?: 1
        return when (freq) {
            Freq.WEEKLY -> RepeatRule(Freq.WEEKLY, n, weekdays = days.ifEmpty { setOf(anchor.dayOfWeek) })
            Freq.MONTHLY -> when (monthMode) {
                1 -> RepeatRule(Freq.MONTHLY, n, weekdays = setOf(anchor.dayOfWeek), nth = nth)
                2 -> RepeatRule(Freq.MONTHLY, n, monthDay = 0)
                else -> RepeatRule(Freq.MONTHLY, n, monthDay = anchor.dayOfMonth)
            }
            else -> RepeatRule(freq, n)
        }.copy(fromCompletion = fromCompletion, until = until)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repeat") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Every")
                    OutlinedTextField(
                        value = interval,
                        onValueChange = { v -> interval = v.filter { it.isDigit() }.take(3) },
                        modifier = Modifier.width(76.dp).padding(horizontal = 10.dp).semantics { contentDescription = "Repeat every how many" },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (f in Freq.entries) {
                        val n = interval.toIntOrNull() ?: 1
                        val name = when (f) { Freq.DAILY -> "day"; Freq.WEEKLY -> "week"; Freq.MONTHLY -> "month"; Freq.YEARLY -> "year" } + if (n == 1) "" else "s"
                        Pill(name, f == freq) { freq = f }
                    }
                }
                if (freq == Freq.WEEKLY) {
                    Text("On", Modifier.padding(top = 16.dp, bottom = 6.dp), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (d in DayOfWeek.entries) {
                            val on = d in days
                            Box(
                                Modifier.size(38.dp).clip(CircleShape)
                                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { days = if (on) days - d else days + d }
                                    .semantics { contentDescription = d.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(d.getDisplayName(TextStyle.NARROW, Locale.getDefault()), color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (freq == Freq.MONTHLY) {
                    val dayName = anchor.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                    for ((mode, label) in listOf(
                        0 to "On day ${anchor.dayOfMonth}",
                        1 to "On the ${RepeatRule.ordinal(nth)} $dayName",
                        2 to "On the last day",
                    )) {
                        Row(Modifier.fillMaxWidth().clickable { monthMode = mode }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = monthMode == mode, onClick = null)
                            Text(label, Modifier.padding(start = 8.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp).clickable { fromCompletion = !fromCompletion }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Count from completion")
                        Text("The next date is set from the day you complete it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = fromCompletion, onCheckedChange = { fromCompletion = it })
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).clickable { pickingUntil = true }, verticalAlignment = Alignment.CenterVertically) {
                    Text(until?.let { "Ends on ${DateTimeFormatter.ofPattern("d MMM yyyy").format(it)}" } ?: "Never ends", Modifier.weight(1f).padding(vertical = 12.dp))
                    if (until != null) IconButton(onClick = { until = null }, Modifier.semantics { contentDescription = "Never ends" }) { Icon(Icons.Rounded.Close, null) }
                }
                Text(build().label(), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = { TextButton(onClick = { onDone(build()); onDismiss() }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickingUntil) DatePickDialog(until ?: anchor.plusMonths(3), onPick = { until = it }, onDismiss = { pickingUntil = false })
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        label,
        Modifier
            .clip(shape)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape) else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
    )
}

/** Adding a reminder: some time before the due time (when it has one), or at a set date and time. */
@Composable
fun ReminderDialog(task: Task, is24Hour: Boolean, onAdd: (Reminder) -> Unit, onDismiss: () -> Unit) {
    var pickingDate by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    val day = pickedDate
    if (day != null) {
        TimePickDialog(task.time ?: LocalTime.of(9, 0), is24Hour, onPick = { t -> onAdd(Reminder(at = LocalDateTime.of(day, t))) }, onDismiss = onDismiss)
        return
    }
    if (pickingDate) {
        DatePickDialog(task.due ?: LocalDate.now(), onPick = { pickedDate = it }, onDismiss = { if (pickedDate == null) onDismiss() })
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add reminder") },
        text = {
            Column {
                if (task.time != null) {
                    for (m in Reminder.presets) {
                        val r = Reminder(minutesBefore = m)
                        val taken = task.reminders.any { it.at == null && it.minutesBefore == m }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = !taken) { onAdd(r); onDismiss() }.padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(r.label(), Modifier.weight(1f), color = if (taken) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                            if (taken) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                } else {
                    Text(
                        "Give the task a time to remind you some minutes before it. Or pick a date and time:",
                        Modifier.padding(bottom = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { pickingDate = true }.padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text("At a date and time…") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** How long a task takes. */
@Composable
fun DurationMenu(expanded: Boolean, current: Int?, onDismiss: () -> Unit, onPick: (Int?) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        for (m in listOf(15, 30, 45, 60, 90, 120, 180, 240, 480)) {
            DropdownMenuItem(
                text = { Text(Dates.durationLabel(m)) },
                onClick = { onPick(m); onDismiss() },
                trailingIcon = { if (m == current) Icon(Icons.Rounded.Check, null) },
            )
        }
        DropdownMenuItem(text = { Text("No duration") }, onClick = { onPick(null); onDismiss() })
    }
}

/** Layout, sorting, grouping and finished tasks for a list. [view] is shown for projects. */
@Composable
fun ViewOptionsDialog(
    options: ViewOptions,
    onChange: (ViewOptions) -> Unit,
    onDismiss: () -> Unit,
    view: io.github.akrishna87.doable.model.ProjectView? = null,
    onView: (io.github.akrishna87.doable.model.ProjectView) -> Unit = {},
    groups: List<GroupBy> = GroupBy.entries,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("View") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                @OptIn(ExperimentalLayoutApi::class)
                @Composable
                fun pills(title: String, content: @Composable () -> Unit) {
                    Text(title, Modifier.padding(top = 12.dp, bottom = 6.dp), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
                }
                if (view != null) {
                    pills("Layout") {
                        for (v in io.github.akrishna87.doable.model.ProjectView.entries) Pill(v.label, v == view) { onView(v) }
                    }
                }
                pills("Sort by") {
                    for (s in SortBy.entries) Pill(s.label, s == options.sort) { onChange(options.copy(sort = s)) }
                }
                if (options.sort != SortBy.MANUAL) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).clickable { onChange(options.copy(descending = !options.descending)) }, verticalAlignment = Alignment.CenterVertically) {
                        Text("Reverse order", Modifier.weight(1f))
                        Switch(checked = options.descending, onCheckedChange = { onChange(options.copy(descending = it)) })
                    }
                }
                pills("Group by") {
                    for (g in groups) Pill(g.label, g == options.group) { onChange(options.copy(group = g)) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).clickable { onChange(options.copy(showCompleted = !options.showCompleted)) }, verticalAlignment = Alignment.CenterVertically) {
                    Text("Show completed tasks", Modifier.weight(1f))
                    Switch(checked = options.showCompleted, onCheckedChange = { onChange(options.copy(showCompleted = it)) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** Ticks labels on and off, and makes new ones. */
@Composable
fun LabelsDialog(all: List<String>, selected: List<String>, onDone: (List<String>) -> Unit, onDismiss: () -> Unit) =
    LabelsDialogFor("@", "Labels", "New label", all, selected, onDone, onDismiss)

/** Picks labels (marked "@") or tags ("#"): tick existing ones, or type a new one. */
@Composable
fun LabelsDialogFor(
    mark: String,
    title: String,
    newHint: String,
    all: List<String>,
    selected: List<String>,
    onDone: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(selected) }
    var known by remember { mutableStateOf((all + selected).distinctBy { it.lowercase() }) }
    var draft by remember { mutableStateOf("") }
    fun addDraft() {
        val l = draft.trim().removePrefix(mark).replace(" ", "-")
        if (l.isEmpty()) return
        if (known.none { it.equals(l, true) }) known = known + l
        if (chosen.none { it.equals(l, true) }) chosen = chosen + l
        draft = ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(newHint) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addDraft() }),
                    trailingIcon = { TextButton(onClick = ::addDraft, enabled = draft.isNotBlank()) { Text("Add") } },
                )
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                    if (known.isEmpty()) {
                        Text("None yet. Type one above.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    for (l in known) {
                        val on = chosen.any { it.equals(l, true) }
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Checkbox) {
                                chosen = if (on) chosen.filterNot { it.equals(l, true) } else chosen + l
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = null)
                            Text("$mark$l", Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { addDraft(); onDone(chosen); onDismiss() }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A name in a box, for new projects, sections, labels and filters; with colours, a favourite switch and a parent when asked for. */
@Composable
fun NameDialog(
    title: String,
    initial: String = "",
    label: String = "Name",
    confirm: String = "Save",
    colors: List<Int>? = null,
    initialColor: Int = 0,
    favorite: Boolean? = null,
    onSave: (String, Int) -> Unit,
    onFavorite: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    var color by remember { mutableIntStateOf(if (initialColor != 0) initialColor else colors?.getOrNull(9) ?: 0) }
    var fav by remember { mutableStateOf(favorite ?: false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun save() {
        if (name.isBlank()) return
        onFavorite(fav)
        onSave(name.trim(), color)
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                )
                if (colors != null) {
                    ColorChoices(colors, color, { color = it }, Modifier.padding(top = 16.dp))
                }
                if (favorite != null) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp).clickable { fav = !fav }, verticalAlignment = Alignment.CenterVertically) {
                        Text("Add to favourites", Modifier.weight(1f))
                        Switch(checked = fav, onCheckedChange = { fav = it })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = ::save, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorChoices(colors: List<Int>, selected: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (c in colors) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.RadioButton, onClickLabel = "Choose colour") { onPick(c) },
                contentAlignment = Alignment.Center,
            ) {
                ColorDot(c, 30.dp)
                if (c == selected) Icon(Icons.Rounded.Check, "Selected", Modifier.size(18.dp), tint = Color.White)
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Default colour for a new project. */
val NewProjectColor = ProjectColors.Default
