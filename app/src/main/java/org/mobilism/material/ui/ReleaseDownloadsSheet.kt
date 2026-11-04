package org.mobilism.material.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mobilism.material.data.DebridState
import org.mobilism.material.designsystem.AppIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReleaseDownloadsSheet(releaseId: Long, title: String, groups: List<DownloadGroup>, notesHtml: String,
    baseUrl: String, debrid: DebridState, requests: DownloadRequestTracker,
    onResolve: suspend (String) -> String, onOpened: () -> Unit,
    onRelease: (Long) -> Unit, onDismiss: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val sheetState = rememberBottomSheetState(SheetValue.Hidden)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().testTag("release-downloads-sheet")) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppHeading("Downloads", HeadingLevel.Page, style = MaterialTheme.typography.titleLarge)
                    Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                AppIconButton(Icons.Outlined.Close, "Close downloads", {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) onDismiss()
                    }
                })
            }
            Box(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                LazyColumn(Modifier.fillMaxWidth().testTag("download-sheet-list"),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (notesHtml.isNotBlank()) item("notes") { ReleaseBody(notesHtml, baseUrl, onRelease) }
                    items(groups, key = { it.id }) { group ->
                        ReleaseDownloadGroup(releaseId, group, baseUrl, debrid, requests, onResolve, onOpened,
                            onCopied = { snackbar.showSnackbar("Link copied") }, onRelease = onRelease)
                    }
                    if (groups.isEmpty()) item("empty") {
                        Text("No download links available.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
internal fun ReleaseDownloadGroup(releaseId: Long, group: DownloadGroup, baseUrl: String, debrid: DebridState,
    requests: DownloadRequestTracker, onResolve: suspend (String) -> String, onOpened: () -> Unit,
    onCopied: suspend () -> Unit, onRelease: (Long) -> Unit) {
    var expanded by rememberSaveable(releaseId, group.id, group.collapsed) { mutableStateOf(!group.collapsed) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .clickable(role = Role.Button, onClick = { expanded = !expanded }),
            verticalAlignment = Alignment.CenterVertically) {
            AppHeading(group.title, HeadingLevel.Page, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null,
                Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (group.notesHtml.isNotBlank()) ReleaseBody(group.notesHtml, baseUrl, onRelease)
                group.links.forEach { link ->
                    DownloadLinkRow(link, debrid, onOpened, resolveLink = onResolve, requests = requests, onCopied = onCopied)
                }
            }
        }
    }
}
