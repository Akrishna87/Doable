package io.github.akrishna87.doable

import android.content.Context
import io.github.akrishna87.doable.model.Activity
import io.github.akrishna87.doable.model.ActivityKind
import io.github.akrishna87.doable.model.Comment
import io.github.akrishna87.doable.model.Csv
import io.github.akrishna87.doable.model.Data
import io.github.akrishna87.doable.model.Filter
import io.github.akrishna87.doable.model.INBOX_ID
import io.github.akrishna87.doable.model.LabelInfo
import io.github.akrishna87.doable.model.Project
import io.github.akrishna87.doable.model.Reminder
import io.github.akrishna87.doable.model.Section
import io.github.akrishna87.doable.model.Settings
import io.github.akrishna87.doable.model.Task
import io.github.akrishna87.doable.widget.TodayWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * Doable's tasks, projects, sections, labels, filters, activity and settings, kept in memory and
 * saved to one JSON file. The app, the widgets and the reminders all read the same [data], so a
 * change anywhere shows up everywhere at once.
 */
object Store {
    private val state = MutableStateFlow(Data())
    val data: StateFlow<Data> = state

    private val writer = Executors.newSingleThreadExecutor()
    private var file: File? = null
    private lateinit var app: Context

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        app = context.applicationContext
        val f = File(app.filesDir, "doable.json")
        file = f
        val loaded = runCatching { if (f.exists()) Data.fromJson(JSONObject(f.readText())) else null }.getOrNull()
        state.value = loaded ?: Data.firstRun()
        if (loaded == null || !f.readTextOrEmpty().contains("\"version\":2")) write(state.value)
    }

    private fun File.readTextOrEmpty() = runCatching { readText() }.getOrDefault("")

    fun update(transform: (Data) -> Data) {
        val next = synchronized(this) { transform(state.value).also { state.value = it } }
        write(next)
        TodayWidget.refresh(app)
        Reminders.sync(app, next)
    }

    /** Replaces everything, for restoring a backup. */
    fun replaceAll(d: Data) = update { d }

    private fun write(d: Data) {
        val f = file ?: return
        val json = d.toJson().toString()
        writer.execute {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(f)) {
                f.writeText(json)
                tmp.delete()
            }
        }
    }

    /** Adds lines to the activity log, keeping the newest [Activity.KEEP]. */
    private fun Data.log(kind: ActivityKind, tasks: List<Task>): Data {
        if (tasks.isEmpty()) return this
        val now = System.currentTimeMillis()
        val added = tasks.map { Activity(now, kind, it.id, it.title, it.projectId) }
        return copy(activity = (activity + added).takeLast(Activity.KEEP))
    }

    // Tasks

    /**
     * Saves a task. A new one goes to the end of its list, and a task given a time for the first
     * time gets the default reminder (Settings → Reminders).
     */
    fun saveTask(t: Task) = update { d ->
        val i = d.tasks.indexOfFirst { it.id == t.id }
        if (i < 0) {
            val placed = withDefaults(t, d.settings, null)
            d.copy(tasks = d.tasks + placed).log(ActivityKind.ADDED, listOf(placed))
        } else {
            val placed = withDefaults(t, d.settings, d.tasks[i])
            d.copy(tasks = d.tasks.toMutableList().also { it[i] = placed })
        }
    }

    private fun withDefaults(t: Task, s: Settings, before: Task?): Task {
        var out = t
        if (before == null && t.order == 0L) out = out.copy(order = System.currentTimeMillis())
        val gainedTime = t.time != null && (before == null || before.time == null)
        if (gainedTime && t.reminders.isEmpty() && s.autoReminder != null && (before == null || before.reminders.isEmpty())) {
            out = out.copy(reminders = listOf(Reminder(minutesBefore = s.autoReminder)))
        }
        // Reminders before the due time make no sense without one.
        if (t.time == null && t.reminders.any { it.at == null }) out = out.copy(reminders = out.reminders.filter { it.at != null })
        return out
    }

    /** Adds many tasks at once (from a template or import), without default reminders. */
    fun addTasks(tasks: List<Task>) = update { d -> d.copy(tasks = d.tasks + tasks).log(ActivityKind.ADDED, tasks) }

    /** Puts tasks back exactly as they were (for Undo), adding any that were deleted. */
    fun restoreTasks(tasks: List<Task>) = update { d ->
        val byId = tasks.associateBy { it.id }
        val kept = d.tasks.map { byId[it.id] ?: it }
        d.copy(tasks = kept + tasks.filter { t -> d.tasks.none { it.id == t.id } })
    }

    /** The task and everything under it, as they are now, for Undo. */
    fun taskFamily(id: String): List<Task> {
        val d = state.value
        val ids = mutableSetOf(id)
        var grew = true
        while (grew) {
            val more = d.tasks.filter { it.parentId in ids && it.id !in ids }.map { it.id }
            grew = more.isNotEmpty()
            ids += more
        }
        return d.tasks.filter { it.id in ids }
    }

    /**
     * Completes a task and its sub-tasks. A repeating task with a date moves to its next date
     * instead, and that date is returned (null when it doesn't repeat, or the repeat has ended).
     */
    fun completeTask(id: String, today: LocalDate = LocalDate.now()): LocalDate? {
        var nextDate: LocalDate? = null
        update { d ->
            val t = d.task(id) ?: return@update d
            val now = System.currentTimeMillis()
            val due = t.due
            val rule = t.repeat
            val next = if (rule != null && due != null) rule.next(due, today, today) else null
            if (next != null) {
                nextDate = next
                // Fixed-time reminders were for this occurrence; reminders before the due time move with it.
                d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(due = next, reminders = it.reminders.filter { r -> r.at == null }) else it })
                    .log(ActivityKind.COMPLETED, listOf(t))
            } else {
                val family = taskFamily(id).map { it.id }.toSet()
                d.copy(tasks = d.tasks.map { if (it.id in family && !it.done) it.copy(done = true, doneAt = now) else it })
                    .log(ActivityKind.COMPLETED, listOf(t))
            }
        }
        return nextDate
    }

    fun uncompleteTask(id: String) = update { d ->
        val t = d.task(id) ?: return@update d
        d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(done = false, doneAt = 0L) else it }).log(ActivityKind.UNCOMPLETED, listOf(t))
    }

    /** Deletes a task with its sub-tasks. */
    fun deleteTask(id: String) = update { d ->
        val family = taskFamily(id).map { it.id }.toSet()
        d.copy(tasks = d.tasks.filter { it.id !in family }).log(ActivityKind.DELETED, listOfNotNull(d.task(id)))
    }

    /** Moves a task (and its sub-tasks) to a project and section, at the end of the list there. */
    fun moveTask(id: String, projectId: String, sectionId: String?) = update { d ->
        val family = taskFamily(id).map { it.id }.toSet()
        val now = System.currentTimeMillis()
        d.copy(tasks = d.tasks.map {
            when {
                it.id == id -> it.copy(projectId = projectId, sectionId = sectionId, parentId = null, order = now)
                it.id in family -> it.copy(projectId = projectId, sectionId = sectionId)
                else -> it
            }
        })
    }

    /** Puts [id] just before [beforeId] in its list (or at the end, with null), for sorting by hand. */
    fun placeTask(id: String, beforeId: String?, siblings: List<Task>) = update { d ->
        val list = siblings.filter { it.id != id }.sortedWith(compareBy({ it.order }, { it.createdAt }))
        val at = if (beforeId == null) list.size else list.indexOfFirst { it.id == beforeId }.let { if (it < 0) list.size else it }
        val prev = list.getOrNull(at - 1)?.order
        val next = list.getOrNull(at)?.order
        val order = when {
            prev == null && next == null -> System.currentTimeMillis()
            prev == null -> next!! - 1000
            next == null -> prev + 1000
            next - prev > 1 -> prev + (next - prev) / 2
            else -> null
        }
        if (order != null) {
            d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(order = order) else it })
        } else {
            // No room between the two: number the whole list again.
            val renumbered = (list.subList(0, at) + listOfNotNull(d.task(id)) + list.subList(at, list.size)).withIndex().associate { it.value.id to (it.index + 1) * 1000L }
            d.copy(tasks = d.tasks.map { t -> renumbered[t.id]?.let { t.copy(order = it) } ?: t })
        }
    }

    /** Moves every overdue task to [day] (today by default). */
    fun rescheduleOverdue(today: LocalDate = LocalDate.now(), day: LocalDate = today) = update { d ->
        d.copy(tasks = d.tasks.map { if (!it.done && it.due != null && it.due.isBefore(today)) it.copy(due = day) else it })
    }

    fun clearCompleted() = update { d -> d.copy(tasks = d.tasks.filter { !it.done }) }

    fun addComment(taskId: String, c: Comment) = update { d ->
        val t = d.task(taskId) ?: return@update d
        d.copy(tasks = d.tasks.map { if (it.id == taskId) it.copy(comments = it.comments + c) else it }).log(ActivityKind.COMMENTED, listOf(t))
    }

    fun deleteComment(taskId: String, commentId: String) = update { d ->
        d.copy(tasks = d.tasks.map { if (it.id == taskId) it.copy(comments = it.comments.filter { c -> c.id != commentId }) else it })
    }

    // Labels

    fun renameLabel(old: String, new: String) = update { d ->
        d.copy(
            tasks = d.tasks.map { t -> t.copy(labels = t.labels.map { if (it.equals(old, true)) new else it }.distinctBy { it.lowercase() }) },
            labelInfo = d.labelInfo.map { if (it.name.equals(old, true)) it.copy(name = new) else it },
        )
    }

    fun deleteLabel(label: String) = update { d ->
        d.copy(
            tasks = d.tasks.map { t -> t.copy(labels = t.labels.filterNot { it.equals(label, true) }) },
            labelInfo = d.labelInfo.filterNot { it.name.equals(label, true) },
        )
    }

    fun saveLabel(info: LabelInfo) = update { d ->
        d.copy(labelInfo = d.labelInfo.filterNot { it.name.equals(info.name, true) } + info)
    }

    // Projects

    fun saveProject(p: Project) = update { d ->
        val i = d.projects.indexOfFirst { it.id == p.id }
        val placed = if (i < 0 && p.order == 0L) p.copy(order = System.currentTimeMillis()) else p
        d.copy(projects = if (i < 0) d.projects + placed else d.projects.toMutableList().also { it[i] = placed })
    }

    /** Deletes a project with its sub-projects, sections and tasks. */
    fun deleteProject(id: String) = update { d ->
        if (id == INBOX_ID) return@update d
        val ids = d.projectAndChildren(id)
        d.copy(
            projects = d.projects.filter { it.id !in ids },
            sections = d.sections.filter { it.projectId !in ids },
            tasks = d.tasks.filter { it.projectId !in ids },
        )
    }

    /** Archives (or brings back) a project with its sub-projects. */
    fun archiveProject(id: String, archived: Boolean) = update { d ->
        val ids = d.projectAndChildren(id)
        d.copy(projects = d.projects.map { if (it.id in ids) it.copy(archived = archived) else it })
    }

    /** Moves a project up or down among the projects next to it. */
    fun moveProject(id: String, by: Int) = update { d ->
        val p = d.projects.firstOrNull { it.id == id } ?: return@update d
        val siblings = d.projects.filter { it.parentId == p.parentId && !it.archived }.sortedWith(compareBy({ it.order }, { it.createdAt }))
        val i = siblings.indexOfFirst { it.id == id }
        val j = (i + by).coerceIn(0, siblings.lastIndex)
        if (i == j) return@update d
        val reordered = siblings.toMutableList().apply { add(j, removeAt(i)) }.withIndex().associate { it.value.id to (it.index + 1) * 1000L }
        d.copy(projects = d.projects.map { pr -> reordered[pr.id]?.let { pr.copy(order = it) } ?: pr })
    }

    /** Adds an imported project with its sections and tasks. */
    fun import(r: Csv.Imported) = update { d ->
        d.copy(projects = d.projects + r.project, sections = d.sections + r.sections, tasks = d.tasks + r.tasks).log(ActivityKind.ADDED, r.tasks)
    }

    // Sections

    fun saveSection(s: Section) = update { d ->
        val i = d.sections.indexOfFirst { it.id == s.id }
        d.copy(sections = if (i < 0) d.sections + s else d.sections.toMutableList().also { it[i] = s })
    }

    /** Deletes a section and its tasks. */
    fun deleteSection(id: String) = update { d ->
        d.copy(sections = d.sections.filter { it.id != id }, tasks = d.tasks.filter { it.sectionId != id })
    }

    fun moveSection(id: String, by: Int) = update { d ->
        val s = d.section(id) ?: return@update d
        val siblings = d.sectionsOf(s.projectId)
        val i = siblings.indexOfFirst { it.id == id }
        val j = (i + by).coerceIn(0, siblings.lastIndex)
        if (i == j) return@update d
        val reordered = siblings.toMutableList().apply { add(j, removeAt(i)) }.withIndex().associate { it.value.id to (it.index + 1).toLong() }
        d.copy(sections = d.sections.map { sec -> reordered[sec.id]?.let { sec.copy(order = it) } ?: sec })
    }

    // Filters

    fun saveFilter(f: Filter) = update { d ->
        val i = d.filters.indexOfFirst { it.id == f.id }
        d.copy(filters = if (i < 0) d.filters + f else d.filters.toMutableList().also { it[i] = f })
    }

    fun deleteFilter(id: String) = update { d -> d.copy(filters = d.filters.filter { it.id != id }) }

    fun updateSettings(transform: (Settings) -> Settings) = update { d -> d.copy(settings = transform(d.settings)) }
}
