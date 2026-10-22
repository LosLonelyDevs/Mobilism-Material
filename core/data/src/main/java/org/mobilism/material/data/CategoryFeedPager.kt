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

    private var cursors = categories.sorted().associateWith { Cursor() }
    private var emittedIds = emptySet<Long>()
    private val permits = Semaphore(4)
    private val ranking = compareBy<Release> { if (order == FeedOrder.Top) it.views else it.publishedAt }
        .thenBy { it.publishedAt }.thenBy { it.id }

    init {
        require(categories.isNotEmpty() && categories.all { it > 0 })
        require(pageSize > 0)
    }

    suspend fun nextPage(): ReleasePage {
        val next = cursors.mapValues { it.value.snapshot() }
        val emitted = emittedIds.toMutableSet()
        val output = mutableListOf<Release>()
        while (output.size < pageSize) {
            // Every live stream needs a frontier before choosing the next item.
            coroutineScope {
                next.filterValues { !it.ended && it.buffer.isEmpty() }.map { (category, cursor) ->
                    async {
                        val response = permits.withPermit { load(category, cursor.page + 1) }
                        val fresh = response.items.filter { cursor.loadedIds.add(it.id) }
                        cursor.page++
                        cursor.ended = !response.canLoadMore || fresh.isEmpty()
                        cursor.buffer.addAll(fresh.sortedWith(ranking.reversed()))
                    }
                }.awaitAll()
            }
            val candidate = next.values.filter { it.buffer.isNotEmpty() }
                .maxWithOrNull { left, right -> ranking.compare(left.buffer.first(), right.buffer.first()) }
                ?: break
            val release = candidate.buffer.removeFirst()
            if (emitted.add(release.id)) output.add(release)
        }
        cursors = next
        emittedIds = emitted
        return ReleasePage(output, next.values.any { !it.ended || it.buffer.isNotEmpty() })
    }
}
