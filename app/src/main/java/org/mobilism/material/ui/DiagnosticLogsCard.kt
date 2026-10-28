package org.mobilism.material.ui

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.mobilism.material.designsystem.ExpressiveCard
import org.mobilism.material.settings.DiagnosticLogExporter
import org.mobilism.material.settings.DiagnosticLogStore

@Composable
internal fun DiagnosticLogsCard(logs: DiagnosticLogStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    ExpressiveCard(onClick = {
        if (!exporting) scope.launch {
            exporting = true
            message = null
            try {
                val uri = DiagnosticLogExporter.export(context, logs)
                if (uri == null) message = "No logs yet. Reproduce the issue and try again."
                else {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newUri(context.contentResolver, "Diagnostic logs", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(share, "Export logs"))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = "Logs could not be exported. Try again."
            } finally { exporting = false }
        }
    }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppHeading("Diagnostic logs", HeadingLevel.Section, style = MaterialTheme.typography.titleLarge)
            SettingsSubtitle(if (exporting) "Preparing logs…" else "Tap to export logs for troubleshooting")
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
