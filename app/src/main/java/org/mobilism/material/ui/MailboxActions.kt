package org.mobilism.material.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mobilism.material.data.ApiFailure
import org.mobilism.material.data.FailureKind
import org.mobilism.material.data.MessagesRepository
import org.mobilism.material.data.PrivateMessage
import org.mobilism.material.data.PrivateMessages

data class NewMessageDraft(val recipient: String = "", val subject: String = "", val body: String = "") {
    val ready get() = recipient.isNotBlank() && subject.isNotBlank() && body.isNotBlank() && body.length <= PrivateMessages.MAX_LENGTH
    override fun toString() = "NewMessageDraft()"
}

data class MailboxActionState(
    val draft: NewMessageDraft, val composing: Boolean = false, val deletion: PrivateMessage? = null,
    val busy: Boolean = false, val uncertainSend: Boolean = false, val uncertainDelete: Boolean = false,
    val error: String? = null, val failure: FailureKind? = null,
)

class MailboxActions internal constructor(
    private val repository: MessagesRepository, private val saved: SavedStateHandle, private val scope: CoroutineScope,
    private val onSentFolder: (sent: Boolean) -> Unit, private val onDeleted: (Long) -> Unit,
) {
    private val mutable = MutableStateFlow(MailboxActionState(
        draft = NewMessageDraft(saved["pm-to"] ?: "", saved["pm-subject"] ?: "", saved["pm-body"] ?: ""),
        composing = saved["pm-composing"] ?: false, uncertainSend = saved["pm-send-attempt"] ?: false,
    ))
    val state = mutable.asStateFlow()
    private val unconfirmedDeletions = mutableSetOf<Long>()

    fun compose() {
        if (state.value.busy) return
        saved["pm-composing"] = true
        mutable.update { it.copy(composing = true, deletion = null, error = null, failure = null) }
    }

    fun edit(draft: NewMessageDraft) {
        if (!state.value.composing || state.value.busy || draft.body.length > PrivateMessages.MAX_LENGTH ||
            draft.recipient.length > PrivateMessages.MAX_LENGTH || draft.subject.length > PrivateMessages.MAX_LENGTH) return
        saved["pm-to"] = draft.recipient
        saved["pm-subject"] = draft.subject
        saved["pm-body"] = draft.body
        mutable.update { it.copy(draft = draft, error = null, failure = null) }
    }

    fun close() {
        if (state.value.busy) return
        saved.remove<Boolean>("pm-composing")
        mutable.update { it.copy(composing = false, deletion = null, error = null, failure = null, uncertainDelete = false) }
    }

    fun viewSent() {
        if (state.value.busy) return
        close()
        onSentFolder(false)
    }

    fun discard() {
        if (state.value.busy) return
        clearDraft()
        close()
    }

    private fun clearDraft() {
        listOf("pm-to", "pm-subject", "pm-body", "pm-send-attempt").forEach { saved.remove<Any>(it) }
        mutable.update { it.copy(draft = NewMessageDraft(), uncertainSend = false) }
    }

    fun send(confirmedResend: Boolean = false) {
        val snapshot = state.value
        if (!snapshot.composing || snapshot.busy || !snapshot.draft.ready || snapshot.uncertainSend && !confirmedResend) return
        val draft = snapshot.draft
        saved["pm-send-attempt"] = true
        mutable.update { it.copy(busy = true, error = null, failure = null) }
        scope.launch {
            try {
                repository.sendMessage(draft.recipient, draft.subject, draft.body)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                val uncertain = snapshot.uncertainSend || !definitelyRejected(failure)
                saved["pm-send-attempt"] = uncertain
                mutable.update { it.copy(busy = false, uncertainSend = uncertain, failure = (failure as? ApiFailure)?.kind,
                    error = if (definitelyRejected(failure)) failure.forScreen().message else null) }
                return@launch
            }
            clearDraft()
            saved.remove<Boolean>("pm-composing")
            mutable.update { it.copy(composing = false, busy = false) }
            onSentFolder(true)
        }
    }

    fun selectForDeletion(message: PrivateMessage) {
        if (state.value.busy || state.value.composing || message.messageId <= 0) return
        val uncertain = message.messageId in unconfirmedDeletions
        mutable.update { it.copy(deletion = message, error = if (uncertain) deletionNotice else null,
            failure = null, uncertainDelete = uncertain) }
    }

    internal fun conversationLoaded(items: List<PrivateMessage>) {
        for (item in items) unconfirmedDeletions.remove(item.messageId)
    }

    fun confirmDelete() {
        val current = state.value
        val id = current.deletion?.messageId ?: return
        if (current.busy || current.uncertainDelete) return
        saved["pm-delete-attempt"] = id
        mutable.update { it.copy(busy = true, error = null, failure = null) }
        scope.launch {
            try {
                repository.deleteMessage(id)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                saved.remove<Long>("pm-delete-attempt")
                val uncertain = !definitelyRejected(failure)
                if (uncertain) unconfirmedDeletions += id
                mutable.update { it.copy(busy = false, uncertainDelete = uncertain, failure = (failure as? ApiFailure)?.kind,
                    error = if (uncertain) deletionNotice else failure.forScreen().message) }
                return@launch
            }
            saved.remove<Long>("pm-delete-attempt")
            mutable.update { it.copy(busy = false, deletion = null) }
            onDeleted(id)
        }
    }

    private fun definitelyRejected(failure: Exception): Boolean = failure is ApiFailure && (
        failure.blockedBeforeRequest || failure.kind in setOf(FailureKind.Credentials, FailureKind.Session, FailureKind.Permission, FailureKind.Dns, FailureKind.Challenge) ||
            failure.httpStatus in 400..499 && failure.httpStatus != 408 ||
            failure.kind == FailureKind.Server && failure.httpStatus in 200..299)

    private companion object {
        const val deletionNotice = "Deletion is unconfirmed. Refresh the conversation to check."
    }
}
