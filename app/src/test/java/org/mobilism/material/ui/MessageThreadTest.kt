package org.mobilism.material.ui

import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.PrivateMessage

class MessageThreadTest {
    private val first = PrivateMessage(10, 42, "Question", "Author", "Reader", "", "First reply", 100, false)

    @Test fun chainShowsLatestReplyButKeepsOlderUnreadMessages() {
        val latest = first.copy(messageId = 44, preview = "Latest reply", publishedAt = 300, read = true)
        val middle = first.copy(messageId = 43, publishedAt = 200)
        val thread = messageThreads(listOf(middle, latest, first, middle)).single()
        assertEquals(latest, thread.latest)
        assertEquals(setOf(42L, 43L), thread.unreadIds.toSet())
        assertEquals(2, thread.unreadIds.size)
    }

    @Test fun identicalSubjectsAndParticipantsDoNotMergeSeparateConversations() {
        val other = first.copy(id = 11, messageId = 50, publishedAt = 150)
        val renamedReply = first.copy(messageId = 45, subject = "Re: Updated question", publishedAt = 200)
        val threads = messageThreads(listOf(first, other, renamedReply))
        assertEquals(listOf(10L, 11L), threads.map { it.id })
        assertEquals(renamedReply, threads.first().latest)
    }

    @Test fun repliesWithTheSameTimestampUseTheNewestMessageId() {
        val newer = first.copy(messageId = 43)
        assertEquals(newer, messageThreads(listOf(newer, first)).single().latest)
        assertEquals(newer, messageThreads(listOf(first, newer)).single().latest)
    }
}
