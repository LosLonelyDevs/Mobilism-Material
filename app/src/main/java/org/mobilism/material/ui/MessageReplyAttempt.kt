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
) {
    fun sameEnvelope(message: PrivateMessage): Boolean =
        message.messageId > (knownIds.maxOrNull() ?: 0) && message.messageId !in knownIds &&
            message.from.trim().equals(sender.trim(), ignoreCase = true) &&
            message.to.trim().equals(recipient.trim(), ignoreCase = true) &&
            subjectKey(message.subject).equals(subjectKey(subject), ignoreCase = true) &&
            (freshBaseline || startedAt > 0 && message.publishedAt >= startedAt / 1000 - 5)

    fun matches(message: PrivateMessage): Boolean = sameEnvelope(message) &&
        message.contentHtml.isNotBlank() && normalize(Jsoup.parseBodyFragment(message.contentHtml).text()) == normalize(text)

    override fun toString(): String = "MessageReplyAttempt(conversationId=$conversationId, targetId=$targetId)"

    private fun subjectKey(value: String) = normalize(value).replace(Regex("^(?:re\\s*:\\s*)+", RegexOption.IGNORE_CASE), "")
    private fun normalize(value: String) = value.replace(Regex("[\\s\u00a0]+"), " ").trim()
}
