package org.mobilism.material.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.*
import org.mobilism.material.following.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowingScreen(
    entries: List<LibraryEntry>, installed: List<InstalledVersion>, check: FollowCheckState, error: ScreenError?,
    preferences: FollowPreferences, settings: FollowingPreferences, notifications: FollowingNotifications, store: AppLibraryStore,
    onRefresh: () -> Unit, onCheck: () -> Unit, onLink: (Long, String?) -> Unit, onRelease: (Long) -> Unit, onBack: () -> Unit,
) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(notifications.allowed()) }
    var frequencyMenu by remember { mutableStateOf(false) }
    var linkEntry by remember { mutableStateOf<LibraryEntry?>(null) }
    val followed = entries.filter { it.watched }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        settings.notifications(granted)
        allowed = notifications.allowed()
        if (granted) onCheck()
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
        title = { AppHeading("Following", HeadingLevel.Page, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = { AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack) },
    ) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxSize(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item(key = "settings") {
                    Card(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppHeading("Update checks", HeadingLevel.Section, style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Background checks", style = MaterialTheme.typography.titleSmall)
                                    Text("Android may delay checks to save battery.", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(preferences.backgroundChecks, settings::background,
                                    modifier = Modifier.semantics { contentDescription = "Background checks" })
                            }
                            Box {
                                FilledTonalButton(onClick = { frequencyMenu = true }, enabled = preferences.backgroundChecks) {
                                    Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                    Text(if (preferences.intervalHours == 1L) "Every hour" else "Every ${preferences.intervalHours} hours")
                                    Icon(Icons.Outlined.ExpandMore, null)
                                }
                                DropdownMenu(frequencyMenu, { frequencyMenu = false }, shape = MaterialTheme.shapes.extraLarge) {
                                    FollowingPreferences.intervals.forEach { hours -> RoundedMenuItem(
                                        label = if (hours == 1L) "Every hour" else "Every $hours hours", accent = theme.featured,
                                        icon = if (hours == preferences.intervalHours) Icons.Outlined.Check else Icons.Outlined.Schedule,
                                        onClick = { settings.interval(hours); frequencyMenu = false },
                                    ) }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("New version notifications", style = MaterialTheme.typography.titleSmall)
                                    Text("One alert per version. Tap it to open the release.", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(preferences.notifications && allowed, { enabled ->
                                    if (!enabled) settings.notifications(false)
                                    else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    else {
                                        settings.notifications(true)
                                        allowed = notifications.allowed()
                                        if (allowed) onCheck() else openNotificationSettings()
                                    }
                                }, modifier = Modifier.semantics { contentDescription = "New version notifications" })
                            }
                            if (!allowed) TextButton(onClick = openNotificationSettings) { Text("Notification settings") }
                            Button(onClick = onCheck, enabled = followed.isNotEmpty() && !check.busy, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.Refresh, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                                Text(if (check.busy) "Checking ${check.done} of ${check.total}…" else "Check now")
                            }
                            if (check.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large))
                            if (!check.busy && check.total > 0) Text("Checked ${check.done} of ${check.total}" +
                                if (check.failed > 0) " · ${check.failed} failed. Try again." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (check.failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                error?.let { item { ErrorPanel(it, onRetry = onCheck) } }
                if (followed.isEmpty()) item { Text("Open an Apps release and choose Follow app. New versions will appear here.", Modifier.padding(16.dp)) }
                items(followed, key = { it.release.id }) { entry ->
                    val device = FollowingVersions.matchingInstalled(entry, installed)
                    FollowingCard(entry, device, onRelease, onLink = { linkEntry = entry }, onUnfollow = {
                        store.watch(entry.release, false)
                        scope.launch { if (snackbar.showSnackbar("App unfollowed", "Undo") == SnackbarResult.ActionPerformed) store.restore(entry) }
                    })
                }
            }
        }
    }
}

@Composable
private fun FollowingCard(entry: LibraryEntry, installed: InstalledVersion?, onRelease: (Long) -> Unit, onLink: () -> Unit, onUnfollow: () -> Unit) {
    val title = remember(entry.release.title) { ReleaseTitles.parse(entry.release.title) }
    val result = remember(entry, installed) { FollowingVersions.evaluate(entry, installed, emptyList()) }
    val latest = result.latest?.let { ReleaseTitles.parse(it.title) }
    val theme = LocalAppTheme.current
    ExpressiveCard({ onRelease(result.latest?.id ?: entry.release.id) }, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(entry.release.thumbnail, null, Modifier.size(56.dp).clip(MaterialTheme.shapes.medium))
                AppHeading(title.name, HeadingLevel.Subsection, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                AppIconButton(Icons.Outlined.BookmarkRemove, "Unfollow ${title.name}", onUnfollow)
            }
            Text(installed?.let { "Installed: ${it.version} (build ${it.versionCode})" }
                ?: if (entry.linkedPackage != null || ReleaseVersions.packageName(entry.release) != null) "Not found on this device" else "Installed app not linked",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            Text("Latest found: " + (latest?.version?.let { "v$it" } ?: "Version not detected") +
                (latest?.build?.let { " (build $it)" } ?: ""), style = MaterialTheme.typography.bodyMedium)
            ReleasePostDate((result.latest ?: entry.release).publishedAt)
            if (installed == null) Text("Tracking the followed release: " + (title.version?.let { "v$it" } ?: "Unknown version"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!result.comparable) Text("This version couldn't be compared automatically.", style = MaterialTheme.typography.bodySmall)
            result.update?.let { update ->
                Button({ onRelease(update.id) }, Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.success, contentColor = theme.onSuccess)) {
                    Icon(Icons.Outlined.NewReleases, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("View update")
                }
            }
            FilledTonalButton(onClick = onLink) { Icon(Icons.Outlined.Link, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(if (installed == null) "Link installed app" else "Change linked app") }
            entry.checkError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            entry.checkedAt?.let { time ->
                Text((if (result.update == null && result.comparable) "No newer matching release found · " else "Checked · ") +
                    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            AppHeading("Link installed app", HeadingLevel.Section, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Find an app") }, singleLine = true,
                shape = MaterialTheme.shapes.extraLarge, leadingIcon = { Icon(Icons.Outlined.Search, null) })
            FilledTonalButton(onClick = { onChoose(null) }) { Text("Match automatically") }
            LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(apps.filter { it.name.contains(query, true) || it.packageName.contains(query, true) }, key = { it.packageName }) { app ->
                    ExpressiveCard({ onChoose(app) }, Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(app.name, style = MaterialTheme.typography.titleSmall)
                            Text("${app.version} · ${app.packageName}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
