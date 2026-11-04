package org.mobilism.material.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mobilism.material.data.ApiFailure
import org.mobilism.material.data.FailureKind
import org.mobilism.material.data.MessageFolder
import org.mobilism.material.data.MessagePage
import org.mobilism.material.data.MessagesRepository
import org.mobilism.material.data.PrivateMessage
import org.mobilism.material.data.PrivateMessages
import org.mobilism.material.settings.MessageReadHistory

data class MailboxState(
    val items: List<PrivateMessage> = emptyList(), val page: Int = 0, val busy: Boolean = false,
    val canLoadMore: Boolean = true, val error: ScreenError? = null, val needsSignIn: Boolean = false,
    val refreshFailed: Boolean = false,
) {
    internal val threads = messageThreads(items)
}

data class MessagesState(
    val folder: MessageFolder = MessageFolder.Inbox,
    val mailboxes: Map<MessageFolder, MailboxState> = MessageFolder.entries.associateWith { MailboxState() },
    val conversationId: Long? = null, val messageId: Long? = null,
    val conversation: List<PrivateMessage> = emptyList(), val loading: Boolean = false,
    val error: ScreenError? = null, val marking: Set<Long> = emptySet(), val readError: ScreenError? = null,
    val replying: Boolean = false, val draft: String = "", val sending: Boolean = false,
    val sendError: String? = null, val notice: String? = null,
    val sendUnconfirmed: Boolean = false, val checkingReply: Boolean = false,
) {
    val mailbox: MailboxState get() = mailboxes.getValue(folder)
    val replyTarget: PrivateMessage? get() = conversation.firstOrNull()?.let { it.copy(messageId = messageId ?: it.id) }
}

