package org.mobilism.material.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jsoup.Jsoup
import org.mobilism.material.data.FailureKind
import org.mobilism.material.data.PrivateMessages
import org.mobilism.material.designsystem.AppTextButton
import org.mobilism.material.designsystem.ExpressiveButton
import org.mobilism.material.designsystem.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MailboxActionDialogs(state: MailboxActionState, actions: MailboxActions,
    refreshConversation: () -> Unit, signIn: () -> Unit) {
    if (!state.composing && state.deletion == null) return
    var verifying by remember { mutableStateOf(false) }
    var verified by remember(state.failure) { mutableStateOf(false) }
    if (verifying) MessageAccessVerification(onDismiss = { verifying = false },
        onContinue = { verifying = false; verified = true })
    val recoverAccess: @Composable () -> Unit = {
        when (state.failure) {
            FailureKind.Challenge -> if (!verified) AppTextButton("Verify access", { verifying = true })
            FailureKind.Credentials, FailureKind.Session -> AppTextButton("Sign in again", signIn)
            else -> Unit
        }
    }
    state.deletion?.let { message ->
        AlertDialog(onDismissRequest = actions::close, title = { Text("Delete this message?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(listOf(message.from, message.subject).filter(String::isNotBlank).joinToString(" · "),
                        style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(remember(message) { Jsoup.parse(message.contentHtml).text().ifBlank { message.preview } },
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    recoverAccess()
                    if (state.busy) LoadingState("Deleting message…")
                }
            },
            confirmButton = {
                if (state.uncertainDelete) AppTextButton("Refresh conversation", { actions.close(); refreshConversation() })
                else AppTextButton("Delete", actions::confirmDelete, enabled = !state.busy)
            },
            dismissButton = { AppTextButton("Cancel", actions::close, enabled = !state.busy) })
        return
    }
    var confirmResend by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    if (confirmResend || confirmDiscard) AlertDialog(
        onDismissRequest = { confirmResend = false; confirmDiscard = false },
        title = { Text(if (confirmResend) "Send this message again?" else "Discard this draft?") },
        text = { Text(if (confirmResend) "The recipient may already have a copy." else "Your draft will be removed.") },
        confirmButton = { AppTextButton(if (confirmResend) "Send again" else "Discard", {
            if (confirmResend) actions.send(confirmedResend = true) else actions.discard()
            confirmResend = false
            confirmDiscard = false
        }) },
        dismissButton = { AppTextButton("Cancel", { confirmResend = false; confirmDiscard = false }) })
    val draft = state.draft
    val locked = state.busy || state.uncertainSend
    ModalBottomSheet(onDismissRequest = actions::close,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        sheetGesturesEnabled = !state.busy,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !state.busy,
            shouldDismissOnClickOutside = !state.busy)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppHeading("New message", HeadingLevel.Page, style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(draft.recipient, { actions.edit(draft.copy(recipient = it)) },
                Modifier.fillMaxWidth().testTag("new-message-to"), singleLine = true, readOnly = locked,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                label = { Text("Recipient username") }, shape = MaterialTheme.shapes.large)
            OutlinedTextField(draft.subject, { actions.edit(draft.copy(subject = it)) },
                Modifier.fillMaxWidth().testTag("new-message-subject"), singleLine = true, readOnly = locked,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                label = { Text("Subject") }, shape = MaterialTheme.shapes.large)
            OutlinedTextField(draft.body, { actions.edit(draft.copy(body = it)) },
                Modifier.fillMaxWidth().testTag("new-message-body"), minLines = 3, maxLines = 8, readOnly = locked,
                label = { Text("Message") }, shape = MaterialTheme.shapes.large,
                supportingText = { Text("${draft.body.length} / ${PrivateMessages.MAX_LENGTH}") })
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.uncertainSend) Text("Delivery is unconfirmed. Check Sent before sending again.")
            recoverAccess()
            if (state.busy) LoadingState("Sending message…")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextButton("Close", actions::close, enabled = !state.busy)
                if (state.uncertainSend) ExpressiveButton(actions::viewSent, Modifier.weight(1f),
                    enabled = !state.busy) { Text("View Sent") }
                else ExpressiveButton({ actions.send() }, Modifier.weight(1f).testTag("new-message-send"),
                    enabled = draft.ready && !state.busy) { Text("Send message") }
            }
            if (state.uncertainSend) AppTextButton("Send again…", { confirmResend = true }, enabled = !state.busy)
            if (draft != NewMessageDraft()) AppTextButton("Discard draft", { confirmDiscard = true }, enabled = !state.busy)
        }
    }
}
