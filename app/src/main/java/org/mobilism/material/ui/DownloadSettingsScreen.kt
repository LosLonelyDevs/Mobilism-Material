package org.mobilism.material.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import org.mobilism.material.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.mobilism.material.data.DebridRepository
import org.mobilism.material.data.DebridState
import org.mobilism.material.data.Alphabetical
import org.mobilism.material.data.HostMatcher
import org.mobilism.material.data.SupportedHost
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.AppSurface
import org.mobilism.material.designsystem.ExpressiveButton
import org.mobilism.material.designsystem.LoadingState
import org.mobilism.material.settings.CpuArchitecture
import org.mobilism.material.settings.DownloadPreferences
import org.mobilism.material.settings.DownloadPreferencesStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsScreen(state: DownloadPreferences, store: DownloadPreferencesStore, repository: DebridRepository, onBack: () -> Unit) {
    val resources = LocalResources.current
    val colors = MaterialTheme.colorScheme
    val debrid by repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ScreenError?>(null) }
    suspend fun loadHosts() {
        busy = true
        error = null
        try { store.catalog(repository.fileHosts()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = ScreenError(resources.getString(R.string.download_hosts_error)) }
        finally { busy = false }
    }
    LaunchedEffect(Unit) { loadHosts() }
    Scaffold(topBar = {
        TopAppBar(title = { AppHeading(stringResource(R.string.settings_downloads), HeadingLevel.Page) }, navigationIcon = {
            AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), onBack)
        })
    }) { insets ->
        SettingsList(Modifier.padding(insets)) {
            item { SettingsSectionLabel(stringResource(R.string.download_files)) }
            item("release-transfers") { ReleaseTransferSettings(state, store) }
            item("download-tracking") {
                SettingsExpandableSection(stringResource(R.string.download_tracking)) {
                    SettingsToggleRow(stringResource(R.string.download_auto_watch), stringResource(R.string.download_auto_watch_summary),
                        Icons.Outlined.Visibility, state.autoWatchDownloads, onCheckedChange = store::autoWatchDownloads)
                }
            }
            item("cpu-preference") {
                SettingsExpandableSection(stringResource(R.string.download_architecture)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.download_device_arch, Build.SUPPORTED_ABIS.firstOrNull() ?: stringResource(R.string.download_arch_unknown)),
                            style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                        Box {
                            ExpressiveButton({ menu = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.filledTonalButtonColors()) {
                                Text(state.architecture?.label ?: stringResource(R.string.download_arch_all), Modifier.weight(1f))
                                Icon(Icons.Outlined.ExpandMore, null)
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.extraLarge) {
                                RoundedMenuItem(stringResource(R.string.download_arch_all), { store.architecture(null); menu = false }, colors.primary)
                                Alphabetical.sorted(CpuArchitecture.entries) { it.label }.forEach { architecture ->
                                    RoundedMenuItem(architecture.label, { store.architecture(architecture); menu = false }, colors.primary)
                                }
                            }
                        }
                    }
                }
            }
            item("host-preferences") {
                SettingsExpandableSection(stringResource(R.string.download_preferred_hosts)) {
                    PreferredHostsCard(state, debrid, busy, error, onRetry = { scope.launch { loadHosts() } },
                        onSelectionChange = store::hosts)
                }
            }
        }
    }
}

private val PreferredHostRowMinHeight = 48.dp
private val PreferredHostRowSpacing = 4.dp
private val PreferredHostsListHeight = PreferredHostRowMinHeight * 6.5f + PreferredHostRowSpacing * 6

@Composable
private fun PreferredHostsCard(state: DownloadPreferences, debrid: DebridState, busy: Boolean, error: ScreenError?,
    onRetry: () -> Unit, onSelectionChange: (Set<String>) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val hosts = remember(state.hosts, state.preferredHosts) {
        val saved = state.preferredHosts.map { SupportedHost(it, HostMatcher.displayName(it)) }
        Alphabetical.sorted((state.hosts + saved).distinctBy { it.domain }) { it.name }
    }
    val filter = query.trim()
    val matches = remember(hosts, filter) {
        hosts.filter { it.name.contains(filter, ignoreCase = true) || it.domain.contains(filter, ignoreCase = true) }
    }
    val colors = MaterialTheme.colorScheme
    AppSurface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = colors.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsSubtitle(stringResource(R.string.download_preferred_hosts_summary))
            OutlinedTextField(query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.download_search_hosts)) }, shape = MaterialTheme.shapes.large,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = if (query.isEmpty()) null else {
                    { AppIconButton(Icons.Outlined.Close, stringResource(R.string.download_clear_host_search), { query = "" }) }
                })
            error?.let { ErrorPanel(it, onRetry = onRetry) }
            key(filter) {
                LazyColumn(Modifier.fillMaxWidth().height(PreferredHostsListHeight),
                    verticalArrangement = Arrangement.spacedBy(PreferredHostRowSpacing)) {
                    if (busy && hosts.isEmpty()) item {
                        LoadingState(stringResource(R.string.download_loading_hosts), Modifier.fillMaxWidth())
                    } else if (matches.isEmpty() && (hosts.isNotEmpty() || error == null)) item {
                        Text(stringResource(if (hosts.isEmpty()) R.string.download_no_hosts else R.string.download_no_host_matches),
                            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    items(matches, key = { it.domain }) { host ->
                        PreferredHostRow(host.name, host.domain in state.preferredHosts,
                            debrid.supportLabel("https://${host.domain}")) { enabled ->
                            onSelectionChange(if (enabled) state.preferredHosts + host.domain else state.preferredHosts - host.domain)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferredHostRow(name: String, checked: Boolean, supportLabel: String?, onCheckedChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
        .background(if (checked) colors.secondaryContainer else colors.surface)
        .heightIn(min = PreferredHostRowMinHeight).toggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange)
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Checkbox(checked, onCheckedChange = null, colors = CheckboxDefaults.colors(
            checkedColor = colors.primary, checkmarkColor = colors.onPrimary, uncheckedColor = colors.onSurfaceVariant))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = if (checked) colors.onSecondaryContainer else colors.onSurface)
            supportLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant) }
        }
    }
}
