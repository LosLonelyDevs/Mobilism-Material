package org.mobilism.material.ui

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.mobilism.material.data.Release
import org.mobilism.material.data.ReleasePage
import org.mobilism.material.data.SearchSort
import org.mobilism.material.data.ApiFailure
import org.mobilism.material.data.FailureKind

internal class SortedSearchPager(
    private val sort: SearchSort,
    private val load: suspend (Int) -> ReleasePage,
) {
    private var sorted: List<Release>? = null
    private var sourcePage = 1
    private var shown = 0
    private val pending = linkedMapOf<Long, Release>()
    private val seen = mutableSetOf<Long>()
    private var sourceHasMore = true
    private var nextActivityAtMost: Long? = null
    private var lastShownDate: Long? = null

    suspend fun next(onProgress: (Int) -> Unit): ReleasePage {
        if (sort == SearchSort.Activity) {
            val result = load(sourcePage)
            sourcePage = (result.lastPage ?: sourcePage) + 1
            return result
        }
        if (sort == SearchSort.Newest) return newest(onProgress)
        if (sorted == null) {
            // The search endpoint pages by reply activity and ignores date-sort parameters.
            val matches = linkedMapOf<Long, Release>()
            var page = 1
            do {
                currentCoroutineContext().ensureActive()
                val result = load(page)
                val lastPage = result.lastPage ?: page
                check(lastPage >= page) { "Search pagination did not advance. Try again." }
                val fresh = result.items.filter { it.id !in matches }
                check(result.items.isEmpty() || fresh.isNotEmpty()) { "Search returned the same results again. Try a narrower search." }
                fresh.forEach { matches[it.id] = it }
                if (matches.size > 2_000) throw ApiFailure(FailureKind.InvalidResponse,
                    "Too many matches to sort oldest first. Narrow your search or use Newest posts.")
                onProgress(matches.size)
                page = lastPage + 1
            } while (result.canLoadMore)
            sorted = matches.values.sortedWith(compareBy<Release> { it.publishedAt <= 0 }
                .thenBy { if (sort == SearchSort.Newest) -it.publishedAt else it.publishedAt }
                .thenByDescending { it.id })
        }
        val all = checkNotNull(sorted)
        val batch = all.drop(shown).take(20)
        shown += batch.size
        return ReleasePage(batch, shown < all.size)
    }

    private suspend fun newest(onProgress: (Int) -> Unit): ReleasePage {
        while (true) {
            currentCoroutineContext().ensureActive()
            val bound = nextActivityAtMost
            // A post cannot be newer than its latest activity. Hold older matches until later pages are ruled out.
            val ready = pending.values.filter { !sourceHasMore || (bound != null && it.publishedAt > bound) }
                .sortedWith(compareBy<Release> { it.publishedAt <= 0 }.thenByDescending { it.publishedAt }.thenByDescending { it.id })
                .take(20)
            if (ready.isNotEmpty() || !sourceHasMore) {
                ready.forEach { pending.remove(it.id) }
                ready.lastOrNull()?.let { lastShownDate = it.publishedAt }
                return ReleasePage(ready, sourceHasMore || pending.isNotEmpty())
            }
            val result = load(sourcePage)
            val lastPage = result.lastPage ?: sourcePage
            check(lastPage >= sourcePage) { "Search pagination did not advance. Try again." }
            val fresh = result.items.filter { it.id !in seen }
            val previousDate = lastShownDate
            if (previousDate != null && fresh.any { it.publishedAt > previousDate })
                throw ApiFailure(FailureKind.InvalidResponse, "Search results changed while loading. Search again to refresh the order.")
            check(result.items.isEmpty() || fresh.isNotEmpty()) { "Search returned the same results again. Try a narrower search." }
            fresh.forEach { seen.add(it.id); pending[it.id] = it }
            onProgress(seen.size)
            sourcePage = lastPage + 1
            sourceHasMore = result.canLoadMore
            nextActivityAtMost = result.nextActivityAtMost
        }
    }
}
