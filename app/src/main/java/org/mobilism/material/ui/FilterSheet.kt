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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mobilism.material.data.FeedFilters
import org.mobilism.material.R
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
    onReset: (() -> Unit)? = null,
    onApply: () -> Unit,
    applyEnabled: Boolean,
    title: String = stringResource(R.string.feed_filters),
    applyLabel: String = stringResource(R.string.filter_apply),
    headerAction: @Composable () -> Unit = { onReset?.let { AppTextButton(stringResource(R.string.filter_reset), it) } },
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
                AppHeading(title, HeadingLevel.Page, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                headerAction()
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
                AppTextButton(stringResource(R.string.download_cancel_action), { close() })
                ExpressiveButton({ close(apply = true) }, Modifier.weight(1f), enabled = applyEnabled) { Text(applyLabel) }
            }
        }
    }
}

@Composable
fun FilterSectionHeader(title: String, description: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppHeading(title, HeadingLevel.Section, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            action?.invoke()
        }
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
    val deviceLabel = stringResource(R.string.category_device)
    val visible = remember(state.items, local.categories, state.section, search, deviceLabel) {
        categoryChoices(state.items, local.categories(state.section)).filter { it.name.contains(search.trim(), ignoreCase = true) ||
            it.group(deviceLabel).orEmpty().contains(search.trim(), ignoreCase = true) }
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
                androidx.compose.material3.Tab(selected = !sortTab, onClick = { sortTab = false }, text = { Text(stringResource(R.string.feed_filters)) })
                androidx.compose.material3.Tab(selected = sortTab, onClick = { sortTab = true }, text = { Text(stringResource(R.string.filter_sort)) })
            }
        }
        if (showSearchSort && sortTab) {
            item("sort-options") { SearchSortOptions(dates.searchSort) { dates = dates.copy(searchSort = it) } }
            return@FilterSheet
        }
        item("post-date") { PostDateFilters(dates) { dates = it } }
        if (showCategories && state.section == org.mobilism.material.data.ContentSection.Books) item("book-formats") {
            BookFormatFilters(dates.bookFormats) { dates = dates.copy(bookFormats = it) }
        }
        if (!showCategories) return@FilterSheet
        item("category-header") {
            val count = selected.size + selectedCustom.size
            FilterSectionHeader(stringResource(R.string.filter_section_categories, state.section.categorySectionLabel()),
                if (count == 0) stringResource(R.string.filter_all_categories)
                else pluralStringResource(R.plurals.filter_selected_categories, count, count)) {
                if (customCategories != null) AppTextButton(stringResource(R.string.category_manage), onManageCategories)
            }
        }
        item("category-search") {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                label = { Text(stringResource(R.string.filter_search_categories)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent),
            )
        }
        if (state.busy) item("category-loading") { LoadingState(stringResource(R.string.filter_loading_categories), Modifier.fillMaxWidth()) }
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
                    category.group(deviceLabel)?.let { Text(it, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        if (!state.busy && state.error == null && visible.isEmpty()) item("category-empty") {
            Text(stringResource(R.string.filter_no_categories), Modifier.padding(vertical = 16.dp))
        }
    }
}
