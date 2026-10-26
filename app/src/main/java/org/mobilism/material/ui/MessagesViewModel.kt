package org.mobilism.material.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mobilism.material.data.ApiFailure
import org.mobilism.material.data.FailureKind
import org.mobilism.material.data.MessageFolder
import org.mobilism.material.data.MessagesRepository
import org.mobilism.material.data.PrivateMessage
import org.mobilism.material.data.PrivateMessages

data class MailboxState(
    val items: List<PrivateMessage> = emptyList(), val page: Int = 0, val busy: Boolean = false,
    val canLoadMore: Boolean = true, val error: ScreenError? = null, val needsSignIn: Boolean = false,
    val refreshFailed: Boolean = false,
)

data class MessagesState(
    val folder: MessageFolder = MessageFolder.Inbox,
    val mailboxes: Map<MessageFolder, MailboxState> = MessageFolder.entries.associateWith { MailboxState() },
    val conversationId: Long? = null, val messageId: Long? = null,
    val conversation: List<PrivateMessage> = emptyList(), val loading: Boolean = false,
    val error: ScreenError? = null, val marking: Set<Long> = emptySet(), val readError: ScreenError? = null,
    val replying: Boolean = false, val draft: String = "", val sending: Boolean = false,
    val sendError: String? = null, val notice: String? = null,
) {
    val mailbox: MailboxState get() = mailboxes.getValue(folder)
    val replyTarget: PrivateMessage? get() = conversation.firstOrNull()?.let { it.copy(messageId = messageId ?: it.id) }
}

