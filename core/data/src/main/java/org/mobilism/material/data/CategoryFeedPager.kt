package org.mobilism.material.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Merges category streams at their next unread item, preserving global ordering
 * across page boundaries. A failed/cancelled page leaves all cursors unchanged.
 */
internal class CategoryFeedPager(
    categories: Set<Long>,
    order: FeedOrder,
    private val pageSize: Int = 20,
    private val localMatches: ((Release) -> Boolean)? = null,
    private val load: suspend (category: Long, page: Int) -> ReleasePage,
) {
    private data class Cursor(
        var page: Int = 0,
        var ended: Boolean = false,
        val buffer: ArrayDeque<Release> = ArrayDeque(),
        val loadedIds: MutableSet<Long> = mutableSetOf(),
    ) {
        fun snapshot() = copy(buffer = ArrayDeque(buffer), loadedIds = loadedIds.toMutableSet())
    }

    private var cursors = (categories + if (localMatches != null) setOf(0L) else emptySet()).sorted().associateWith { Cursor() }
    private var emittedIds = emptySet<Long>()
    private val permits = Semaphore(4)
    private val ranking = compareBy<Release> { if (order == FeedOrder.Top) it.views else it.publishedAt }
        .thenBy { it.publishedAt }.thenBy { it.id }

    init {
        require((categories.isNotEmpty() || localMatches != null) && categories.all { it > 0 })
        require(pageSize > 0)
    }

    suspend fun nextPage(): ReleasePage {
        val next = cursors.mapValues { it.value.snapshot() }
        val emitted = emittedIds.toMutableSet()
        val output = mutableListOf<Release>()
        var localPages = 0
        while (output.size < pageSize) {
            // Keep sparse local scans resumable without emitting past an unknown frontier.
            val local = next[0L]
            if (localPages >= 5 && local != null && !local.ended && local.buffer.isEmpty()) break
            // Every live stream needs a frontier before choosing the next item.
            coroutineScope {
                next.filterValues { !it.ended && it.buffer.isEmpty() }.map { (category, cursor) ->
                    async {
                        val response = permits.withPermit { load(category, cursor.page + 1) }
                        if (category == 0L) localPages++
                        val fresh = response.items.filter { cursor.loadedIds.add(it.id) }
                        cursor.page++
                        cursor.ended = !response.canLoadMore || fresh.isEmpty()
                        cursor.buffer.addAll(fresh.sortedWith(ranking.reversed()))
                    }
                }.awaitAll()
            }
            val candidate = next.entries.filter { it.value.buffer.isNotEmpty() }
                .maxWithOrNull { left, right -> ranking.compare(left.value.buffer.first(), right.value.buffer.first()) }
                ?: break
            val release = candidate.value.buffer.removeFirst()
            if ((candidate.key != 0L || localMatches?.invoke(release) == true) && emitted.add(release.id)) output.add(release)
        }
        cursors = next
        emittedIds = emitted
        return ReleasePage(output, next.values.any { !it.ended || it.buffer.isNotEmpty() })
    }
}
