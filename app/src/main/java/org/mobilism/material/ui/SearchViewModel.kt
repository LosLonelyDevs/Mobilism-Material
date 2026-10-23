package org.mobilism.material.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mobilism.material.data.MobilismRepository
import org.mobilism.material.data.Release
import org.mobilism.material.data.SearchScope
import org.mobilism.material.data.ContentSection
import org.mobilism.material.data.FeedFilters

data class SearchState(
    val query: String = "", val submitted: String = "", val scope: SearchScope = SearchScope.All,
    val items: List<Release> = emptyList(), val page: Int = 0, val busy: Boolean = false,
    val canLoadMore: Boolean = true, val error: ScreenError? = null,
    val filters: FeedFilters = FeedFilters(), val awaitingMore: Boolean = false,
)

class SearchViewModel(
    private val repository: MobilismRepository,
    initialScope: SearchScope,
    initialQuery: String = "",
    initialFilters: Map<ContentSection, FeedFilters> = emptyMap(),
    private val onFiltersChanged: (ContentSection, FeedFilters) -> Unit = { _, _ -> },
) : ViewModel() {
    private val savedFilters = SearchScope.entries.associateWith { initialFilters[it.section] ?: FeedFilters() }.toMutableMap()
    private val mutableState = MutableStateFlow(SearchState(scope = initialScope, query = initialQuery,
        filters = savedFilters.getValue(initialScope)))
    val state = mutableState.asStateFlow()
    private val mutableCategories = MutableStateFlow(CategoryState(section = initialScope.section ?: ContentSection.Apps))
    val categories = mutableCategories.asStateFlow()
    private var categoryJob: Job? = null
    private var job: Job? = null

    init { if (initialQuery.isNotBlank()) submit() }

    fun editQuery(value: String) { mutableState.update { it.copy(query = value) } }

    fun selectScope(scope: SearchScope) {
        if (scope == mutableState.value.scope) return
        val snapshot = mutableState.value
        job?.cancel()
        categoryJob?.cancel()
        mutableCategories.value = CategoryState(section = scope.section ?: ContentSection.Apps)
        mutableState.value = SearchState(query = snapshot.query, scope = scope, filters = savedFilters.getValue(scope))
        if (snapshot.submitted.isNotEmpty()) submit()
    }

    fun submit() {
        val snapshot = mutableState.value
        val query = snapshot.query.trim()
        job?.cancel()
        mutableState.value = SearchState(query = snapshot.query, submitted = query, scope = snapshot.scope, filters = snapshot.filters)
        if (query.isNotEmpty()) loadMore()
    }

    fun loadCategories() {
        val section = mutableState.value.scope.section ?: return
        if (categoryJob?.isActive == true || mutableCategories.value.items.isNotEmpty()) return
        categoryJob = viewModelScope.launch {
            mutableCategories.value = CategoryState(section = section, busy = true)
            try { mutableCategories.value = CategoryState(section, repository.categories(section)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableCategories.value = CategoryState(section, error = error.forScreen()) }
        }
    }

    fun applyFilters(filters: FeedFilters) {
        if (!filters.validDates || filters == mutableState.value.filters) return
        val snapshot = mutableState.value
        savedFilters[snapshot.scope] = filters
        snapshot.scope.section?.let { onFiltersChanged(it, filters) }
        job?.cancel()
        mutableState.value = SearchState(query = snapshot.query, submitted = snapshot.submitted, scope = snapshot.scope, filters = filters)
        if (snapshot.submitted.isNotBlank()) loadMore()
    }

    fun loadMore() {
        val snapshot = mutableState.value
        if (job?.isActive == true || snapshot.submitted.isBlank() || !snapshot.canLoadMore) return
        job = viewModelScope.launch {
            mutableState.update { it.copy(busy = true, error = null) }
            try {
                val page = repository.search(snapshot.submitted, snapshot.scope, snapshot.page + 1, snapshot.filters)
                val items = (snapshot.items + page.items).distinctBy { it.id }
                mutableState.update { it.copy(items = items, page = page.lastPage ?: (snapshot.page + 1), busy = false,
                    awaitingMore = page.items.isEmpty() && page.canLoadMore,
                    canLoadMore = page.canLoadMore && (page.items.isEmpty() || items.size > snapshot.items.size)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.update { it.copy(busy = false, error = error.forScreen()) } }
        }
    }
}
