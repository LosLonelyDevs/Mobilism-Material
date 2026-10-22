package org.mobilism.material.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.mobilism.material.data.SearchScope
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.ExpressiveButton
import org.mobilism.material.designsystem.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchState, onQuery: (String) -> Unit, onSubmit: () -> Unit,
    onScope: (SearchScope) -> Unit, onMore: () -> Unit, onRelease: (Long) -> Unit, onBack: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val list = rememberLazyListState()
    var filters by remember { mutableStateOf(false) }
    val submit = { keyboard?.hide(); onSubmit() }
    LaunchedEffect(state.submitted, state.scope) { list.scrollToItem(0) }
    LaunchedEffect(list, state.items.size, state.busy, state.error, state.canLoadMore) {
        if (state.items.isNotEmpty() && !state.busy && state.error == null && state.canLoadMore) {
            snapshotFlow { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.items.size - 3 }
                .distinctUntilChanged().filter { it }.collect { onMore() }
        }
    }
    if (filters) {
        var selection by remember { mutableStateOf(state.scope) }
        FilterSheet(
            onDismiss = { filters = false }, onReset = { selection = SearchScope.All },
            onApply = { onScope(selection) }, applyEnabled = true,
        ) {
            item { FilterSectionHeader("Search in", "Choose the release section to search.") }
            items(SearchScope.entries) { scope ->
                Surface(shape = MaterialTheme.shapes.large,
                    color = if (selection == scope) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.selectable(selection == scope, role = Role.RadioButton) { selection = scope }
                        .padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selection == scope, onClick = null)
                        Text(scope.label, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { AppHeading("Search releases", HeadingLevel.Page) },
            navigationIcon = { AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack) })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                OutlinedTextField(
                    value = state.query, onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    label = { Text("Search releases") }, singleLine = true, shape = MaterialTheme.shapes.extraLarge,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (state.query.isNotEmpty()) AppIconButton(Icons.Outlined.Close, "Clear search", { onQuery("") }) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExpressiveButton({ keyboard?.hide(); filters = true }, modifier = Modifier.weight(1f), compact = true,
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors()) {
                        Icon(Icons.Outlined.FilterList, null, Modifier.padding(end = 8.dp))
                        Text(state.scope.label)
                    }
                    ExpressiveButton(submit, compact = true, enabled = state.query.isNotBlank()) { Text("Search") }
                }
                LazyColumn(state = list, modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.items, key = { it.id }) { release ->
                        ReleaseCard(release, MaterialTheme.colorScheme.primary) { onRelease(release.id) }
                    }
                    if (state.busy) item { LoadingState("Searching…", Modifier.fillMaxWidth()) }
                    state.error?.let { error -> item { ErrorPanel(error, onRetry = onMore) } }
                    if (!state.busy && state.error == null && state.items.isEmpty()) item {
                        Text(if (state.submitted.isEmpty()) "Find apps, games, and books." else "No releases found for “${state.submitted}”.",
                            Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
