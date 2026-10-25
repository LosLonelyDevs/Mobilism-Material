package org.mobilism.material.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import org.mobilism.material.designsystem.AppSurface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.ExpressiveButton
import org.mobilism.material.settings.DeveloperPreferences
import org.mobilism.material.settings.DiagnosticLogStore
import org.mobilism.material.settings.DiagnosticLogExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun DeveloperOptionsRoute(state: DeveloperPreferences, logs: DiagnosticLogStore,
    onVerboseLogging: (Boolean) -> Unit, onDebridEnabled: (Boolean) -> Unit,
    onDisable: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    DeveloperOptionsScreen(state, onVerboseLogging, onDebridEnabled, onDisable, onBack,
        exporting = exporting, exportMessage = exportMessage, onExportLogs = {
            if (!exporting) scope.launch {
                exporting = true
                exportMessage = null
                try {
                    val uri = DiagnosticLogExporter.export(context, logs)
                    if (uri == null) exportMessage = "No logs yet. Enable verbose logging and reproduce the issue."
                    else {
                        val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            clipData = android.content.ClipData.newUri(context.contentResolver, "Diagnostic logs", uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(share, "Export logs"))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    exportMessage = "Logs could not be exported. Try again."
                } finally {
                    exporting = false
                }
            }
        })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperOptionsScreen(state: DeveloperPreferences, onVerboseLogging: (Boolean) -> Unit,
    onDebridEnabled: (Boolean) -> Unit, onDisable: () -> Unit, onBack: () -> Unit,
    onExportLogs: () -> Unit, exporting: Boolean = false, exportMessage: String? = null) {
    Scaffold(topBar = {
        TopAppBar(title = { AppHeading("Developer options", HeadingLevel.Page) }, navigationIcon = {
            AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
        })
    }) { insets ->
        LazyColumn(Modifier.padding(insets), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                DeveloperToggle("Developer mode", "Turn off to hide these options",
                    state.enabled, { if (!it) onDisable() })
            }
            item {
                DeveloperToggle("Verbose logging", "Record diagnostic details",
                    state.verboseLogging, onVerboseLogging)
            }
            item {
                AppSurface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        AppHeading("Diagnostic logs", HeadingLevel.Section, style = MaterialTheme.typography.titleLarge)
                        Text("Reproduce the issue, then export.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ExpressiveButton(onExportLogs, enabled = !exporting) {
                            Icon(Icons.Outlined.Share, null, Modifier.padding(end = 8.dp))
                            Text(if (exporting) "Preparing logs" else "Export logs")
                        }
                        exportMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            item {
                DeveloperToggle("Experimental premium services", "Mobilism Premium, MegaDebrid, and Real-Debrid",
                    state.debridEnabled, onDebridEnabled)
            }
        }
    }
}

@Composable
private fun DeveloperToggle(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    AppSurface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppHeading(title, HeadingLevel.Section, style = MaterialTheme.typography.titleLarge)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}
