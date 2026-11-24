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

enum class BrowseTab(@androidx.annotation.StringRes val labelRes: Int) {
    Home(org.mobilism.material.R.string.browse_tab_home),
    Latest(org.mobilism.material.R.string.browse_tab_latest),
    Top(org.mobilism.material.R.string.browse_tab_top),
}
enum class HomeCollection { Latest, Popular, Categories, Reviews, Discussions, Discover }

data class HomeShelf<T>(val items: List<T> = emptyList(), val busy: Boolean = false,
    val error: ScreenError? = null, val loaded: Boolean = false)

data class HomeForum(val topics: HomeTopics? = null, val busy: Boolean = false,
    val error: ScreenError? = null, val loaded: Boolean = false)

data class HomeState(
    val section: ContentSection = ContentSection.Apps,
    val latest: HomeShelf<Release> = HomeShelf(), val popular: HomeShelf<Release> = HomeShelf(),
    val categories: HomeShelf<ReleaseCategory> = HomeShelf(), val reviews: HomeForum = HomeForum(),
    val discussions: HomeForum = HomeForum(), val newReleaseIds: Set<Long> = emptySet(),
    val discovery: HomeShelf<Release> = HomeShelf(), val day: Int = 0, val refreshing: Boolean = false,
) {
    val busy: Boolean get() = latest.busy || popular.busy || categories.busy || reviews.busy || discussions.busy || discovery.busy
}

