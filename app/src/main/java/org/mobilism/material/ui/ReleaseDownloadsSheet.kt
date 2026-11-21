package org.mobilism.material.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.mobilism.material.data.DebridState
import org.mobilism.material.designsystem.AppIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReleaseDownloadsSheet(releaseId: Long, title: String, groups: List<DownloadGroup>, notesHtml: String,
    baseUrl: String, debrid: DebridState, requests: DownloadRequestTracker,
    onResolve: suspend (String) -> String, onOpened: () -> Unit,
    onRelease: (Long) -> Unit, onDismiss: () -> Unit,
    downloadFile: (suspend (String, suspend () -> String) -> Unit)? = null) {
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
                    if (notesHtml.isNotBlank()) item("notes") {
                        DownloadNotes(notesHtml, baseUrl, onRelease, onBeforeHelp = onDismiss)
                    }
                    items(groups, key = { it.id }) { group ->
                        ReleaseDownloadGroup(releaseId, group, baseUrl, debrid, requests, onResolve, onOpened,
                            onCopied = { snackbar.showSnackbar("Link copied") }, onRelease = onRelease,
                            onBeforeHelp = onDismiss, downloadFile = downloadFile)
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReleaseDownloadGroup(releaseId: Long, group: DownloadGroup, baseUrl: String, debrid: DebridState,
    requests: DownloadRequestTracker, onResolve: suspend (String) -> String, onOpened: () -> Unit,
    onCopied: suspend () -> Unit, onRelease: (Long) -> Unit, onBeforeHelp: () -> Unit = {},
    downloadFile: (suspend (String, suspend () -> String) -> Unit)? = null) {
    var expanded by rememberSaveable(releaseId, group.id, group.collapsed) { mutableStateOf(!group.collapsed) }
    val motion = MaterialTheme.motionScheme
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, motion.fastSpatialSpec(), label = "Download group chevron")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .clickable(role = Role.Button, onClick = { expanded = !expanded })
            .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            val titleStyle = MaterialTheme.typography.titleSmall
            AppHeading(group.title, HeadingLevel.Page, Modifier.weight(1f), style = if (LocalForumFormatting.current.cleanUpReleaseText)
                titleStyle.copy(fontSize = (titleStyle.fontSize.value + 1).sp, fontWeight = FontWeight.SemiBold) else titleStyle)
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(24.dp).rotate(rotation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(expanded,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
            exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec())) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (group.notesHtml.isNotBlank()) DownloadNotes(group.notesHtml, baseUrl, onRelease, onBeforeHelp)
                group.links.forEach { link ->
                    DownloadLinkRow(link, debrid, onOpened, resolveLink = onResolve, requests = requests,
                        downloadFile = downloadFile, onCopied = onCopied)
                }
            }
        }
    }
}