class MessagesViewModel(private val repository: MessagesRepository, private val username: String,
    private val savedState: SavedStateHandle, private val now: () -> Long = System::currentTimeMillis,
    private val readHistory: MessageReadHistory = MessageReadHistory()) : ViewModel() {
    private val mutableState = MutableStateFlow(MessagesState(
        folder = MessageFolder.entries.firstOrNull { it.name == savedState.get<String>("message-folder") } ?: MessageFolder.Inbox,
        conversationId = savedState["conversation-id"], messageId = savedState["message-id"],
    ))
    val state = mutableState.asStateFlow()
    private val mailboxJobs = mutableMapOf<MessageFolder, Job>()
    private val readIds = mutableSetOf<Long>()
    private val readErrors = mutableMapOf<Long, ScreenError>()
    private var readGeneration = 0
    private var conversationJob: Job? = null
    private var deliveryJob: Job? = null
    private var deliveryGeneration = 0
    private var pendingReply: MessageReplyAttempt? = null

    init {
        loadMailbox()
        if (state.value.conversationId != null) {
            restoreDraft()
            loadConversation()
        }
    }

    fun selectFolder(folder: MessageFolder) {
        clearReadErrors()
        savedState["message-folder"] = folder.name
        mutableState.update { it.copy(folder = folder) }
        if (state.value.mailbox.page == 0) loadMailbox()
    }

    fun loadMailbox(refresh: Boolean = false, folder: MessageFolder = state.value.folder) {
        val snapshot = state.value.mailboxes.getValue(folder)
        if (!refresh && (mailboxJobs[folder]?.isActive == true || !snapshot.canLoadMore)) return
        if (refresh && folder == state.value.folder && state.value.conversationId == null) clearReadErrors()
        mailboxJobs[folder]?.cancel()
        updateMailbox(folder) { it.copy(busy = true, error = null, needsSignIn = false) }
        mailboxJobs[folder] = viewModelScope.launch {
            try {
                val page = if (refresh) 1 else snapshot.page + 1
                val result = repository.messages(folder, page)
                ensureActive()
                if (folder == MessageFolder.Inbox) readHistory.remember(result.items.filter { it.read }.map { it.messageId })
                val previous = if (refresh) emptyList() else snapshot.items
                val items = (previous + result.items).associateBy { it.messageId }.values.map {
                    if (folder == MessageFolder.Inbox && it.messageId in readHistory) it.copy(read = true) else it
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

    fun openMessage(message: PrivateMessage) = openConversation(message.id, message.messageId)

    fun openConversation(conversationId: Long, messageId: Long) {
        if (conversationId <= 0 || messageId <= 0) return
        if (state.value.sending) return
        conversationJob?.cancel()
        deliveryGeneration++
        deliveryJob?.cancel()
        savedState["conversation-id"] = conversationId
        savedState["message-id"] = messageId
        mutableState.update { it.copy(conversationId = conversationId, messageId = messageId,
            conversation = emptyList(), error = null, readError = null, replying = false, notice = null,
            checkingReply = false) }
        restoreDraft()
        loadConversation()
    }

    fun closeConversation() {
        if (state.value.sending) return
        clearReadErrors()
        conversationJob?.cancel()
        deliveryGeneration++
        deliveryJob?.cancel()
        pendingReply = null
        savedState.remove<Long>("conversation-id")
        savedState.remove<Long>("message-id")
        mutableState.update { it.copy(conversationId = null, messageId = null, conversation = emptyList(),
            loading = false, error = null, readError = null, replying = false, draft = "", sendError = null,
            sendUnconfirmed = false, checkingReply = false) }
    }

    fun loadConversation() {
        val id = state.value.conversationId ?: return
        if (state.value.sending) return
        conversationJob?.cancel()
        clearReadErrors()
        mutableState.update { it.copy(loading = true, error = null) }
        conversationJob = viewModelScope.launch {
            try {
                val messages = repository.conversation(id)
                ensureActive()
                if (messages.isEmpty()) throw ApiFailure(FailureKind.InvalidResponse, "This conversation is no longer available.")
                // Viewing a message clears its local badge even if the server read receipt fails.
                val incomingIds = messages.filter { it.to.equals(username, ignoreCase = true) }.map { it.id }
                readHistory.remember(incomingIds)
                updateMailbox(MessageFolder.Inbox) { box -> box.copy(items = box.items.map {
                    if (it.messageId in readHistory) it.copy(read = true) else it
                }) }
                messages.filter { it.read && it.to.equals(username, ignoreCase = true) }.forEach { confirmRead(it.id) }
                mutableState.update { it.copy(loading = false, conversation = messages.map { message ->
                    if (message.to.equals(username, ignoreCase = true) && message.id in readHistory) message.copy(read = true) else message
                }) }
                messages.filter { !it.read && it.to.equals(username, ignoreCase = true) }.forEach { markRead(it.id) }
                if (state.value.sendUnconfirmed) startDeliveryCheck(messages)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutableState.update { it.copy(loading = false, error = failure.forScreen()) } }
        }
    }

    fun markRead(id: Long) {
        if (id in state.value.marking || id in readIds) return
        val generation = readGeneration
        val conversationId = state.value.conversationId ?: state.value.mailboxes.values
            .flatMap { it.items }.firstOrNull { it.messageId == id }?.id
        readErrors.remove(id)
        mutableState.update { it.copy(marking = it.marking + id, readError = readErrors.values.firstOrNull()) }
        viewModelScope.launch {
            try {
                try {
                    repository.markMessageRead(id)
                } catch (failure: ApiFailure) {
                    if (failure.kind != FailureKind.InvalidResponse) throw failure
                    // Some read acknowledgements are missing even when the server saved the change.
                    val confirmed = conversationId?.let { thread ->
                        readDelivery { repository.conversation(thread) }?.any { it.id == id && it.read }
                    } == true
                    if (!confirmed) throw ApiFailure(FailureKind.InvalidResponse,
                        "Couldn't confirm this message was marked as read. Refresh to check again.")
                }
                ensureActive()
                confirmRead(id)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (generation == readGeneration && id !in readIds) {
                    readErrors[id] = failure.forScreen()
                    mutableState.update { it.copy(readError = readErrors.values.firstOrNull()) }
                }
            }
            finally { mutableState.update { it.copy(marking = it.marking - id) } }
        }
    }

    private fun confirmRead(id: Long) {
        readIds += id
        readHistory.remember(listOf(id))
        readErrors.remove(id)
        mutableState.update { state -> state.copy(
            mailboxes = state.mailboxes.mapValues { (folder, box) -> box.copy(items = box.items.map {
                if (folder == MessageFolder.Inbox && it.messageId == id) it.copy(read = true) else it
            }) },
            conversation = state.conversation.map { if (it.id == id) it.copy(read = true) else it },
            readError = readErrors.values.firstOrNull(),
        ) }
    }

    private fun clearReadErrors() {
        readGeneration++
        readErrors.clear()
        mutableState.update { it.copy(readError = null) }
    }

    fun openReply() {
        if (state.value.replyTarget?.recipient(username).isNullOrBlank() || state.value.loading) return
        mutableState.update { it.copy(replying = true) }
    }

    fun closeReply() { if (!state.value.sending) mutableState.update { it.copy(replying = false) } }

    fun editReply(text: String) {
        val id = state.value.messageId ?: return
        if (state.value.sending || state.value.sendUnconfirmed || text.length > PrivateMessages.MAX_LENGTH) return
        savedState["message-draft-$id"] = text
        mutableState.update { it.copy(draft = text) }
    }

    fun sendReply() = beginSend(allowUnconfirmed = false)

    fun retryReply() {
        if (state.value.sendUnconfirmed && !state.value.checkingReply) beginSend(allowUnconfirmed = true)
    }

    private fun beginSend(allowUnconfirmed: Boolean) {
        val snapshot = state.value
        val target = snapshot.replyTarget ?: return
        val conversationId = snapshot.conversationId ?: return
        val text = snapshot.draft.trim()
        if (snapshot.sending || snapshot.loading || snapshot.checkingReply ||
            snapshot.sendUnconfirmed && !allowUnconfirmed || text.isBlank() || text.length > PrivateMessages.MAX_LENGTH) return
        mutableState.update { it.copy(sending = true, sendError = null) }
        viewModelScope.launch {
            var checkDelivery = false
            try {
                val (baseline, sentBaseline) = coroutineScope {
                    val conversation = async { readDelivery { repository.conversation(conversationId) }?.takeIf { it.isNotEmpty() } }
                    val sent = async { readDelivery { repository.messages(MessageFolder.Sent, 1) } }
                    conversation.await() to sent.await()
                }
                ensureActive()
                // A reply may arrive after the user opens the retry dialog.
                if (allowUnconfirmed && pendingReply?.let { attempt ->
                    withTimeoutOrNull(12_000) { replyDelivered(attempt, baseline, sentBaseline) } == true
                } == true) {
                    finishReply(target.messageId)
                    return@launch
                }
                val attempt = MessageReplyAttempt(conversationId, target.messageId, username, target.recipient(username),
                    target.subject, text, now(), (snapshot.conversation + baseline.orEmpty()).map { it.messageId }.toSet() + target.messageId,
                    freshBaseline = baseline != null, sentIds = sentBaseline?.items?.map { it.messageId }?.toSet())
                saveAttempt(attempt)
                repository.replyToMessage(target, text)
                finishReply(target.messageId)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                val rejected = failure is ApiFailure && (failure.kind == FailureKind.Server && failure.httpStatus in 200..299 ||
                    failure.httpStatus in 400..499 && failure.httpStatus != 408 ||
                    failure.kind in setOf(FailureKind.Credentials, FailureKind.Session, FailureKind.Permission, FailureKind.Dns))
                if (rejected) clearAttempt(target.messageId)
                mutableState.update { it.copy(sendUnconfirmed = !rejected, checkingReply = !rejected,
                    sendError = if (rejected) failure.forScreen().message else null) }
                checkDelivery = !rejected
            } finally { mutableState.update { it.copy(sending = false) } }
            if (checkDelivery) startDeliveryCheck()
        }
    }

    fun checkReplyDelivery() = startDeliveryCheck()

    private fun startDeliveryCheck(initialConversation: List<PrivateMessage>? = null) {
        val snapshot = state.value
        if (!snapshot.sendUnconfirmed || snapshot.sending || deliveryJob?.isActive == true) return
        val target = snapshot.replyTarget ?: return
        // Older saved drafts have no send baseline. Keep them unconfirmed instead of guessing.
        val attempt = pendingReply ?: MessageReplyAttempt(snapshot.conversationId ?: return, target.messageId,
            username, target.recipient(username), target.subject, snapshot.draft.trim(), 0, setOf(target.messageId), false)
        mutableState.update { it.copy(checkingReply = true, sendError = null) }
        val generation = ++deliveryGeneration
        deliveryJob = viewModelScope.launch {
            var readSucceeded = false
            try {
                val found = withTimeoutOrNull(12_000) {
                    repeat(3) { round ->
                        if (round > 0) delay(round * 750L)
                        val matched = coroutineScope {
                            val conversation = async {
                                if (round == 0 && initialConversation != null) initialConversation
                                else readDelivery { repository.conversation(attempt.conversationId) }
                            }
                            val sent = async { readDelivery { repository.messages(MessageFolder.Sent, 1) } }
                            val messages = conversation.await()
                            ensureActive()
                            if (messages != null) {
                                readSucceeded = true
                                if (messages.isNotEmpty()) mutableState.update { it.copy(conversation = messages.map { message ->
                                    if (message.to.equals(username, ignoreCase = true) && message.id in readHistory) message.copy(read = true) else message
                                }) }
                                if (messages.any(attempt::matches)) { sent.cancel(); return@coroutineScope true }
                            }
                            val page = sent.await()
                            if (page != null) readSucceeded = true
                            replyDelivered(attempt, messages, page)
                        }
                        if (matched) return@withTimeoutOrNull true
                    }
                    false
                } == true
                ensureActive()
                if (found) finishReply(attempt.targetId)
                else mutableState.update { it.copy(sendError = if (readSucceeded) UNCONFIRMED_REPLY else DELIVERY_UNAVAILABLE) }
            } finally {
                if (generation == deliveryGeneration) mutableState.update { it.copy(checkingReply = false) }
            }
        }
    }

    private suspend fun replyDelivered(attempt: MessageReplyAttempt, conversation: List<PrivateMessage>?, sent: MessagePage?): Boolean {
        if (conversation?.any(attempt::matches) == true) return true
        val candidates = sent?.items.orEmpty().filter { attempt.newSentCandidate(it) || attempt.sameEnvelope(it) }
        for (candidate in candidates.take(3)) {
            if (attempt.matchesSent(candidate) || candidate.id == attempt.conversationId && attempt.matches(candidate)) return true
            val thread = readDelivery { repository.conversation(candidate.id) } ?: continue
            // A newly added Sent entry can have its own conversation ID without the original thread's history.
            if (thread.any { it.messageId == candidate.messageId && attempt.matchesSent(it) }) return true
            if (thread.any { it.messageId in attempt.knownIds } && thread.any(attempt::matches)) return true
        }
        return false
    }

    private suspend fun <T> readDelivery(block: suspend () -> T): T? = withTimeoutOrNull(4_000) {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }

    private fun finishReply(id: Long) {
        savedState.remove<String>("message-draft-$id")
        clearAttempt(id)
        mutableState.update { it.copy(sending = false, checkingReply = false, sendUnconfirmed = false,
            replying = false, draft = "", sendError = null, notice = "Reply sent") }
        loadConversation()
        MessageFolder.entries.forEach { loadMailbox(refresh = true, folder = it) }
    }

    private fun saveAttempt(attempt: MessageReplyAttempt) {
        pendingReply = attempt
        val key = "message-pending-${attempt.targetId}"
        savedState[key] = true
        savedState["$key-conversation"] = attempt.conversationId
        savedState["$key-sender"] = attempt.sender
        savedState["$key-recipient"] = attempt.recipient
        savedState["$key-subject"] = attempt.subject
        savedState["$key-text"] = attempt.text
        savedState["$key-started"] = attempt.startedAt
        savedState["$key-known"] = attempt.knownIds.toLongArray()
        savedState["$key-fresh"] = attempt.freshBaseline
        if (attempt.sentIds != null) savedState["$key-sent"] = attempt.sentIds.toLongArray()
        else savedState.remove<LongArray>("$key-sent")
    }

    private fun clearAttempt(id: Long) {
        val key = "message-pending-$id"
        listOf("", "-conversation", "-sender", "-recipient", "-subject", "-text", "-started", "-known", "-fresh", "-sent")
            .forEach { savedState.remove<Any>(key + it) }
        pendingReply = null
    }

    fun noticeShown() { mutableState.update { it.copy(notice = null) } }

    private fun restoreDraft() {
        val id = state.value.messageId ?: return
        val key = "message-pending-$id"
        val pending = savedState.get<Boolean>(key) == true
        val recipient = savedState.get<String>("$key-recipient")
        val subject = savedState.get<String>("$key-subject")
        val text = savedState.get<String>("$key-text")
        val sender = savedState.get<String>("$key-sender")
        pendingReply = if (pending && recipient != null && subject != null && text != null && sender == username)
            MessageReplyAttempt(savedState["$key-conversation"] ?: state.value.conversationId ?: id, id, sender, recipient,
                subject, text, savedState["$key-started"] ?: 0, savedState.get<LongArray>("$key-known")?.toSet().orEmpty(),
                savedState["$key-fresh"] ?: false, savedState.get<LongArray>("$key-sent")?.toSet()) else null
        mutableState.update { it.copy(draft = savedState["message-draft-$id"] ?: "",
            sendUnconfirmed = pending, checkingReply = false, sendError = if (pending) UNCONFIRMED_REPLY else null) }
    }

    private fun updateMailbox(folder: MessageFolder, change: (MailboxState) -> MailboxState) {
        mutableState.update { it.copy(mailboxes = it.mailboxes + (folder to change(it.mailboxes.getValue(folder)))) }
    }

    private companion object {
        const val UNCONFIRMED_REPLY = "Couldn't confirm sending. Check Sent before sending again."
        const val DELIVERY_UNAVAILABLE = "Couldn't check send status. Try again when connected."
    }
}
