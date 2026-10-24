package org.mobilism.material.data

/** Bound sparse scans so users can cancel or continue instead of waiting through the whole archive. */
internal suspend fun filteredReleasePages(
    firstPage: Int,
    filters: FeedFilters,
    newestFirst: Boolean = false,
    excludedCategories: Set<Long> = emptySet(),
    load: suspend (Int) -> ReleasePage,
): ReleasePage {
    require(firstPage > 0 && filters.validDates)
    val seen = mutableSetOf<Long>()
    var page = firstPage
    var nextActivityAtMost: Long? = null
    repeat(5) {
        val source = load(page)
        nextActivityAtMost = source.nextActivityAtMost
        val fresh = source.items.filter { seen.add(it.id) }
        val matches = fresh.filter { it.categoryId !in excludedCategories && filters.matches(it) }
        val pastStart = filters.postedFrom != null && (
            (newestFirst && fresh.isNotEmpty() && fresh.all {
                it.publishedAt > 0 && java.time.Instant.ofEpochSecond(it.publishedAt)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate() < filters.postedFrom
            }) || source.nextActivityAtMost?.let {
                java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() < filters.postedFrom
            } == true)
        val more = source.canLoadMore && fresh.isNotEmpty() && !pastStart
        if (matches.isNotEmpty() || !more || (!filters.isActive && excludedCategories.isEmpty()))
            return ReleasePage(matches, more, page, nextActivityAtMost)
        page++
    }
    return ReleasePage(emptyList(), true, page - 1, nextActivityAtMost)
}