class MessagesViewModel(private val repository: MessagesRepository, private val username: String,
    private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(MessagesState(
        folder = MessageFolder.entries.firstOrNull { it.name == savedState.get<String>("message-folder") } ?: MessageFolder.Inbox,
        conversationId = savedState["conversation-id"], messageId = savedState["message-id"],
    ))
    val state = mutableState.asStateFlow()
    private val mailboxJobs = mutableMapOf<MessageFolder, Job>()
    private val readIds = mutableSetOf<Long>()
    private var conversationJob: Job? = null

    init {
        loadMailbox()
        if (state.value.conversationId != null) {
            restoreDraft()
            loadConversation()
        }
    }

    fun selectFolder(folder: MessageFolder) {
        savedState["message-folder"] = folder.name
        mutableState.update { it.copy(folder = folder) }
        if (state.value.mailbox.page == 0) loadMailbox()
    }

    fun loadMailbox(refresh: Boolean = false, folder: MessageFolder = state.value.folder) {
        val snapshot = state.value.mailboxes.getValue(folder)
        if (!refresh && (mailboxJobs[folder]?.isActive == true || !snapshot.canLoadMore)) return
        mailboxJobs[folder]?.cancel()
        updateMailbox(folder) { it.copy(busy = true, error = null, needsSignIn = false) }
        mailboxJobs[folder] = viewModelScope.launch {
            try {
                val page = if (refresh) 1 else snapshot.page + 1
                val result = repository.messages(folder, page)
                ensureActive()
                val previous = if (refresh) emptyList() else snapshot.items
                val items = (previous + result.items).distinctBy { it.messageId }.map {
                    if (it.messageId in readIds) it.copy(read = true) else it
                }
                updateMailbox(folder) { MailboxState(items, page, canLoadMore = result.canLoadMore &&
                    (refresh || items.size > previous.size)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                updateMailbox(folder) { it.copy(busy = false, error = failure.forScreen(), refreshFailed = refresh,
                    needsSignIn = failure is ApiFailure && failure.kind in setOf(FailureKind.Credentials, FailureKind.Session)) }
            }
        }
    }

    fun openMessage(message: PrivateMessage) {
        if (state.value.sending) return
        conversationJob?.cancel()
        savedState["conversation-id"] = message.id
        savedState["message-id"] = message.messageId
        mutableState.update { it.copy(conversationId = message.id, messageId = message.messageId,
            conversation = emptyList(), error = null, readError = null, replying = false, notice = null) }
        restoreDraft()
        loadConversation()
    }

    fun closeConversation() {
        if (state.value.sending) return
        conversationJob?.cancel()
        savedState.remove<Long>("conversation-id")
        savedState.remove<Long>("message-id")
        mutableState.update { it.copy(conversationId = null, messageId = null, conversation = emptyList(),
            loading = false, error = null, readError = null, replying = false, draft = "", sendError = null) }
    }

    fun loadConversation() {
        val id = state.value.conversationId ?: return
        if (state.value.sending) return
        conversationJob?.cancel()
        mutableState.update { it.copy(loading = true, error = null) }
        conversationJob = viewModelScope.launch {
            try {
                val messages = repository.conversation(id)
                ensureActive()
                if (messages.isEmpty()) throw ApiFailure(FailureKind.InvalidResponse, "This conversation is no longer available.")
                mutableState.update { it.copy(loading = false, conversation = messages.map { message ->
                    if (message.id in readIds) message.copy(read = true) else message
                }) }
                messages.filter { !it.read && it.to.equals(username, ignoreCase = true) }.forEach { markRead(it.id) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutableState.update { it.copy(loading = false, error = failure.forScreen()) } }
        }
    }

    fun markRead(id: Long) {
        if (id in state.value.marking || id in readIds) return
        mutableState.update { it.copy(marking = it.marking + id, readError = null) }
        viewModelScope.launch {
            try {
                repository.markMessageRead(id)
                ensureActive()
                readIds += id
                mutableState.update { state -> state.copy(
                    mailboxes = state.mailboxes.mapValues { (_, box) -> box.copy(items = box.items.map {
                        if (it.messageId == id) it.copy(read = true) else it
                    }) },
                    conversation = state.conversation.map { if (it.id == id) it.copy(read = true) else it },
                ) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutableState.update { it.copy(readError = failure.forScreen()) } }
            finally { mutableState.update { it.copy(marking = it.marking - id) } }
        }
    }

    fun openReply() {
        if (state.value.replyTarget?.recipient(username).isNullOrBlank() || state.value.loading) return
        mutableState.update { it.copy(replying = true) }
    }

    fun closeReply() { if (!state.value.sending) mutableState.update { it.copy(replying = false) } }

    fun editReply(text: String) {
        val id = state.value.messageId ?: return
        if (state.value.sending || text.length > PrivateMessages.MAX_LENGTH) return
        savedState["message-draft-$id"] = text
        mutableState.update { it.copy(draft = text) }
    }

    fun sendReply() {
        val snapshot = state.value
        val target = snapshot.replyTarget ?: return
        val text = snapshot.draft.trim()
        if (snapshot.sending || snapshot.loading || text.isBlank() || text.length > PrivateMessages.MAX_LENGTH) return
        savedState["message-pending-${target.messageId}"] = true
        mutableState.update { it.copy(sending = true, sendError = null) }
        viewModelScope.launch {
            try {
                repository.replyToMessage(target, text)
                savedState.remove<String>("message-draft-${target.messageId}")
                savedState.remove<Boolean>("message-pending-${target.messageId}")
                mutableState.update { it.copy(sending = false, replying = false, draft = "", notice = "Reply sent") }
                loadConversation()
                MessageFolder.entries.forEach { loadMailbox(refresh = true, folder = it) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                val rejected = failure is ApiFailure && (failure.httpStatus in 200..299 || failure.httpStatus in 400..499 ||
                    failure.kind in setOf(FailureKind.Credentials, FailureKind.Session, FailureKind.Permission, FailureKind.Dns))
                if (rejected) savedState.remove<Boolean>("message-pending-${target.messageId}")
                mutableState.update { it.copy(sendError = if (rejected) failure.forScreen().message else UNCONFIRMED_REPLY) }
            } finally { mutableState.update { it.copy(sending = false) } }
        }
    }

    fun noticeShown() { mutableState.update { it.copy(notice = null) } }

    private fun restoreDraft() {
        val id = state.value.messageId ?: return
        mutableState.update { it.copy(draft = savedState["message-draft-$id"] ?: "",
            sendError = if (savedState.get<Boolean>("message-pending-$id") == true) UNCONFIRMED_REPLY else null) }
    }

    private fun updateMailbox(folder: MessageFolder, change: (MailboxState) -> MailboxState) {
        mutableState.update { it.copy(mailboxes = it.mailboxes + (folder to change(it.mailboxes.getValue(folder)))) }
    }

    private companion object {
        const val UNCONFIRMED_REPLY = "Sending could not be confirmed. Your draft is kept. Check the conversation and Sent before trying again to avoid a duplicate."
    }
}
