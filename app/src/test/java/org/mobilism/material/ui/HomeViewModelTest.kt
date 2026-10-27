package org.mobilism.material.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mobilism.material.data.*

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun collectionsFailIndependentlyAndRetryOnlyTheFailedCollection() = runTest(dispatcher) {
        val repo = Repository().apply { failPopular = true }
        val model = HomeViewModel(repo, ContentSection.Apps)
        advanceUntilIdle()
        assertEquals(1, model.state.value.latest.items.size)
        assertNotNull(model.state.value.popular.error)
        assertTrue(model.state.value.categories.loaded)
        assertTrue(model.state.value.reviews.loaded)
        assertFalse(model.state.value.busy)
        repo.failPopular = false
        model.retry(HomeCollection.Popular)
        advanceUntilIdle()
        assertNull(model.state.value.popular.error)
        assertEquals(1, repo.calls.count { it == "Apps-Latest" })
        assertEquals(2, repo.calls.count { it == "Apps-Top" })
    }

    @Test fun switchingSectionsCachesCompletedCollectionsAndRejectsLateResults() = runTest(dispatcher) {
        val repo = Repository().apply { slowApps = true }
        val model = HomeViewModel(repo, ContentSection.Apps)
        runCurrent()
        model.selectSection(ContentSection.Books)
        advanceUntilIdle()
        assertEquals(ContentSection.Books, model.state.value.section)
        assertEquals(ContentSection.Books, model.state.value.latest.items.single().section)
        assertNull(model.state.value.popular.error)
        repo.slowApps = false
        model.selectSection(ContentSection.Games)
        advanceUntilIdle()
        val count = repo.calls.size
        model.selectSection(ContentSection.Books)
        advanceUntilIdle()
        assertEquals(count, repo.calls.size)
        assertEquals(ContentSection.Books, model.state.value.latest.items.single().section)
    }

    @Test fun adultPreferenceChangeInvalidatesEverySectionIncludingBeforeCollectorStarts() = runTest(dispatcher) {
        val hidden = MutableStateFlow(false)
        val repo = Repository()
        val model = HomeViewModel(repo, ContentSection.Apps, hidden)
        hidden.value = true
        advanceUntilIdle()
        assertEquals(2, repo.calls.count { it == "Apps-Latest" })
        model.selectSection(ContentSection.Books)
        advanceUntilIdle()
        hidden.value = false
        advanceUntilIdle()
        model.selectSection(ContentSection.Apps)
        advanceUntilIdle()
        assertEquals(3, repo.calls.count { it == "Apps-Latest" })
    }

    private class Repository : HomeRepository {
        val calls = mutableListOf<String>()
        var failPopular = false
        var slowApps = false
        override suspend fun homeReleases(section: ContentSection, order: FeedOrder): List<Release> {
            calls += "$section-$order"
            if (slowApps && section == ContentSection.Apps) withContext(NonCancellable) {
                delay(100)
                if (order == FeedOrder.Top) error("Old cancelled request failed")
            }
            if (failPopular && order == FeedOrder.Top) error("Offline")
            val release = Release(section.ordinal.toLong() + 1, section.label, "Member", null, 0, 0, 20, section = section)
            return listOf(release, release)
        }
        override suspend fun homeCategories(section: ContentSection) = listOf(ReleaseCategory(1, "Utilities"))
        override suspend fun homeReviews(section: ContentSection): HomeTopics? = null
        override suspend fun homeDiscussions(section: ContentSection) = HomeTopics(
            DiscussionCategory(12, "Recent discussions", DiscussionArea.valueOf(section.name)), emptyList())
    }
}
