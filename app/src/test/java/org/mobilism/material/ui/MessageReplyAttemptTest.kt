package org.mobilism.material.ui

import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.PrivateMessage

class MessageReplyAttemptTest {
    private val attempt = MessageReplyAttempt(10, 42, "Reader", "Author", "Re: Question",
        "Thanks & hello\nAnother line 😊", 1_700_000_100_000, setOf(42, 43), true)
    private val reply = PrivateMessage(44, 44, "Re: Re: Question", "reader", "AUTHOR",
        "<p>Thanks &amp; hello<br>Another&nbsp;line 😊</p>", "Thanks & hello", 1_700_000_100, true)

    @Test fun fullBodyHandlesHtmlEntitiesWhitespaceAndReplyPrefixes() {
        assertTrue(attempt.matches(reply))
    }

    @Test fun previewOldIdsAndDifferentEnvelopeDoNotProveDelivery() {
        assertFalse(attempt.matches(reply.copy(contentHtml = "")))
        assertFalse(attempt.matches(reply.copy(messageId = 43)))
        assertFalse(attempt.matches(reply.copy(messageId = 41)))
        assertFalse(attempt.matches(reply.copy(from = "Other")))
        assertFalse(attempt.matches(reply.copy(to = "Other")))
        assertFalse(attempt.matches(reply.copy(subject = "Different topic")))
        assertFalse(attempt.matches(reply.copy(contentHtml = reply.contentHtml + " more")))
    }

    @Test fun staleBaselineRequiresRecentTimestampAndLegacyAttemptsStayUnconfirmed() {
        val stale = attempt.copy(freshBaseline = false)
        assertTrue(stale.matches(reply))
        assertFalse(stale.matches(reply.copy(publishedAt = 1_700_000_000)))
        assertFalse(stale.copy(startedAt = 0).matches(reply))
    }

    @Test fun toStringDoesNotExposePrivateContentOrParticipants() {
        assertEquals("MessageReplyAttempt(conversationId=10, targetId=42)", attempt.toString())
    }
}
