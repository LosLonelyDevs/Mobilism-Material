package org.mobilism.material.ui

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mobilism.material.downloads.ReleaseDownloads
import org.mobilism.material.downloads.ReleaseFolder
import org.mobilism.material.downloads.savedReleaseIntent
import org.mobilism.material.settings.DownloadPreferences
import org.mobilism.material.settings.DownloadPreferencesStore
import org.mobilism.material.designsystem.AppSurface
import org.mobilism.material.designsystem.AppTextButton
import org.mobilism.material.designsystem.LocalAppTheme
import org.mobilism.material.designsystem.LocalReducedMotion
import org.mobilism.material.designsystem.LoadingState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReleaseTransferSettings(state: DownloadPreferences, store: DownloadPreferencesStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var transfers by rememberSaveable { mutableStateOf(false) }
    val current = state.downloadFolder?.let(Uri::parse)
    val folderLabel by produceState<String?>(null, current, state.internalDownloads) {
        value = current?.takeIf { state.internalDownloads }?.let { uri ->
            withContext(Dispatchers.IO) {
                val id = DocumentsContract.getTreeDocumentId(uri)
                val root = DocumentsContract.buildDocumentUriUsingTree(uri, id)
                runCatching { context.contentResolver.query(root, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()
                    ?: id.substringAfter(':').ifBlank { id }
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    ReleaseFolder(context, uri).requireWritable()
                }
                store.useInternal(uri.toString())
                error = null
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Choose a folder that allows files to be created." }
        }
    }
    fun chooseFolder() {
        val initial = current?.let { DocumentsContract.buildDocumentUriUsingTree(it, DocumentsContract.getTreeDocumentId(it)) }
        try { picker.launch(initial) }
        catch (_: android.content.ActivityNotFoundException) { error = "No folder picker is available on this device." }
    }
    AppSurface(modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(4.dp)) {
        SettingsToggleRow("Internal downloader", if (state.internalDownloads) "Save release files to your selected folder."
            else "Open release links in your external browser.", Icons.Outlined.Download, state.internalDownloads) { enabled ->
            if (enabled) chooseFolder() else store.useBrowser()
        }
        val motion = MaterialTheme.motionScheme
        AnimatedVisibility(state.internalDownloads,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
            exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec())) {
            Column {
                SettingsNavigationRow("Download folder", folderLabel.orEmpty().ifBlank { "Selected folder" }, Icons.Outlined.Folder, ::chooseFolder)
                SettingsToggleRow("Extract ZIP files", "Save extracted files directly into your download folder.",
                    Icons.Outlined.FolderZip, state.extractZips, store::extractZips)
            }
        }
        SettingsNavigationRow("Release downloads", "View progress, cancel transfers, or open saved files.",
            Icons.Outlined.Downloading) { transfers = true }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }) }
    } }
    if (transfers) ReleaseTransfersDialog { transfers = false }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReleaseTransfersDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { WorkManager.getInstance(context) }
    val work by remember(manager) { manager.getWorkInfosByTagFlow(ReleaseDownloads.TAG) }.collectAsStateWithLifecycle(null)
    val ordered = remember(work) { work.orEmpty().sortedWith(compareBy<WorkInfo> { it.state.isFinished }
        .thenByDescending { item -> item.tags.firstOrNull { it.startsWith("time:") } }) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.padding(vertical = 16.dp), shape = MaterialTheme.shapes.extraLarge,
        title = { AppHeading("Release downloads", HeadingLevel.Page, style = MaterialTheme.typography.headlineSmall) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (work == null) LoadingState("Loading downloads…", Modifier.fillMaxWidth())
            else if (ordered.isEmpty()) Text("No recent downloads.")
            val motion = MaterialTheme.motionScheme
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ordered, key = { it.id }) { item ->
                    ReleaseTransferCard(item, Modifier.animateItem(motion.fastEffectsSpec(), motion.defaultSpatialSpec(),
                        motion.fastEffectsSpec()), onCancel = { manager.cancelWorkById(item.id) }) { file ->
                        try {
                            context.startActivity(savedReleaseIntent(context, Uri.parse(file)))
                        } catch (_: Exception) { error = "Open the saved file from your download folder." }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        }
    }, confirmButton = { AppTextButton("Close", onDismiss) })
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReleaseTransferCard(item: WorkInfo, modifier: Modifier, onCancel: () -> Unit, onOpen: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val title = item.tags.firstOrNull { it.startsWith("title:") }?.removePrefix("title:").orEmpty()
    val status = when (item.state) {
        WorkInfo.State.CANCELLED -> "Cancelled"
        WorkInfo.State.RUNNING -> item.progress.getString("status") ?: "Starting"
        WorkInfo.State.FAILED -> item.outputData.getString("status") ?: "Download failed"
        WorkInfo.State.SUCCEEDED -> item.outputData.getString("status") ?: "Saved"
        else -> "Queued"
    }
    val (icon, tint) = when (item.state) {
        WorkInfo.State.SUCCEEDED -> Icons.Outlined.CheckCircle to LocalAppTheme.current.success
        WorkInfo.State.FAILED -> Icons.Outlined.ErrorOutline to colors.error
        WorkInfo.State.CANCELLED -> Icons.Outlined.Cancel to colors.onSurfaceVariant
        WorkInfo.State.RUNNING -> Icons.Outlined.Download to colors.primary
        else -> Icons.Outlined.Schedule to colors.onSurfaceVariant
    }
    val percent = item.progress.getInt("percent", -1)
    val fileName = item.outputData.getString("name") ?: item.progress.getString("name")
    Surface(modifier, shape = MaterialTheme.shapes.large, color = colors.surfaceContainerHighest) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, Modifier.size(20.dp), tint = tint)
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (percent >= 0 && item.state == WorkInfo.State.RUNNING)
                    Text("$percent%", style = MaterialTheme.typography.labelLarge)
            }
            if (!fileName.isNullOrBlank() && fileName != title)
                Text(fileName, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(status, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = if (item.state == WorkInfo.State.FAILED) colors.error else colors.onSurfaceVariant)
            if (item.state == WorkInfo.State.RUNNING && status != "Queued") {
                if (percent >= 0) {
                    val fraction by animateFloatAsState(percent / 100f, MaterialTheme.motionScheme.fastEffectsSpec(), label = "File progress")
                    if (LocalReducedMotion.current) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    else LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                } else if (!LocalReducedMotion.current) LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (!item.state.isFinished) AppTextButton("Cancel download", onCancel, Modifier.align(Alignment.End)
                .semantics { contentDescription = "Cancel download of " + (fileName ?: title) })
            item.outputData.getString("file")?.let { file ->
                AppTextButton(if (item.outputData.getBoolean("multiple", false)) "Open folder" else "Open file",
                    { onOpen(file) }, Modifier.align(Alignment.End))
            }
        }
    }
}
