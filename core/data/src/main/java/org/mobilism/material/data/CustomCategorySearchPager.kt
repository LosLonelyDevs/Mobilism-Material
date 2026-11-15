package org.mobilism.material.data

import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal class CustomCategorySearchPager(
    items: List<CategorizedItem>,
    section: ContentSection,
    private val order: FeedOrder,
    private val pageSize: Int = 20,
    private val matches: (Release) -> Boolean,
    private val load: suspend (query: String, page: Int) -> ReleasePage,
) {
    private data class Cursor(
        var page: Int = 0,
        var ended: Boolean = false,
        var activityBound: Long? = null,
        val seen: MutableSet<Long> = mutableSetOf(),
    ) {
        fun snapshot() = copy(seen = seen.toMutableSet())
    }

    private var cursors = items.flatMap { it.releases }.filter { it.section == section }
        .map { ReleaseTitles.parse(it.title, section).name.trim() }.filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.ROOT) }.associateWith { Cursor() }
    private var pending = linkedMapOf<Long, Release>()
    private var emitted = emptySet<Long>()
    private var lastDate: Long? = null
    private val pages = mutableMapOf<Int, ReleasePage>()
    private val permits = Semaphore(4)
    private var requests = 0
    private val ranking = compareByDescending<Release> { if (order == FeedOrder.Top) it.views else it.publishedAt }
        .thenByDescending { it.publishedAt }.thenByDescending { it.id }

    init { require(pageSize > 0) }

    suspend fun page(number: Int): ReleasePage {
        pages[number]?.let { return it }
        check(number == pages.size + 1) { "Category search pages must be loaded in sequence." }
        val next = cursors.mapValues { it.value.snapshot() }
        val candidates = LinkedHashMap(pending)
        val shown = emitted.toMutableSet()
        while (true) {
            currentCoroutineContext().ensureActive()
            val active = next.values.filterNot { it.ended }
            // Search is ordered by reply activity. A later page cannot contain a post newer than its activity bound.
            // Views have no such bound, so Top must finish the app searches before ranking their results.
            val ready = candidates.values.filter { release ->
                active.isEmpty() || (order == FeedOrder.Latest && active.all { cursor ->
                    cursor.activityBound?.let { release.publishedAt > it } == true
                })
            }.sortedWith(ranking).take(pageSize)
            if (ready.isNotEmpty() || active.isEmpty()) {
                ready.forEach { candidates.remove(it.id); shown.add(it.id) }
                cursors = next
                pending = candidates
                emitted = shown
                ready.lastOrNull()?.let { lastDate = it.publishedAt }
                return ReleasePage(ready, active.isNotEmpty() || candidates.isNotEmpty()).also { pages[number] = it }
            }
            val searches = next.filterValues { !it.ended }
            if (requests + searches.size > 6) throw ApiFailure(FailureKind.InvalidResponse,
                "This category needs too many searches. Use a smaller category or search for an app directly.")
            requests += searches.size
            val responses = coroutineScope {
                searches.map { (query, cursor) ->
                    async { query to permits.withPermit { load(query, cursor.page + 1) } }
                }.awaitAll()
            }
            for ((query, response) in responses) {
                val cursor = next.getValue(query)
                val sourcePage = response.lastPage ?: (cursor.page + 1)
                check(sourcePage > cursor.page) { "Category search did not advance. Refresh to try again." }
                val fresh = response.items.filter { cursor.seen.add(it.id) }
                val found = fresh.filter { it.id !in shown && matches(it) }
                if (order == FeedOrder.Latest && lastDate?.let { date -> found.any { it.publishedAt > date } } == true)
                    throw ApiFailure(FailureKind.InvalidResponse, "Search results changed while loading. Refresh the category.")
                found.forEach { candidates[it.id] = it }
                cursor.page = sourcePage
                cursor.activityBound = response.nextActivityAtMost
                cursor.ended = !response.canLoadMore || (response.items.isNotEmpty() && fresh.isEmpty())
            }
        }
    }
}
