package org.mobilism.material.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CategoryFeedPagerTest {
    private fun item(id: Long, date: Long = id, views: Long = id) =
        Release(id, "Release $id", "", null, date, 0, views)

    @Test fun fetchesTheNextSourcePageBeforeEmittingOlderCategories() = runBlocking {
        val streams = mapOf(1L to (100L downTo 71L).map { item(it) }, 2L to (20L downTo 11L).map { item(it) })
        val pager = CategoryFeedPager(setOf(1, 2), FeedOrder.Latest) { category, page ->
            val items = streams.getValue(category).drop((page - 1) * 10).take(10)
            ReleasePage(items, items.isNotEmpty())
        }
        assertEquals((100L downTo 81L).toList(), pager.nextPage().items.map { it.id })
        assertEquals((80L downTo 71L).toList() + (20L downTo 11L).toList(), pager.nextPage().items.map { it.id })
        assertFalse(pager.nextPage().canLoadMore)
    }

    @Test fun failedPageCanBeRetriedWithoutLosingItems() = runBlocking {
        var fail = true
        val pager = CategoryFeedPager(setOf(1), FeedOrder.Latest, pageSize = 3) { _, page ->
            if (page == 2 && fail) { fail = false; error("Temporary failure") }
            val items = listOf(item(5), item(4), item(3), item(2)).drop((page - 1) * 2).take(2)
            ReleasePage(items, items.isNotEmpty())
        }
        assertTrue(runCatching { pager.nextPage() }.isFailure)
        assertEquals(listOf(5L, 4L, 3L), pager.nextPage().items.map { it.id })
        assertEquals(listOf(2L), pager.nextPage().items.map { it.id })
    }

    @Test fun repeatedPagesAndOverlappingCategoriesDoNotRepeatOrLoop() = runBlocking {
        var calls = 0
        val pager = CategoryFeedPager(setOf(1, 2), FeedOrder.Top) { _, _ ->
            calls++
            ReleasePage(listOf(item(1, views = 50), item(2, views = 100)), true)
        }
        val page = pager.nextPage()
        assertEquals(listOf(2L, 1L), page.items.map { it.id })
        assertFalse(page.canLoadMore)
        assertEquals(4, calls)
    }

    @Test fun localAndNativeSourcesMergeWithoutDependingOnRowCategoryMetadata() = runBlocking {
        for (order in FeedOrder.entries) {
            val native = listOf(item(3), item(1))
            val all = (5L downTo 1L).map { item(it) }
            val pager = CategoryFeedPager(setOf(10), order, localMatches = { it.id == 4L || it.id == 2L || it.id == 3L }) { source, page ->
                val items = (if (source == 0L) all else native).drop((page - 1) * 2).take(2)
                ReleasePage(items, items.isNotEmpty())
            }
            val page = pager.nextPage()
            assertEquals(listOf(4L, 3L, 2L, 1L), page.items.map { it.id })
            assertFalse(page.canLoadMore)
        }
    }

    @Test fun sparseLocalSourcesPauseBeforeEmittingPastUnknownOrderingAndResume() = runBlocking {
        val calls = mutableListOf<Int>()
        val pager = CategoryFeedPager(setOf(10), FeedOrder.Latest, localMatches = { it.id == 94L }) { source, page ->
            if (source != 0L) ReleasePage(listOf(item(1)), false)
            else {
                calls += page
                ReleasePage(listOf(item(100L - page)), page < 6)
            }
        }
        val first = pager.nextPage()
        assertTrue(first.items.isEmpty())
        assertTrue(first.canLoadMore)
        assertEquals((1..5).toList(), calls)
        val next = pager.nextPage()
        assertEquals(listOf(94L, 1L), next.items.map { it.id })
        assertFalse(next.canLoadMore)
    }

    @Test fun sparsePagerPagesStayResumableThroughDateFiltering() = runBlocking {
        val first = filteredReleasePages(1, FeedFilters()) { ReleasePage(emptyList(), true) }
        assertTrue(first.items.isEmpty())
        assertTrue(first.canLoadMore)
        assertEquals(1, first.lastPage)
    }
}
