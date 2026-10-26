package org.mobilism.material.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mobilism.material.data.MessageFolder
import org.mobilism.material.data.PrivateMessage
import org.mobilism.material.data.PrivateMessages
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.AppSurface
import org.mobilism.material.designsystem.AppTextButton
import org.mobilism.material.designsystem.ExpressiveButton
import org.mobilism.material.designsystem.ExpressiveCard
import org.mobilism.material.designsystem.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(state: MessagesState, username: String, onFolder: (MessageFolder) -> Unit,
    onRefresh: () -> Unit, onMore: () -> Unit, onOpen: (PrivateMessage) -> Unit, onRead: (Long) -> Unit,
    onBack: () -> Unit, onSignIn: () -> Unit, onRefreshConversation: () -> Unit,
    onReply: () -> Unit, onEdit: (String) -> Unit, onSend: () -> Unit, onCloseReply: () -> Unit,
    onNoticeShown: () -> Unit, onRelease: (Long) -> Unit = {}) {
    val snackbar = remember { SnackbarHostState() }
    val inboxScroll = rememberLazyListState()
    val sentScroll = rememberLazyListState()
    val detail = state.conversationId != null
    BackHandler(detail) { if (!state.sending) onBack() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); onNoticeShown() } }
    if (state.replying) MessageReplyComposer(state, username, onEdit, onSend, onCloseReply,
        onCheck = { onCloseReply(); onRefreshConversation() })
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(
            title = { AppHeading(if (detail) "Conversation" else "Messages", HeadingLevel.Page,
                style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack, enabled = !state.sending) },
            actions = { AppIconButton(Icons.Outlined.Refresh, "Refresh messages",
                if (detail) onRefreshConversation else onRefresh,
                enabled = !state.sending && !(if (detail) state.loading else state.mailbox.busy)) },
        ) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!detail) PrimaryTabRow(selectedTabIndex = state.folder.ordinal) {
                MessageFolder.entries.forEach { folder ->
                    Tab(selected = state.folder == folder, onClick = { onFolder(folder) }, text = { Text(folder.label) })
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                if (detail) key(state.conversationId) {
                    LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxSize().testTag("message-conversation"),
                        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.conversation.firstOrNull()?.let { item -> item("subject") {
                            AppHeading(item.subject.ifBlank { "No subject" }, HeadingLevel.Section,
                                style = MaterialTheme.typography.headlineSmall)
                        } }
                        if (state.loading) item { LoadingState("Loading conversation…", Modifier.fillMaxWidth()) }
                        state.error?.let { item { ErrorPanel(it, onRetry = onRefreshConversation) } }
                        state.readError?.let { item { ErrorPanel(it) } }
                        items(state.conversation, key = { it.id }) { message ->
                            AppSurface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                                color = if (message.from.equals(username, true)) MaterialTheme.colorScheme.surfaceContainerHigh
                                else MaterialTheme.colorScheme.surfaceContainer) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("${message.from} to ${message.to}", style = MaterialTheme.typography.titleSmall)
                                    ReleasePostDate(message.publishedAt)
                                    ReleaseBody(message.contentHtml, "https://forum.mobilism.org/", onRelease)
                                    if (!message.read && message.to.equals(username, true)) AppTextButton("Mark as read",
                                        { onRead(message.id) }, enabled = message.id !in state.marking)
                                }
                            }
                        }
                        if (state.replyTarget != null) item("reply") {
                            ExpressiveButton(onReply, Modifier.fillMaxWidth(), enabled = !state.loading && !state.sending) {
                                Text(if (state.draft.isBlank()) "Reply" else "Continue reply")
                            }
                        }
                    }
                } else {
                    val mailbox = state.mailbox
                    LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxSize().testTag("message-mailbox"),
                        state = if (state.folder == MessageFolder.Inbox) inboxScroll else sentScroll,
                        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.readError?.let { item { ErrorPanel(it) } }
                        items(mailbox.items, key = { it.messageId }) { message ->
                            ExpressiveCard({ onOpen(message) }, Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(if (state.folder == MessageFolder.Inbox) message.from else "To ${message.to}",
                                            Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                                        if (state.folder == MessageFolder.Inbox && !message.read) Text("Unread",
                                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                    }
                                    Text(message.subject.ifBlank { "No subject" }, style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (!message.read && state.folder == MessageFolder.Inbox) FontWeight.Bold else FontWeight.Normal)
                                    if (message.preview.isNotBlank()) Text(message.preview, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    ReleasePostDate(message.publishedAt)
                                    if (state.folder == MessageFolder.Inbox && !message.read) AppTextButton("Mark as read",
                                        { onRead(message.messageId) }, enabled = message.messageId !in state.marking)
                                }
                            }
                        }
                        if (mailbox.busy) item { LoadingState("Loading messages…", Modifier.fillMaxWidth()) }
                        mailbox.error?.let { item {
                            ErrorPanel(it, onRetry = if (mailbox.page == 0 || mailbox.refreshFailed) onRefresh else onMore)
                            if (mailbox.needsSignIn) AppTextButton("Sign in again", onSignIn)
                        } }
                        if (!mailbox.busy && mailbox.error == null) {
                            if (mailbox.items.isEmpty()) item { Text(if (state.folder == MessageFolder.Inbox) "Your inbox is empty." else "No sent messages.") }
                            else if (mailbox.canLoadMore) item { AppTextButton("Load more messages", onMore) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageReplyComposer(state: MessagesState, username: String, onEdit: (String) -> Unit,
    onSend: () -> Unit, onClose: () -> Unit, onCheck: () -> Unit) {
    ModalBottomSheet(onDismissRequest = { if (!state.sending) onClose() },
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        sheetGesturesEnabled = !state.sending,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !state.sending,
            shouldDismissOnClickOutside = !state.sending)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppHeading("Reply to ${state.replyTarget?.recipient(username).orEmpty()}", HeadingLevel.Page,
                style = MaterialTheme.typography.headlineSmall)
            Text(state.replyTarget?.subject.orEmpty(), style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(state.draft, onEdit, Modifier.fillMaxWidth().testTag("message-draft"),
                readOnly = state.sending, label = { Text("Message") }, minLines = 4, maxLines = 8,
                shape = MaterialTheme.shapes.large, supportingText = { Text("${state.draft.length} / ${PrivateMessages.MAX_LENGTH}") })
            state.sendError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                AppTextButton("Check conversation", onCheck, enabled = !state.sending)
            }
            if (state.sending) LoadingState("Sending reply…", Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AppTextButton("Close", onClose, enabled = !state.sending)
                ExpressiveButton(onSend, Modifier.weight(1f).testTag("send-message"),
                    enabled = !state.sending && state.draft.isNotBlank() && state.draft.length <= PrivateMessages.MAX_LENGTH) { Text("Send reply") }
            }
            Text("Your draft stays here when you close this sheet.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
