package org.mobilism.material.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.mobilism.material.R
import kotlinx.coroutines.launch
import org.mobilism.material.data.AppLibraryStore
import org.mobilism.material.data.FollowingVersions
import org.mobilism.material.data.InstalledVersion
import org.mobilism.material.data.InstalledReleaseCatalog
import org.mobilism.material.data.latestAppKey
import kotlinx.coroutines.CancellationException
import org.mobilism.material.data.LibraryEntry
import org.mobilism.material.data.ReleaseTitles
import org.mobilism.material.data.Alphabetical
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.ExpressiveCard
import org.mobilism.material.designsystem.LoadingState
import org.mobilism.material.designsystem.LocalAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    entries: List<LibraryEntry>, installed: InstalledState, store: AppLibraryStore,
    onRefresh: () -> Unit, onSearch: (String) -> Unit, onFollowing: () -> Unit, onRelease: (Long) -> Unit, onBack: () -> Unit,
    hideAdult: Boolean, loadCatalog: suspend (Boolean) -> InstalledReleaseCatalog,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val theme = LocalAppTheme.current
    val installedScroll = rememberLazyListState()
    val downloadedScroll = rememberLazyListState()
    val versions = remember(installed.apps) { installed.apps.map { InstalledVersion(it.packageName, it.label, it.version, 0L) } }
    fun linkedPackages(follows: List<LibraryEntry>) = follows.asSequence().filter { it.watched }
        .mapNotNull { FollowingVersions.matchingInstalled(it, versions)?.packageName }.toSet()
    val linked = remember(entries, versions) { linkedPackages(entries) }
    val names = remember(installed.apps) { installed.apps.groupingBy { latestAppKey(it.label) }.eachCount() }
    var catalog by remember(hideAdult) { mutableStateOf<InstalledReleaseCatalog?>(null) }
    val currentScroll = if (tab == 0) installedScroll else downloadedScroll
    val showTop by remember(currentScroll) { derivedStateOf { currentScroll.firstVisibleItemIndex > 0 } }
    val reducedMotion = org.mobilism.material.designsystem.LocalReducedMotion.current
    LifecycleResumeEffect(tab, hideAdult) {
        val job = if (tab == 0) {
            onRefresh()
            scope.launch {
                catalog = try { loadCatalog(hideAdult) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { InstalledReleaseCatalog() }
            }
        } else null
        onPauseOrDispose { job?.cancel() }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, floatingActionButton = {
        if (showTop) SmallFloatingActionButton(onClick = { scope.launch {
            if (reducedMotion) currentScroll.scrollToItem(0) else currentScroll.animateScrollToItem(0)
        } }) { Icon(Icons.Outlined.VerticalAlignTop, stringResource(R.string.library_back_to_top)) }
    }, topBar = { TopAppBar(
        title = { AppHeading(stringResource(R.string.drawer_library), HeadingLevel.Page, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = { AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), onBack) },
        actions = {
            AppIconButton(Icons.Outlined.Bookmarks, stringResource(R.string.drawer_following), onFollowing)
            if (tab == 0) AppIconButton(Icons.Outlined.Refresh, stringResource(R.string.library_refresh), onRefresh)
        },
    ) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            PrimaryTabRow(tab, modifier = Modifier.widthIn(max = 840.dp)) {
                listOf(R.string.library_installed, R.string.library_downloaded).forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(label)) })
                }
            }
            if (tab == 0) OutlinedTextField(query, { query = it; installedScroll.requestScrollToItem(0) },
                Modifier.widthIn(max = 840.dp).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                singleLine = true, label = { Text(stringResource(R.string.library_filter)) },
                shape = MaterialTheme.shapes.extraLarge, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) AppIconButton(Icons.Outlined.Close, stringResource(R.string.library_clear_filter), {
                    query = ""; installedScroll.requestScrollToItem(0)
                }) })
            if (tab == 0 && catalog == null) LinearProgressIndicator(Modifier.widthIn(max = 840.dp).fillMaxWidth())
            LazyColumn(state = currentScroll,
                modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tab == 0) {
                    item(key = "installed-controls") {
                        FilterChip(showSystem, { showSystem = !showSystem; installedScroll.requestScrollToItem(0) },
                            label = { Text(stringResource(R.string.library_system_apps)) }, shape = MaterialTheme.shapes.large,
                            leadingIcon = { Icon(if (showSystem) Icons.Outlined.Check else Icons.Outlined.PhoneAndroid, null, Modifier.size(18.dp)) })
                    }
                    val apps = Alphabetical.sorted(installed.apps) { it.label }.filter { (showSystem || !it.system) &&
                        (it.label.contains(query, true) || it.packageName.contains(query, true)) }
                    if (installed.busy) item { LoadingState(stringResource(R.string.library_reading), Modifier.fillMaxWidth()) }
                    installed.error?.let { item { ErrorPanel(it, onRetry = onRefresh) } }
                    items(apps, key = { it.packageName }) { app ->
                        val followed = app.packageName in linked
                        val match = catalog?.match(app.packageName, app.label, names[latestAppKey(app.label)] == 1)
                        ExpressiveCard({
                            val current = store.state.value
                            val alreadyFollowing = app.packageName in (if (current === entries) linked else linkedPackages(current))
                            if (!alreadyFollowing) {
                                if (match != null) onRelease(match.id) else onSearch(app.label)
                            }
                        }, Modifier.fillMaxWidth(), enabled = !followed && catalog != null) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                val icon = remember(app.icon) { app.icon.toBitmap(144, 144).asImageBitmap() }
                                Image(icon, null, Modifier.size(56.dp).clip(MaterialTheme.shapes.medium))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    AppHeading(app.label, HeadingLevel.Subsection, style = MaterialTheme.typography.titleMedium)
                                    Text(app.version.ifBlank { stringResource(R.string.library_no_version) }, style = MaterialTheme.typography.bodySmall)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                when {
                                    followed -> Column(horizontalAlignment = Alignment.End) {
                                        Icon(Icons.Outlined.BookmarkAdded, stringResource(R.string.library_already_following), tint = theme.featured)
                                        Text(stringResource(R.string.drawer_following), style = MaterialTheme.typography.labelSmall)
                                    }
                                    match != null -> Column(horizontalAlignment = Alignment.End) {
                                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, stringResource(R.string.library_cached_release), tint = theme.featured)
                                        Text(stringResource(R.string.follow_version, match.version), Modifier.widthIn(max = 92.dp),
                                            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    catalog != null -> Icon(Icons.Outlined.Search, stringResource(R.string.library_find), tint = theme.featured)
                                    else -> Unit
                                }
                            }
                        }
                    }
                    if (!installed.busy && installed.error == null && apps.isEmpty()) item {
                        Text(stringResource(R.string.library_no_matches), Modifier.padding(16.dp))
                    }
                } else {
                    val saved = Alphabetical.sorted(entries.filter { it.downloaded }) { ReleaseTitles.parse(it.release.title, it.release.section).name }
                    if (saved.isEmpty()) item { Text(stringResource(R.string.library_empty_downloads), Modifier.padding(16.dp)) }
                    items(saved, key = { it.release.id }) { entry ->
                        val title = remember(entry.release.title, entry.release.section) { ReleaseTitles.parse(entry.release.title, entry.release.section) }
                        ExpressiveCard({ onRelease(entry.release.id) }, Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                ReleaseArtwork(entry.release.thumbnail, null, Modifier.size(56.dp).clip(MaterialTheme.shapes.medium))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    AppHeading(title.name, HeadingLevel.Subsection, style = MaterialTheme.typography.titleMedium)
                                    title.subtitle.takeIf { it.isNotBlank() }?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall)
                                    }
                                    ReleaseViewCount(entry.release.views)
                                    ReleasePostDate(entry.release.publishedAt)
                                }
                                val cleared = stringResource(R.string.library_download_cleared)
                                val undo = stringResource(R.string.action_undo)
                                AppIconButton(Icons.Outlined.DeleteOutline, stringResource(R.string.library_remove_download, title.name), {
                                    store.downloaded(entry.release, false)
                                    scope.launch { if (snackbar.showSnackbar(cleared, undo) == SnackbarResult.ActionPerformed)
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
