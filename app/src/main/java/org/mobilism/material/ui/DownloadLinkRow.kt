package org.mobilism.material.ui

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import org.mobilism.material.R
import org.mobilism.material.data.DebridState
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.AppTextButton
import org.mobilism.material.designsystem.ExpressiveButton
import org.mobilism.material.designsystem.LocalAppTheme
import org.mobilism.material.designsystem.LocalReducedMotion
import org.mobilism.material.downloads.ReleaseFiles

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadLinkRow(
    link: DownloadLink, debrid: DebridState, onOpened: () -> Unit,
    resolveLink: suspend (String) -> String = { throw IllegalStateException() },
    openLink: ((String) -> Boolean)? = null,
    requests: DownloadRequestTracker = remember { DownloadRequestTracker() },
    downloadFile: (suspend (String, suspend () -> String) -> Unit)? = null,
    onCopied: suspend () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val providers = debrid.supporting(link.url).map { it.provider.label }
    val name = debrid.hostName(link.url)
    val hostWeight = if (LocalForumFormatting.current.cleanUpReleaseText) FontWeight.Bold else null
    val supported = providers.isNotEmpty()
    val fileName = remember(link.url) { ReleaseFiles.directName(link.url) }
    val internal = downloadFile != null && supported
    val pending by requests.pending.collectAsStateWithLifecycle()
    val resolving = link.url in pending
    var error by remember(link.url) { mutableStateOf<String?>(null) }
    fun open(url: String): Boolean {
        val opened = openLink?.invoke(url) ?: openForumBrowser(context, url)
        if (opened) onOpened() else error = resources.getString(R.string.download_browser_missing)
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
                                        downloadFile(link.url) { resolveLink(link.url) }
                                        onOpened()
                                    } else {
                                        val resolved = resolveLink(link.url)
                                        ensureActive()
                                        open(resolved)
                                    }
                                }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: Exception) {
                                    open(link.url)
                                }
                            }.invokeOnCompletion { requests.finish(link.url) }
                        }
                    }
                },
                modifier = Modifier.weight(1f).testTag("download:${link.url}").semantics {
                    stateDescription = if (resolving) resources.getString(R.string.download_resolving) else if (internal) resources.getString(R.string.download_to_selected)
                        else if (supported) resources.getString(R.string.download_through_providers, providers.joinToString()) else resources.getString(R.string.release_open_browser)
                },
                enabled = !resolving,
                colors = colors,
                minHeight = ButtonDefaults.MinHeight,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(Modifier.padding(horizontal = if (supported || resolving) 24.dp else 0.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        val label = when {
                            resolving -> resources.getString(R.string.download_resolving_host, name)
                            internal && fileName != null -> fileName
                            else -> name
                        }
                        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (label == name) hostWeight else null,
                            maxLines = if (internal) 2 else 1,
                            overflow = TextOverflow.Ellipsis)
                        if (!resolving && internal && fileName != null)
                            Text(name, style = MaterialTheme.typography.labelSmall, fontWeight = hostWeight,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (resolving && !LocalReducedMotion.current) LoadingIndicator(Modifier.align(Alignment.CenterStart).size(18.dp))
                    else if (!resolving && supported) Icon(Icons.Outlined.Check, null,
                        Modifier.align(Alignment.CenterStart).size(18.dp).testTag("premium-download-check"))
                }
            }
            val linkLabel = fileName ?: name
            AppIconButton(Icons.Outlined.ContentCopy, resources.getString(R.string.download_copy_host_link, linkLabel), {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(resources.getString(R.string.download_clip_label), link.url)))
                    onCopied()
                }
            })
        }
    }
    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text(resources.getString(R.string.download_unavailable_title)) },
            text = { Text(message) },
            confirmButton = {
                AppTextButton(resources.getString(R.string.download_open_original), { if (open(link.url)) error = null })
            },
            dismissButton = { AppTextButton(resources.getString(R.string.download_cancel_action), { error = null }) },
            shape = MaterialTheme.shapes.extraLarge,
        )
    }
}
