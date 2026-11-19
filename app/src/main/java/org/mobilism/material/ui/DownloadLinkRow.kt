package org.mobilism.material.ui

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import org.mobilism.material.data.DebridException
import org.mobilism.material.data.DebridState
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.AppTextButton
import org.mobilism.material.designsystem.ExpressiveButton
import org.mobilism.material.designsystem.LocalAppTheme
import org.mobilism.material.designsystem.LocalReducedMotion
import org.mobilism.material.downloads.DownloadProblem
import org.mobilism.material.downloads.ReleaseFiles

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadLinkRow(
    link: DownloadLink, debrid: DebridState, onOpened: () -> Unit,
    resolveLink: suspend (String) -> String = { throw DebridException("Debrid is not available. Try again.") },
    openLink: ((String) -> Boolean)? = null,
    requests: DownloadRequestTracker = remember { DownloadRequestTracker() },
    downloadFile: (suspend (String, suspend () -> String) -> Unit)? = null,
    onCopied: suspend () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val providers = debrid.supporting(link.url).map { it.provider.label }
    val name = debrid.hostName(link.url)
    val supported = providers.isNotEmpty()
    val fileName = remember(link.url) { ReleaseFiles.directName(link.url) }
    val internal = downloadFile != null && (supported || fileName != null)
    val pending by requests.pending.collectAsStateWithLifecycle()
    val resolving = link.url in pending
    var error by remember(link.url) { mutableStateOf<String?>(null) }
    fun open(url: String): Boolean {
        val opened = openLink?.invoke(url) ?: openForumBrowser(context, url)
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
                        if (!supported && !internal) open(link.url) else if (requests.begin(link.url)) {
                            scope.launch {
                                try {
                                    if (internal) {
                                        downloadFile(link.url) { if (supported) resolveLink(link.url) else link.url }
                                        onOpened()
                                    } else {
                                        val resolved = resolveLink(link.url)
                                        ensureActive()
                                        open(resolved)
                                    }
                                }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: DebridException) { error = failure.message }
                                catch (failure: DownloadProblem) { error = failure.message }
                                catch (_: Exception) { error = "The download could not be resolved. Please try again." }
                            }.invokeOnCompletion { requests.finish(link.url) }
                        }
                    }
                },
                modifier = Modifier.weight(1f).testTag("download:${link.url}").semantics {
                    stateDescription = if (resolving) "Resolving download" else if (internal) "Download to selected folder"
                        else if (supported) "Download through " + providers.joinToString() else "Open in browser"
                },
                enabled = !resolving,
                colors = colors,
                compact = true,
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(Modifier.padding(horizontal = if (supported || resolving) 24.dp else 0.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        val label = when {
                            resolving -> if (supported) "Resolving $name" else "Queuing download"
                            internal && fileName != null -> fileName
                            else -> name
                        }
                        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = if (internal) 2 else 1,
                            overflow = TextOverflow.Ellipsis)
                        if (!resolving && internal && fileName != null)
                            Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (resolving && !LocalReducedMotion.current) LoadingIndicator(Modifier.align(Alignment.CenterStart).size(20.dp))
                    else if (!resolving && supported) Icon(Icons.Outlined.Check, null,
                        Modifier.align(Alignment.CenterStart).size(20.dp).testTag("premium-download-check"))
                }
            }
            val linkLabel = fileName ?: name
            if (internal) AppIconButton(Icons.Outlined.OpenInBrowser, "Open $linkLabel in browser", { open(link.url) })
            AppIconButton(Icons.Outlined.ContentCopy, "Copy $linkLabel link", {
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
                AppTextButton("Open original host", { if (open(link.url)) error = null })
            },
            dismissButton = { AppTextButton("Cancel", { error = null }) },
            shape = MaterialTheme.shapes.extraLarge,
        )
    }
}
