package io.github.akrishna87.doable.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
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

/** Colours for projects, labels and filters. */
object ProjectColors {
    val choices = listOf(
        0xFFD1453B.toInt(), // red
        0xFFEB8909.toInt(), // orange
        0xFFE0B000.toInt(), // yellow
        0xFF7ECC49.toInt(), // lime
        0xFF299438.toInt(), // green
        0xFF6ACCBC.toInt(), // mint
        0xFF158FAD.toInt(), // teal
        0xFF14AAF5.toInt(), // sky
        0xFF246FE0.toInt(), // blue
        0xFF5B5BD6.toInt(), // indigo
        0xFFAF38EB.toInt(), // violet
        0xFFEB96EB.toInt(), // lavender
        0xFFE05194.toInt(), // pink
        0xFFFF8D85.toInt(), // salmon
        0xFF808080.toInt(), // grey
        0xFFCCAC93.toInt(), // taupe
    )
    val Inbox = 0xFF246FE0.toInt()
    val Default = 0xFF5B5BD6.toInt()
}

/** How a project shows its tasks. */
enum class ProjectView(val label: String) { LIST("List"), BOARD("Board"), CALENDAR("Calendar") }

enum class SortBy(val label: String) { MANUAL("Manual"), DATE("Date"), PRIORITY("Priority"), NAME("Name"), ADDED("Date added") }

enum class GroupBy(val label: String) { NONE("None"), DATE("Date"), PRIORITY("Priority"), LABEL("Label"), PROJECT("Project") }

/** How a list is laid out: its sort, grouping and whether finished tasks show. Each project, label and filter keeps its own. */
data class ViewOptions(
    val sort: SortBy = SortBy.MANUAL,
    val descending: Boolean = false,
    val group: GroupBy = GroupBy.NONE,
    val showCompleted: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("sort", sort.name).put("descending", descending).put("group", group.name).put("showCompleted", showCompleted)

    companion object {
        fun fromJson(o: JSONObject?): ViewOptions {
            if (o == null) return ViewOptions()
            return ViewOptions(
                sort = runCatching { SortBy.valueOf(o.optString("sort")) }.getOrDefault(SortBy.MANUAL),
                descending = o.optBoolean("descending"),
                group = runCatching { GroupBy.valueOf(o.optString("group")) }.getOrDefault(GroupBy.NONE),
                showCompleted = o.optBoolean("showCompleted"),
            )
        }
    }
}

/**
 * A reminder: either [minutesBefore] the task's due time (it moves with the task, and with each
 * repeat), or at a fixed moment [at].
 */
data class Reminder(
    val id: String = newId(),
    val minutesBefore: Int = 0,
    val at: LocalDateTime? = null,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("minutesBefore", minutesBefore).put("at", at?.toString() ?: "")

    /** When it goes off for [task], or null if it can't (a relative reminder on a task with no time). */
    fun time(task: Task): LocalDateTime? = at ?: task.due?.let { d -> task.time?.let { LocalDateTime.of(d, it).minusMinutes(minutesBefore.toLong()) } }

    fun label(): String = when {
        at != null -> "At a set time"
        minutesBefore == 0 -> "At the due time"
        minutesBefore % 1440 == 0 -> plural(minutesBefore / 1440, "day") + " before"
        minutesBefore % 60 == 0 -> plural(minutesBefore / 60, "hour") + " before"
        else -> plural(minutesBefore, "minute") + " before"
    }

    companion object {
        /** The choices offered for a reminder before the due time, in minutes. */
        val presets = listOf(0, 5, 10, 15, 30, 60, 120, 1440)

        fun fromJson(o: JSONObject) = Reminder(
            id = o.optString("id").ifEmpty { newId() },
            minutesBefore = o.optInt("minutesBefore", 0).coerceAtLeast(0),
            at = o.optString("at").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
        )
    }
}

/** A file kept with a comment: a photo or document copied into the app's own storage. */
data class Attachment(val file: String, val name: String, val mime: String, val size: Long) {
    val isImage: Boolean get() = mime.startsWith("image/")

