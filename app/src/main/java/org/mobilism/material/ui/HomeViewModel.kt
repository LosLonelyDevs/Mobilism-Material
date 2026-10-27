package org.mobilism.material.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mobilism.material.data.ContentSection
import org.mobilism.material.data.FeedOrder
import org.mobilism.material.data.HomeRepository
import org.mobilism.material.data.HomeTopics
import org.mobilism.material.data.Release
import org.mobilism.material.data.ReleaseCategory

enum class BrowseTab(val label: String) { Home("Home"), Latest("Latest"), Top("Top") }
enum class HomeCollection { Latest, Popular, Categories, Reviews, Discussions }

data class HomeShelf<T>(val items: List<T> = emptyList(), val busy: Boolean = false,
    val error: ScreenError? = null, val loaded: Boolean = false)

data class HomeForum(val topics: HomeTopics? = null, val busy: Boolean = false,
    val error: ScreenError? = null, val loaded: Boolean = false)

data class HomeState(
    val section: ContentSection = ContentSection.Apps,
    val latest: HomeShelf<Release> = HomeShelf(), val popular: HomeShelf<Release> = HomeShelf(),
    val categories: HomeShelf<ReleaseCategory> = HomeShelf(), val reviews: HomeForum = HomeForum(),
    val discussions: HomeForum = HomeForum(),
) {
    val busy: Boolean get() = latest.busy || popular.busy || categories.busy || reviews.busy || discussions.busy
}

class HomeViewModel(private val repository: HomeRepository, initialSection: ContentSection,
    hideAdult: StateFlow<Boolean> = MutableStateFlow(false)) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeState(initialSection))
    val state = mutableState.asStateFlow()
    private val cache = mutableMapOf<ContentSection, HomeState>()
    private val jobs = mutableMapOf<HomeCollection, Job>()
    private var adultFilter = hideAdult.value

    init {
        refresh()
        viewModelScope.launch {
            hideAdult.collect { hidden ->
                if (hidden == adultFilter) return@collect
                adultFilter = hidden
                cancelLoads()
                cache.clear()
                mutableState.value = HomeState(state.value.section)
                refresh()
            }
        }
    }

    fun selectSection(section: ContentSection) {
        if (section == state.value.section) return
        cancelLoads()
        cache[state.value.section] = state.value.let { it.copy(latest = it.latest.copy(busy = false),
            popular = it.popular.copy(busy = false), categories = it.categories.copy(busy = false),
            reviews = it.reviews.copy(busy = false), discussions = it.discussions.copy(busy = false)) }
        mutableState.value = cache[section] ?: HomeState(section)
        HomeCollection.entries.filterNot(::loaded).forEach(::retry)
    }

    fun refresh() = HomeCollection.entries.forEach(::retry)

    fun retry(collection: HomeCollection) {
        jobs.remove(collection)?.cancel()
        val section = state.value.section
        mark(collection, busy = true)
        jobs[collection] = viewModelScope.launch {
            try {
                when (collection) {
                    HomeCollection.Latest, HomeCollection.Popular -> {
                        val order = if (collection == HomeCollection.Latest) FeedOrder.Latest else FeedOrder.Top
                        val items = repository.homeReleases(section, order).distinctBy { it.id }
                        ensureActive()
                        mutableState.update { if (collection == HomeCollection.Latest) it.copy(latest = HomeShelf(items, loaded = true))
                            else it.copy(popular = HomeShelf(items, loaded = true)) }
                    }
                    HomeCollection.Categories -> {
                        val items = repository.homeCategories(section).distinctBy { it.id }
                        ensureActive()
                        mutableState.update { it.copy(categories = HomeShelf(items, loaded = true)) }
                    }
                    HomeCollection.Reviews -> {
                        val topics = repository.homeReviews(section)
                        ensureActive()
                        mutableState.update { it.copy(reviews = HomeForum(topics, loaded = true)) }
                    }
                    HomeCollection.Discussions -> {
                        val topics = repository.homeDiscussions(section)
                        ensureActive()
                        mutableState.update { it.copy(discussions = HomeForum(topics, loaded = true)) }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                ensureActive()
                mark(collection, busy = false, error = error.forScreen())
            }
        }
    }

    private fun loaded(collection: HomeCollection) = with(state.value) {
        when (collection) {
            HomeCollection.Latest -> latest.loaded
            HomeCollection.Popular -> popular.loaded
            HomeCollection.Categories -> categories.loaded
            HomeCollection.Reviews -> reviews.loaded
            HomeCollection.Discussions -> discussions.loaded
        }
    }

    private fun mark(collection: HomeCollection, busy: Boolean, error: ScreenError? = null) {
        mutableState.update { when (collection) {
            HomeCollection.Latest -> it.copy(latest = it.latest.copy(busy = busy, error = error))
            HomeCollection.Popular -> it.copy(popular = it.popular.copy(busy = busy, error = error))
            HomeCollection.Categories -> it.copy(categories = it.categories.copy(busy = busy, error = error))
            HomeCollection.Reviews -> it.copy(reviews = it.reviews.copy(busy = busy, error = error))
            HomeCollection.Discussions -> it.copy(discussions = it.discussions.copy(busy = busy, error = error))
        } }
    }

    private fun cancelLoads() { jobs.values.forEach(Job::cancel); jobs.clear() }
}
