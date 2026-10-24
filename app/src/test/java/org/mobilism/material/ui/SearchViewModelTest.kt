package org.mobilism.material.ui

import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mobilism.material.data.*

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMain() = Dispatchers.resetMain()

    @Test fun changingContentVisibilityReplacesSearchResultsAndKeepsFilters() = runTest(dispatcher) {
        val filters = FeedFilters(postedFrom = LocalDate.of(2026, 10, 1))
        val hidden = MutableStateFlow(false)
        val repository = SearchRepository().apply {
            response = ReleasePage(listOf(Release(1, "Example v1", "", null, 2, 0, 0)), true, nextActivityAtMost = 1)
        }
        val model = SearchViewModel(repository, SearchScope.Apps, "Example",
            mapOf(ContentSection.Apps to filters), hideAdultContent = hidden)
        advanceUntilIdle()
        repository.response = ReleasePage(listOf(Release(2, "Another example v1", "", null, 1, 0, 0)), false)
        hidden.value = true
        advanceUntilIdle()
        assertEquals(2, repository.requests.size)
        assertEquals(1, repository.requests.last().page)
        assertEquals(filters, repository.requests.last().filters)
        assertEquals(listOf(2L), model.state.value.items.map { it.id })
    }

    @Test fun installedAppSearchAndResubmissionKeepActiveFilters() = runTest(dispatcher) {
        val filters = FeedFilters(setOf(10, 20), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20))
        val repository = SearchRepository()
        val model = SearchViewModel(repository, SearchScope.Apps, "Example", mapOf(ContentSection.Apps to filters))
        advanceUntilIdle()
        assertEquals(filters, repository.requests.single().filters)
        model.editQuery("Another example")
        model.submit()
        advanceUntilIdle()
        assertEquals(filters, repository.requests.last().filters)
        assertEquals(1, repository.requests.last().page)
    }

    @Test fun scopeChangesRestoreEachSectionsFiltersAndEditsAreSaved() = runTest(dispatcher) {
        val filters = FeedFilters(setOf(10))
        val saved = mutableListOf<Pair<ContentSection, FeedFilters>>()
        val model = SearchViewModel(SearchRepository(), SearchScope.Apps, initialFilters = mapOf(ContentSection.Apps to filters),
            onFiltersChanged = { section, value -> saved += section to value })
        model.selectScope(SearchScope.Books)
        assertEquals(FeedFilters(), model.state.value.filters)
        model.selectScope(SearchScope.Apps)
        assertEquals(filters, model.state.value.filters)
        val updated = filters.copy(postedThrough = LocalDate.of(2026, 10, 20))
        model.applyFilters(updated)
        assertEquals(listOf(ContentSection.Apps to updated), saved)
        model.selectScope(SearchScope.All)
        model.selectScope(SearchScope.Apps)
        assertEquals(updated, model.state.value.filters)
    }

    @Test fun anEmptyFilteredBatchDoesNotEndSearchOrRestartItsSourcePages() = runTest(dispatcher) {
        val repository = SearchRepository()
        repository.response = ReleasePage(emptyList(), true, lastPage = 5)
        val model = SearchViewModel(repository, SearchScope.Apps, "Example",
            initialFilters = mapOf(ContentSection.Apps to FeedFilters(searchSort = SearchSort.Activity)))
        advanceUntilIdle()
        assertTrue(model.state.value.canLoadMore)
        assertTrue(model.state.value.awaitingMore)
        repository.response = ReleasePage(listOf(Release(1, "Example v1", "", null, 1, 0, 0)), true, lastPage = 6)
        model.loadMore()
        advanceUntilIdle()
        assertEquals(6, repository.requests.last().page)
        assertFalse(model.state.value.awaitingMore)
        assertEquals(1L, model.state.value.items.single().id)
        model.applyFilters(FeedFilters(postedFrom = LocalDate.of(2026, 10, 1), searchSort = SearchSort.Activity))
        advanceUntilIdle()
        assertEquals(1, repository.requests.last().page)
    }

    private class SearchRepository : MobilismRepository {
        data class Request(val scope: SearchScope, val page: Int, val filters: FeedFilters)
        val requests = mutableListOf<Request>()
        var response = ReleasePage(emptyList(), false)
        override val auth = MutableStateFlow<AuthState>(AuthState.Guest)
        override suspend fun search(query: String, scope: SearchScope, page: Int, filters: FeedFilters): ReleasePage {
            requests += Request(scope, page, filters)
            return response
        }
        override suspend fun categories(section: ContentSection) = emptyList<ReleaseCategory>()
        override suspend fun restoreSession() = Unit
        override suspend fun signIn(username: String, password: String) = Unit
        override suspend fun continueAsGuest() = Unit
        override suspend fun signOut() = Unit
        override suspend fun register(username: String, email: String, password: String) = Unit
        override suspend fun restorePassword(email: String) = Unit
        override suspend fun releases(section: ContentSection, order: FeedOrder, page: Int, filters: FeedFilters) = error("Unused")
        override suspend fun release(id: Long): Release = error("Unused")
        override suspend fun toggleLike(release: Release): Boolean = error("Unused")
        override suspend fun comments(id: Long, page: Int): CommentPage = error("Unused")
    }
}
