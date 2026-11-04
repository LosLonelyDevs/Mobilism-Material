package org.mobilism.material.ui

import org.mobilism.material.data.PrivateMessage

internal data class MessageThread(val latest: PrivateMessage, val unreadIds: List<Long>) {
    val id: Long get() = latest.id
}

internal fun messageThreads(messages: List<PrivateMessage>): List<MessageThread> = messages
    .distinctBy { it.messageId }
    .groupBy { it.id }
    .values.map { replies ->
        MessageThread(replies.maxWith(compareBy<PrivateMessage> { it.publishedAt }.thenBy { it.messageId }),
            replies.filterNot { it.read }.map { it.messageId })
    }
    .sortedWith(compareByDescending<MessageThread> { it.latest.publishedAt }.thenByDescending { it.latest.messageId })
