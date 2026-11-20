package org.mobilism.material.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mobilism.material.downloads.ReleaseDownloads
import org.mobilism.material.downloads.ReleaseFolder
import org.mobilism.material.settings.DownloadPreferences
import org.mobilism.material.settings.DownloadPreferencesStore
import org.mobilism.material.designsystem.AppSurface
import org.mobilism.material.designsystem.AppTextButton

@Composable
internal fun rememberDownloadNotificationRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            val history = context.getSharedPreferences("release-download-notifications", Context.MODE_PRIVATE)
            if (!history.getBoolean("requested", false)) {
                history.edit().putBoolean("requested", true).apply()
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReleaseTransferSettings(state: DownloadPreferences, store: DownloadPreferencesStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val requestDownloadNotifications = rememberDownloadNotificationRequest()
    var error by remember { mutableStateOf<String?>(null) }
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
                if (!state.internalDownloads) requestDownloadNotifications()
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
                val notifications = remember(context) { context.getSystemService(NotificationManager::class.java) }
                fun blocked() = !notifications.areNotificationsEnabled() ||
                    notifications.getNotificationChannel(ReleaseDownloads.TAG)?.importance == NotificationManager.IMPORTANCE_NONE
                var notificationsBlocked by remember { mutableStateOf(blocked()) }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationsBlocked = blocked() }
                if (notificationsBlocked) AppTextButton("Allow download notifications", {
                    val settings = if (notifications.areNotificationsEnabled()) Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, ReleaseDownloads.TAG) else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    context.startActivity(settings.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                })
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }) }
    } }
}