    fun toJson(): JSONObject = JSONObject().put("file", file).put("name", name).put("mime", mime).put("size", size)

    companion object {
        fun fromJson(o: JSONObject?): Attachment? {
            if (o == null || o.optString("file").isEmpty()) return null
            return Attachment(o.optString("file"), o.optString("name"), o.optString("mime"), o.optLong("size"))
        }
    }
}

/** A comment on a task, Todoist style: some text and maybe a file. */
data class Comment(
    val id: String = newId(),
    val text: String,
    val attachment: Attachment? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("text", text).put("attachment", attachment?.toJson() ?: JSONObject.NULL).put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject) = Comment(
            id = o.optString("id").ifEmpty { newId() },
            text = o.optString("text"),
            attachment = Attachment.fromJson(o.optJSONObject("attachment")),
            createdAt = o.optLong("createdAt"),
        )
    }
}

data class Task(
    val id: String = newId(),
    val title: String,
    val description: String = "",
    val projectId: String = INBOX_ID,
    val sectionId: String? = null,
    val labels: List<String> = emptyList(),
    val priority: Int = Priority.NONE,
    val due: LocalDate? = null,
    /** Only meaningful with a [due] date. */
    val time: LocalTime? = null,
    /** How long it takes, in minutes. */
    val duration: Int? = null,
    /** The date it really must be done by, apart from when it's planned ([due]). */
    val deadline: LocalDate? = null,
    val repeat: RepeatRule? = null,
    val reminders: List<Reminder> = emptyList(),
    /** Set on subtasks: the task they belong to. */
    val parentId: String? = null,
    val comments: List<Comment> = emptyList(),
    /** Where it sits when a list is sorted by hand; smaller first. */
    val order: Long = 0L,
    val done: Boolean = false,
    val doneAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("description", description)
        .put("projectId", projectId)
        .put("sectionId", sectionId ?: "")
        .put("labels", JSONArray(labels))
        .put("priority", priority)
        .put("due", due?.toString() ?: "")
        .put("time", time?.toString() ?: "")
        .put("duration", duration ?: 0)
        .put("deadline", deadline?.toString() ?: "")
        .put("rule", repeat?.toJson() ?: JSONObject.NULL)
        .put("reminders", JSONArray(reminders.map { it.toJson() }))
        .put("parentId", parentId ?: "")
        .put("comments", JSONArray(comments.map { it.toJson() }))
        .put("order", order)
        .put("done", done)
        .put("doneAt", doneAt)
        .put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject): Task {
            val time = o.optString("time").takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            val created = o.optLong("createdAt", System.currentTimeMillis())
            // Doable 1 had one switch, "remind me at the due time", on by default.
            val reminders = if (o.has("reminders")) {
                o.optJSONArray("reminders").objects().map(Reminder::fromJson)
            } else if (time != null && o.optBoolean("reminder", true)) {
                listOf(Reminder(minutesBefore = 0))
            } else {
                emptyList()
            }
            return Task(
                id = o.optString("id").ifEmpty { newId() },
                title = o.optString("title"),
                description = o.optString("description"),
                projectId = o.optString("projectId").ifEmpty { INBOX_ID },
                sectionId = o.optString("sectionId").ifEmpty { null },
                labels = o.optJSONArray("labels").strings(),
                priority = o.optInt("priority", Priority.NONE).coerceIn(Priority.P1, Priority.NONE),
                due = o.optString("due").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                time = time,
                duration = o.optInt("duration", 0).takeIf { it > 0 },
                deadline = o.optString("deadline").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                repeat = RepeatRule.fromJson(o.optJSONObject("rule")) ?: RepeatRule.fromLegacy(o.optString("repeat")),
                reminders = reminders,
                parentId = o.optString("parentId").ifEmpty { null },
                comments = o.optJSONArray("comments").objects().map(Comment::fromJson),
                order = o.optLong("order", created),
                done = o.optBoolean("done"),
                doneAt = o.optLong("doneAt"),
                createdAt = created,
            )
        }
    }
}

