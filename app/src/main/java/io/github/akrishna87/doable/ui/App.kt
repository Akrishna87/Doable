package io.github.akrishna87.doable.ui

import android.Manifest
import android.graphics.Color as AndroidColor
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akrishna87.doable.LaunchRequest
import io.github.akrishna87.doable.MainActivity
import io.github.akrishna87.doable.Reminders
import io.github.akrishna87.doable.Store
import io.github.akrishna87.doable.model.Data
import io.github.akrishna87.doable.model.Dates
import io.github.akrishna87.doable.model.INBOX_ID
import io.github.akrishna87.doable.model.SwipeAction
import io.github.akrishna87.doable.model.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Rounded.Today),
    UPCOMING("Upcoming", Icons.Rounded.CalendarMonth),
    SEARCH("Search", Icons.Rounded.Search),
    BROWSE("Browse", Icons.Rounded.GridView),
}

/** Screens that open on top of the tabs. */
sealed interface Page {
    data class ProjectPage(val id: String) : Page
    data class LabelPage(val name: String) : Page
    data class FilterPage(val id: String) : Page
    data class TaskPage(val id: String) : Page
    data object Completed : Page
    data object SettingsPage : Page
    data object FiltersLabels : Page
    data object Productivity : Page
    data object ActivityLog : Page
    data object Archived : Page
}

/** What a new task starts with, depending on where Quick Add was opened. */
data class QuickAddDefaults(
    val due: LocalDate? = null,
    val projectId: String = INBOX_ID,
    val sectionId: String? = null,
    val labels: List<String> = emptyList(),
    val parentId: String? = null,
    /** Text to start with, for a task shared from another app. */
    val text: String = "",
)

class Nav {
    var tab by mutableStateOf(Tab.TODAY)
    val pages = mutableStateListOf<Page>()
    var quickAdd by mutableStateOf<QuickAddDefaults?>(null)

    /** A task whose date is being picked (from a swipe or a menu). */
    var scheduling by mutableStateOf<Task?>(null)

    /** A task being moved to another project or section. */
    var moving by mutableStateOf<Task?>(null)

    fun push(page: Page) {
        pages.add(page)
    }

    fun pop() {
        if (pages.isNotEmpty()) pages.removeAt(pages.lastIndex)
    }

    /** Opens what Settings → Start page names: a tab, or a project, label or filter on top of Today. */
    fun open(start: String, data: Data) {
        pages.clear()
        when {
            start == "upcoming" -> tab = Tab.UPCOMING
            start == "search" -> tab = Tab.SEARCH
            start == "browse" -> tab = Tab.BROWSE
            start == "inbox" -> { tab = Tab.TODAY; push(Page.ProjectPage(INBOX_ID)) }
            start.startsWith("project:") && data.projects.any { it.id == start.removePrefix("project:") } -> { tab = Tab.BROWSE; push(Page.ProjectPage(start.removePrefix("project:"))) }
            start.startsWith("filter:") && data.filter(start.removePrefix("filter:")) != null -> { tab = Tab.BROWSE; push(Page.FilterPage(start.removePrefix("filter:"))) }
            start.startsWith("label:") -> { tab = Tab.BROWSE; push(Page.LabelPage(start.removePrefix("label:"))) }
            else -> tab = Tab.TODAY
        }
    }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("No Nav") }

/** Asks once for permission to post notifications, the first time a task gets a reminder. */
val LocalAskNotifications = staticCompositionLocalOf<() -> Unit> { {} }

/** Completing, scheduling and deleting tasks, with Undo in a snackbar. */
class TaskActions(private val scope: CoroutineScope, private val snackbar: SnackbarHostState, private val nav: Nav) {
    fun toggle(task: Task) {
        if (task.done) Store.uncompleteTask(task.id) else complete(task)
    }

    fun complete(task: Task) {
        val before = Store.taskFamily(task.id)
        val next = Store.completeTask(task.id)
        val message = if (next != null) "Completed. Next: ${Dates.dayLabel(next, LocalDate.now())}" else "Completed"
        offerUndo(message) { Store.restoreTasks(before) }
    }

    fun delete(task: Task) {
        val before = Store.taskFamily(task.id)
        Store.deleteTask(task.id)
        offerUndo("Task deleted") { Store.restoreTasks(before) }
    }

    fun schedule(task: Task) {
        nav.scheduling = task
    }

    fun swipe(task: Task, action: SwipeAction) {
        when (action) {
            SwipeAction.COMPLETE -> toggle(task)
            SwipeAction.SCHEDULE -> schedule(task)
            SwipeAction.DELETE -> delete(task)
            SwipeAction.NONE -> {}
        }
    }

    fun message(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text, duration = SnackbarDuration.Short)
        }
    }

    fun offerUndo(text: String, undo: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(text, actionLabel = "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) undo()
        }
    }
}

