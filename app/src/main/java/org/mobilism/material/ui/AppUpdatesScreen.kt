package org.mobilism.material.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.mobilism.material.BuildConfig
import org.mobilism.material.data.AppRelease
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.updates.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppUpdatesScreen(checker: AppUpdateChecker, updater: AppUpdateViewModel, onBack: () -> Unit,
    requiredBuild: Int? = null, onCheckNotices: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by updater.state.collectAsStateWithLifecycle()
    val update by checker.state.collectAsStateWithLifecycle()
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updater.notice("If installation was canceled, choose Install update to try again.")
    }
    fun install() {
        scope.launch {
            val file = updater.fileForInstall() ?: return@launch
            if (runCatching { installer.launch(updateInstallIntent(context, file)) }.isFailure)
                updater.notice("Android's installer could not open. Try Install update again.")
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (canInstallUpdates(context)) install()
        else updater.notice("Installation permission was not enabled. Choose Install update to try again.")
    }
    fun requestInstall() {
        if (canInstallUpdates(context)) install()
        else {
            updater.notice("Allow this app to install unknown apps, then return here.")
            if (runCatching { permission.launch(updatePermissionIntent(context)) }.isFailure)
                updater.notice("Open Android settings and allow installs from this app, then try again.")
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentRequestInstall = rememberUpdatedState(::requestInstall)
    LaunchedEffect(state.installRequested, lifecycle) {
        if (state.installRequested) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (updater.consumeInstallRequest()) currentRequestInstall.value()
        }
    }
    LaunchedEffect(update.prompt) { checker.dismissPrompt() }
    BackHandler(onBack = onBack)
    Scaffold(topBar = {
        TopAppBar(title = { AppHeading("Updates", HeadingLevel.Page) }, navigationIcon = {
            AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
        })
    }) { insets ->
        AppUpdateSettings(checker, updater::downloadAndInstall, Modifier.fillMaxSize().padding(insets),
            state, ::requestInstall, updater::cancel, requiredBuild = requiredBuild, onCheckNotices = onCheckNotices)
    }
}

@Composable
internal fun AppUpdateSettings(checker: AppUpdateChecker, onDownload: (AppRelease) -> Unit,
    modifier: Modifier = Modifier,
    install: UpdateInstallState = UpdateInstallState(), onInstall: () -> Unit = {}, onCancel: () -> Unit = {},
    openUrl: (String) -> Unit = LocalUriHandler.current::openUri, requiredBuild: Int? = null,
    onCheckNotices: (() -> Unit)? = null) {
    val state by checker.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var browserError by remember { mutableStateOf(false) }
    LazyColumn(modifier, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (requiredBuild != null) item("required") {
            Text("Update the app to continue. If a download is unavailable, return to the notice and refresh it.",
                modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        }
        if (requiredBuild == null) item("enabled") {
            SettingsRow("Enable app updates", "Show new version alerts.",
                Icons.Outlined.NewReleases, Modifier.toggleable(state.enabled, role = Role.Switch) { enabled ->
                    checker.setEnabled(enabled)
                    if (enabled) scope.launch { checker.check(automatic = true) }
                }) {
                Switch(state.enabled, onCheckedChange = null, thumbContent = if (state.enabled) {
                    { Icon(Icons.Outlined.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                } else null)
            }
        }
        item("check") {
            SettingsRow(if (state.checking) "Checking for updates..." else "Check for updates",
                "Installed version v${BuildConfig.VERSION_NAME}", Icons.Outlined.Refresh,
                Modifier.clickable(enabled = !state.checking && !install.busy && !install.installRequested, role = Role.Button) {
                    scope.launch { checker.check() }
                })
        }
        if (onCheckNotices != null) item("check-notices") {
            SettingsRow("Check app notices", "Check notices for this account and version.", Icons.Outlined.Campaign,
                Modifier.clickable(enabled = !install.busy && !install.installRequested, role = Role.Button, onClick = onCheckNotices))
        }
        if (state.checking || state.checked || state.available != null || state.error != null ||
            install.message != null || browserError) item("status") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val release = state.available
                val message = when {
                    state.checking -> "Checking for updates..."
                    state.error != null -> state.error
                    release != null -> "Version ${release.version} is available."
                    state.checked && requiredBuild != null -> "No newer release is available yet. Try again shortly or refresh the notice."
                    state.checked -> "You're up to date (v${BuildConfig.VERSION_NAME})."
                    else -> null
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (state.checking || install.phase == UpdatePhase.Verifying || install.phase == UpdatePhase.Cancelling)
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (install.phase == UpdatePhase.Downloading) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearWavyProgressIndicator(progress = { install.progress / 100f }, modifier = Modifier.weight(1f))
                        IconButton(onCancel) { Icon(Icons.Outlined.Cancel, "Cancel download") }
                    }
                    Text("${install.progress}% downloaded", style = MaterialTheme.typography.labelLarge)
                }
                install.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (browserError) Text("No browser is available.", color = MaterialTheme.colorScheme.error)
            }
        }
        val release = state.available
        if (!install.busy && (release != null || install.phase == UpdatePhase.Ready)) item("update-action") {
            val ready = install.phase == UpdatePhase.Ready
            val title = when {
                ready -> "Install update"
                release?.apk != null -> "Download"
                else -> "View release"
            }
            SettingsRow(title,
                if (ready) "Android will ask you to confirm installation." else "Version ${(install.release ?: release)?.version}",
                if (ready) Icons.Outlined.SystemUpdate else Icons.Outlined.Download,
                Modifier.clickable(enabled = !state.checking, role = Role.Button) {
                    when {
                        ready -> onInstall()
                        release?.apk != null -> onDownload(release)
                        release != null -> browserError = runCatching { openUrl(release.pageUrl) }.isFailure
                    }
                })
        }
    }
}

@Composable
internal fun AppUpdateHost(checker: AppUpdateChecker, updater: AppUpdateViewModel,
    remoteControl: org.mobilism.material.remote.RemoteControlStore,
    changelog: AppChangelog?, onChangelogDismiss: () -> Unit, content: @Composable (() -> Unit) -> Unit) {
    val update by checker.state.collectAsStateWithLifecycle()
    val installing by updater.state.collectAsStateWithLifecycle()
    var showUpdates by rememberSaveable { mutableStateOf(false) }
    val notice by remoteControl.notice.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val required = notice?.required == true
    Box(Modifier.fillMaxSize()) {
        if (required) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
        } else {
            Box(if (showUpdates) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()) {
                content { showUpdates = true }
            }
        }
        if (showUpdates) AppUpdatesScreen(checker, updater, onBack = { showUpdates = false },
            requiredBuild = notice?.takeIf { it.required }?.requiredVersionCode, onCheckNotices = {
                scope.launch {
                    updater.notice("Checking app notices...")
                    remoteControl.refresh(force = true)
                    if (remoteControl.notice.value != null) showUpdates = false
                    else updater.notice(if (remoteControl.hasConfiguration) "No matching notices for this account and version."
                        else "Notices couldn't be checked. App defaults are active.")
                }
            })
    }
    if (notice != null && !showUpdates) {
        val current = checkNotNull(notice)
        RemoteNoticeDialog(current, onDismiss = { remoteControl.dismiss(current) }, onUpdate = {
            remoteControl.dismiss(current)
            showUpdates = true
            scope.launch { checker.check() }
        }, onRefresh = { scope.launch { remoteControl.refresh(force = true) } })
    }
    else if (!required && changelog != null && !showUpdates) AppChangelogDialog(changelog, onChangelogDismiss)
    else if (!showUpdates && !installing.busy) {
        update.prompt?.takeIf { update.enabled }?.let { release ->
            AppUpdateDialog(release.version, release.apk != null, onUpdate = {
                checker.dismissPrompt()
                showUpdates = true
                if (release.apk != null) updater.downloadAndInstall(release)
            }, onDismiss = checker::dismissPrompt)
        }
    }
}

@Composable
internal fun AppUpdatesEntry(onClick: () -> Unit) {
    SettingsRow("Updates", "Check, download, and install new versions.", Icons.Outlined.SystemUpdate,
        Modifier.clickable(role = Role.Button, onClick = onClick))
}

@Composable
internal fun AppUpdateDialog(version: String, downloadable: Boolean, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    AppUpdateModal("Update available", version, Icons.Outlined.SystemUpdate,
        if (downloadable) "Download" else "Open updates", Icons.Outlined.Download,
        onConfirm = onUpdate, onDismiss = onDismiss, dismissText = "Later") {
        Text("A new version of Mobilism Material is available. " +
            if (downloadable) "Download it now to get the latest changes." else "Open Updates to see this release.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
