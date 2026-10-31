package org.mobilism.material.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.mobilism.material.BuildConfig
import org.mobilism.material.MobilismApplication
import org.mobilism.material.designsystem.AppSurface
import org.mobilism.material.updates.AppUpdateChecker
import org.mobilism.material.updates.AppUpdateNotifications

@Composable
internal fun rememberAppUpdateChecker(): AppUpdateChecker {
    val context = LocalContext.current.applicationContext
    return remember(context) { (context as? MobilismApplication)?.appUpdates ?: AppUpdateChecker(context) }
}

@Composable
internal fun AppUpdatePermissionEffect(checker: AppUpdateChecker) {
    val context = LocalContext.current
    val notifications = remember(context) { AppUpdateNotifications(context) }
    val state by checker.state.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) checker.notifyAvailable()
    }
    LaunchedEffect(state.available, state.enabled) {
        if (state.enabled && state.available != null && notifications.permissionNeeded() && !checker.permissionRequested) {
            checker.permissionRequested = true
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
internal fun AppUpdatesCard(checker: AppUpdateChecker = rememberAppUpdateChecker(),
    openUrl: (String) -> Unit = LocalUriHandler.current::openUri) {
    val context = LocalContext.current
    val notifications = remember(context) { AppUpdateNotifications(context) }
    val state by checker.state.collectAsStateWithLifecycle()
    var allowed by remember { mutableStateOf(notifications.allowed()) }
    var browserError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = notifications.allowed()
        if (granted) checker.notifyAvailable()
    }
    LifecycleResumeEffect(checker) {
        allowed = notifications.allowed()
        if (allowed) checker.notifyAvailable()
        onPauseOrDispose { }
    }
    AppSurface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppHeading("App updates", HeadingLevel.Section, style = MaterialTheme.typography.titleLarge)
            SettingsSubtitle("New GitHub releases.")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Update notifications", style = MaterialTheme.typography.titleSmall)
                    SettingsSubtitle("At most twice daily.")
                }
                Switch(state.enabled, checker::setEnabled,
                    modifier = Modifier.semantics { contentDescription = "App update notifications" })
            }
            if (state.enabled && !allowed) {
                TextButton(onClick = {
                    if (notifications.permissionNeeded() && !checker.permissionRequested) {
                        checker.permissionRequested = true
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, AppUpdateNotifications.CHANNEL))
                    }
                }) { Text("Allow notifications") }
            }
            Button(onClick = { browserError = false; scope.launch { checker.check() } },
                enabled = !state.checking, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.checking) "Checking..." else "Check for updates")
            }
            if (state.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            val release = state.available
            if (release != null) {
                Text("Version ${release.version} is available.", style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = {
                    browserError = runCatching { openUrl(release.apkUrl ?: release.pageUrl) }.isFailure
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (release.apkUrl != null) "Download update" else "View release")
                }
            } else if (state.checked && state.error == null && !state.checking) {
                Text("You're up to date (v${BuildConfig.VERSION_NAME}).", style = MaterialTheme.typography.bodyMedium)
            }
            if (browserError) Text("No browser is available.", color = MaterialTheme.colorScheme.error)
        }
    }
}