data class Project(
    val id: String = newId(),
    val name: String,
    val color: Int = ProjectColors.Default,
    /** Set on sub-projects. */
    val parentId: String? = null,
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val view: ProjectView = ProjectView.LIST,
    val options: ViewOptions = ViewOptions(),
    val order: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("color", color)
        .put("parentId", parentId ?: "")
        .put("favorite", favorite)
        .put("archived", archived)
        .put("view", view.name)
        .put("options", options.toJson())
        .put("order", order)
        .put("createdAt", createdAt)

    companion object {
        val Inbox = Project(INBOX_ID, "Inbox", ProjectColors.Inbox, createdAt = 0L)

        fun fromJson(o: JSONObject): Project {
            val created = o.optLong("createdAt")
            return Project(
                id = o.optString("id").ifEmpty { newId() },
                name = o.optString("name"),
                color = o.optInt("color", ProjectColors.Default),
                parentId = o.optString("parentId").ifEmpty { null },
                favorite = o.optBoolean("favorite"),
                archived = o.optBoolean("archived"),
                view = runCatching { ProjectView.valueOf(o.optString("view")) }.getOrDefault(ProjectView.LIST),
                options = ViewOptions.fromJson(o.optJSONObject("options")),
                order = o.optLong("order", created),
                createdAt = created,
            )
        }
    }
}

/** A heading inside a project that tasks are grouped under (a column in board view). */
data class Section(
    val id: String = newId(),
    val projectId: String,
    val name: String,
    val order: Long = System.currentTimeMillis(),
    val collapsed: Boolean = false,
    val archived: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("projectId", projectId).put("name", name).put("order", order).put("collapsed", collapsed).put("archived", archived)

    companion object {
        fun fromJson(o: JSONObject) = Section(
            id = o.optString("id").ifEmpty { newId() },
            projectId = o.optString("projectId").ifEmpty { INBOX_ID },
            name = o.optString("name"),
            order = o.optLong("order"),
            collapsed = o.optBoolean("collapsed"),
            archived = o.optBoolean("archived"),
        )
    }
}

/** A label's colour and whether it's a favourite. Labels without one of these still work, in grey. */
data class LabelInfo(val name: String, val color: Int = 0xFF808080.toInt(), val favorite: Boolean = false, val options: ViewOptions = ViewOptions()) {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("color", color).put("favorite", favorite).put("options", options.toJson())

    companion object {
        fun fromJson(o: JSONObject) = LabelInfo(o.optString("name"), o.optInt("color", 0xFF808080.toInt()), o.optBoolean("favorite"), ViewOptions.fromJson(o.optJSONObject("options")))
    }
}

/** A saved search in Todoist's filter language, like "today | overdue" or "#Work & p1". */
data class Filter(
    val id: String = newId(),
    val name: String,
    val query: String,
    val color: Int = 0xFF808080.toInt(),
    val favorite: Boolean = false,
    val options: ViewOptions = ViewOptions(sort = SortBy.DATE),
    val order: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("query", query).put("color", color).put("favorite", favorite).put("options", options.toJson()).put("order", order)

    companion object {
        fun fromJson(o: JSONObject) = Filter(
            id = o.optString("id").ifEmpty { newId() },
            name = o.optString("name"),
            query = o.optString("query"),
            color = o.optInt("color", 0xFF808080.toInt()),
            favorite = o.optBoolean("favorite"),
            options = ViewOptions.fromJson(o.optJSONObject("options")),
            order = o.optLong("order"),
        )
    }
}

enum class ActivityKind(val verb: String) { ADDED("added"), COMPLETED("completed"), UNCOMPLETED("uncompleted"), UPDATED("updated"), DELETED("deleted"), COMMENTED("commented on") }

/** One line of the activity log. */
data class Activity(val at: Long, val kind: ActivityKind, val taskId: String, val title: String, val projectId: String) {
    fun toJson(): JSONObject = JSONObject().put("at", at).put("kind", kind.name).put("taskId", taskId).put("title", title).put("projectId", projectId)

