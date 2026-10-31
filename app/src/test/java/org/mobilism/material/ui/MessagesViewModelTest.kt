package org.mobilism.material.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mobilism.material.data.*

@OptIn(ExperimentalCoroutinesApi::class)
class MessagesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMain() = Dispatchers.resetMain()

    @Test fun tabsKeepIndependentPagesAndStopRepeatedResults() = runTest(dispatcher) {
        val repo = Repository()
        val model = MessagesViewModel(repo, "Reader", SavedStateHandle())
        advanceUntilIdle()
        model.selectFolder(MessageFolder.Sent)
        advanceUntilIdle()
        assertEquals("Reader", model.state.value.mailbox.items.single().from)
        model.selectFolder(MessageFolder.Inbox)
        model.loadMailbox()
        advanceUntilIdle()
        assertEquals(listOf(MessageFolder.Inbox to 1, MessageFolder.Sent to 1, MessageFolder.Inbox to 2), repo.pages)
        assertFalse(model.state.value.mailbox.canLoadMore)
        model.loadMailbox()
        advanceUntilIdle()
        assertEquals(3, repo.pages.size)
    }

    @Test fun failedRefreshKeepsRowsAndRetriesPageOneEvenAfterPaginationEnds() = runTest(dispatcher) {
        val repo = Repository()
        val model = MessagesViewModel(repo, "Reader", SavedStateHandle())
        advanceUntilIdle()
        model.loadMailbox()
        advanceUntilIdle()
        assertFalse(model.state.value.mailbox.canLoadMore)
        repo.mailboxError = ApiFailure(FailureKind.Connection, "Offline")
        model.loadMailbox(refresh = true)
        advanceUntilIdle()
        assertTrue(model.state.value.mailbox.refreshFailed)
        assertEquals(1, model.state.value.mailbox.items.size)
        repo.mailboxError = null
        model.loadMailbox(refresh = true)
        advanceUntilIdle()
        assertEquals(1, model.state.value.mailbox.page)
        assertFalse(model.state.value.mailbox.refreshFailed)
        assertNull(model.state.value.mailbox.error)
    }

    @Test fun openMarksIncomingMessagesOnlyAfterServerConfirms() = runTest(dispatcher) {
        val repo = Repository()
        val model = MessagesViewModel(repo, "reader", SavedStateHandle())
        advanceUntilIdle()
        model.openMessage(incoming)
        runCurrent()
        assertEquals(listOf(42L), repo.reads)
        assertFalse(model.state.value.mailbox.items.single().read)
        assertTrue(42L in model.state.value.marking)
        repo.readResult.complete(Unit)
        advanceUntilIdle()
        assertTrue(model.state.value.mailbox.items.single().read)
        assertTrue(model.state.value.conversation.first().read)
        model.closeConversation()
        model.loadMailbox(refresh = true)
        advanceUntilIdle()
        assertTrue(model.state.value.mailbox.items.single().read)
    }

    @Test fun failedReadKeepsUnreadAndOffersRetry() = runTest(dispatcher) {
        val repo = Repository()
        val model = MessagesViewModel(repo, "Reader", SavedStateHandle())
        advanceUntilIdle()
        model.markRead(42)
        repo.readResult.completeExceptionally(ApiFailure(FailureKind.Connection, "Offline"))
        advanceUntilIdle()
        assertFalse(model.state.value.mailbox.items.single().read)
        assertNotNull(model.state.value.readError)
        assertTrue(model.state.value.marking.isEmpty())
        repo.readResult = CompletableDeferred(Unit)
        model.markRead(42)
        advanceUntilIdle()
        assertTrue(model.state.value.mailbox.items.single().read)
        assertNull(model.state.value.readError)
    }

    @Test fun replyUsesSelectedMessageAndCannotDoubleSendOrLoseDraftOnFailure() = runTest(dispatcher) {
        val repo = Repository().apply { readResult.complete(Unit) }
        val saved = SavedStateHandle()
        val model = MessagesViewModel(repo, "Reader", saved)
        advanceUntilIdle()
        model.openMessage(incoming)
        advanceUntilIdle()
        model.openReply()
        model.editReply("  Thanks & hello  ")
        model.sendReply()
        model.sendReply()
        model.closeConversation()
        runCurrent()
        assertEquals(10L, model.state.value.conversationId)
        assertEquals(listOf(42L to "Thanks & hello"), repo.posts)
        repo.sendResult.completeExceptionally(ApiFailure(FailureKind.Connection, "timeout"))
        advanceUntilIdle()
        assertEquals("  Thanks & hello  ", model.state.value.draft)
        assertTrue(model.state.value.sendUnconfirmed)
        assertTrue(model.state.value.sendError!!.contains("may still arrive"))
        model.sendReply()
        model.editReply("Changed")
        advanceUntilIdle()
        assertEquals("  Thanks & hello  ", model.state.value.draft)
        model.closeReply()
        model.openReply()
        advanceUntilIdle()
        assertEquals(1, repo.posts.size)
        val restored = MessagesViewModel(repo, "Reader", saved)
        advanceUntilIdle()
        assertEquals(model.state.value.draft, restored.state.value.draft)
        assertNotNull(restored.state.value.sendError)
    }

    @Test fun confirmedSendClearsDraftEvenIfReloadFails() = runTest(dispatcher) {
        val repo = Repository().apply { readResult.complete(Unit) }
        val model = MessagesViewModel(repo, "Reader", SavedStateHandle())
        advanceUntilIdle()
        model.openMessage(incoming)
        advanceUntilIdle()
        model.editReply("Accepted")
        model.sendReply()
        runCurrent()
        repo.conversationError = ApiFailure(FailureKind.Connection, "Offline")
        repo.sendResult.complete(Unit)
        advanceUntilIdle()
        assertEquals("Reply sent", model.state.value.notice)
        assertEquals("", model.state.value.draft)
        assertNotNull(model.state.value.error)
        model.sendReply()
        advanceUntilIdle()
        assertEquals(1, repo.posts.size)
    }

    @Test fun cancelledConversationCannotReopenAfterBack() = runTest(dispatcher) {
        val repo = Repository().apply { pendingConversation = CompletableDeferred() }
        val model = MessagesViewModel(repo, "Reader", SavedStateHandle())
        advanceUntilIdle()
        model.openMessage(incoming)
        runCurrent()
        model.closeConversation()
        repo.pendingConversation!!.complete(listOf(incoming))
        advanceUntilIdle()
        assertNull(model.state.value.conversationId)
        assertTrue(model.state.value.conversation.isEmpty())
        assertTrue(repo.reads.isEmpty())
    }

    @Test fun missingAcknowledgementIsRecoveredFromConversationWithoutAnotherPost() = runTest(dispatcher) {
        val repo = Repository()
        val saved = SavedStateHandle()
        val model = ready(repo, saved)
        model.sendReply()
        runCurrent()
        repo.conversationItems += delivered
        repo.sendResult.completeExceptionally(ApiFailure(FailureKind.InvalidResponse, "Unknown response", 200))
        advanceUntilIdle()
        assertEquals("Reply sent", model.state.value.notice)
        assertEquals("", model.state.value.draft)
        assertFalse(model.state.value.replying)
        assertFalse(model.state.value.sendUnconfirmed)
        assertEquals(1, repo.posts.size)
        assertTrue(saved.keys().none { it.startsWith("message-pending-") || it.startsWith("message-draft-") })
    }

    @Test fun sentPreviewRequiresFullMatchingReplyInTheSameThread() = runTest(dispatcher) {
        val repo = Repository()
        val model = ready(repo)
        model.sendReply()
        runCurrent()
        repo.sentItems = listOf(delivered.copy(id = 99, contentHtml = "", preview = "Thanks & hello"))
        repo.otherConversations[99] = listOf(delivered.copy(contentHtml = "Thanks & hello, with more text"))
        repo.sendResult.completeExceptionally(ApiFailure(FailureKind.Connection, "Timeout"))
        advanceUntilIdle()
        assertTrue(model.state.value.sendUnconfirmed)
        // A full match in another conversation still does not confirm this reply.
        repo.otherConversations[99] = listOf(delivered)
        model.checkReplyDelivery()
        advanceUntilIdle()
        assertTrue(model.state.value.sendUnconfirmed)
        repo.otherConversations[99] = listOf(incoming.copy(id = 42), delivered)
        model.checkReplyDelivery()
        advanceUntilIdle()
        assertEquals("Reply sent", model.state.value.notice)
        assertEquals(1, repo.posts.size)
        assertTrue(99L in repo.conversationCalls)
    }

    @Test fun identicalOlderReplyCannotConfirmANewAttempt() = runTest(dispatcher) {
        val repo = Repository().apply { conversationItems += delivered }
        val model = ready(repo)
        model.sendReply()
        runCurrent()
        repo.sendResult.completeExceptionally(ApiFailure(FailureKind.Connection, "Timeout"))
        advanceUntilIdle()
        assertTrue(model.state.value.sendUnconfirmed)
        assertEquals("Thanks & hello", model.state.value.draft)
        assertNull(model.state.value.notice)
    }

    @Test fun retryFindsLateDeliveryBeforePostingAgain() = runTest(dispatcher) {
        val repo = Repository()
        val model = ready(repo)
        model.sendReply()
        runCurrent()
        repo.sendResult.completeExceptionally(ApiFailure(FailureKind.Connection, "Timeout"))
        advanceUntilIdle()
        repo.conversationItems += delivered
        model.retryReply()
        advanceUntilIdle()
        assertEquals(1, repo.posts.size)
        assertEquals("Reply sent", model.state.value.notice)
    }

    @Test fun explicitRetryCanSendButNormalSendCannotReplayUnconfirmedReply() = runTest(dispatcher) {
        val repo = Repository()
        val model = ready(repo)
        model.sendReply()
        runCurrent()
        repo.sendResult.completeExceptionally(ApiFailure(FailureKind.Server, "Request timeout", 408))
        advanceUntilIdle()
        model.sendReply()
        advanceUntilIdle()
        assertEquals(1, repo.posts.size)
        repo.sendResult = CompletableDeferred(Unit)
        model.retryReply()
        model.retryReply()
        advanceUntilIdle()
        assertEquals(2, repo.posts.size)
        assertEquals("Reply sent", model.state.value.notice)
    }

    @Test fun offlineDeliveryCheckKeepsDraftAndManualCheckRecovers() = runTest(dispatcher) {
        val repo = Repository()
        val model = ready(repo)
        model.sendReply()
        runCurrent()
        repo.conversationError = ApiFailure(FailureKind.Connection, "Offline")
        repo.mailboxError = repo.conversationError
        repo.sendResult.completeExceptionally(repo.conversationError!!)
        advanceUntilIdle()
        assertTrue(model.state.value.sendError!!.contains("couldn't check delivery"))
        assertEquals("Thanks & hello", model.state.value.draft)
        repo.conversationError = null
        repo.mailboxError = null
        repo.conversationItems += delivered
        model.checkReplyDelivery()
        advanceUntilIdle()
        assertEquals("Reply sent", model.state.value.notice)
        assertEquals(1, repo.posts.size)
    }

    @Test fun restoredPendingReplyChecksDeliveryWithoutSendingAgain() = runTest(dispatcher) {
        val repo = Repository()
        val saved = SavedStateHandle()
        val model = ready(repo, saved)
        model.sendReply()
        runCurrent()
        repo.sendResult.completeExceptionally(ApiFailure(FailureKind.Connection, "Timeout"))
        advanceUntilIdle()
        val snapshot = SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) })
        repo.conversationItems += delivered
        val restored = MessagesViewModel(repo, "Reader", snapshot)
        advanceUntilIdle()
        assertEquals("Reply sent", restored.state.value.notice)
        assertEquals("", restored.state.value.draft)
        assertEquals(1, repo.posts.size)
    }

    @Test fun leavingConversationCancelsDeliveryCheckAndCannotClearAnotherDraft() = runTest(dispatcher) {
        val repo = Repository()
        val model = ready(repo)
        model.sendReply()
        runCurrent()
        repo.pendingConversation = CompletableDeferred()
        repo.sendResult.completeExceptionally(ApiFailure(FailureKind.Connection, "Timeout"))
        runCurrent()
        assertTrue(model.state.value.checkingReply)
        model.closeConversation()
        repo.pendingConversation!!.complete(listOf(incoming, delivered))
        repo.pendingConversation = null
        val other = incoming.copy(id = 11, messageId = 50)
        model.openMessage(other)
        advanceUntilIdle()
        model.editReply("Another draft")
        advanceUntilIdle()
        assertEquals(11L, model.state.value.conversationId)
        assertEquals("Another draft", model.state.value.draft)
        assertNull(model.state.value.notice)
        assertFalse(model.state.value.checkingReply)
    }

    @Test fun definiteServerRejectionKeepsDraftEditableWithoutDeliveryChecks() = runTest(dispatcher) {
        val repo = Repository()
        val model = ready(repo)
        model.sendReply()
        runCurrent()
        val reads = repo.conversationCalls.size
        repo.sendResult.completeExceptionally(ApiFailure(FailureKind.Server, "Recipient unavailable", 200))
        advanceUntilIdle()
        assertFalse(model.state.value.sendUnconfirmed)
        assertFalse(model.state.value.checkingReply)
        assertNotNull(model.state.value.sendError)
        assertEquals(reads, repo.conversationCalls.size)
        model.editReply("Revised")
        assertEquals("Revised", model.state.value.draft)
    }

    @Test fun stalledReadBackTimesOutAndLeavesManualRecoveryAvailable() = runTest(dispatcher) {
        val repo = Repository()
        val model = ready(repo)
        model.sendReply()
        runCurrent()
        repo.pendingConversation = CompletableDeferred()
        repo.sendResult.completeExceptionally(ApiFailure(FailureKind.Connection, "Timeout"))
        runCurrent()
        assertTrue(model.state.value.checkingReply)
        model.retryReply()
        advanceUntilIdle()
        assertFalse(model.state.value.checkingReply)
        assertTrue(model.state.value.sendUnconfirmed)
        assertEquals("Thanks & hello", model.state.value.draft)
        assertEquals(1, repo.posts.size)
    }

    @Test fun deliveryCheckStartsWithoutAnIdleUnconfirmedFrame() = runTest(dispatcher) {
        val repo = Repository()
        val model = ready(repo)
        val states = mutableListOf<MessagesState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.toList(states) }
        model.sendReply()
        runCurrent()
        repo.sendResult.completeExceptionally(ApiFailure(FailureKind.Connection, "Timeout"))
        advanceUntilIdle()
        assertTrue(states.filter { it.sendUnconfirmed && !it.sending }
            .all { it.checkingReply || it.sendError != null })
    }

    @Test fun reopeningSameConversationDoesNotCancelItsNewDeliveryState() = runTest(dispatcher) {
        val repo = Repository()
        val model = ready(repo)
        model.sendReply()
        runCurrent()
        repo.sendResult.completeExceptionally(ApiFailure(FailureKind.Connection, "Timeout"))
        runCurrent()
        assertTrue(model.state.value.checkingReply)
        model.closeConversation()
        model.openMessage(incoming)
        runCurrent()
        assertTrue(model.state.value.checkingReply)
        repo.conversationItems += delivered
        advanceUntilIdle()
        assertEquals("Reply sent", model.state.value.notice)
        assertEquals(1, repo.posts.size)
    }

    private fun TestScope.ready(repo: Repository, saved: SavedStateHandle = SavedStateHandle()): MessagesViewModel {
        repo.readResult.complete(Unit)
        val model = MessagesViewModel(repo, "Reader", saved) { 1_700_000_100_000 }
        advanceUntilIdle()
        model.openMessage(incoming)
        advanceUntilIdle()
        model.openReply()
        model.editReply("Thanks & hello")
        return model
    }

    private class Repository : MessagesRepository {
        val pages = mutableListOf<Pair<MessageFolder, Int>>()
        val reads = mutableListOf<Long>()
        val posts = mutableListOf<Pair<Long, String>>()
        var readResult = CompletableDeferred<Unit>()
        var sendResult = CompletableDeferred<Unit>()
        var conversationItems = listOf(incoming.copy(id = 42), outgoing.copy(id = 43))
        var sentItems = listOf(outgoing)
        val otherConversations = mutableMapOf<Long, List<PrivateMessage>>()
        val conversationCalls = mutableListOf<Long>()
        var conversationError: Exception? = null
        var mailboxError: Exception? = null
        var pendingConversation: CompletableDeferred<List<PrivateMessage>>? = null
        override suspend fun messages(folder: MessageFolder, page: Int): MessagePage {
            pages += folder to page
            mailboxError?.let { throw it }
            return MessagePage(if (folder == MessageFolder.Inbox) listOf(incoming) else sentItems, true)
        }
        override suspend fun conversation(id: Long): List<PrivateMessage> {
            conversationCalls += id
            conversationError?.let { throw it }
            return pendingConversation?.await() ?: otherConversations[id] ?: conversationItems
        }
        override suspend fun markMessageRead(id: Long) { reads += id; readResult.await() }
        override suspend fun replyToMessage(message: PrivateMessage, text: String) { posts += message.messageId to text; sendResult.await() }
    }

    companion object {
        private val incoming = PrivateMessage(10, 42, "Question", "Author", "Reader", "Hello", "Hello", 1, false)
        private val outgoing = incoming.copy(messageId = 43, from = "Reader", to = "Author", read = false)
        private val delivered = outgoing.copy(id = 44, messageId = 44, subject = "Re: Question",
            contentHtml = "<p>Thanks &amp; hello</p>", publishedAt = 1_700_000_100, read = true)
    }
}
