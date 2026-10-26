package org.mobilism.material.ui


import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
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
import org.mobilism.material.data.ReleaseComments
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
    val awaitingMore: Boolean = false,
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
    val contentPreferences: org.mobilism.material.data.ContentPreferencesStore = org.mobilism.material.data.ContentPreferencesStore(context),
    val developer: org.mobilism.material.settings.DeveloperPreferencesStore = org.mobilism.material.settings.DeveloperPreferencesStore(context),
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
    var releaseAfterSignIn: Long? = null
        private set

    fun signInForRelease(id: Long) {
        releaseAfterSignIn = id
        signOut()
    }

    fun releaseSignInHandled() { releaseAfterSignIn = null }

    init {
        var previousVisibility = contentPreferences.hideAdult.value
        viewModelScope.launch {
            contentPreferences.hideAdult.collect { hidden ->
                if (hidden == previousVisibility) return@collect
                previousVisibility = hidden
                feedJob?.cancel()
                categoryJob?.cancel()
                mutableCategories.value = CategoryState(section = mutableFeed.value.section)
                mutableFeed.value = newFeed(mutableFeed.value.section, mutableFeed.value.order)
                loadFeed(refresh = true)
            }
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

    fun continueAsGuest() = authAction {
        repository.continueAsGuest()
        releaseAfterSignIn = null
    }

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

    fun filtersFor(section: ContentSection): FeedFilters {
        val ids = preferences.getStringSet("filters-" + section.name + "-categories", emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull()?.takeIf { id -> id > 0 } }.toSet()
        fun date(key: String) = preferences.getString("filters-${section.name}-$key", null)?.let {
            runCatching { java.time.LocalDate.parse(it) }.getOrNull()
        }
        val sort = runCatching { org.mobilism.material.data.SearchSort.valueOf(
            preferences.getString("filters-${section.name}-search-sort", null).orEmpty())
        }.getOrDefault(org.mobilism.material.data.SearchSort.Newest)
        return FeedFilters(ids, date("from"), date("through"), sort)
    }

    private fun newFeed(section: ContentSection, order: FeedOrder = FeedOrder.Latest) =
        FeedState(section = section, order = order, filters = filtersFor(section))

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
        saveFilters(snapshot.section, normalized)
    }

    fun saveFilters(section: ContentSection, filters: FeedFilters) {
        if (!filters.validDates || filters == filtersFor(section)) return
        preferences.edit().putStringSet("filters-${section.name}-categories", filters.categoryIds.map { it.toString() }.toSet())
            .putString("filters-${section.name}-from", filters.postedFrom?.toString())
            .putString("filters-${section.name}-through", filters.postedThrough?.toString())
            .putString("filters-${section.name}-search-sort", filters.searchSort.name).apply()
        val snapshot = mutableFeed.value
        if (snapshot.section != section) return
        feedJob?.cancel()
        mutableFeed.value = FeedState(section = section, order = snapshot.order, filters = filters)
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
                    items = combined, page = result.lastPage ?: page, busy = false,
                    awaitingMore = result.items.isEmpty() && result.canLoadMore,
                    canLoadMore = result.canLoadMore && (result.items.isEmpty() || refresh || combined.size > existing.size),
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
data class CommentComposerState(
    val open: Boolean = false, val draft: String = "", val posting: Boolean = false,
    val error: String? = null, val replyTo: CommentReplyTarget? = null,
)

data class CommentReplyTarget(val id: Long, val author: String, val html: String)

data class ReleaseState(val release: Release? = null, val busy: Boolean = true, val error: ScreenError? = null,
    val comments: CommentsState = CommentsState(), val likeBusy: Boolean = false, val actionMessage: String? = null,
    val composer: CommentComposerState = CommentComposerState())

class ReleaseViewModel(private val id: Long, private val repository: MobilismRepository,
    private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(ReleaseState(composer = CommentComposerState(
        draft = savedState["comment-draft"] ?: "",
        open = savedState["comment-open"] ?: false,
        error = if (savedState.get<Boolean>("comment-pending") == true) UNCONFIRMED_COMMENT else null,
        replyTo = savedState.get<Long>("comment-reply-id")?.let {
            CommentReplyTarget(it, savedState["comment-reply-author"] ?: "", savedState["comment-reply-html"] ?: "")
        },
    )))
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var commentsJob: Job? = null
    private var likeJob: Job? = null
    private var postJob: Job? = null

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

    fun openComposer() {
        if (repository.auth.value !is AuthState.SignedIn) return
        savedState["comment-open"] = true
        mutableState.update { it.copy(composer = it.composer.copy(open = true)) }
    }

    fun closeComposer() {
        if (mutableState.value.composer.posting) return
        savedState["comment-open"] = false
        mutableState.update { it.copy(composer = it.composer.copy(open = false)) }
    }

    fun replyToComment(comment: org.mobilism.material.data.ReleaseComment) {
        if (repository.auth.value !is AuthState.SignedIn || mutableState.value.composer.posting) return
        val target = CommentReplyTarget(comment.id, comment.author, comment.contentHtml.take(20_000))
        savedState["comment-reply-id"] = target.id
        savedState["comment-reply-author"] = target.author
        savedState["comment-reply-html"] = target.html
        mutableState.update { it.copy(composer = it.composer.copy(replyTo = target)) }
        openComposer()
    }

    fun clearCommentReply() {
        if (mutableState.value.composer.posting) return
        savedState.remove<Long>("comment-reply-id")
        savedState.remove<String>("comment-reply-author")
        savedState.remove<String>("comment-reply-html")
        mutableState.update { it.copy(composer = it.composer.copy(replyTo = null)) }
    }

    fun editComment(text: String) {
        if (mutableState.value.composer.posting || text.length > ReleaseComments.MAX_LENGTH) return
        savedState["comment-draft"] = text
        mutableState.update { it.copy(composer = it.composer.copy(draft = text)) }
    }

    fun postComment() {
        val snapshot = mutableState.value
        val author = (repository.auth.value as? AuthState.SignedIn)?.username ?: return
        val text = snapshot.composer.draft.trim()
        if (snapshot.release == null || snapshot.busy || snapshot.composer.posting || postJob?.isActive == true ||
            text.isBlank() || text.length > ReleaseComments.MAX_LENGTH) return
        savedState["comment-pending"] = true
        mutableState.update { it.copy(composer = it.composer.copy(posting = true, error = null)) }
        postJob = viewModelScope.launch {
            try {
                val target = snapshot.composer.replyTo
                val commentId = repository.postComment(id, text, target?.id)
                val posted = ReleaseComments.posted(commentId, author, text, System.currentTimeMillis() / 1000).copy(
                    quotes = target?.let { listOf(org.mobilism.material.data.CommentQuote(it.author, it.html)) }.orEmpty())
                commentsJob?.cancel()
                commentsJob = null
                savedState["comment-draft"] = ""
                savedState["comment-open"] = false
                savedState["comment-pending"] = false
                savedState.remove<Long>("comment-reply-id")
                savedState.remove<String>("comment-reply-author")
                savedState.remove<String>("comment-reply-html")
                mutableState.update { it.copy(
                    composer = CommentComposerState(), actionMessage = "Comment posted",
                    release = it.release?.copy(replies = it.release.replies + 1),
                    comments = CommentsState(items = listOf(posted)),
                ) }
                loadComments()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                // A successful HTTP status can still carry the API's explicit error response.
                val rejected = error is ApiFailure && (error.httpStatus in 200..299 || error.httpStatus in 400..499 || error.kind in setOf(
                    FailureKind.Credentials, FailureKind.Session, FailureKind.Permission, FailureKind.Dns))
                if (rejected) savedState["comment-pending"] = false
                val message = if (rejected) error.forScreen().message else UNCONFIRMED_COMMENT
                mutableState.update { it.copy(composer = it.composer.copy(error = message)) }
            } finally {
                mutableState.update { it.copy(composer = it.composer.copy(posting = false)) }
            }
        }
    }

    fun refreshComments() {
        if (mutableState.value.composer.posting) return
        commentsJob?.cancel()
        commentsJob = null
        mutableState.update { it.copy(comments = CommentsState()) }
        loadComments()
    }

    fun load() {
        if (likeJob?.isActive == true || mutableState.value.composer.posting) return
        job?.cancel()
        job = viewModelScope.launch {
            mutableState.update { it.copy(busy = true, error = null) }
            try {
                val release = repository.release(id)
                mutableState.update { it.copy(release = release, busy = false) }
                if (mutableState.value.comments.page == 0) loadComments()
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.update { it.copy(busy = false, error = error.forScreen()) } }
        }
    }

    fun loadComments() {
        val snapshot = mutableState.value.comments
        if (commentsJob?.isActive == true || !snapshot.canLoadMore) return
        commentsJob = viewModelScope.launch {
            mutableState.update { it.copy(comments = snapshot.copy(busy = true, error = null)) }
            try {
                val result = repository.comments(id, snapshot.page + 1)
                val items = if (snapshot.page == 0) (result.items + snapshot.items).distinctBy { it.id }.sortedByDescending { it.publishedAt }
                    else (snapshot.items + result.items).distinctBy { it.id }
                mutableState.update { it.copy(comments = CommentsState(
                    items = items, page = snapshot.page + 1,
                    canLoadMore = result.canLoadMore && (snapshot.page == 0 || items.size > snapshot.items.size),
                )) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableState.update { it.copy(comments = snapshot.copy(error = error.forScreen())) }
            }
        }
    }

    private companion object {
        const val UNCONFIRMED_COMMENT = "Posting could not be confirmed. Your draft is kept. Check the comments before posting again to avoid a duplicate."
    }
}