    companion object {
        const val KEEP = 5000

        fun fromJson(o: JSONObject): Activity? {
            val kind = runCatching { ActivityKind.valueOf(o.optString("kind")) }.getOrNull() ?: return null
            return Activity(o.optLong("at"), kind, o.optString("taskId"), o.optString("title"), o.optString("projectId").ifEmpty { INBOX_ID })
        }
    }
}

enum class ThemeMode(val label: String) { SYSTEM("System default"), LIGHT("Light"), DARK("Dark") }

/** Colour themes, like Todoist Pro's. */
enum class Accent(val label: String, val light: Int, val dark: Int) {
    INDIGO("Doable", 0xFF5B5BF0.toInt(), 0xFFA5A6FF.toInt()),
    TOMATO("Tomato", 0xFFDC4C3E.toInt(), 0xFFFF8A7E.toInt()),
    MOONSTONE("Moonstone", 0xFF5C6B7A.toInt(), 0xFFA9B6C4.toInt()),
    TANGERINE("Tangerine", 0xFFE36B00.toInt(), 0xFFFFA45C.toInt()),
    KALE("Kale", 0xFF2E7D32.toInt(), 0xFF7CC47F.toInt()),
    BLUEBERRY("Blueberry", 0xFF1E63D6.toInt(), 0xFF8AB4F8.toInt()),
    LAVENDER("Lavender", 0xFF8E44C9.toInt(), 0xFFCDA4F2.toInt()),
    RASPBERRY("Raspberry", 0xFFC2185B.toInt(), 0xFFF48FB1.toInt()),
}

/** What a swipe on a task does. */
enum class SwipeAction(val label: String) { COMPLETE("Complete"), SCHEDULE("Schedule"), DELETE("Delete"), NONE("Nothing") }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.INDIGO,
    val weekStartsSunday: Boolean = false,
    val reminders: Boolean = true,
    /** The reminder a task gets when it's given a time, in minutes before; null for none. */
    val autoReminder: Int? = 0,
    /** Doable has asked once for permission to post notifications. */
    val askedNotifications: Boolean = false,
    /** What opens first: "today", "upcoming", "inbox", "project:<id>", "filter:<id>" or "label:<name>". */
    val startPage: String = "today",
    /** A notification each morning listing the day's tasks; null for none. */
    val dailySummary: LocalTime? = null,
    val karma: Boolean = true,
    val dailyGoal: Int = 5,
    val weeklyGoal: Int = 25,
    val daysOff: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
    /** Vacation mode: streaks don't break and goals don't count. */
    val vacation: Boolean = false,
    /** Read dates, times, #projects and @labels typed into a task's name. */
    val smartDates: Boolean = true,
    val swipeRight: SwipeAction = SwipeAction.COMPLETE,
    val swipeLeft: SwipeAction = SwipeAction.SCHEDULE,
    /** Options for the Today view. */
    val todayOptions: ViewOptions = ViewOptions(sort = SortBy.DATE),
    /** The Inbox's layout and options (it has no project record of its own). */
    val inboxView: ProjectView = ProjectView.LIST,
    val inboxOptions: ViewOptions = ViewOptions(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("theme", theme.name)
        .put("accent", accent.name)
        .put("weekStartsSunday", weekStartsSunday)
        .put("reminders", reminders)
        .put("autoReminder", autoReminder ?: -1)
        .put("askedNotifications", askedNotifications)
        .put("startPage", startPage)
        .put("dailySummary", dailySummary?.toString() ?: "")
        .put("karma", karma)
        .put("dailyGoal", dailyGoal)
        .put("weeklyGoal", weeklyGoal)
        .put("daysOff", JSONArray(daysOff.sortedBy { it.value }.map { it.name }))
        .put("vacation", vacation)
        .put("smartDates", smartDates)
        .put("swipeRight", swipeRight.name)
        .put("swipeLeft", swipeLeft.name)
        .put("todayOptions", todayOptions.toJson())
        .put("inboxView", inboxView.name)
        .put("inboxOptions", inboxOptions.toJson())

    companion object {
        fun fromJson(o: JSONObject?): Settings {
            val d = Settings()
            if (o == null) return d
            return Settings(
                theme = runCatching { ThemeMode.valueOf(o.optString("theme")) }.getOrDefault(d.theme),
                accent = runCatching { Accent.valueOf(o.optString("accent")) }.getOrDefault(d.accent),
                weekStartsSunday = o.optBoolean("weekStartsSunday", d.weekStartsSunday),
                reminders = o.optBoolean("reminders", d.reminders),
                autoReminder = if (o.has("autoReminder")) o.optInt("autoReminder", 0).takeIf { it >= 0 } else d.autoReminder,
                askedNotifications = o.optBoolean("askedNotifications", d.askedNotifications),
                startPage = o.optString("startPage").ifEmpty { d.startPage },
                dailySummary = o.optString("dailySummary").takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                karma = o.optBoolean("karma", d.karma),
                dailyGoal = o.optInt("dailyGoal", d.dailyGoal).coerceIn(1, 100),
                weeklyGoal = o.optInt("weeklyGoal", d.weeklyGoal).coerceIn(1, 700),
                daysOff = if (o.has("daysOff")) o.optJSONArray("daysOff").strings().mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.toSet() else d.daysOff,
                vacation = o.optBoolean("vacation", d.vacation),
                smartDates = o.optBoolean("smartDates", d.smartDates),
                swipeRight = runCatching { SwipeAction.valueOf(o.optString("swipeRight")) }.getOrDefault(d.swipeRight),
                swipeLeft = runCatching { SwipeAction.valueOf(o.optString("swipeLeft")) }.getOrDefault(d.swipeLeft),
                todayOptions = if (o.has("todayOptions")) ViewOptions.fromJson(o.optJSONObject("todayOptions")) else d.todayOptions,
                inboxView = runCatching { ProjectView.valueOf(o.optString("inboxView")) }.getOrDefault(d.inboxView),
                inboxOptions = ViewOptions.fromJson(o.optJSONObject("inboxOptions")),
            )
        }
    }
}

