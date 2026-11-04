package org.mobilism.material.ui

import org.jsoup.Jsoup
import org.mobilism.material.data.PrivateMessage

internal data class MessageReplyAttempt(
    val conversationId: Long,
    val targetId: Long,
    val sender: String,
    val recipient: String,
    val subject: String,
    val text: String,
    val startedAt: Long,
    val knownIds: Set<Long>,
    val freshBaseline: Boolean,
    val sentIds: Set<Long>? = null,
) {
    fun sameEnvelope(message: PrivateMessage): Boolean =
        message.messageId > (knownIds.maxOrNull() ?: 0) && message.messageId !in knownIds &&
            message.messageId !in sentIds.orEmpty() &&
            sameParticipantsAndSubject(message) &&
            (freshBaseline || startedAt > 0 && message.publishedAt >= startedAt / 1000 - 5)

    fun newSentCandidate(message: PrivateMessage): Boolean = sentIds != null &&
        message.messageId > (sentIds.maxOrNull() ?: 0) && message.messageId !in sentIds &&
        message.messageId !in knownIds &&
        sameParticipantsAndSubject(message)

    fun matchesSent(message: PrivateMessage): Boolean = newSentCandidate(message) && sameBody(message)

    private fun sameParticipantsAndSubject(message: PrivateMessage): Boolean =
        message.from.trim().equals(sender.trim(), ignoreCase = true) &&
            message.to.trim().equals(recipient.trim(), ignoreCase = true) &&
            subjectKey(message.subject).equals(subjectKey(subject), ignoreCase = true)

    fun matches(message: PrivateMessage): Boolean = sameEnvelope(message) && sameBody(message)

    private fun sameBody(message: PrivateMessage): Boolean =
        message.contentHtml.isNotBlank() && normalize(Jsoup.parseBodyFragment(message.contentHtml).text()) == normalize(text)

    override fun toString(): String = "MessageReplyAttempt(conversationId=$conversationId, targetId=$targetId)"

    private fun subjectKey(value: String) = normalize(value).replace(Regex("^(?:re\\s*:\\s*)+", RegexOption.IGNORE_CASE), "")
    private fun normalize(value: String) = value.replace(Regex("[\\s\u00a0]+"), " ").trim()
}
