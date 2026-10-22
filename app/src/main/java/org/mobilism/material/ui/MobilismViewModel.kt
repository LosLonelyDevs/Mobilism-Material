package org.mobilism.material.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mobilism.material.data.ApiFailure
import org.mobilism.material.data.AuthState
import org.mobilism.material.data.ContentSection
import org.mobilism.material.data.DebridRepository
import org.mobilism.material.data.FailureKind
import org.mobilism.material.data.FeedOrder
import org.mobilism.material.data.FeedFilters
import org.mobilism.material.data.ReleaseCategory
import org.mobilism.material.data.MobilismRepository
import org.mobilism.material.data.Release
import org.mobilism.material.settings.AppearanceStore
import org.mobilism.material.settings.DownloadPreferencesStore

data class ScreenError(val message: String, val showNetworkSettings: Boolean = false)

internal fun Exception.forScreen(): ScreenError = when (this) {
    is ApiFailure -> ScreenError(message, kind == FailureKind.Permission || kind == FailureKind.Dns)
    else -> ScreenError("Something went wrong. Please try again.")
}

data class LoginState(val busy: Boolean = false, val error: ScreenError? = null)

data class FeedState(
    val section: ContentSection = ContentSection.Apps,
    val order: FeedOrder = FeedOrder.Latest,
    val items: List<Release> = emptyList(),
    val page: Int = 0,
    val busy: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: ScreenError? = null,
    val filters: FeedFilters = FeedFilters(),
)

data class CategoryState(
    val section: ContentSection = ContentSection.Apps,
    val items: List<ReleaseCategory> = emptyList(),
    val busy: Boolean = false,
    val error: ScreenError? = null,
)

