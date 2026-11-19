package org.mobilism.material.messages

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.*
import org.mobilism.material.settings.MessageReadHistory

@OptIn(ExperimentalCoroutinesApi::class)
class MessageNotificationCheckerTest {
    @Test fun repeatedForegroundStartsShareTheCooldownButNewMessagesArriveOnTheNextCheck() = runTest {
        val f = Fixture()
        repeat(10) { f.checker.check() }
        assertEquals(1, f.loads)
        f.pages = mapOf(1 to MessagePage(listOf(message), false))
        f.time += 60_000
        f.checker.check()
        assertEquals(2, f.loads)
        assertEquals(listOf("Reader" to 42L), f.posts)
    }

    @Test fun failedPollsStopAcrossRepeatedResumesAndCanRecover() = runTest {
        val f = Fixture()
        f.afterLoad = { throw ApiFailure(FailureKind.Server, "Unavailable", 503) }
        repeat(10) { runCatching { f.checker.check() } }
        assertEquals(1, f.loads)
        f.time += 60_000
        runCatching { f.checker.check() }
        f.time += 60_000
        f.checker.check()
        assertEquals(2, f.loads)
        f.afterLoad = {}
        f.time += 60_000
        f.checker.check()
        assertEquals(3, f.loads)
    }

    private val message = PrivateMessage(10, 42, "Subject", "Sender", "Reader", "", "Preview", 100, false)

    private class Fixture : MessagesRepository {
        var username: String? = "Reader"
        var allowed = true
        var acceptsPosts = true
        var loads = 0
        var time = 1_000L
        var pages = emptyMap<Int, MessagePage>()
        var afterLoad: suspend () -> Unit = {}
        val histories = mutableMapOf<String, MessageReadHistory>()
        val read = MessageReadHistory()
        val posts = mutableListOf<Pair<String, Long>>()
        val cancelled = mutableSetOf<Long>()
        val checker = MessageNotificationChecker(this, { username }, { allowed },
            { histories.getOrPut(it) { MessageReadHistory() } }, { read },
            { account, message -> if (acceptsPosts) { posts += account to message.messageId; true } else false },
            { _, ids -> cancelled += ids }, now = { time })
        override suspend fun messages(folder: MessageFolder, page: Int): MessagePage {
            assertEquals(MessageFolder.Inbox, folder)
            loads++
            afterLoad()
            return pages[page] ?: MessagePage(emptyList(), false)
        }
        override suspend fun conversation(id: Long): List<PrivateMessage> = error("Notification checks must not mark conversations read")
        override suspend fun markMessageRead(id: Long): Unit = error("Notification checks must not send read receipts")
        override suspend fun replyToMessage(message: PrivateMessage, text: String): Unit = error("Notification checks must not send messages")
        override suspend fun sendMessage(recipient: String, subject: String, body: String): Unit = error("Unexpected send")
        override suspend fun deleteMessage(messageId: Long): Unit = error("Unexpected deletion")
    }

    @Test fun onlyUnreadIncomingMessagesAlertOnceAcrossRepeatedChecks() = runTest {
        val f = Fixture()
        f.read.remember(listOf(44))
        f.pages = mapOf(1 to MessagePage(listOf(message, message.copy(messageId = 43, read = true),
            message.copy(messageId = 44), message.copy(messageId = 45, to = "Someone else")), false))
        f.checker.check()
        f.checker.check()
        assertEquals(listOf("Reader" to 42L), f.posts)
        assertEquals(setOf(43L, 44L), f.cancelled)
    }

    @Test fun permissionsAndSignOutPreventPollingWithoutLosingUnreadMessages() = runTest {
        val f = Fixture().apply { pages = mapOf(1 to MessagePage(listOf(message), false)); allowed = false }
        f.checker.check()
        f.allowed = true
        f.username = null
        f.checker.check()
        assertEquals(0, f.loads)
        f.username = "Reader"
        f.checker.check()
        assertEquals(listOf("Reader" to 42L), f.posts)
    }

    @Test fun accountSwitchAndPermissionChangesDuringFetchDiscardTheResult() = runTest {
        for (switchAccount in listOf(true, false)) {
            val f = Fixture().apply { pages = mapOf(1 to MessagePage(listOf(message), false)) }
            f.afterLoad = { if (switchAccount) f.username = "Another account" else f.allowed = false }
            f.checker.check()
            assertTrue(f.posts.isEmpty())
            assertFalse(42L in f.histories.getValue("Reader"))
        }
    }

    @Test fun failedNotificationCanBeRetriedAndNewRepliesUseTheirOwnMessageId() = runTest {
        val f = Fixture().apply {
            pages = mapOf(1 to MessagePage(listOf(message), false)); acceptsPosts = false
        }
        f.checker.check()
        assertFalse(42L in f.histories.getValue("Reader"))
        f.acceptsPosts = true
        f.time += 60_000
        f.checker.check()
        f.pages = mapOf(1 to MessagePage(listOf(message.copy(messageId = 50, publishedAt = 200), message), false))
        f.time += 60_000
        f.checker.check()
        assertEquals(listOf("Reader" to 42L, "Reader" to 50L), f.posts)
    }

    @Test fun backgroundAndForegroundChecksCannotPostTheSameMessageTwice() = runTest {
        val response = CompletableDeferred<Unit>()
        val f = Fixture().apply {
            pages = mapOf(1 to MessagePage(listOf(message), false)); afterLoad = { response.await() }
        }
        val first = async { f.checker.check() }
        runCurrent()
        val second = async { f.checker.check() }
        runCurrent()
        assertEquals(1, f.loads)
        response.complete(Unit)
        first.await(); second.await()
        assertEquals(1, f.loads)
        assertEquals(listOf("Reader" to 42L), f.posts)
    }

    @Test fun newerPagesAreCheckedAndRepeatedServerPagesCannotLoop() = runTest {
        val f = Fixture().apply { pages = mapOf(
            1 to MessagePage(listOf(message.copy(messageId = 43)), true),
            2 to MessagePage(listOf(message), true),
            3 to MessagePage(listOf(message), true)) }
        f.checker.check()
        assertEquals(setOf(42L, 43L), f.posts.map { it.second }.toSet())
        assertEquals(3, f.loads)
    }

    @Test fun accountHistoriesRemainSeparate() = runTest {
        val f = Fixture().apply { pages = mapOf(1 to MessagePage(listOf(message), false)) }
        f.checker.check()
        f.username = "Other"
        f.pages = mapOf(1 to MessagePage(listOf(message.copy(to = "Other")), false))
        f.checker.check()
        assertEquals(listOf("Reader" to 42L, "Other" to 42L), f.posts)
    }
}
