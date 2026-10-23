package org.mobilism.material.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FeedFiltersTest {
    private val date = LocalDate.of(2026, 10, 10)
    private fun release(id: Long, timestamp: String = "2026-10-10T12:00:00Z", category: Long? = 10) =
        Release(id, "Example v1.0", "", null, Instant.parse(timestamp).epochSecond, 0, 0, categoryId = category)

    @Test fun hiddenCategoriesDoNotEndPaginationWithoutOtherFilters() = runBlocking {
        val result = filteredReleasePages(1, FeedFilters(), excludedCategories = setOf(20)) { page ->
            ReleasePage(listOf(release(page.toLong(), category = if (page < 3) 20 else 10)), true)
        }
        assertEquals(listOf(3L), result.items.map { it.id })
        assertEquals(3, result.lastPage)
        assertTrue(result.canLoadMore)
    }

    @Test fun hiddenCategoriesCombineWithSelectedCategoriesAndDates() = runBlocking {
        val result = filteredReleasePages(1, FeedFilters(setOf(10, 20), date, date), excludedCategories = setOf(20)) {
            ReleasePage(listOf(release(1, category = 20), release(2, category = 30),
                release(3, "2026-10-11T12:00:00Z"), release(4)), false)
        }
        assertEquals(listOf(4L), result.items.map { it.id })
        assertFalse(result.canLoadMore)
    }

    @Test fun dateBoundsIncludeTheWholeLocalDayAndRespectCategories() {
        val filters = FeedFilters(setOf(10), date, date)
        val zone = ZoneId.of("America/New_York")
        assertFalse(filters.matches(release(1, "2026-10-10T03:59:59Z"), zone))
        assertTrue(filters.matches(release(1, "2026-10-10T04:00:00Z"), zone))
        assertTrue(filters.matches(release(1, "2026-10-11T03:59:59Z"), zone))
        assertFalse(filters.matches(release(1, "2026-10-11T04:00:00Z"), zone))
        assertFalse(filters.matches(release(1, category = 20), zone))
        assertFalse(filters.matches(release(1, category = null), zone))
        assertFalse(filters.matches(release(1).copy(publishedAt = 0), zone))
        assertTrue(FeedFilters().matches(release(1).copy(publishedAt = 0), zone))
        assertFalse(filters.copy(postedThrough = date.minusDays(1)).validDates)
    }

    @Test fun searchUsesOriginalPostDateAndForumCategoryFromRelativeLinks() {
        val parsed = SearchJson.page(JSONObject("""{"releases":[
            {"id":1,"subject":"Example v1.0","date":1700000000,"topic_last_post_time":1900000000,
            "browser_url":"/viewtopic.php?f=10&t=1","section":"app"}
        ]}""")).items.single()
        assertEquals(10L, parsed.categoryId)
        assertEquals("https://forum.mobilism.org/viewtopic.php?f=10&t=1", parsed.browserUrl)
        assertFalse(FeedFilters(postedFrom = date).matches(parsed))
        assertTrue(FeedFilters(categoryIds = setOf(10)).matches(parsed))
    }

    @Test fun skipsUnmatchedPagesAndReturnsTheRealContinuation() = runBlocking {
        val calls = mutableListOf<Int>()
        val result = filteredReleasePages(1, FeedFilters(categoryIds = setOf(10))) { page ->
            calls += page
            ReleasePage(listOf(release(page.toLong(), category = if (page == 3) 10 else 20)), true)
        }
        assertEquals(listOf(1, 2, 3), calls)
        assertEquals(listOf(3L), result.items.map { it.id })
        assertEquals(3, result.lastPage)
        assertTrue(result.canLoadMore)
    }

    @Test fun sparseResultsStayResumableAndRepeatedPagesStop() = runBlocking {
        val filters = FeedFilters(categoryIds = setOf(10))
        val first = filteredReleasePages(1, filters) { page -> ReleasePage(listOf(release(page.toLong(), category = 20)), true) }
        assertTrue(first.items.isEmpty())
        assertTrue(first.canLoadMore)
        assertEquals(5, first.lastPage)
        val next = filteredReleasePages(first.lastPage!! + 1, filters) { page -> ReleasePage(listOf(release(page.toLong())), true) }
        assertEquals(6L, next.items.single().id)
        val repeated = filteredReleasePages(1, filters) { ReleasePage(listOf(release(1, category = 20)), true) }
        assertFalse(repeated.canLoadMore)
        assertEquals(2, repeated.lastPage)
    }

    @Test fun onlyDateSortedFeedsCanStopAtTheLowerBound() = runBlocking {
        val filters = FeedFilters(postedFrom = date)
        suspend fun load(page: Int) = ReleasePage(listOf(release(page.toLong(),
            if (page == 1) "2026-10-01T12:00:00Z" else "2026-10-12T12:00:00Z")), true)
        val feed = filteredReleasePages(1, filters, newestFirst = true, load = ::load)
        assertFalse(feed.canLoadMore)
        assertTrue(feed.items.isEmpty())
        val search = filteredReleasePages(1, filters, load = ::load)
        assertEquals(2L, search.items.single().id)
    }

    @Test fun errorsDoNotReturnPartialResultsAndCancellationPropagates() = runBlocking {
        val filters = FeedFilters(categoryIds = setOf(10))
        val error = runCatching { filteredReleasePages(1, filters) { page ->
            if (page == 2) throw kotlinx.coroutines.CancellationException("Cancelled")
            ReleasePage(listOf(release(1, category = 20)), true)
        } }.exceptionOrNull()
        assertTrue(error is kotlinx.coroutines.CancellationException)
    }
}
