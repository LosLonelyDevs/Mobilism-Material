package org.mobilism.material.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CustomCategorySearchPagerTest {
    private fun release(id: Long, name: String = "Alpha", version: String = "1.0", date: Long = id, views: Long = id) =
        Release(id, "$name v$version", "", null, date, 0, views, section = ContentSection.Apps)
    private fun member(vararg releases: Release) = CategorizedItem(releases.first().id.toString(), releases.toList(), setOf("apps"))

    @Test fun searchesAssignedAppNamesAndReturnsOlderAndNewerVersionsWithoutScanningTheGlobalFeed() = runBlocking {
        val calls = mutableListOf<Pair<String, Int>>()
        val items = listOf(member(release(10, version = "2.0")), member(release(20, "Beta")))
        val pager = CustomCategorySearchPager(items, ContentSection.Apps, FeedOrder.Latest,
            matches = { candidate -> items.any { it.matches(candidate) } }) { name, page ->
            calls += name to page
            ReleasePage(if (name == "Alpha") listOf(release(1), release(3, version = "3.0"), release(99, "Unrelated"))
                else listOf(release(2, "Beta")), false)
        }
        val result = pager.page(1)
        assertEquals(listOf(3L, 2L, 1L), result.items.map { it.id })
        assertFalse(result.canLoadMore)
        assertEquals(setOf("Alpha" to 1, "Beta" to 1), calls.toSet())
    }

    @Test fun emptyAndUnmatchedPagesContinueAutomaticallyPastFiveBatches() = runBlocking {
        val calls = mutableListOf<Int>()
        val pager = CustomCategorySearchPager(listOf(member(release(1))), ContentSection.Apps, FeedOrder.Latest,
            matches = { ReleaseVersions.sameApp(it, release(1)) }) { _, page ->
            calls += page
            when {
                page < 4 -> ReleasePage(emptyList(), true)
                page < 8 -> ReleasePage(listOf(release(page.toLong(), "Unrelated")), true)
                else -> ReleasePage(listOf(release(100)), false)
            }
        }
        assertEquals(listOf(100L), pager.page(1).items.map { it.id })
        assertEquals((1..8).toList(), calls)
    }

    @Test fun latestUsesActivityBoundsToKeepOldActiveThreadsBehindNewReleases() = runBlocking {
        val pager = CustomCategorySearchPager(listOf(member(release(1))), ContentSection.Apps, FeedOrder.Latest,
            pageSize = 1, matches = { true }) { _, page ->
            when (page) {
                1 -> ReleasePage(listOf(release(1, date = 10)), true, nextActivityAtMost = 100)
                2 -> ReleasePage(listOf(release(2, date = 90)), true, nextActivityAtMost = 80)
                else -> ReleasePage(emptyList(), false)
            }
        }
        assertEquals(2L, pager.page(1).items.single().id)
        assertEquals(1L, pager.page(2).items.single().id)
        assertFalse(pager.page(2).canLoadMore)
    }

    @Test fun topRanksAcrossEveryAppSearchPageAndDeduplicatesSharedResults() = runBlocking {
        val pager = CustomCategorySearchPager(listOf(member(release(1)), member(release(2, "Beta"))),
            ContentSection.Apps, FeedOrder.Top, pageSize = 2, matches = { true }) { _, page ->
            when (page) {
                1 -> ReleasePage(listOf(release(1, views = 10), release(2, "Beta", views = 50)), true)
                2 -> ReleasePage(listOf(release(3, views = 100)), false)
                else -> error("Unexpected page")
            }
        }
        assertEquals(listOf(3L, 2L), pager.page(1).items.map { it.id })
        assertEquals(listOf(1L), pager.page(2).items.map { it.id })
        assertFalse(pager.page(2).canLoadMore)
    }

    @Test fun failedOrCancelledSearchCanRetryWithoutSkippingResults() = runBlocking {
        for (failure in listOf(IllegalStateException("Offline"), CancellationException("Cancelled"))) {
            var fail = true
            val pager = CustomCategorySearchPager(listOf(member(release(1))), ContentSection.Apps, FeedOrder.Latest,
                matches = { true }) { _, page ->
                if (page == 2 && fail) { fail = false; throw failure }
                if (page == 1) ReleasePage(listOf(release(1)), true) else ReleasePage(listOf(release(2)), false)
            }
            val actual = runCatching { pager.page(1) }.exceptionOrNull()
            assertEquals(failure.javaClass, actual?.javaClass)
            assertEquals(failure.message, actual?.message)
            assertEquals(listOf(2L, 1L), pager.page(1).items.map { it.id })
        }
    }

    @Test fun retriesOfMergedNativePagesReplayTheSameCustomSearchPage() = runBlocking {
        var calls = 0
        var fail = true
        val search = CustomCategorySearchPager(listOf(member(release(1))), ContentSection.Apps, FeedOrder.Latest,
            matches = { true }) { _, _ -> calls++; ReleasePage(listOf(release(4)), false) }
        val merged = CategoryFeedPager(setOf(10), FeedOrder.Latest, localMatches = { true }) { category, page ->
            if (category == 0L) search.page(page)
            else if (page == 1) ReleasePage(listOf(release(3)), true)
            else if (fail) { fail = false; error("Offline") }
            else ReleasePage(listOf(release(1)), false)
        }
        assertTrue(runCatching { merged.nextPage() }.isFailure)
        assertEquals(listOf(4L, 3L, 1L), merged.nextPage().items.map { it.id })
        assertEquals(1, calls)
    }

    @Test fun repeatedPagesStopAndDuplicateNamesUseOneSearchWhileKnownAliasesRemainSearchable() = runBlocking {
        val calls = mutableListOf<Pair<String, Int>>()
        val items = listOf(member(release(1), release(2, version = "2.0"), release(3, "Renamed Alpha")),
            member(release(4, "Alpha").copy(section = ContentSection.Games)))
        val pager = CustomCategorySearchPager(items, ContentSection.Apps, FeedOrder.Latest, matches = { true }) { query, page ->
            calls += query to page
            ReleasePage(listOf(release(10)), true)
        }
        assertEquals(listOf(10L), pager.page(1).items.map { it.id })
        assertFalse(pager.page(1).canLoadMore)
        assertEquals(setOf("Alpha" to 1, "Alpha" to 2, "Renamed Alpha" to 1, "Renamed Alpha" to 2), calls.toSet())
    }
}
