package io.github.akrishna87.doable

import android.content.Context
import io.github.akrishna87.doable.model.Data
import io.github.akrishna87.doable.model.Dates
import io.github.akrishna87.doable.model.INBOX_ID
import io.github.akrishna87.doable.model.Project
import io.github.akrishna87.doable.model.Repeat
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
 * Doable's tasks, projects and settings, kept in memory and saved to one JSON
 * file. The app, the widget and the reminders all read the same [data], so a change anywhere shows
 * up everywhere at once.
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
        if (loaded == null || !f.readTextOrEmpty().contains("\"version\":1")) write(state.value)
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

    // Tasks

    fun saveTask(t: Task) = update { d ->
        val i = d.tasks.indexOfFirst { it.id == t.id }
        d.copy(tasks = if (i < 0) d.tasks + t else d.tasks.toMutableList().also { it[i] = t })
    }

    /** Puts tasks back exactly as they were (for Undo), adding any that were deleted. */
    fun restoreTasks(tasks: List<Task>) = update { d ->
        val byId = tasks.associateBy { it.id }
        val kept = d.tasks.map { byId[it.id] ?: it }
        d.copy(tasks = kept + tasks.filter { t -> d.tasks.none { it.id == t.id } })
    }

    /** The task and its subtasks, as they are now, for Undo. */
    fun taskFamily(id: String): List<Task> = state.value.tasks.filter { it.id == id || it.parentId == id }

    /**
     * Completes a task and its subtasks. A repeating task with a date moves to its next date
     * instead, and that date is returned.
     */
    fun completeTask(id: String, today: LocalDate = LocalDate.now()): LocalDate? {
        var nextDate: LocalDate? = null
        update { d ->
            val t = d.task(id) ?: return@update d
            val now = System.currentTimeMillis()
            val due = t.due
            if (t.repeat != Repeat.NONE && due != null) {
                val next = Dates.nextDue(due, t.repeat, today)
                nextDate = next
                d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(due = next) else it })
            } else {
                d.copy(tasks = d.tasks.map { if (it.id == id || (it.parentId == id && !it.done)) it.copy(done = true, doneAt = now) else it })
            }
        }
        return nextDate
    }

    fun uncompleteTask(id: String) = update { d ->
        d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(done = false, doneAt = 0L) else it })
    }

    /** Deletes a task with its subtasks. */
    fun deleteTask(id: String) = update { d -> d.copy(tasks = d.tasks.filter { it.id != id && it.parentId != id }) }

    /** Moves every overdue task to today. */
    fun rescheduleOverdue(today: LocalDate = LocalDate.now()) = update { d ->
        d.copy(tasks = d.tasks.map { if (!it.done && it.due != null && it.due.isBefore(today)) it.copy(due = today) else it })
    }

    fun clearCompleted() = update { d -> d.copy(tasks = d.tasks.filter { !it.done }) }

    fun renameLabel(old: String, new: String) = update { d ->
        d.copy(tasks = d.tasks.map { t -> t.copy(labels = t.labels.map { if (it.equals(old, true)) new else it }.distinctBy { it.lowercase() }) })
    }

    fun deleteLabel(label: String) = update { d ->
        d.copy(tasks = d.tasks.map { t -> t.copy(labels = t.labels.filterNot { it.equals(label, true) }) })
    }

    // Projects

    fun saveProject(p: Project) = update { d ->
        val i = d.projects.indexOfFirst { it.id == p.id }
        d.copy(projects = if (i < 0) d.projects + p else d.projects.toMutableList().also { it[i] = p })
    }

    /** Deletes a project and its tasks. */
    fun deleteProject(id: String) = update { d ->
        if (id == INBOX_ID) d else d.copy(projects = d.projects.filter { it.id != id }, tasks = d.tasks.filter { it.projectId != id })
    }

    fun updateSettings(transform: (Settings) -> Settings) = update { d -> d.copy(settings = transform(d.settings)) }
}
