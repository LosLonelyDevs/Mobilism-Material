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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mobilism.material.R
import org.mobilism.material.data.Alphabetical
import org.mobilism.material.data.CategoryNameException
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
    fun group(deviceLabel: String): String? = forum?.group ?: custom?.let { deviceLabel }
}

internal fun categoryChoices(forum: List<ReleaseCategory>, custom: List<CustomCategory>): List<CategoryChoice> =
    Alphabetical.sorted(forum.map { CategoryChoice(forum = it) } + custom.map { CategoryChoice(custom = it) }) { it.name }

@Composable
internal fun ContentSection.categorySectionLabel() = stringResource(when (this) {
    ContentSection.Apps -> R.string.category_section_apps
    ContentSection.Games -> R.string.category_section_games
    ContentSection.Books -> R.string.category_section_books
})

@Composable
private fun CategoryEditor(category: CustomCategory? = null, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by rememberSaveable(category?.id) { mutableStateOf(category?.name.orEmpty()) }
    var description by rememberSaveable(category?.id) { mutableStateOf(category?.description.orEmpty()) }
    var error by rememberSaveable(category?.id) { mutableStateOf<Int?>(null) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(if (category == null) R.string.category_create else R.string.category_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it; error = null }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.category_name)) }, singleLine = true, isError = error != null,
                    supportingText = error?.let { reason -> { Text(stringResource(reason)) } })
                OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.category_description)) }, maxLines = 3)
            }
        },
        confirmButton = { AppTextButton(stringResource(R.string.category_save), {
            try { onSave(name, description); onDismiss() }
            catch (invalid: CategoryNameException) { error = invalid.reason }
        }) },
        dismissButton = { AppTextButton(stringResource(R.string.download_cancel_action), onDismiss) },
    )
}

@Composable
fun CategoryAssignmentSheet(release: Release, store: CustomCategoryStore, onDismiss: () -> Unit,
    onManage: (ContentSection) -> Unit) {
    val state by store.state.collectAsStateWithLifecycle()
    val section = release.section ?: return
    val assigned = state.memberships(release)
    // Keep pending edits when returning from Manage and pick up any assignments changed there.
    var added by rememberSaveable(release.id, section) { mutableStateOf(emptyList<String>()) }
    var removed by rememberSaveable(release.id, section) { mutableStateOf(emptyList<String>()) }
    val selected = (assigned + added) - removed.toSet()
    fun select(id: String, checked: Boolean) {
        added = if (checked && id !in assigned) (added - id) + id else added - id
        removed = if (!checked && id in assigned) (removed - id) + id else removed - id
    }
    var creating by rememberSaveable { mutableStateOf(false) }
    if (creating) CategoryEditor(onDismiss = { creating = false }, onSave = { name, description ->
        select(store.create(section, name, description).id, true)
    })
    FilterSheet(onDismiss = onDismiss, onApply = { store.assign(release, selected) }, applyEnabled = true,
        headerAction = { AppTextButton(stringResource(R.string.category_manage), { onManage(section) }) },
        title = stringResource(R.string.category_assignment_title), applyLabel = stringResource(R.string.category_save_assignments)) {
        item("create") { AppTextButton(stringResource(R.string.category_create), { creating = true }) }
        items(state.categories(section), key = { it.id }) { category ->
            val checked = category.id in selected
            Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox,
                onValueChange = { select(category.id, it) }).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Checkbox(checked, onCheckedChange = null)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(category.name, style = MaterialTheme.typography.bodyLarge)
                    if (category.description.isNotBlank()) Text(category.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (state.categories(section).isEmpty()) item("empty") {
            Text(stringResource(R.string.category_empty), Modifier.padding(12.dp))
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
    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val back = { if (selectedId != null) selectedId = null else onBack() }
    androidx.activity.compose.BackHandler(selectedId != null) { selectedId = null }
    if (editing) CategoryEditor(selected, onDismiss = { editing = false }, onSave = { name, description ->
        if (selected == null) store.create(section, name, description) else store.update(selected.id, name, description)
    })
    if (deleting && selected != null) AlertDialog(onDismissRequest = { deleting = false },
        title = { Text(stringResource(R.string.category_delete_title, selected.name)) },
        text = { Text(stringResource(R.string.category_delete_message)) },
        confirmButton = { AppTextButton(stringResource(R.string.category_delete_confirm), { store.delete(selected.id); deleting = false; selectedId = null }) },
        dismissButton = { AppTextButton(stringResource(R.string.download_cancel_action), { deleting = false }) },
    )
    Scaffold(topBar = { TopAppBar(
        title = { AppHeading(selected?.name ?: stringResource(R.string.release_categories), HeadingLevel.Page) },
        navigationIcon = { AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), back) },
        actions = {
            if (selected == null) AppIconButton(Icons.Outlined.Add, stringResource(R.string.category_create), { editing = true })
            else {
                AppIconButton(Icons.Outlined.Edit, stringResource(R.string.category_edit), { editing = true })
                AppIconButton(Icons.Outlined.DeleteOutline, stringResource(R.string.category_delete), { deleting = true })
            }
        },
    ) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selected == null) {
                item("section") { Text(stringResource(R.string.category_device_section, section.categorySectionLabel()), style = MaterialTheme.typography.bodyMedium) }
                items(state.categories(section), key = { it.id }) { category ->
                    val count = state.items.count { category.id in it.categoryIds }
                    ExpressiveCard({ selectedId = category.id }, Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppHeading(category.name, HeadingLevel.Section, style = MaterialTheme.typography.titleLarge)
                            Text(pluralStringResource(R.plurals.category_items, count, count), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (state.categories(section).isEmpty()) item("empty") { Text(stringResource(R.string.category_empty)) }
            } else {
                val members = Alphabetical.sorted(state.items.filter { selected.id in it.categoryIds }) {
                    ReleaseTitles.parse(it.release.title, section).name
                }
                if (selected.description.isNotBlank()) item("description") {
                    Text(selected.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item("hint") { Text(stringResource(R.string.category_assign_hint), style = MaterialTheme.typography.bodyMedium) }
                items(members, key = { it.id }) { item ->
                    val title = ReleaseTitles.parse(item.release.title, section)
                    ExpressiveCard({ onRelease(item.release.id) }, Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                AppHeading(title.name, HeadingLevel.Subsection, style = MaterialTheme.typography.titleMedium)
                                if (title.subtitle.isNotBlank()) Text(title.subtitle, style = MaterialTheme.typography.bodySmall)
                            }
                            AppIconButton(Icons.Outlined.RemoveCircleOutline, stringResource(R.string.category_remove_member, title.name, selected.name), {
                                store.remove(item.id, selected.id)
                            })
                        }
                    }
                }
                if (members.isEmpty()) item("empty") { Text(stringResource(R.string.category_empty_members)) }
            }
        }
    }
}
