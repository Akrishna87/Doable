package io.github.akrishna87.doable

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.akrishna87.doable.ui.DoableApp
import kotlinx.coroutines.flow.MutableStateFlow

/** What the app was opened to do: from a widget, a shortcut, a reminder or text shared from another app. */
sealed interface LaunchRequest {
    /** Quick Add, starting with [text] (empty for a blank task due today). */
    data class QuickAdd(val text: String = "") : LaunchRequest
    data class OpenTask(val id: String) : LaunchRequest
    /** A start page: "today", "upcoming", "search", "inbox", "project:<id>" and so on. */
    data class Open(val page: String) : LaunchRequest
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.init(this)
        handle(intent)
        setContent { DoableApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent == null) return
        when {
            intent.action == Intent.ACTION_SEND -> {
                // Text or a link shared from another app becomes a task, with the subject (a page's title) first.
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty().trim()
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().trim()
                val task = listOf(subject, text).filter { it.isNotEmpty() }.distinct().joinToString(" ").replace(Regex("\\s+"), " ")
                launch.value = LaunchRequest.QuickAdd(task)
            }
            intent.getBooleanExtra(EXTRA_QUICK_ADD, false) -> launch.value = LaunchRequest.QuickAdd()
            intent.getStringExtra(EXTRA_OPEN_TASK) != null -> launch.value = LaunchRequest.OpenTask(intent.getStringExtra(EXTRA_OPEN_TASK)!!)
            intent.getStringExtra(EXTRA_OPEN) != null -> launch.value = LaunchRequest.Open(intent.getStringExtra(EXTRA_OPEN)!!)
        }
    }

    companion object {
        const val EXTRA_QUICK_ADD = "quick_add"
        const val EXTRA_OPEN_TASK = "open_task"
        const val EXTRA_OPEN = "open"

        /** Read (and cleared) by the app once it has acted on it. */
        val launch = MutableStateFlow<LaunchRequest?>(null)
    }
}
