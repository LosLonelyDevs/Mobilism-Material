package org.mobilism.material.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.mobilism.material.data.AppLibraryStore
import org.mobilism.material.data.LibraryEntry
import org.mobilism.material.data.ReleaseTitles
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.ExpressiveCard
import org.mobilism.material.designsystem.LoadingState
import org.mobilism.material.designsystem.LocalAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    entries: List<LibraryEntry>, installed: InstalledState, store: AppLibraryStore,
    onRefresh: () -> Unit, onSearch: (String) -> Unit, onFollowing: () -> Unit, onRelease: (Long) -> Unit, onBack: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val theme = LocalAppTheme.current
    val installedScroll = rememberLazyListState()
    val downloadedScroll = rememberLazyListState()
    LifecycleResumeEffect(Unit) { onRefresh(); onPauseOrDispose { } }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = { TopAppBar(
        title = { AppHeading("My Applications", HeadingLevel.Page, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = { AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack) },
        actions = {
            AppIconButton(Icons.Outlined.Bookmarks, "Following", onFollowing)
            if (tab == 0) AppIconButton(Icons.Outlined.Refresh, "Refresh installed apps", onRefresh)
        },
    ) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            PrimaryTabRow(tab, modifier = Modifier.widthIn(max = 840.dp)) {
                listOf("Installed", "Downloaded").forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
                }
            }
            LazyColumn(state = if (tab == 0) installedScroll else downloadedScroll,
                modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tab == 0) {
                    item(key = "installed-controls") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text("Find an installed app") }, shape = MaterialTheme.shapes.extraLarge,
                                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                                trailingIcon = { if (query.isNotEmpty()) AppIconButton(Icons.Outlined.Close, "Clear app filter", { query = "" }) })
                            FilterChip(showSystem, { showSystem = !showSystem }, label = { Text("Include system apps") },
                                shape = MaterialTheme.shapes.large,
                                leadingIcon = { Icon(if (showSystem) Icons.Outlined.Check else Icons.Outlined.PhoneAndroid, null, Modifier.size(18.dp)) })
                            Text("Tap an app to find its releases on Mobilism.", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val apps = installed.apps.filter { (showSystem || !it.system) &&
                        (it.label.contains(query, true) || it.packageName.contains(query, true)) }
                    if (installed.busy) item { LoadingState("Reading installed apps…", Modifier.fillMaxWidth()) }
                    installed.error?.let { item { ErrorPanel(it, onRetry = onRefresh) } }
                    items(apps, key = { it.packageName }) { app ->
                        ExpressiveCard({ onSearch(app.label) }, Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                val icon = remember(app.icon) { app.icon.toBitmap(144, 144).asImageBitmap() }
                                Image(icon, null, Modifier.size(56.dp).clip(MaterialTheme.shapes.medium))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    AppHeading(app.label, HeadingLevel.Subsection, style = MaterialTheme.typography.titleMedium)
                                    Text(app.version.ifBlank { "Version unavailable" }, style = MaterialTheme.typography.bodySmall)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.Outlined.Search, "Find releases", tint = theme.featured)
                            }
                        }
                    }
                    if (!installed.busy && installed.error == null && apps.isEmpty()) item {
                        Text("No matching apps. Try including system apps.", Modifier.padding(16.dp))
                    }
                } else {
                    val saved = entries.filter { it.downloaded }
                    if (saved.isEmpty()) item { Text("Releases you mark as downloaded appear here.", Modifier.padding(16.dp)) }
                    items(saved, key = { it.release.id }) { entry ->
                        val title = remember(entry.release.title) { ReleaseTitles.parse(entry.release.title) }
                        ExpressiveCard({ onRelease(entry.release.id) }, Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(entry.release.thumbnail, null, Modifier.size(56.dp).clip(MaterialTheme.shapes.medium))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    AppHeading(title.name, HeadingLevel.Subsection, style = MaterialTheme.typography.titleMedium)
                                    Text(listOfNotNull(title.version?.let { "v$it" }, title.build?.let { "build $it" }).joinToString(" · ")
                                        .ifBlank { "Version not detected" }, style = MaterialTheme.typography.bodySmall)
                                    ReleasePostDate(entry.release.publishedAt)
                                }
                                AppIconButton(Icons.Outlined.DeleteOutline, "Remove ${title.name} from downloaded", {
                                    store.downloaded(entry.release, false)
                                    scope.launch { if (snackbar.showSnackbar("Downloaded mark cleared", "Undo") == SnackbarResult.ActionPerformed)
                                        store.downloaded(entry.release, true) }
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}
