package org.mobilism.material.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.Release
import org.mobilism.material.data.ReleasePage
import org.mobilism.material.data.SearchSort

class SortedSearchPagerTest {
    private fun release(id: Long, date: Long) = Release(id, "Sample v1", "", null, date, 0, 0)

    @Test fun newestSortChecksLaterSourcePagesBeforeShowingResults() = runTest {
        val requests = mutableListOf<Int>()
        val progress = mutableListOf<Int>()
        val pager = SortedSearchPager(SearchSort.Newest) { page ->
            requests += page
            when (page) {
                1 -> ReleasePage((1L..20L).map { release(it, it) }, true)
                2 -> ReleasePage(listOf(release(100, 100), release(1, 1), release(200, 0)), false)
                else -> error("Unexpected page")
            }
        }
        val first = pager.next(progress::add)
        assertEquals(listOf(1, 2), requests)
        assertEquals(100L, first.items.first().id)
        assertEquals(20, first.items.size)
        assertTrue(first.canLoadMore)
        val last = pager.next(progress::add)
        assertEquals(listOf(1L, 200L), last.items.map { it.id })
        assertFalse(last.canLoadMore)
        assertEquals(listOf(1, 2), requests)
        assertEquals(listOf(20, 22), progress)
    }

    @Test fun oldestSortKeepsUnknownDatesLastAndUsesFilteredSourceCursor() = runTest {
        val pages = mutableListOf<Int>()
        val pager = SortedSearchPager(SearchSort.Oldest) { page ->
            pages += page
            if (page == 1) ReleasePage(emptyList(), true, lastPage = 5)
            else ReleasePage(listOf(release(3, 30), release(2, 20), release(1, 0)), false, lastPage = 6)
        }
        assertEquals(listOf(2L, 3L, 1L), pager.next {}.items.map { it.id })
        assertEquals(listOf(1, 6), pages)
    }

    @Test fun newestStreamsOnlyWhenLaterPagesCannotContainNewerPosts() = runTest {
        val requests = mutableListOf<Int>()
        val pager = SortedSearchPager(SearchSort.Newest) { page ->
            requests += page
            when (page) {
                1 -> ReleasePage(listOf(release(1, 100), release(2, 5)), true, nextActivityAtMost = 90)
                2 -> ReleasePage(listOf(release(3, 80), release(4, 20)), true, nextActivityAtMost = 70)
                3 -> ReleasePage(listOf(release(5, 60)), false)
                else -> error("Unexpected page")
            }
        }
        val first = pager.next {}
        assertEquals(listOf(1L), first.items.map { it.id })
        assertEquals(listOf(1), requests)
        val second = pager.next {}
        assertEquals(listOf(3L), second.items.map { it.id })
        assertEquals(listOf(1, 2), requests)
        val last = pager.next {}
        assertEquals(listOf(5L, 4L, 2L), last.items.map { it.id })
        assertFalse(last.canLoadMore)
        assertEquals(listOf(100L, 80L, 60L, 20L, 5L), (first.items + second.items + last.items).map { it.publishedAt })
    }

    @Test fun activitySortKeepsStreamingWithoutScanningAhead() = runTest {
        val pages = mutableListOf<Int>()
        val pager = SortedSearchPager(SearchSort.Activity) { page ->
            pages += page
            ReleasePage(listOf(release(page.toLong(), page.toLong())), true, lastPage = page + 4)
        }
        assertEquals(1L, pager.next {}.items.single().id)
        assertEquals(listOf(1), pages)
        assertEquals(6L, pager.next {}.items.single().id)
    }

    @Test fun changedServerOrderCannotAppendNewerPostsBelowOlderOnes() = runTest {
        val pager = SortedSearchPager(SearchSort.Newest) { page ->
            if (page == 1) ReleasePage(listOf(release(1, 100)), true, nextActivityAtMost = 90)
            else ReleasePage(listOf(release(2, 110)), false)
        }
        assertEquals(100L, pager.next {}.items.single().publishedAt)
        try { pager.next {}; fail("Expected refresh request") }
        catch (failure: org.mobilism.material.data.ApiFailure) {
            assertTrue(failure.message.contains("Search again"))
        }
    }

    @Test fun cancellationDoesNotPublishPartiallySortedResults() = runTest {
        val pager = SortedSearchPager(SearchSort.Newest) { page ->
            if (page == 1) ReleasePage(listOf(release(1, 1)), true) else throw CancellationException()
        }
        try { pager.next {}; fail("Expected cancellation") } catch (_: CancellationException) { }
    }
}
