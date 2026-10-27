package org.mobilism.material.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import org.mobilism.material.designsystem.LocalAppTheme
import org.mobilism.material.settings.CpuArchitecture
import org.mobilism.material.settings.DownloadPreferences
import org.mobilism.material.settings.DownloadPreferencesStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsScreen(state: DownloadPreferences, store: DownloadPreferencesStore, repository: DebridRepository, onBack: () -> Unit) {
    val theme = LocalAppTheme.current
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
        catch (_: Exception) { error = ScreenError("The host list could not be refreshed. Saved preferences are still available.") }
        finally { busy = false }
    }
    LaunchedEffect(Unit) { loadHosts() }
    Scaffold(topBar = {
        TopAppBar(title = { AppHeading("Download preferences", HeadingLevel.Page) }, navigationIcon = {
            AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
        })
    }) { insets ->
        LazyColumn(Modifier.padding(insets), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                CollapsibleSection("download-tracking", "Download tracking", Icons.Outlined.Visibility, theme.success) {
                    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                        .toggleable(state.autoWatchDownloads, role = Role.Switch, onValueChange = store::autoWatchDownloads)
                        .padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Auto-watch downloaded apps", Modifier.weight(1f))
                        Switch(state.autoWatchDownloads, onCheckedChange = null)
                    }
                    Text("Opening a file host marks that release downloaded. It's a rough record, not a completion check. You can clear the mark on the release page.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                CollapsibleSection("cpu-preference", "CPU architecture", Icons.Outlined.Memory, theme.reading) {
                    Text("Matching and universal downloads stay open. Other architectures are collapsed, so you can still access them.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("This device: " + (Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown"),
                        style = MaterialTheme.typography.labelLarge, color = theme.reading)
                    Box {
                        ExpressiveButton({ menu = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(
                            containerColor = theme.reading, contentColor = theme.onReading,
                        )) {
                            Text(state.architecture?.label ?: "All architectures", Modifier.weight(1f))
                            Icon(Icons.Outlined.ExpandMore, null)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.extraLarge) {
                            RoundedMenuItem("All architectures", { store.architecture(null); menu = false }, theme.reading)
                            Alphabetical.sorted(CpuArchitecture.entries) { it.label }.forEach { architecture ->
                                RoundedMenuItem(architecture.label, { store.architecture(architecture); menu = false }, theme.reading)
                            }
                        }
                    }
                }
            }
            item {
                CollapsibleSection("host-preferences", "Preferred file hosts", Icons.Outlined.StarOutline, colors.primary) {
                    Text("Selected hosts appear first within each architecture group. Other mirrors remain available.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (busy && state.hosts.isEmpty()) LoadingState("Loading Mobilism hosts…", Modifier.fillMaxWidth())
                    error?.let { ErrorPanel(it, onRetry = { scope.launch { loadHosts() } }) }
                    val hosts = Alphabetical.sorted((state.hosts + state.preferredHosts.map { SupportedHost(it, HostMatcher.displayName(it)) })
                        .distinctBy { it.domain }) { it.name }
                    hosts.forEach { host ->
                        val checked = host.domain in state.preferredHosts
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                                .background(if (checked) colors.surfaceContainerHighest else colors.surfaceContainerLow)
                                .border(1.dp, if (checked) colors.primary else colors.outlineVariant, MaterialTheme.shapes.large)
                                .heightIn(min = 64.dp).toggleable(checked, role = Role.Checkbox, onValueChange = {
                                store.hosts(if (it) state.preferredHosts + host.domain else state.preferredHosts - host.domain)
                            }).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Checkbox(checked, onCheckedChange = null, colors = CheckboxDefaults.colors(
                                checkedColor = colors.primary, checkmarkColor = colors.onPrimary,
                                uncheckedColor = colors.onSurfaceVariant))
                            Column(Modifier.weight(1f)) {
                                Text(host.name, color = colors.onSurface)
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
