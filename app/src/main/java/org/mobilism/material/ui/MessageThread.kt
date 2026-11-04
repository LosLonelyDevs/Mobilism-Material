package org.mobilism.material.ui

import org.mobilism.material.data.PrivateMessage

internal data class MessageThread(val latest: PrivateMessage, val unreadIds: List<Long>, val avatars: Map<String, String> = emptyMap()) {
    val id: Long get() = latest.id
    fun avatarFor(username: String): String? = avatars[username.lowercase(java.util.Locale.ROOT)]
        ?: latest.avatar.takeIf { latest.from.equals(username, ignoreCase = true) }
}

internal fun messageThreads(messages: List<PrivateMessage>): List<MessageThread> = messages
    .distinctBy { it.messageId }
    .groupBy { it.id }
    .values.map { replies ->
        MessageThread(replies.maxWith(compareBy<PrivateMessage> { it.publishedAt }.thenBy { it.messageId }),
            replies.filterNot { it.read }.map { it.messageId },
            replies.sortedWith(compareBy<PrivateMessage> { it.publishedAt }.thenBy { it.messageId })
                .mapNotNull { reply -> reply.avatar?.let { reply.from.lowercase(java.util.Locale.ROOT) to it } }.toMap())
    }
    .sortedWith(compareByDescending<MessageThread> { it.latest.publishedAt }.thenByDescending { it.latest.messageId })