/** Everything Doable keeps, saved as one JSON file (and exported as the same JSON for backups). */
data class Data(
    val tasks: List<Task> = emptyList(),
    val projects: List<Project> = emptyList(),
    val sections: List<Section> = emptyList(),
    val labelInfo: List<LabelInfo> = emptyList(),
    val filters: List<Filter> = emptyList(),
    val activity: List<Activity> = emptyList(),
    val settings: Settings = Settings(),
) {
    /** The Inbox first, then the user's projects (not archived), each followed by its sub-projects. */
    val allProjects: List<Project> get() = listOf(Project.Inbox) + projectTree(includeArchived = false).map { it.first }

    /** Projects as a tree: each with its depth (0 for top level), parents before their children. */
    fun projectTree(includeArchived: Boolean = false): List<Pair<Project, Int>> {
        val shown = projects.filter { includeArchived || !it.archived }
        val ids = shown.map { it.id }.toSet()
        val byParent = shown.groupBy { p -> p.parentId?.takeIf { it in ids } }
        val out = mutableListOf<Pair<Project, Int>>()
        fun walk(parent: String?, depth: Int) {
            for (p in byParent[parent].orEmpty().sortedWith(compareBy({ it.order }, { it.createdAt }))) {
                out += p to depth
                if (depth < 10) walk(p.id, depth + 1)
            }
        }
        walk(null, 0)
        return out
    }

    fun project(id: String?): Project = projects.firstOrNull { it.id == id } ?: Project.Inbox

    /** A project and every project under it. */
    fun projectAndChildren(id: String): Set<String> {
        val out = mutableSetOf(id)
        var grew = true
        while (grew) {
            val more = projects.filter { it.parentId in out && it.id !in out }.map { it.id }
            grew = more.isNotEmpty()
            out += more
        }
        return out
    }

    fun task(id: String?): Task? = tasks.firstOrNull { it.id == id }

    fun section(id: String?): Section? = sections.firstOrNull { it.id == id }

    fun sectionsOf(projectId: String): List<Section> = sections.filter { it.projectId == projectId && !it.archived }.sortedBy { it.order }

    fun subtasks(parentId: String): List<Task> = tasks.filter { it.parentId == parentId }.sortedWith(compareBy({ it.order }, { it.createdAt }))

    fun filter(id: String?): Filter? = filters.firstOrNull { it.id == id }

    /** Every label: those used on tasks and those given a colour, alphabetically. */
    val labels: List<String>
        get() = (tasks.flatMap { it.labels } + labelInfo.map { it.name }).distinctBy { it.lowercase() }.sortedBy { it.lowercase() }

    fun labelInfo(name: String): LabelInfo = labelInfo.firstOrNull { it.name.equals(name, true) } ?: LabelInfo(name)

    fun toJson(): JSONObject = JSONObject()
        .put("version", 2)
        .put("tasks", JSONArray(tasks.map { it.toJson() }))
        .put("projects", JSONArray(projects.map { it.toJson() }))
        .put("sections", JSONArray(sections.map { it.toJson() }))
        .put("labels", JSONArray(labelInfo.map { it.toJson() }))
        .put("filters", JSONArray(filters.map { it.toJson() }))
        .put("activity", JSONArray(activity.map { it.toJson() }))
        .put("settings", settings.toJson())

    companion object {
        fun fromJson(o: JSONObject): Data {
            val projects = o.optJSONArray("projects").objects().map(Project::fromJson)
            val projectIds = projects.map { it.id }.toSet()
            val sections = o.optJSONArray("sections").objects().map(Section::fromJson).filter { it.projectId == INBOX_ID || it.projectId in projectIds }
            val sectionIds = sections.associate { it.id to it.projectId }
            val tasks = o.optJSONArray("tasks").objects().map(Task::fromJson).map { t ->
                val project = if (t.projectId == INBOX_ID || t.projectId in projectIds) t.projectId else INBOX_ID
                // A section has to be in the task's own project.
                val section = t.sectionId?.takeIf { sectionIds[it] == project }
                if (project == t.projectId && section == t.sectionId) t else t.copy(projectId = project, sectionId = section)
            }
            // A Daybook backup's labels are just names on tasks; Doable's own file lists them too.
            val labels = o.optJSONArray("labels")?.let { a -> List(a.length()) { a.opt(it) } }.orEmpty()
                .mapNotNull { (it as? JSONObject)?.let(LabelInfo::fromJson) }
            return Data(
                tasks = tasks,
                projects = projects.map { p -> if (p.parentId != null && p.parentId !in projectIds) p.copy(parentId = null) else p },
                sections = sections,
                labelInfo = labels,
                filters = o.optJSONArray("filters").objects().map(Filter::fromJson),
                activity = o.optJSONArray("activity").objects().mapNotNull(Activity::fromJson),
                settings = Settings.fromJson(o.optJSONObject("settings")),
            )
        }

        /** What a new install starts with: three tasks that teach the basics, and two handy filters. */
        fun firstRun(): Data {
            val now = System.currentTimeMillis()
            return Data(
                tasks = listOf(
                    Task(title = "Add a task with Quick Add: try “Pay rent every 1st 9am p1 @bills {jan 5}”", createdAt = now, order = 1),
                    Task(title = "Swipe a task right to complete it, or left to schedule it", createdAt = now + 1, order = 2),
                    Task(title = "Tap a task to add sub-tasks, reminders, a deadline or comments", createdAt = now + 2, order = 3),
                ),
                filters = listOf(
                    Filter(name = "Priority 1", query = "p1", color = Priority.color(1), order = 1),
                    Filter(name = "Next 7 days", query = "7 days | overdue", color = 0xFF299438.toInt(), order = 2),
                ),
            )
        }
    }
}

/** "1 day", "3 hours". */
fun plural(n: Int, one: String): String = if (n == 1) "1 $one" else "$n ${one}s"

internal fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else List(length()) { opt(it) }.filterIsInstance<JSONObject>()

internal fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else List(length()) { optString(it) }.filter { it.isNotBlank() }
