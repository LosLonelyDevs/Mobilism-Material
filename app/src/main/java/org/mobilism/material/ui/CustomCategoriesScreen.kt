package org.mobilism.material.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mobilism.material.data.Alphabetical
import org.mobilism.material.data.ContentSection
import org.mobilism.material.data.CustomCategory
import org.mobilism.material.data.CustomCategoryStore
import org.mobilism.material.data.Release
import org.mobilism.material.data.ReleaseCategory
import org.mobilism.material.data.ReleaseTitles
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.AppTextButton
import org.mobilism.material.designsystem.ExpressiveCard

internal data class CategoryChoice(val forum: ReleaseCategory? = null, val custom: CustomCategory? = null) {
    val key: String get() = forum?.let { "forum-${it.id}" } ?: "custom-${checkNotNull(custom).id}"
    val name: String get() = forum?.name ?: checkNotNull(custom).name
    val group: String? get() = forum?.group ?: custom?.let { "On this device" }
}

internal fun categoryChoices(forum: List<ReleaseCategory>, custom: List<CustomCategory>): List<CategoryChoice> =
    Alphabetical.sorted(forum.map { CategoryChoice(forum = it) } + custom.map { CategoryChoice(custom = it) }) { it.name }

@Composable
private fun CategoryNameDialog(initial: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (initial.isEmpty()) "Create category" else "Rename category") },
        text = {
            OutlinedTextField(name, { name = it; error = null }, label = { Text("Category name") },
                singleLine = true, isError = error != null, supportingText = { error?.let { Text(it) } })
        },
        confirmButton = { AppTextButton("Save", {
            try { onSave(name); onDismiss() }
            catch (invalid: IllegalArgumentException) { error = invalid.message }
        }) },
        dismissButton = { AppTextButton("Cancel", onDismiss) },
    )
}

@Composable
fun CategoryAssignmentSheet(release: Release, store: CustomCategoryStore, onDismiss: () -> Unit) {
    val state by store.state.collectAsStateWithLifecycle()
    val section = release.section ?: return
    var selected by remember(release.id, section) { mutableStateOf(state.memberships(release)) }
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(state.categories, section) { selected = state.retain(section, selected) }
    if (creating) CategoryNameDialog(onDismiss = { creating = false }, onSave = {
        selected = selected + store.create(section, it).id
    })
    FilterSheet(onDismiss = onDismiss, onReset = { selected = emptySet() },
        onApply = { store.assign(release, selected) }, applyEnabled = true,
        title = "Categories", applyLabel = "Save categories") {
        item("create") { AppTextButton("Create category", { creating = true }) }
        items(state.categories(section), key = { it.id }) { category ->
            val checked = category.id in selected
            Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox,
                onValueChange = { selected = if (it) selected + category.id else selected - category.id }).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Checkbox(checked, onCheckedChange = null)
                Text(category.name, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (state.categories(section).isEmpty()) item("empty") {
            Text("Create a category for ${section.label.lowercase()} to start organizing releases.", Modifier.padding(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomCategoriesScreen(section: ContentSection, store: CustomCategoryStore, onBack: () -> Unit,
    onRelease: (Long) -> Unit) {
    val state by store.state.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = state.categories.firstOrNull { it.id == selectedId && it.section == section }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val back = { if (selectedId != null) selectedId = null else onBack() }
    androidx.activity.compose.BackHandler(selectedId != null) { selectedId = null }
    if (editing) CategoryNameDialog(initial = selected?.name.orEmpty(), onDismiss = { editing = false }, onSave = { name ->
        selected?.let { store.rename(it.id, name) } ?: store.create(section, name)
    })
    if (deleting && selected != null) AlertDialog(onDismissRequest = { deleting = false },
        title = { Text("Delete ${selected.name}?") },
        text = { Text("This removes the category and its assignments. Following, downloaded marks, and other categories stay unchanged.") },
        confirmButton = { AppTextButton("Delete", { store.delete(selected.id); deleting = false; selectedId = null }) },
        dismissButton = { AppTextButton("Cancel", { deleting = false }) },
    )
    Scaffold(topBar = { TopAppBar(
        title = { AppHeading(selected?.name ?: "Manage categories", HeadingLevel.Page) },
        navigationIcon = { AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", back) },
        actions = {
            if (selected == null) AppIconButton(Icons.Outlined.Add, "Create category", { editing = true })
            else {
                AppIconButton(Icons.Outlined.Edit, "Rename category", { editing = true })
                AppIconButton(Icons.Outlined.DeleteOutline, "Delete category", { deleting = true })
            }
        },
    ) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selected == null) {
                item("section") { Text("${section.label} · On this device", style = MaterialTheme.typography.bodyMedium) }
                items(state.categories(section), key = { it.id }) { category ->
                    val count = state.items.count { category.id in it.categoryIds }
                    ExpressiveCard({ selectedId = category.id }, Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppHeading(category.name, HeadingLevel.Section, style = MaterialTheme.typography.titleLarge)
                            Text(if (count == 1) "1 item" else "$count items", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (state.categories(section).isEmpty()) item("empty") { Text("No categories yet. Create one to get started.") }
            } else {
                val members = Alphabetical.sorted(state.items.filter { selected.id in it.categoryIds }) {
                    ReleaseTitles.parse(it.release.title, section).name
                }
                item("hint") { Text("Open a release and choose Categories to add it here.", style = MaterialTheme.typography.bodyMedium) }
                items(members, key = { it.id }) { item ->
                    val title = ReleaseTitles.parse(item.release.title, section)
                    ExpressiveCard({ onRelease(item.release.id) }, Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                AppHeading(title.name, HeadingLevel.Subsection, style = MaterialTheme.typography.titleMedium)
                                if (title.subtitle.isNotBlank()) Text(title.subtitle, style = MaterialTheme.typography.bodySmall)
                            }
                            AppIconButton(Icons.Outlined.RemoveCircleOutline, "Remove ${title.name} from ${selected.name}", {
                                store.remove(item.id, selected.id)
                            })
                        }
                    }
                }
                if (members.isEmpty()) item("empty") { Text("No items in this category yet.") }
            }
        }
    }
}
