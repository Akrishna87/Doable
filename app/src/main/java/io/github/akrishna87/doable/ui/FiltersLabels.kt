package io.github.akrishna87.doable.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.akrishna87.doable.Store
import io.github.akrishna87.doable.model.Data
import io.github.akrishna87.doable.model.Dates
import io.github.akrishna87.doable.model.Filter
import io.github.akrishna87.doable.model.Filters
import io.github.akrishna87.doable.model.LabelInfo
import io.github.akrishna87.doable.model.ProjectColors

/** Saved filters and labels, each with its count of open tasks. */
@Composable
fun FiltersLabelsPage(data: Data) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    var newFilter by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf(false) }
    val open = data.tasks.filter { !it.done }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar("Filters & Labels", onBack = { nav.pop() })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                SectionHeader("Filters") {
                    IconButton(onClick = { newFilter = true }, Modifier.semantics { contentDescription = "Add filter" }) { Icon(Icons.Rounded.Add, null) }
                }
            }
            if (data.filters.isEmpty()) {
                item { Text("Save searches like “today | overdue” or “#Work & p1”. Tap + to add one.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(data.filters.sortedBy { it.order }, key = { "f-" + it.id }) { f ->
                val n = Filters.run(f.query, data, today, Dates.weekStart(data.settings)).getOrNull()?.sumOf { it.second.size }
                ListRow(
                    f.name,
                    leading = { Icon(Icons.Rounded.FilterList, null, tint = Color(f.color)) },
                    detail = f.query,
                    trailing = {
                        if (f.favorite) Icon(Icons.Rounded.Star, "Favourite", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (n == null) Text("Mistake", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium) else Count(n)
                    },
                    onClick = { nav.push(Page.FilterPage(f.id)) },
                )
            }
            item {
                SectionHeader("Labels") {
                    IconButton(onClick = { newLabel = true }, Modifier.semantics { contentDescription = "Add label" }) { Icon(Icons.Rounded.Add, null) }
                }
            }
            if (data.labels.isEmpty()) {
                item { Text("Add @labels to tasks to gather them across projects.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(data.labels, key = { "l-$it" }) { l ->
                val info = data.labelInfo(l)
                ListRow(
                    l,
                    leading = { Icon(Icons.Rounded.Label, null, tint = Color(info.color)) },
                    trailing = {
                        if (info.favorite) Icon(Icons.Rounded.Star, "Favourite", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Count(open.count { t -> t.labels.any { it.equals(l, true) } })
                    },
                    onClick = { nav.push(Page.LabelPage(l)) },
                )
            }
        }
    }
    if (newFilter) FilterDialog(null, onDismiss = { newFilter = false })
    if (newLabel) {
        var fav by remember { mutableStateOf(false) }
        NameDialog(
            title = "New label",
            confirm = "Add",
            colors = ProjectColors.choices,
            initialColor = ProjectColors.choices[14],
            favorite = false,
            onFavorite = { fav = it },
            onSave = { name, color -> Store.saveLabel(LabelInfo(name.removePrefix("@").replace(" ", "-"), color, fav)) },
            onDismiss = { newLabel = false },
        )
    }
}

/** Making or changing a filter: a name, its query (checked as you type), a colour, and favourite. */
@Composable
fun FilterDialog(filter: Filter?, onDismiss: () -> Unit, initialQuery: String = "") {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    var name by remember { mutableStateOf(filter?.name ?: "") }
    var query by remember { mutableStateOf(filter?.query ?: initialQuery) }
    var color by remember { mutableIntStateOf(filter?.color ?: ProjectColors.choices[9]) }
    var favorite by remember { mutableStateOf(filter?.favorite ?: false) }
    val data = Store.data.value
    val check = if (query.isBlank()) null else Filters.run(query, data, today, Dates.weekStart(data.settings))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (filter == null) "New filter" else "Edit filter") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Filter name" },
                    label = { Text("Name") }, singleLine = true,
                )
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentDescription = "Filter query" },
                    label = { Text("Query") },
                    placeholder = { Text("today | overdue") },
                    isError = check?.isFailure == true,
                    supportingText = {
                        Text(
                            when {
                                check == null -> "Combine with & (and), | (or), ! (not); commas make separate lists"
                                check.isFailure -> check.exceptionOrNull()?.message ?: "Not a filter"
                                else -> check.getOrNull().orEmpty().sumOf { it.second.size }.let { if (it == 1) "1 task matches" else "$it tasks match" }
                            },
                        )
                    },
                )
                Text(
                    "Examples: today · overdue · 7 days · p1 · #Work · ##Work · /Section · @label · no date · recurring · " +
                        "search: words · due before: friday · created after: -7 days · has: comments · (today | overdue) & #Work",
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ColorChoices(ProjectColors.choices, color, { color = it }, Modifier.padding(top = 12.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Add to favourites", Modifier.weight(1f))
                    Switch(checked = favorite, onCheckedChange = { favorite = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val f = (filter ?: Filter(name = "", query = "")).copy(name = name.trim().ifEmpty { query.trim() }, query = query.trim(), color = color, favorite = favorite)
                    Store.saveFilter(f)
                    onDismiss()
                    if (filter == null) nav.push(Page.FilterPage(f.id))
                },
                enabled = query.isNotBlank() && check?.isSuccess == true,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
