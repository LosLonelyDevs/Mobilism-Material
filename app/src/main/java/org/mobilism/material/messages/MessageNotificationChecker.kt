package org.mobilism.material.messages

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mobilism.material.data.MessageFolder
import org.mobilism.material.data.MessagesRepository
import org.mobilism.material.data.PrivateMessage
import org.mobilism.material.settings.MessageReadHistory

class MessageNotificationChecker(
    private val repository: MessagesRepository,
    private val username: () -> String?,
    private val allowed: () -> Boolean,
    private val history: (String) -> MessageReadHistory,
    private val readHistory: (String) -> MessageReadHistory,
    private val post: (String, PrivateMessage) -> Boolean,
    private val cancelRead: (String, Set<Long>) -> Unit,
) {
    private val mutex = Mutex()

    suspend fun check() = mutex.withLock {
        val account = username() ?: return@withLock
        if (!allowed()) return@withLock
        val notified = history(account)
        val read = readHistory(account)
        val visited = mutableSetOf<Long>()
        // Bound catch-up work; normal checks stop at the first previously observed page.
        for (page in 1..5) {
            val result = repository.messages(MessageFolder.Inbox, page)
            currentCoroutineContext().ensureActive()
            if (username() != account || !allowed()) return@withLock
            val incoming = result.items.filter { it.to.equals(account, ignoreCase = true) && !it.from.equals(account, ignoreCase = true) }
            val known = incoming.any { it.messageId in notified }
            cancelRead(account, incoming.filter { it.read || it.messageId in read }.map { it.messageId }.toSet())
            incoming.distinctBy { it.messageId }.sortedBy { it.publishedAt }.forEach { message ->
                if (username() != account || !allowed()) return@withLock
                if (message.read || message.messageId in read) notified.remember(listOf(message.messageId))
                else if (message.messageId !in notified && post(account, message)) notified.remember(listOf(message.messageId))
            }
            val newIds = incoming.map { it.messageId }.filter { visited.add(it) }
            if (!result.canLoadMore || result.items.isEmpty() || known || newIds.isEmpty()) break
        }
    }
}