class HomeViewModel(private val repository: HomeRepository, initialSection: ContentSection,
    hideAdult: StateFlow<Boolean> = MutableStateFlow(false),
    private val observeNewReleases: (ContentSection, List<Release>) -> Set<Long> = { _, _ -> emptySet() },
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val discover: suspend (Boolean, Boolean) -> List<Release> = { _, _ -> emptyList() },
    private val localDay: () -> Int = { java.time.LocalDate.now().toEpochDay().toInt() }) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeState(initialSection, day = localDay()))
    val state = mutableState.asStateFlow()
    private val cache = mutableMapOf<ContentSection, HomeState>()
    private val jobs = mutableMapOf<HomeCollection, Job>()
    private data class ShelfStamp(val generation: Int, val loadedAt: Long, val cachedAgeMs: Long)
    private val releaseStamps = mutableMapOf<Pair<ContentSection, FeedOrder>, ShelfStamp>()
    private var adultFilter = hideAdult.value
    private var visible = false
    private var discoveryStamp: ShelfStamp? = null

    init {
        viewModelScope.launch {
            hideAdult.collect { hidden ->
                if (hidden == adultFilter) return@collect
                adultFilter = hidden
                cancelLoads()
                cache.clear()
                releaseStamps.clear()
                discoveryStamp = null
                mutableState.value = HomeState(state.value.section, day = localDay())
                if (visible) HomeCollection.entries.forEach(::retry)
            }
        }
    }

    fun selectSection(section: ContentSection) {
        if (section == state.value.section) return
        cancelLoads()
        cache[state.value.section] = state.value.let { it.copy(latest = it.latest.copy(busy = false),
            popular = it.popular.copy(busy = false), categories = it.categories.copy(busy = false),
            reviews = it.reviews.copy(busy = false), discussions = it.discussions.copy(busy = false), discovery = it.discovery.copy(busy = false), refreshing = false) }
        mutableState.value = cache[section] ?: HomeState(section)
        if (visible) { updateDay(); loadMissing() }
    }

    fun updateDay() {
        val today = localDay()
        if (today != state.value.day) mutableState.update { it.copy(day = today) }
    }

    fun setVisible(value: Boolean) {
        if (value) updateDay()
        visible = value
        if (value) loadMissing()
        else {
            cancelLoads()
            HomeCollection.entries.forEach { mark(it, busy = false) }
            mutableState.update { it.copy(refreshing = false) }
        }
    }

    private fun loadMissing() = HomeCollection.entries.filter { !loaded(it) && jobs[it]?.isActive != true }
        .forEach { load(it, fresh = false) }

    fun refresh() {
        if (state.value.busy) return
        HomeCollection.entries.forEach(::retry)
        mutableState.update { it.copy(refreshing = it.busy) }
    }

    fun retry(collection: HomeCollection) = load(collection, fresh = true)

    private fun load(collection: HomeCollection, fresh: Boolean) {
        val section = state.value.section
        if (collection == HomeCollection.Discover && section != ContentSection.Apps) return
        jobs.remove(collection)?.cancel()
        mark(collection, busy = true)
        jobs[collection] = viewModelScope.launch {
            try {
                when (collection) {
                    HomeCollection.Discover -> {
                        val generation = repository.feedPolicy(FeedOrder.Top).generation
                        val items = discover(adultFilter, fresh)
                        ensureActive()
                        discoveryStamp = ShelfStamp(generation, now(), 0)
                        mutableState.update { it.copy(discovery = HomeShelf(items, loaded = true)) }
                    }
                    HomeCollection.Latest, HomeCollection.Popular -> {
                        val order = if (collection == HomeCollection.Latest) FeedOrder.Latest else FeedOrder.Top
                        val generation = repository.feedPolicy(order).generation
                        val page = repository.homeReleasePage(section, order, fresh)
                        val items = page.items.distinctBy { it.id }
                        ensureActive()
                        releaseStamps[section to order] = ShelfStamp(generation, now(), page.cachedAgeMs)
                        val newReleaseIds = if (collection == HomeCollection.Latest) observeNewReleases(section, items) else emptySet()
                        mutableState.update { if (collection == HomeCollection.Latest) it.copy(latest = HomeShelf(items, loaded = true),
                            newReleaseIds = newReleaseIds)
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
                if (collection == HomeCollection.Discover) discoveryStamp = ShelfStamp(repository.feedPolicy(FeedOrder.Top).generation, now(), 0)
                mark(collection, busy = false, error = error.forScreen())
            } finally {
                mutableState.update { if (it.refreshing && !it.busy) it.copy(refreshing = false) else it }
            }
        }
    }

    private fun loaded(collection: HomeCollection) = with(state.value) {
        when (collection) {
            HomeCollection.Discover -> section != ContentSection.Apps || discoveryStamp?.let { stamp ->
                val policy = repository.feedPolicy(FeedOrder.Top)
                stamp.generation == policy.generation && now() - stamp.loadedAt in 0 until policy.ttlMs
            } == true
            HomeCollection.Latest, HomeCollection.Popular -> {
                val order = if (collection == HomeCollection.Latest) FeedOrder.Latest else FeedOrder.Top
                val policy = repository.feedPolicy(order)
                releaseStamps[section to order]?.let { stamp ->
                    val age = now() - stamp.loadedAt + (if (policy.expireOnReturn) stamp.cachedAgeMs else 0L)
                    stamp.generation == policy.generation && (!policy.expireHome || age in 0 until policy.ttlMs)
                } == true
            }
            HomeCollection.Categories -> categories.loaded
            HomeCollection.Reviews -> reviews.loaded
            HomeCollection.Discussions -> discussions.loaded
        }
    }

    private fun mark(collection: HomeCollection, busy: Boolean, error: ScreenError? = null) {
        mutableState.update { when (collection) {
            HomeCollection.Discover -> it.copy(discovery = if (error != null) HomeShelf(error = error)
                else it.discovery.copy(busy = busy))
            HomeCollection.Latest -> it.copy(latest = it.latest.copy(busy = busy, error = error))
            HomeCollection.Popular -> it.copy(popular = it.popular.copy(busy = busy, error = error))
            HomeCollection.Categories -> it.copy(categories = it.categories.copy(busy = busy, error = error))
            HomeCollection.Reviews -> it.copy(reviews = it.reviews.copy(busy = busy, error = error))
            HomeCollection.Discussions -> it.copy(discussions = it.discussions.copy(busy = busy, error = error))
        } }
    }

    private fun cancelLoads() { jobs.values.forEach(Job::cancel); jobs.clear() }
}
