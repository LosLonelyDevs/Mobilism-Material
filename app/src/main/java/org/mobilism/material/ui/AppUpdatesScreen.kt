package org.mobilism.material.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import org.mobilism.material.R
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
    val resources = LocalResources.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by updater.state.collectAsStateWithLifecycle()
    val update by checker.state.collectAsStateWithLifecycle()
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updater.notice(resources.getString(R.string.updates_install_cancelled))
    }
    fun install() {
        scope.launch {
            val file = updater.fileForInstall() ?: return@launch
            if (runCatching { installer.launch(updateInstallIntent(context, file)) }.isFailure)
                updater.notice(resources.getString(R.string.updates_no_installer))
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (canInstallUpdates(context)) install()
        else updater.notice(resources.getString(R.string.updates_permission_denied))
    }
    fun requestInstall() {
        if (canInstallUpdates(context)) install()
        else {
            updater.notice(resources.getString(R.string.updates_allow_install))
            if (runCatching { permission.launch(updatePermissionIntent(context)) }.isFailure)
                updater.notice(resources.getString(R.string.updates_system_settings))
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
        TopAppBar(title = { AppHeading(stringResource(R.string.settings_updates), HeadingLevel.Page) }, navigationIcon = {
            AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), onBack)
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
    SettingsList(modifier) {
        if (requiredBuild != null) item("required") {
            Text(stringResource(R.string.updates_required_summary),
                modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        }
        if (requiredBuild == null) {
            item("alerts-heading") { SettingsSectionLabel(stringResource(R.string.updates_alerts)) }
            item("enabled") {
                SettingsToggleRow(stringResource(R.string.updates_enable), stringResource(R.string.updates_enable_summary), Icons.Outlined.NewReleases, state.enabled) { enabled ->
                    checker.setEnabled(enabled)
                    if (enabled) scope.launch { checker.check(automatic = true) }
                }
            }
        }
        item("checks-heading") { SettingsSectionLabel(stringResource(R.string.updates_manual_checks)) }
        item("check") {
            SettingsRow(if (state.checking) stringResource(R.string.updates_checking) else stringResource(R.string.updates_check),
                stringResource(R.string.updates_installed, BuildConfig.VERSION_NAME), Icons.Outlined.Refresh,
                Modifier.clickable(enabled = !state.checking && !install.busy && !install.installRequested, role = Role.Button) {
                    scope.launch { checker.check() }
                })
        }
        if (onCheckNotices != null) item("check-notices") {
            SettingsRow(stringResource(R.string.updates_check_notices), stringResource(R.string.updates_check_notices_summary), Icons.Outlined.Campaign,
                Modifier.clickable(enabled = !install.busy && !install.installRequested, role = Role.Button, onClick = onCheckNotices))
        }
        if (state.checking || state.checked || state.available != null || state.error != null ||
            install.message != null || browserError) item("status") {
            SettingsSectionLabel(stringResource(R.string.updates_status))
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val release = state.available
                val message = when {
                    state.checking -> stringResource(R.string.updates_checking)
                    state.error != null -> state.error
                    release != null -> stringResource(R.string.updates_available_version, release.version)
                    state.checked && requiredBuild != null -> stringResource(R.string.updates_none_yet)
                    state.checked -> stringResource(R.string.updates_up_to_date, BuildConfig.VERSION_NAME)
                    else -> null
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (state.checking || install.phase == UpdatePhase.Verifying || install.phase == UpdatePhase.Cancelling)
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (install.phase == UpdatePhase.Downloading) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearWavyProgressIndicator(progress = { install.progress / 100f }, modifier = Modifier.weight(1f))
                        IconButton(onCancel) { Icon(Icons.Outlined.Cancel, stringResource(R.string.updates_cancel)) }
                    }
                    Text(stringResource(R.string.updates_progress, install.progress), style = MaterialTheme.typography.labelLarge)
                }
                install.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (browserError) Text(stringResource(R.string.updates_no_browser), color = MaterialTheme.colorScheme.error)
            }
        }
        val release = state.available
        if (!install.busy && (release != null || install.phase == UpdatePhase.Ready)) item("update-action") {
            val ready = install.phase == UpdatePhase.Ready
            val title = when {
                ready -> stringResource(R.string.updates_install)
                release?.apk != null -> stringResource(R.string.updates_download)
                else -> stringResource(R.string.updates_view)
            }
            SettingsRow(title,
                if (ready) stringResource(R.string.updates_confirm_summary) else stringResource(R.string.updates_version, (install.release ?: release)?.version.orEmpty()),
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
    val resources = LocalResources.current
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
                    updater.notice(resources.getString(R.string.updates_notices_checking))
                    remoteControl.refresh(force = true)
                    if (remoteControl.notice.value != null) showUpdates = false
                    else updater.notice(if (remoteControl.hasConfiguration) resources.getString(R.string.updates_no_notices)
                        else resources.getString(R.string.updates_notices_failed))
                }
            })
    }
    if (notice != null && !showUpdates) {
        val current = checkNotNull(notice)
        RemoteNoticeDialog(current, onDismiss = { remoteControl.dismiss(current) }, onUpdate = {
            remoteControl.dismiss(current)
            showUpdates = true
            scope.launch { checker.check() }
        }, onRefresh = { scope.launch { remoteControl.refresh(force = true) } }, onRendered = { remoteControl.rendered(current) })
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
    SettingsRow(stringResource(R.string.settings_updates), stringResource(R.string.updates_entry_summary), Icons.Outlined.SystemUpdate,
        Modifier.clickable(role = Role.Button, onClick = onClick))
}

@Composable
internal fun AppUpdateDialog(version: String, downloadable: Boolean, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    AppUpdateModal(stringResource(R.string.updates_available), version, Icons.Outlined.SystemUpdate,
        if (downloadable) stringResource(R.string.updates_download) else stringResource(R.string.updates_open), Icons.Outlined.Download,
        onConfirm = onUpdate, onDismiss = onDismiss, dismissText = stringResource(R.string.updates_later)) {
        Text(stringResource(if (downloadable) R.string.updates_download_available else R.string.updates_view_available),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
