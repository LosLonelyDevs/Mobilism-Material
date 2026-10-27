package org.mobilism.material.ui

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import org.mobilism.material.data.DebridException
import org.mobilism.material.data.DebridState
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.ExpressiveButton
import org.mobilism.material.designsystem.LocalAppTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadLinkRow(
    link: DownloadLink, debrid: DebridState, onOpened: () -> Unit,
    resolveLink: suspend (String) -> String = { throw DebridException("Debrid is not available. Try again.") },
    openLink: ((String) -> Boolean)? = null,
    onCopied: suspend () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val providers = debrid.supporting(link.url).map { it.provider.label }
    val name = debrid.hostName(link.url)
    val supported = providers.isNotEmpty()
    var resolving by remember(link.url) { mutableStateOf(false) }
    var error by remember(link.url) { mutableStateOf<String?>(null) }
    fun open(url: String): Boolean {
        val opened = openLink?.invoke(url) ?: openWeb(context, url)
        if (opened) onOpened() else error = "No browser is available to open this download."
        return opened
    }
    val theme = LocalAppTheme.current
    val colors = if (supported) ButtonDefaults.buttonColors(
        containerColor = theme.success, contentColor = theme.onSuccess,
    ) else ButtonDefaults.buttonColors()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpressiveButton(
                onClick = {
                    if (!resolving) {
                        if (!supported) open(link.url) else {
                            resolving = true
                            scope.launch {
                                try { open(resolveLink(link.url)) }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: DebridException) { error = failure.message }
                                catch (_: Exception) { error = "The download could not be resolved. Please try again." }
                                finally { resolving = false }
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f).testTag("download:${link.url}").semantics {
                    stateDescription = if (resolving) "Resolving download" else if (supported) "Download through " + providers.joinToString() else "Open in browser"
                },
                enabled = !resolving,
                colors = colors,
                compact = true,
            ) {
                if (resolving) LoadingIndicator(Modifier.padding(end = 8.dp).size(24.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (resolving) "Resolving $name" else name, style = MaterialTheme.typography.labelLarge)
                    if (providers.isNotEmpty()) Text(providers.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                }
            }
            AppIconButton(Icons.Outlined.ContentCopy, "Copy $name link", {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Download link", link.url)))
                    onCopied()
                }
            })
        }
    }
    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Download unavailable") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { if (open(link.url)) error = null }) { Text("Open original host") }
            },
            dismissButton = { TextButton(onClick = { error = null }) { Text("Cancel") } },
            shape = MaterialTheme.shapes.extraLarge,
        )
    }
}
