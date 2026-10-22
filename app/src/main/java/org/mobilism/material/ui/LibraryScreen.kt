package org.mobilism.material.ui

import android.text.format.DateUtils
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
    entries: List<LibraryEntry>, installed: InstalledState, check: UpdateCheckState, store: AppLibraryStore,
    onRefresh: () -> Unit, onCheck: () -> Unit, onSearch: (String) -> Unit, onRelease: (Long) -> Unit, onBack: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val theme = LocalAppTheme.current
    val installedScroll = rememberLazyListState()
    val watchScroll = rememberLazyListState()
    val downloadedScroll = rememberLazyListState()
    LifecycleResumeEffect(Unit) { onRefresh(); onPauseOrDispose { } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(
            title = { AppHeading("My Applications", HeadingLevel.Page, style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack) },
            actions = { if (tab == 0) AppIconButton(Icons.Outlined.Refresh, "Refresh installed apps", onRefresh) },
        ) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.widthIn(max = 840.dp)) {
                listOf("Installed", "Watchlist", "Downloaded").forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
                }
            }
            LazyColumn(
                state = when (tab) { 0 -> installedScroll; 1 -> watchScroll; else -> downloadedScroll },
                modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (tab == 0) {
                    item(key = "installed-controls") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text("Find an installed app") }, shape = MaterialTheme.shapes.extraLarge,
                                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                                trailingIcon = { if (query.isNotEmpty()) AppIconButton(Icons.Outlined.Close, "Clear app filter", { query = "" }) })
                            FilterChip(selected = showSystem, onClick = { showSystem = !showSystem }, label = { Text("Include system apps") },
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
                    val saved = entries.filter { if (tab == 1) it.watched else it.downloaded }
                    if (tab == 1) item(key = "check-updates") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = onCheck, enabled = saved.isNotEmpty() && !check.busy, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.Update, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                                Text(if (check.busy) "Checking ${check.done} of ${check.total}…" else "Check for updates")
                            }
                            if (check.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large))
                            if (!check.busy && check.total > 0) Text(
                                buildString {
                                    append("Checked ${check.done} of ${check.total}")
                                    if (check.failed > 0) append(" · ${check.failed} failed. Try again.")
                                    if (check.unknown > 0) append(" · ${check.unknown} versions could not be compared")
                                }, style = MaterialTheme.typography.bodySmall,
                                color = if (check.failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("Checks recent matching Apps releases. Your library is saved on this device.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (saved.isEmpty()) item {
                        Text(if (tab == 1) "Open an app release and choose Watch this version to track updates."
                            else "Releases you mark as downloaded appear here.", Modifier.padding(16.dp))
                    }
                    items(saved, key = { it.release.id }) { entry ->
                        LibraryCard(entry, showUpdate = tab == 1, onRelease = onRelease, onRemove = {
                            store.remove(entry)
                            scope.launch {
                                if (snackbar.showSnackbar("Removed from library", actionLabel = "Undo") == SnackbarResult.ActionPerformed) store.restore(entry)
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(entry: LibraryEntry, showUpdate: Boolean, onRelease: (Long) -> Unit, onRemove: () -> Unit) {
    val title = remember(entry.release.title) { ReleaseTitles.parse(entry.release.title) }
    val theme = LocalAppTheme.current
    ExpressiveCard({ onRelease(entry.release.id) }, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(entry.release.thumbnail, null, Modifier.size(56.dp).clip(MaterialTheme.shapes.medium))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppHeading(title.name, HeadingLevel.Subsection, style = MaterialTheme.typography.titleMedium)
                    Text(listOfNotNull(title.version?.let { "v$it" }, title.build?.let { "build $it" }).joinToString(" · ")
                        .ifBlank { "Version not detected" }, style = MaterialTheme.typography.bodySmall)
                }
                AppIconButton(Icons.Outlined.DeleteOutline, "Remove ${title.name} from library", onRemove)
            }
            if (showUpdate) {
                entry.update?.let { update ->
                    val newer = remember(update.title) { ReleaseTitles.parse(update.title) }
                    Button(onClick = { onRelease(update.id) }, colors = ButtonDefaults.buttonColors(containerColor = theme.success, contentColor = theme.onSuccess),
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.NewReleases, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text("View v${newer.version}" + (newer.build?.let { " · build $it" } ?: ""))
                    }
                }
                val checkedAt = entry.checkedAt
                Text(if (checkedAt == null) "Not checked yet" else {
                    (if (entry.update == null) "No newer matching release found · " else "Checked · ") +
                        DateUtils.getRelativeTimeSpanString(checkedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
