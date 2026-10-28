package org.mobilism.material.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mobilism.material.data.FeedFilters
import org.mobilism.material.data.Alphabetical
import org.mobilism.material.designsystem.AppTextButton
import org.mobilism.material.designsystem.ExpressiveButton
import org.mobilism.material.designsystem.LoadingState

/**
 * Shared filter shell. The lazy content slot supports independent filter sections
 * (multi/single choice, switches, ranges) without coupling the sheet to categories.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    applyEnabled: Boolean,
    title: String = "Filters",
    applyLabel: String = "Apply filters",
    content: LazyListScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    fun close(apply: Boolean = false) {
        scope.launch {
            sheetState.hide()
            if (apply) onApply()
            onDismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppHeading(title, HeadingLevel.Page, style = MaterialTheme.typography.headlineMedium)
                AppTextButton("Reset", onReset)
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content,
            )
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTextButton("Cancel", { close() })
                ExpressiveButton({ close(apply = true) }, Modifier.weight(1f), enabled = applyEnabled) { Text(applyLabel) }
            }
        }
    }
}

@Composable
fun FilterSectionHeader(title: String, description: String) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AppHeading(title, HeadingLevel.Section, style = MaterialTheme.typography.titleLarge)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CategoryFilterSheet(
    state: CategoryState,
    initial: FeedFilters,
    onRetry: () -> Unit,
    onApply: (FeedFilters) -> Unit,
    onDismiss: () -> Unit,
    showCategories: Boolean = true,
    showSearchSort: Boolean = false,
    customCategories: org.mobilism.material.data.CustomCategoryStore? = null,
    onManageCategories: () -> Unit = {},
) {
    var selected by remember { mutableStateOf(initial.categoryIds) }
    val local = customCategories?.state?.collectAsStateWithLifecycle()?.value ?: org.mobilism.material.data.CustomCategoryState()
    var selectedCustom by remember { mutableStateOf(initial.customCategoryIds) }
    LaunchedEffect(local.categories, state.section) { selectedCustom = local.retain(state.section, selectedCustom) }
    var dates by remember { mutableStateOf(initial) }
    var sortTab by remember { mutableStateOf(false) }
    val sectionStyle = state.section.style()
    var search by remember { mutableStateOf("") }
    val visible = remember(state.items, local.categories, state.section, search) {
        categoryChoices(state.items, local.categories(state.section)).filter { it.name.contains(search.trim(), ignoreCase = true) ||
            it.group.orEmpty().contains(search.trim(), ignoreCase = true) }
    }
    FilterSheet(
        onDismiss = onDismiss,
        onReset = { selected = emptySet(); selectedCustom = emptySet(); dates = FeedFilters(); search = "" },
        onApply = { onApply(dates.copy(categoryIds = selected, customCategoryIds = selectedCustom)) },
        applyEnabled = dates.validDates,
    ) {
        if (showSearchSort) item("filter-tabs") {
            androidx.compose.material3.PrimaryTabRow(selectedTabIndex = if (sortTab) 1 else 0,
                modifier = Modifier.clip(MaterialTheme.shapes.large)) {
                androidx.compose.material3.Tab(selected = !sortTab, onClick = { sortTab = false }, text = { Text("Filters") })
                androidx.compose.material3.Tab(selected = sortTab, onClick = { sortTab = true }, text = { Text("Sort") })
            }
        }
        if (showSearchSort && sortTab) {
            item("sort-options") { SearchSortOptions(dates.searchSort) { dates = dates.copy(searchSort = it) } }
            return@FilterSheet
        }
        item("post-date") { PostDateFilters(dates) { dates = it } }
        if (!showCategories) return@FilterSheet
        item("category-header") {
            FilterSectionHeader("Categories · " + state.section.label,
                if (selected.isEmpty() && selectedCustom.isEmpty()) "All categories. Select any combination to narrow the feed."
                else (selected.size + selectedCustom.size).toString() + " selected")
        }
        if (customCategories != null) item("manage-categories") {
            AppTextButton("Manage categories", onManageCategories)
        }
        item("category-search") {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                label = { Text("Search categories") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
        }
        if (state.busy) item("category-loading") { LoadingState("Loading categories…", Modifier.fillMaxWidth()) }
        state.error?.let { error -> item("category-error") { ErrorPanel(error, onRetry = onRetry) } }
        items(visible, key = { it.key }, contentType = { "multi-select" }) { category ->
            val checked = category.forum?.id in selected || category.custom?.id in selectedCustom
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(lerp(MaterialTheme.colorScheme.surfaceContainerHigh, sectionStyle.accent, if (checked) 0.22f else 0.06f))
                    .toggleable(value = checked, role = Role.Checkbox, onValueChange = {
                        category.forum?.let { forum -> selected = if (it) selected + forum.id else selected - forum.id }
                        category.custom?.let { custom -> selectedCustom = if (it) selectedCustom + custom.id else selectedCustom - custom.id }
                    }).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Checkbox(checked = checked, onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = sectionStyle.accent, checkmarkColor = sectionStyle.onAccent))
                Column(Modifier.weight(1f)) {
                    Text(category.name, style = MaterialTheme.typography.bodyLarge)
                    category.group?.let { Text(it, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        if (!state.busy && state.error == null && visible.isEmpty()) item("category-empty") {
            Text("No matching categories.", Modifier.padding(vertical = 16.dp))
        }
    }
}
