package org.mobilism.material.data

/** Bound sparse scans so users can cancel or continue instead of waiting through the whole archive. */
internal suspend fun filteredReleasePages(
    firstPage: Int,
    filters: FeedFilters,
    newestFirst: Boolean = false,
    load: suspend (Int) -> ReleasePage,
): ReleasePage {
    require(firstPage > 0 && filters.validDates)
    val seen = mutableSetOf<Long>()
    var page = firstPage
    repeat(5) {
        val source = load(page)
        val fresh = source.items.filter { seen.add(it.id) }
        val matches = fresh.filter { filters.matches(it) }
        val pastStart = newestFirst && filters.postedFrom != null && fresh.isNotEmpty() && fresh.all {
            it.publishedAt > 0 && java.time.Instant.ofEpochSecond(it.publishedAt)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate() < filters.postedFrom
        }
        val more = source.canLoadMore && fresh.isNotEmpty() && !pastStart
        if (matches.isNotEmpty() || !more || !filters.isActive) return ReleasePage(matches, more, page)
        page++
    }
    return ReleasePage(emptyList(), true, page - 1)
}
