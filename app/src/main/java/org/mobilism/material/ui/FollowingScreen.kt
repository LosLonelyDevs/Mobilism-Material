package org.mobilism.material.ui

import android.content.Intent
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.mobilism.material.R
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.*
import org.mobilism.material.following.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowingScreen(
    entries: List<LibraryEntry>, installed: List<InstalledVersion>, check: FollowCheckState, error: ScreenError?,
    preferences: FollowPreferences, settings: FollowingPreferences, notifications: FollowingNotifications, store: AppLibraryStore,
    onRefresh: () -> Unit, onCheck: () -> Unit, onLink: (Long, String?) -> Unit, onRelease: (Long) -> Unit, onBack: () -> Unit,
    limits: ApiRequestLimits = ApiRequestLimits.Defaults,
) {
    val context = LocalContext.current
    val sharedCache = limits.followCacheBatchSize > 0 && org.mobilism.material.BuildConfig.DAILY_USAGE_ENDPOINT.isNotBlank()
    val theme = LocalAppTheme.current
    val snackbar = remember { SnackbarHostState() }
    val unfollowedLabel = stringResource(R.string.follow_unfollowed)
    val undoLabel = stringResource(R.string.action_undo)
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(notifications.allowed()) }
    var frequencyMenu by remember { mutableStateOf(false) }
    var linkEntry by remember { mutableStateOf<LibraryEntry?>(null) }
    var filter by rememberSaveable { mutableStateOf("") }
    val followed = remember(entries) {
        Alphabetical.sorted(entries.filter { it.watched }) { ReleaseTitles.parse(it.release.title).name }
    }
    val visible = remember(followed, filter) {
        val name = filter.trim()
        if (name.isEmpty()) followed else followed.filter { ReleaseTitles.parse(it.release.title).name.contains(name, ignoreCase = true) }
    }
    val openNotificationSettings = {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
    LifecycleResumeEffect(Unit) {
        allowed = notifications.allowed(); onRefresh()
        onPauseOrDispose { }
    }
    linkEntry?.let { entry -> InstalledAppSheet(installed, onDismiss = { linkEntry = null }, onChoose = { app ->
        onLink(entry.release.id, app?.packageName); linkEntry = null
    }) }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = { TopAppBar(
        title = { AppHeading(stringResource(R.string.drawer_following), HeadingLevel.Page, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = { AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), onBack) },
    ) }) { padding ->
        FeedRefreshContainer(check.busy, check.busy, onCheck, modifier = Modifier.padding(padding),
            enabled = limits.followPullToRefreshEnabled && sharedCache && followed.isNotEmpty(),
            indicatorDescription = stringResource(R.string.follow_checking_apps)) {
            LazyColumn(Modifier.align(Alignment.TopCenter).widthIn(max = 840.dp).fillMaxSize(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item(key = "settings") {
                    Card(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val expanded = preferences.checksExpanded
                            val stateLabel = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable(role = Role.Button) { settings.checksExpanded(!expanded) }
                                .semantics { stateDescription = stateLabel }, verticalAlignment = Alignment.CenterVertically) {
                                AppHeading(stringResource(R.string.follow_update_checks), HeadingLevel.Section, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                            }
                            if (!expanded) return@Column
                            Box {
                                FilledTonalButton(onClick = { frequencyMenu = true }) {
                                    Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                    Text(followIntervalLabel(preferences.intervalHours))
                                    Icon(Icons.Outlined.ExpandMore, null)
                                }
                                DropdownMenu(frequencyMenu, { frequencyMenu = false }, shape = MaterialTheme.shapes.extraLarge) {
                                    FollowingPreferences.intervals.forEach { hours -> RoundedMenuItem(
                                        label = followIntervalLabel(hours), accent = theme.featured,
                                        icon = if (hours == preferences.intervalHours) Icons.Outlined.Check else Icons.Outlined.Schedule,
                                        onClick = { settings.interval(hours); frequencyMenu = false },
                                    ) }
                                }
                            }
                            if (!allowed) TextButton(onClick = openNotificationSettings) { Text(stringResource(R.string.follow_notification_settings)) }
                            if (check.busy) {
                                Text(stringResource(R.string.follow_check_progress, check.done, check.total), style = MaterialTheme.typography.bodySmall)
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large))
                            }
                            if (!check.busy && check.total > 0) Text(
                                if (check.failed > 0) stringResource(R.string.follow_check_failed, check.done, check.total, check.failed)
                                else stringResource(R.string.follow_check_complete, check.done, check.total),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (check.failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                error?.let { item { ErrorPanel(it, onRetry = onCheck) } }
                if (followed.isNotEmpty()) item(key = "filter") {
                    AppSearchField(filter, { filter = it }, stringResource(R.string.follow_filter), searchIcon = Icons.Outlined.Search)
                }
                if (followed.isEmpty()) item { Text(stringResource(R.string.follow_empty), Modifier.padding(16.dp)) }
                else if (visible.isEmpty()) item { Text(stringResource(R.string.follow_no_matches), Modifier.padding(16.dp)) }
                items(visible, key = { it.release.id }) { entry ->
                    val device = FollowingVersions.matchingInstalled(entry, installed)
                    FollowingCard(entry, device, onRelease, onLink = { linkEntry = entry }, onUnfollow = {
                        store.watch(entry.release, false)
                        scope.launch {
                            if (snackbar.showSnackbar(unfollowedLabel, undoLabel) == SnackbarResult.ActionPerformed)
                                store.restore(entry)
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun followIntervalLabel(hours: Long): String = when (hours) {
    0L -> stringResource(R.string.follow_automatic)
    else -> pluralStringResource(R.plurals.follow_every_hours, hours.toInt(), hours)
}

@Composable
private fun FollowingCard(entry: LibraryEntry, installed: InstalledVersion?, onRelease: (Long) -> Unit, onLink: () -> Unit, onUnfollow: () -> Unit) {
    val title = remember(entry.release.title) { ReleaseTitles.parse(entry.release.title) }
    val result = remember(entry, installed) { FollowingVersions.evaluate(entry, installed, emptyList()) }
    val latest = result.latest?.let { ReleaseTitles.parse(it.title) }
    val theme = LocalAppTheme.current
    ExpressiveCard({ onRelease(result.latest?.id ?: entry.release.id) }, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(entry.release.thumbnail, null, Modifier.size(72.dp).clip(MaterialTheme.shapes.medium))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppHeading(title.name, HeadingLevel.Subsection, style = MaterialTheme.typography.titleMedium)
                    val version = latest?.version?.let { stringResource(R.string.follow_version, it) } ?: stringResource(R.string.follow_no_version)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(latest?.build?.let { stringResource(R.string.follow_latest_build, version, it) }
                            ?: stringResource(R.string.follow_latest, version), style = MaterialTheme.typography.bodySmall)
                        result.latest?.let { ReleasePostDate(it.publishedAt) }
                    }
                }
            }
            result.update?.let { update ->
                Button({ onRelease(update.id) }, Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.success, contentColor = theme.onSuccess)) {
                    Icon(Icons.Outlined.NewReleases, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.follow_view_update))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    entry.checkedAt?.let { time ->
                        Text(stringResource(R.string.follow_checked_at,
                            DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                AppIconButton(Icons.Outlined.BookmarkRemove, stringResource(R.string.follow_unfollow_app, title.name), onUnfollow)
                AppIconButton(
                    if (installed == null) Icons.Outlined.LinkOff else Icons.Outlined.Link,
                    stringResource(if (installed == null) R.string.follow_link_app else R.string.follow_change_app),
                    onLink
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstalledAppSheet(apps: List<InstalledVersion>, onDismiss: () -> Unit, onChoose: (InstalledVersion?) -> Unit) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppHeading(stringResource(R.string.follow_link_app), HeadingLevel.Section, style = MaterialTheme.typography.titleLarge)
            AppSearchField(query, { query = it }, stringResource(R.string.follow_find_app), searchIcon = Icons.Outlined.Search)
            FilledTonalButton(onClick = { onChoose(null) }) { Text(stringResource(R.string.follow_match_automatically)) }
            LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Alphabetical.sorted(apps.filter { it.name.contains(query, true) || it.packageName.contains(query, true) }) { it.name }, key = { it.packageName }) { app ->
                    ExpressiveCard({ onChoose(app) }, Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(app.name, style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.follow_package_version, app.version, app.packageName), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
