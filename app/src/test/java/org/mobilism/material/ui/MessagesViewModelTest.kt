package org.mobilism.material.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        assertTrue(model.state.value.sendError!!.contains("Check the conversation"))
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

    private class Repository : MessagesRepository {
        val pages = mutableListOf<Pair<MessageFolder, Int>>()
        val reads = mutableListOf<Long>()
        val posts = mutableListOf<Pair<Long, String>>()
        var readResult = CompletableDeferred<Unit>()
        val sendResult = CompletableDeferred<Unit>()
        var conversationError: Exception? = null
        var mailboxError: Exception? = null
        var pendingConversation: CompletableDeferred<List<PrivateMessage>>? = null
        override suspend fun messages(folder: MessageFolder, page: Int): MessagePage {
            pages += folder to page
            mailboxError?.let { throw it }
            return MessagePage(listOf(if (folder == MessageFolder.Inbox) incoming else outgoing), true)
        }
        override suspend fun conversation(id: Long): List<PrivateMessage> {
            conversationError?.let { throw it }
            return pendingConversation?.await() ?: listOf(incoming.copy(id = 42), outgoing.copy(id = 43))
        }
        override suspend fun markMessageRead(id: Long) { reads += id; readResult.await() }
        override suspend fun replyToMessage(message: PrivateMessage, text: String) { posts += message.messageId to text; sendResult.await() }
    }

    companion object {
        private val incoming = PrivateMessage(10, 42, "Question", "Author", "Reader", "Hello", "Hello", 1, false)
        private val outgoing = incoming.copy(messageId = 43, from = "Reader", to = "Author", read = false)
    }
}