class MobilismViewModel(
    context: Context,
    val repository: MobilismRepository,
    val appearance: AppearanceStore,
    val debrid: DebridRepository,
    val library: org.mobilism.material.data.AppLibraryStore,
) : ViewModel() {
    val downloads = DownloadPreferencesStore(context)
    private val preferences = context.getSharedPreferences("material-navigation", Context.MODE_PRIVATE)
    val preferredSection: ContentSection?
        get() = ContentSection.entries.firstOrNull { it.name == preferences.getString("section", null) }
    val auth = repository.auth
    private val mutableLogin = MutableStateFlow(LoginState())
    val login = mutableLogin.asStateFlow()
    private val mutableFeed = MutableStateFlow(newFeed(preferredSection ?: ContentSection.Apps))
    val feed = mutableFeed.asStateFlow()
    private val mutableCategories = MutableStateFlow(CategoryState(section = mutableFeed.value.section))
    val categories = mutableCategories.asStateFlow()
    private var categoryJob: Job? = null
    private var feedJob: Job? = null
    private var loginJob: Job? = null

    init {
        viewModelScope.launch {
            try { debrid.load() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Settings exposes retry and key replacement. */ }
        }
        viewModelScope.launch {
            try { appearance.load() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Keep the bundled theme if local preferences are unreadable. */ }
        }
        viewModelScope.launch {
            auth.collect { state ->
                if (state == AuthState.SignedOut) {
                    feedJob?.cancel()
                    categoryJob?.cancel()
                    mutableFeed.value = newFeed(preferredSection ?: ContentSection.Apps)
                    mutableCategories.value = CategoryState(section = mutableFeed.value.section)
                }
            }
        }
        viewModelScope.launch {
            try { repository.restoreSession() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableLogin.value = LoginState(error = error.forScreen()) }
        }
    }

    fun signIn(username: String, password: String, onSuccess: () -> Unit = {}) = authAction {
        if (username.isBlank() || password.isEmpty()) {
            mutableLogin.value = LoginState(error = ScreenError("Enter your username and password."))
        } else {
            repository.signIn(username, password)
            onSuccess()
        }
    }

    fun continueAsGuest() = authAction { repository.continueAsGuest() }

    private fun authAction(block: suspend () -> Unit) {
        if (loginJob?.isActive == true) return
        loginJob = viewModelScope.launch {
            mutableLogin.value = LoginState(busy = true)
            try {
                block()
                mutableLogin.update { it.copy(busy = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableLogin.value = LoginState(error = error.forScreen()) }
        }
    }

    fun signOut() {
        feedJob?.cancel()
        categoryJob?.cancel()
        authAction {
            repository.signOut()
            mutableFeed.value = newFeed(mutableFeed.value.section)
        }
    }

    fun selectSection(section: ContentSection) {
        preferences.edit().putString("section", section.name).apply()
        feedJob?.cancel()
        categoryJob?.cancel()
        mutableCategories.value = CategoryState(section = section)
        mutableFeed.value = newFeed(section, mutableFeed.value.order)
        loadFeed(refresh = true)
    }

    fun selectOrder(order: FeedOrder) {
        if (mutableFeed.value.order == order) return
        feedJob?.cancel()
        mutableFeed.value = FeedState(section = mutableFeed.value.section, order = order, filters = mutableFeed.value.filters)
        loadFeed(refresh = true)
    }

    private fun newFeed(section: ContentSection, order: FeedOrder = FeedOrder.Latest): FeedState {
        val ids = preferences.getStringSet("filters-" + section.name + "-categories", emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull()?.takeIf { id -> id > 0 } }.toSet()
        return FeedState(section = section, order = order, filters = FeedFilters(ids))
    }

    fun loadCategories() {
        val section = mutableFeed.value.section
        if (categoryJob?.isActive == true ||
            (mutableCategories.value.section == section && mutableCategories.value.items.isNotEmpty() &&
                mutableCategories.value.error == null)) return
        categoryJob = viewModelScope.launch {
            mutableCategories.value = CategoryState(section = section, busy = true)
            try {
                val items = repository.categories(section)
                if (mutableFeed.value.section == section) mutableCategories.value = CategoryState(section, items)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (mutableFeed.value.section == section) {
                    mutableCategories.value = CategoryState(section, error = error.forScreen())
                }
            }
        }
    }

    fun applyFilters(filters: FeedFilters) {
        val snapshot = mutableFeed.value
        val allowed = mutableCategories.value.takeIf { it.section == snapshot.section }
            ?.items.orEmpty().map { it.id }.toSet()
        val selected = if (allowed.isEmpty()) filters.categoryIds else filters.categoryIds.intersect(allowed)
        val normalized = filters.copy(categoryIds = if (selected == allowed) emptySet() else selected)
        if (normalized == snapshot.filters) return
        preferences.edit().putStringSet("filters-" + snapshot.section.name + "-categories",
            normalized.categoryIds.map { it.toString() }.toSet()).apply()
        feedJob?.cancel()
        mutableFeed.value = FeedState(section = snapshot.section, order = snapshot.order, filters = normalized)
        loadFeed(refresh = true)
    }

    fun loadFeed(refresh: Boolean = false) {
        if (auth.value == AuthState.SignedOut || auth.value == AuthState.Restoring) return
        if (!refresh && (feedJob?.isActive == true || !mutableFeed.value.canLoadMore)) return
        feedJob?.cancel()
        val snapshot = mutableFeed.value
        val page = if (refresh) 1 else snapshot.page + 1
        feedJob = viewModelScope.launch {
            mutableFeed.update { it.copy(busy = true, error = null) }
            try {
                val result = repository.releases(snapshot.section, snapshot.order, page, snapshot.filters)
                val existing = if (refresh) emptyList() else snapshot.items
                val combined = (existing + result.items).distinctBy { it.id }
                mutableFeed.update { it.copy(
                    items = combined, page = page, busy = false,
                    canLoadMore = result.canLoadMore && (refresh || combined.size > existing.size),
                ) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableFeed.update { it.copy(busy = false, error = error.forScreen()) }
            }
        }
    }
}

data class CommentsState(
    val items: List<org.mobilism.material.data.ReleaseComment> = emptyList(), val page: Int = 0,
    val busy: Boolean = false, val canLoadMore: Boolean = true, val error: ScreenError? = null,
)
data class ReleaseState(val release: Release? = null, val busy: Boolean = true, val error: ScreenError? = null,
    val comments: CommentsState = CommentsState(), val likeBusy: Boolean = false, val actionMessage: String? = null)

class ReleaseViewModel(private val id: Long, private val repository: MobilismRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(ReleaseState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var commentsJob: Job? = null
    private var likeJob: Job? = null

    init { load() }

    fun toggleLike() {
        val release = mutableState.value.release ?: return
        if (likeJob?.isActive == true || mutableState.value.busy) return
        likeJob = viewModelScope.launch {
            mutableState.update { it.copy(likeBusy = true, actionMessage = null) }
            try {
                val liked = repository.toggleLike(release)
                mutableState.update { it.copy(release = it.release?.copy(liked = liked),
                    actionMessage = if (liked) "Thumbs up added" else "Thumbs up removed") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.update { it.copy(actionMessage = error.forScreen().message) } }
            finally { mutableState.update { it.copy(likeBusy = false) } }
        }
    }

    fun actionMessageShown() { mutableState.update { it.copy(actionMessage = null) } }

    fun load() {
        if (likeJob?.isActive == true) return
        job?.cancel()
        job = viewModelScope.launch {
            mutableState.update { it.copy(busy = true, error = null) }
            try {
                val release = repository.release(id)
                mutableState.update { it.copy(release = release, busy = false) }
                if (mutableState.value.comments.page == 0) loadComments()
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.value = ReleaseState(busy = false, error = error.forScreen()) }
        }
    }

    fun loadComments() {
        val snapshot = mutableState.value.comments
        if (commentsJob?.isActive == true || !snapshot.canLoadMore) return
        commentsJob = viewModelScope.launch {
            mutableState.update { it.copy(comments = snapshot.copy(busy = true, error = null)) }
            try {
                val result = repository.comments(id, snapshot.page + 1)
                val items = (snapshot.items + result.items).distinctBy { it.id }
                mutableState.update { it.copy(comments = CommentsState(
                    items = items, page = snapshot.page + 1,
                    canLoadMore = result.canLoadMore && items.size > snapshot.items.size,
                )) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableState.update { it.copy(comments = snapshot.copy(error = error.forScreen())) }
            }
        }
    }
}
