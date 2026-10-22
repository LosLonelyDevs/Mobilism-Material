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

data class SearchState(
    val query: String = "", val submitted: String = "", val scope: SearchScope = SearchScope.All,
    val items: List<Release> = emptyList(), val page: Int = 0, val busy: Boolean = false,
    val canLoadMore: Boolean = true, val error: ScreenError? = null,
)

class SearchViewModel(private val repository: MobilismRepository, initialScope: SearchScope, initialQuery: String = "") : ViewModel() {
    private val mutableState = MutableStateFlow(SearchState(scope = initialScope, query = initialQuery))
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    init { if (initialQuery.isNotBlank()) submit() }

    fun editQuery(value: String) { mutableState.update { it.copy(query = value) } }

    fun selectScope(scope: SearchScope) {
        if (scope == mutableState.value.scope) return
        val snapshot = mutableState.value
        job?.cancel()
        mutableState.value = SearchState(query = snapshot.query, scope = scope)
        if (snapshot.submitted.isNotEmpty()) submit()
    }

    fun submit() {
        val snapshot = mutableState.value
        val query = snapshot.query.trim()
        job?.cancel()
        mutableState.value = SearchState(query = snapshot.query, submitted = query, scope = snapshot.scope)
        if (query.isNotEmpty()) loadMore()
    }

    fun loadMore() {
        val snapshot = mutableState.value
        if (job?.isActive == true || snapshot.submitted.isBlank() || !snapshot.canLoadMore) return
        job = viewModelScope.launch {
            mutableState.update { it.copy(busy = true, error = null) }
            try {
                val page = repository.search(snapshot.submitted, snapshot.scope, snapshot.page + 1)
                val items = (snapshot.items + page.items).distinctBy { it.id }
                mutableState.update { it.copy(items = items, page = snapshot.page + 1, busy = false,
                    canLoadMore = page.canLoadMore && items.size > snapshot.items.size) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.update { it.copy(busy = false, error = error.forScreen()) } }
        }
    }
}
