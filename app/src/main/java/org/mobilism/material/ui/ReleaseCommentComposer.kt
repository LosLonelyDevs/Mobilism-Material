package org.mobilism.material.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mobilism.material.data.ReleaseComments
import org.mobilism.material.designsystem.AppTextButton
import org.mobilism.material.designsystem.ExpressiveButton
import org.mobilism.material.designsystem.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReleaseCommentComposer(
    state: CommentComposerState, username: String, releaseTitle: String,
    onEdit: (String) -> Unit, onPost: () -> Unit, onDismiss: () -> Unit, onCheckComments: () -> Unit,
) {
    ModalBottomSheet(
        modifier = Modifier.testTag("comment-composer"),
        onDismissRequest = { if (!state.posting) onDismiss() },
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        sheetGesturesEnabled = !state.posting,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !state.posting,
            shouldDismissOnClickOutside = !state.posting),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
            .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppHeading("Write a comment", HeadingLevel.Page, style = MaterialTheme.typography.headlineSmall)
                Text(releaseTitle, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Posting publicly as $username", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = state.draft, onValueChange = onEdit, readOnly = state.posting,
                modifier = Modifier.fillMaxWidth().testTag("comment-draft"),
                label = { Text("Comment") }, placeholder = { Text("Join the conversation") },
                minLines = 4, maxLines = 8, shape = MaterialTheme.shapes.large,
                supportingText = { Text("${state.draft.length} / ${ReleaseComments.MAX_LENGTH}") },
            )
            state.error?.let {
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    AppTextButton("Check comments", onCheckComments, enabled = !state.posting)
                }
            }
            if (state.posting) LoadingState("Posting comment…", Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                AppTextButton("Close", onDismiss, enabled = !state.posting)
                ExpressiveButton(onPost, Modifier.weight(1f).testTag("post-comment"),
                    enabled = !state.posting && state.draft.isNotBlank() && state.draft.length <= ReleaseComments.MAX_LENGTH) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Post comment", Modifier.padding(start = 8.dp))
                }
            }
            Text("Your draft stays here when you close this sheet.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
