package io.github.akrishna87.doable.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

const val INBOX_ID = "inbox"

/** Priority 1 is the most urgent; 4 means no priority, as in Todoist. */
object Priority {
    const val P1 = 1
    const val P2 = 2
    const val P3 = 3
    const val NONE = 4

    fun color(p: Int): Int = when (p) {
        P1 -> 0xFFD1453B.toInt()
        P2 -> 0xFFEB8909.toInt()
        P3 -> 0xFF246FE0.toInt()
        else -> 0xFF8A8C99.toInt()
    }

    fun label(p: Int): String = if (p in P1..P3) "Priority $p" else "No priority"
}

enum class Repeat(val label: String) {
    NONE("Doesn't repeat"),
    DAILY("Every day"),
    WEEKDAYS("Every weekday"),
    WEEKLY("Every week"),
    MONTHLY("Every month"),
    YEARLY("Every year"),
}

/** Colours for projects. */
object ProjectColors {
    val choices = listOf(
        0xFFD1453B.toInt(), // red
        0xFFEB8909.toInt(), // orange
        0xFFE0B000.toInt(), // yellow
        0xFF299438.toInt(), // green
        0xFF158FAD.toInt(), // teal
        0xFF246FE0.toInt(), // blue
        0xFF5B5BD6.toInt(), // indigo
        0xFFAF38EB.toInt(), // violet
        0xFFE05194.toInt(), // pink
        0xFF808080.toInt(), // grey
    )
    val Inbox = 0xFF246FE0.toInt()
}

data class Task(
    val id: String = newId(),
    val title: String,
    val description: String = "",
    val projectId: String = INBOX_ID,
    val labels: List<String> = emptyList(),
    val priority: Int = Priority.NONE,
    val due: LocalDate? = null,
    /** Only meaningful with a [due] date. */
    val time: LocalTime? = null,
    val repeat: Repeat = Repeat.NONE,
    /** Notify at the due time (only for tasks with a time). */
    val reminder: Boolean = true,
    /** Set on subtasks: the task they belong to. */
    val parentId: String? = null,
    val done: Boolean = false,
    val doneAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("description", description)
        .put("projectId", projectId)
        .put("labels", JSONArray(labels))
        .put("priority", priority)
        .put("due", due?.toString() ?: "")
        .put("time", time?.toString() ?: "")
        .put("repeat", repeat.name)
        .put("reminder", reminder)
        .put("parentId", parentId ?: "")
        .put("done", done)
        .put("doneAt", doneAt)
        .put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject) = Task(
            id = o.optString("id").ifEmpty { newId() },
            title = o.optString("title"),
            description = o.optString("description"),
            projectId = o.optString("projectId").ifEmpty { INBOX_ID },
            labels = o.optJSONArray("labels").strings(),
            priority = o.optInt("priority", Priority.NONE).coerceIn(Priority.P1, Priority.NONE),
            due = o.optString("due").takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
            time = o.optString("time").takeIf { it.isNotEmpty() }?.let(LocalTime::parse),
            repeat = runCatching { Repeat.valueOf(o.optString("repeat")) }.getOrDefault(Repeat.NONE),
            reminder = o.optBoolean("reminder", true),
            parentId = o.optString("parentId").ifEmpty { null },
            done = o.optBoolean("done"),
            doneAt = o.optLong("doneAt"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )
    }
}

data class Project(
    val id: String = newId(),
    val name: String,
    val color: Int = ProjectColors.choices[6],
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("color", color).put("createdAt", createdAt)

    companion object {
        val Inbox = Project(INBOX_ID, "Inbox", ProjectColors.Inbox, 0L)

        fun fromJson(o: JSONObject) = Project(
            id = o.optString("id").ifEmpty { newId() },
            name = o.optString("name"),
            color = o.optInt("color", ProjectColors.choices[6]),
            createdAt = o.optLong("createdAt"),
        )
    }
}

enum class ThemeMode(val label: String) { SYSTEM("System default"), LIGHT("Light"), DARK("Dark") }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val weekStartsSunday: Boolean = false,
    val reminders: Boolean = true,
    /** Doable has asked once for permission to post notifications. */
    val askedNotifications: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("theme", theme.name)
        .put("weekStartsSunday", weekStartsSunday)
        .put("reminders", reminders)
        .put("askedNotifications", askedNotifications)

    companion object {
        fun fromJson(o: JSONObject?): Settings {
            val d = Settings()
            if (o == null) return d
            return Settings(
                theme = runCatching { ThemeMode.valueOf(o.optString("theme")) }.getOrDefault(d.theme),
                weekStartsSunday = o.optBoolean("weekStartsSunday", d.weekStartsSunday),
                reminders = o.optBoolean("reminders", d.reminders),
                askedNotifications = o.optBoolean("askedNotifications", d.askedNotifications),
            )
        }
    }
}

/** Everything Doable keeps, saved as one JSON file (and exported as the same JSON for backups). */
data class Data(
    val tasks: List<Task> = emptyList(),
    val projects: List<Project> = emptyList(),
    val settings: Settings = Settings(),
) {
    /** The Inbox first, then the user's projects in the order they were made. */
    val allProjects: List<Project> get() = listOf(Project.Inbox) + projects.sortedBy { it.createdAt }

    fun project(id: String?): Project = projects.firstOrNull { it.id == id } ?: Project.Inbox

    fun task(id: String?): Task? = tasks.firstOrNull { it.id == id }

    fun subtasks(parentId: String): List<Task> = tasks.filter { it.parentId == parentId }.sortedBy { it.createdAt }

    /** Every label used on a task, alphabetically. */
    val labels: List<String> get() = tasks.flatMap { it.labels }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }

    fun toJson(): JSONObject = JSONObject()
        .put("version", 1)
        .put("tasks", JSONArray(tasks.map { it.toJson() }))
        .put("projects", JSONArray(projects.map { it.toJson() }))
        .put("settings", settings.toJson())

    companion object {
        fun fromJson(o: JSONObject): Data {
            val projects = o.optJSONArray("projects").objects().map(Project::fromJson)
            val tasks = o.optJSONArray("tasks").objects().map(Task::fromJson).map { t ->
                if (t.projectId == INBOX_ID || projects.any { it.id == t.projectId }) t else t.copy(projectId = INBOX_ID)
            }
            return Data(
                tasks = tasks,
                projects = projects,
                settings = Settings.fromJson(o.optJSONObject("settings")),
            )
        }

        /** What a new install starts with: three tasks that teach the basics. */
        fun firstRun(): Data {
            val now = System.currentTimeMillis()
            return Data(
                tasks = listOf(
                    Task(title = "Add a task with Quick Add: try “Pay rent friday 9am p1 @bills every month”", createdAt = now),
                    Task(title = "Swipe a task right to complete it, or left to delete it", createdAt = now + 1),
                    Task(title = "Tap a task to add a description, subtasks or a reminder", createdAt = now + 2),
                ),
            )
        }
    }
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else List(length()) { getJSONObject(it) }

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else List(length()) { getString(it) }.filter { it.isNotBlank() }