@Composable
fun DoableApp() {
    val data by Store.data.collectAsStateWithLifecycle()
    val dark = isDark(data.settings.theme)
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(dark) {
        // Keep the status and navigation bar icons readable when the app's theme differs from the phone's.
        val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    DoableTheme(dark, data.settings.accent) { AppContent(data) }
}

@Composable
private fun AppContent(data: Data) {
    val context = LocalContext.current
    val nav = remember { Nav().also { it.open(Store.data.value.settings.startPage, Store.data.value) } }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val actions = remember { TaskActions(scope, snackbar, nav) }
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            val n = LocalDateTime.now()
            value = n
            delay(60_000L - n.second * 1000L - n.nano / 1_000_000L)
        }
    }
    val clock = Clock(now, DateFormat.is24HourFormat(context))

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val askNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(context) && !Store.data.value.settings.askedNotifications) {
            Store.updateSettings { it.copy(askedNotifications = true) }
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        MainActivity.launch.collect { request ->
            when (request) {
                is LaunchRequest.QuickAdd -> nav.quickAdd = QuickAddDefaults(due = if (request.text.isEmpty()) LocalDate.now() else null, text = request.text)
                is LaunchRequest.OpenTask -> if (Store.data.value.task(request.id) != null) nav.push(Page.TaskPage(request.id))
                is LaunchRequest.Open -> nav.open(request.page, Store.data.value)
                null -> {}
            }
            if (request != null) MainActivity.launch.value = null
        }
    }

    BackHandler(enabled = nav.pages.isNotEmpty() || nav.tab != Tab.TODAY) {
        if (nav.pages.isNotEmpty()) nav.pop() else nav.tab = Tab.TODAY
    }

    CompositionLocalProvider(
        LocalNav provides nav,
        LocalActions provides actions,
        LocalClock provides clock,
        LocalAskNotifications provides askNotifications,
    ) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                bottomBar = { BottomBar(nav.tab) { nav.tab = it } },
                floatingActionButton = {
                    if (nav.tab != Tab.SEARCH) {
                        Fab("Add task") { nav.quickAdd = QuickAddDefaults(due = if (nav.tab == Tab.BROWSE) null else LocalDate.now()) }
                    }
                },
            ) { padding ->
                when (nav.tab) {
                    Tab.TODAY -> TodayScreen(data, padding)
                    Tab.UPCOMING -> UpcomingScreen(data, padding)
                    Tab.SEARCH -> SearchScreen(data, padding)
                    Tab.BROWSE -> BrowseScreen(data, padding)
                }
            }

            AnimatedContent(
                targetState = nav.pages.lastOrNull(),
                transitionSpec = { (slideInHorizontally { it / 8 } + fadeIn()) togetherWith fadeOut() },
                label = "page",
            ) { page ->
                if (page != null) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { PageContent(page, data) }
                } else {
                    Box(Modifier)
                }
            }

            nav.quickAdd?.let { defaults -> QuickAddPanel(defaults, data) { nav.quickAdd = null } }

            nav.scheduling?.let { t ->
                ScheduleDialog(t, data, onDismiss = { nav.scheduling = null }) { day ->
                    val before = Store.taskFamily(t.id)
                    val cur = Store.data.value.task(t.id) ?: return@ScheduleDialog
                    Store.saveTask(if (day == null) cur.copy(due = null, time = null, repeat = null) else cur.copy(due = day))
                    actions.offerUndo(if (day == null) "Date removed" else "Scheduled for ${Dates.dayLabel(day, LocalDate.now())}") { Store.restoreTasks(before) }
                }
            }
            nav.moving?.let { t ->
                MoveDialog(t, data, onDismiss = { nav.moving = null }) { projectId, sectionId ->
                    val before = Store.taskFamily(t.id)
                    Store.moveTask(t.id, projectId, sectionId)
                    actions.offerUndo("Moved to ${data.project(projectId).name}" + (data.section(sectionId)?.let { " / ${it.name}" } ?: "")) { Store.restoreTasks(before) }
                }
            }

            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 92.dp),
            )
        }
    }
}

@Composable
private fun PageContent(page: Page, data: Data) {
    when (page) {
        is Page.ProjectPage -> ProjectPage(page.id, data)
        is Page.LabelPage -> LabelPage(page.name, data)
        is Page.FilterPage -> FilterPage(page.id, data)
        is Page.TaskPage -> TaskDetailPage(page.id, data)
        Page.Completed -> CompletedPage(data)
        Page.SettingsPage -> SettingsPage(data)
        Page.FiltersLabels -> FiltersLabelsPage(data)
        Page.Productivity -> ProductivityPage(data)
        Page.ActivityLog -> ActivityPage(data)
        Page.Archived -> ArchivedPage(data)
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    FloatingNavBar(Tab.entries.map { NavItem(it.label, it.icon) }, Tab.entries.indexOf(selected)) { onSelect(Tab.entries[it]) }
}

@Composable
fun Fab(label: String, onClick: () -> Unit) {
    // Labelled on the button itself so screen readers (and the UI tests) find it.
    KitFab(label, Icons.Rounded.Add, onClick)
}
