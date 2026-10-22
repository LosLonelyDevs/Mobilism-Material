package org.mobilism.material.ui

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mobilism.material.data.DebridState
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.ExpressiveButton
import org.mobilism.material.designsystem.LocalAppTheme

@Composable
fun DownloadLinkRow(link: DownloadLink, debrid: DebridState, onOpened: () -> Unit, onCopied: suspend () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val providers = debrid.supporting(link.url).map { it.provider.label }
    val name = debrid.hostName(link.url)
    val supported = providers.isNotEmpty()
    val theme = LocalAppTheme.current
    val colors = if (supported) ButtonDefaults.buttonColors(
        containerColor = theme.success, contentColor = theme.onSuccess,
    ) else ButtonDefaults.filledTonalButtonColors()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpressiveButton(
                onClick = { if (openWeb(context, link.url)) onOpened() },
                modifier = Modifier.weight(1f).semantics {
                    stateDescription = if (supported) "Supported by " + providers.joinToString() else "Open in browser"
                },
                colors = colors,
                compact = true,
            ) {
                if (supported) Icon(Icons.Outlined.CheckCircle, null, Modifier.padding(end = 8.dp))
                Text(name, style = MaterialTheme.typography.labelLarge)
            }
            AppIconButton(Icons.Outlined.ContentCopy, "Copy $name link", {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Download link", link.url)))
                    onCopied()
                }
            })
        }
    }
}
