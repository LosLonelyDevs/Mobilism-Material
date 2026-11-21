package org.mobilism.material.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import org.mobilism.material.data.Alphabetical
import org.mobilism.material.data.HostMatcher
import org.mobilism.material.data.SupportedHost
import org.mobilism.material.designsystem.AppIconButton
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
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SettingsSubtitle(stringResource(R.string.download_preferred_hosts_summary))
                        if (busy && state.hosts.isEmpty()) LoadingState(stringResource(R.string.download_loading_hosts), Modifier.fillMaxWidth())
                        error?.let { ErrorPanel(it, onRetry = { scope.launch { loadHosts() } }) }
                        val hosts = Alphabetical.sorted((state.hosts + state.preferredHosts.map { SupportedHost(it, HostMatcher.displayName(it)) })
                            .distinctBy { it.domain }) { it.name }
                        hosts.forEach { host ->
                            val checked = host.domain in state.preferredHosts
                            Row(
                                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                                    .background(if (checked) colors.secondaryContainer else colors.surface)
                                    .heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox, onValueChange = {
                                    store.hosts(if (it) state.preferredHosts + host.domain else state.preferredHosts - host.domain)
                                }).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Checkbox(checked, onCheckedChange = null, colors = CheckboxDefaults.colors(
                                    checkedColor = colors.primary, checkmarkColor = colors.onPrimary,
                                    uncheckedColor = colors.onSurfaceVariant))
                                Column(Modifier.weight(1f)) {
                                    Text(host.name, style = MaterialTheme.typography.titleSmall,
                                        color = if (checked) colors.onSecondaryContainer else colors.onSurface)
                                    debrid.supportLabel("https://${host.domain}")?.let {
                                        Text(it, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
