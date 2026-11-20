package org.mobilism.material.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.mobilism.material.data.*

data class DiscussionListState(
    val items: List<DiscussionTopic> = emptyList(), val page: Int = 0, val busy: Boolean = false,
    val canLoadMore: Boolean = true, val error: ScreenError? = null, val refreshFailed: Boolean = false,
)

data class DiscussionsState(
    val area: DiscussionArea = DiscussionArea.Apps, val categories: List<DiscussionCategory> = emptyList(),
    val loading: Boolean = false, val error: ScreenError? = null, val category: DiscussionCategory? = null,
    val topics: DiscussionListState = DiscussionListState(), val query: String = "", val submittedQuery: String = "",
    val topic: DiscussionTopic? = null, val comments: CommentsState = CommentsState(),
    val commentRefreshFailed: Boolean = false, val composer: CommentComposerState = CommentComposerState(),
    val notice: String? = null, val forward: Boolean = true,
    val categoryActivity: Map<String, Long> = emptyMap(),
    val announcementsOnly: Boolean = false,
    val activityGeneration: Int = 0,
) {
    val destination: String get() = topic?.let { "topic:${it.key}" } ?: category?.let { "category:${it.key}" } ?: "categories"
}

class DiscussionsViewModel(
    private val repository: DiscussionsRepository, private val username: String?, private val saved: SavedStateHandle,
    initialTopic: DiscussionTopic? = null, initialCategory: DiscussionCategory? = null,
    private val announcementsOnly: Boolean = false,
    private val linkedAnnouncement: (suspend () -> LinkedAnnouncement)? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DiscussionsState(
        area = if (announcementsOnly) DiscussionArea.Community else initialCategory?.area
            ?: DiscussionArea.entries.firstOrNull { it.name == saved.get<String>("discussion-area") } ?: DiscussionArea.Apps,
        announcementsOnly = announcementsOnly,
    ))
    val state = mutableState.asStateFlow()
    private var categoriesJob: Job? = null
    private var topicsJob: Job? = null
    private var commentsJob: Job? = null
    private val confirmed = mutableMapOf<String, List<ReleaseComment>>()
    private val activityRequests = Semaphore(3)
    private val activityCache = mutableMapOf<String, Pair<Long, Long?>>()
    private val refreshActivityKeys = mutableSetOf<String>()

    init {
        if (linkedAnnouncement != null) loadAnnouncement() else loadCategories(refreshActivity = false)
        initialCategory?.let(::openCategory)
        initialTopic?.let(::openTopic)
    }

    fun selectArea(area: DiscussionArea) {
        if (announcementsOnly || state.value.area == area) return
        saved["discussion-area"] = area.name
        mutableState.update { it.copy(area = area, categories = emptyList(), categoryActivity = emptyMap(), error = null) }
        loadCategories(refreshActivity = false)
    }

    fun loadCategories() {
        if (linkedAnnouncement != null) loadAnnouncement() else loadCategories(refreshActivity = true)
    }

    private fun loadAnnouncement() {
        val load = linkedAnnouncement ?: return
        categoriesJob?.cancel()
        mutableState.update { it.copy(loading = true, error = null) }
        categoriesJob = viewModelScope.launch {
            try {
                val result = load()
                ensureActive()
                showTopic(result.topic, result.forumComments)
                mutableState.update { it.copy(loading = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutableState.update { it.copy(loading = false, error = failure.forScreen()) } }
        }
    }

    private fun loadCategories(refreshActivity: Boolean) {
        categoriesJob?.cancel()
        val area = state.value.area
        mutableState.update { it.copy(loading = true, error = null) }
        categoriesJob = viewModelScope.launch {
            try {
                val result = (if (refreshActivity) repository.refreshDiscussionCategories(area)
                    else repository.discussionCategories(area)).let { categories ->
                    if (announcementsOnly) categories.filter { it.section == "Announcements" } else categories
                }
                ensureActive()
                val cached = result.mapNotNull { category -> activityCache[category.key]?.second?.let { category.key to it } }.toMap()
                if (refreshActivity) {
                    activityCache.clear()
                    refreshActivityKeys.addAll(result.map { it.key })
                }
                mutableState.update { it.copy(categories = result, loading = false, categoryActivity = cached,
                    activityGeneration = it.activityGeneration + 1) }
                if (announcementsOnly && result.size == 1 && state.value.category == null) openCategory(result.single())
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutableState.update { it.copy(loading = false, error = failure.forScreen()) } }
        }
    }

    suspend fun loadActivity(category: DiscussionCategory) {
        if (category.topics == 0L || state.value.category != null || category !in state.value.categories) return
        val generation = state.value.activityGeneration
        activityRequests.withPermit {
            if (generation != state.value.activityGeneration || state.value.category != null || category !in state.value.categories) return@withPermit
            val checkedAt = activityCache[category.key]?.first
            if (checkedAt != null && System.currentTimeMillis() - checkedAt < 5 * 60_000) return@withPermit
            try {
                val timestamp = (if (category.key in refreshActivityKeys) repository.refreshDiscussionLastActivity(category)
                    else repository.discussionLastActivity(category))?.takeIf { it > 0 }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (generation != state.value.activityGeneration || category !in state.value.categories) return@withPermit
                refreshActivityKeys.remove(category.key)
                activityCache[category.key] = System.currentTimeMillis() to timestamp
                mutableState.update { it.copy(categoryActivity = if (timestamp == null) it.categoryActivity - category.key
                    else it.categoryActivity + (category.key to timestamp)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (generation == state.value.activityGeneration)
                    activityCache[category.key] = System.currentTimeMillis() to state.value.categoryActivity[category.key]
            }
        }
    }

    fun openCategory(category: DiscussionCategory) {
        if (state.value.composer.posting) return
        topicsJob?.cancel()
        mutableState.update { it.copy(category = category, topics = DiscussionListState(), query = "", submittedQuery = "", forward = true) }
        loadTopics()
    }

    fun editQuery(query: String) { mutableState.update { it.copy(query = query.take(200)) } }

    fun search() {
        topicsJob?.cancel()
        mutableState.update { it.copy(submittedQuery = it.query.trim(), topics = DiscussionListState()) }
        loadTopics()
    }

    fun clearSearch() { editQuery(""); search() }

    fun loadTopics(refresh: Boolean = false) {
        val snapshot = state.value
        val category = snapshot.category ?: return
        val restart = refresh || snapshot.topics.refreshFailed
        if (!restart && (snapshot.topics.busy || !snapshot.topics.canLoadMore)) return
        topicsJob?.cancel()
        mutableState.update { it.copy(topics = it.topics.copy(busy = true, error = null)) }
        topicsJob = viewModelScope.launch {
            try {
                val page = if (restart) 1 else snapshot.topics.page + 1
                val result = if (restart) repository.refreshDiscussionTopics(category, snapshot.submittedQuery)
                    else repository.discussionTopics(category, page, snapshot.submittedQuery)
                ensureActive()
                val previous = if (restart) emptyList() else snapshot.topics.items
                val items = (previous + result.items).distinctBy { it.id }
                mutableState.update { it.copy(topics = DiscussionListState(items, page,
                    canLoadMore = result.canLoadMore && (restart || items.size > previous.size))) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutableState.update { it.copy(topics = it.topics.copy(busy = false,
                error = failure.forScreen(), refreshFailed = restart)) } }
        }
    }

    fun openTopic(topic: DiscussionTopic) = showTopic(topic, null)

    private fun showTopic(topic: DiscussionTopic, forumComments: CommentPage?) {
        if (state.value.composer.posting) return
        commentsJob?.cancel()
        // Forum HTML starts with the oldest replies. An explicit next request starts at API page one.
        val comments = forumComments?.let { CommentsState(items = it.items, canLoadMore = it.canLoadMore) } ?: CommentsState()
        mutableState.update { it.copy(topic = topic, comments = comments, commentRefreshFailed = false,
            composer = CommentComposerState(draft = saved["draft:${topic.key}"] ?: "",
                replyTo = saved.get<Long>("reply:${topic.key}")?.let { id ->
                    CommentReplyTarget(id, saved["author:${topic.key}"] ?: "", saved["quote:${topic.key}"] ?: "")
                }, error = if (saved.get<Boolean>("pending:${topic.key}") == true) UNCONFIRMED else null),
            notice = null, forward = true) }
        if (forumComments == null) loadComments()
    }

    fun back(): Boolean {
        if (state.value.composer.posting) return true
        if (state.value.composer.open) { closeComposer(); return true }
        if (linkedAnnouncement != null) return false
        if (state.value.topic != null) {
            commentsJob?.cancel()
            mutableState.update { it.copy(topic = null, comments = CommentsState(), composer = CommentComposerState(), notice = null, forward = false) }
            return true
        }
        if (state.value.category != null) {
            if (announcementsOnly && state.value.categories.size == 1) return false
            topicsJob?.cancel()
            mutableState.update { it.copy(category = null, topics = DiscussionListState(), query = "", submittedQuery = "", forward = false) }
            return true
        }
        return false
    }

    fun loadComments(refresh: Boolean = false) {
        val snapshot = state.value
        val topic = snapshot.topic ?: return
        if (snapshot.composer.posting) return
        val restart = refresh || snapshot.commentRefreshFailed
        if (!restart && (snapshot.comments.busy || !snapshot.comments.canLoadMore)) return
        commentsJob?.cancel()
        mutableState.update { it.copy(comments = it.comments.copy(busy = true, error = null)) }
        commentsJob = viewModelScope.launch {
            try {
                val page = if (restart) 1 else snapshot.comments.page + 1
                val result = repository.discussionComments(topic, page)
                ensureActive()
                val previous = if (page == 1) emptyList() else snapshot.comments.items
                val serverItems = (previous + result.items).distinctBy { it.id }
                val items = (confirmed[topic.key].orEmpty() + serverItems).distinctBy { it.id }
                mutableState.update { it.copy(comments = CommentsState(items, page,
                    canLoadMore = result.canLoadMore && (restart || serverItems.any { item -> previous.none { it.id == item.id } })),
                    commentRefreshFailed = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutableState.update { it.copy(comments = it.comments.copy(busy = false,
                error = failure.forScreen()), commentRefreshFailed = restart) } }
        }
    }

    fun openComposer(reply: ReleaseComment? = null) {
        if (username == null || state.value.topic == null || state.value.composer.posting) return
        if (reply != null) setReply(CommentReplyTarget(reply.id, reply.author, reply.contentHtml))
        mutableState.update { it.copy(composer = it.composer.copy(open = true)) }
    }

    fun refreshManagedComment(commentId: Long) {
        val topic = state.value.topic ?: return
        if (state.value.composer.posting) return
        // Locally confirmed posts must not overwrite a later forum edit or restore a deleted comment.
        confirmed[topic.key] = confirmed[topic.key].orEmpty().filterNot { it.id == commentId }
        loadComments(refresh = true)
    }

    fun removeReply() = setReply(null)

    private fun setReply(reply: CommentReplyTarget?) {
        val key = state.value.topic?.key ?: return
        if (state.value.composer.posting) return
        saved["reply:$key"] = reply?.id
        saved["author:$key"] = reply?.author
        saved["quote:$key"] = reply?.html?.take(16_000)
        mutableState.update { it.copy(composer = it.composer.copy(replyTo = reply)) }
    }

    fun editDraft(text: String) {
        val key = state.value.topic?.key ?: return
        if (state.value.composer.posting || text.length > ReleaseComments.MAX_LENGTH) return
        saved["draft:$key"] = text
        mutableState.update { it.copy(composer = it.composer.copy(draft = text)) }
    }

    fun closeComposer() { if (!state.value.composer.posting) mutableState.update { it.copy(composer = it.composer.copy(open = false)) } }

    fun post() {
        val snapshot = state.value
        val topic = snapshot.topic ?: return
        val author = username ?: return
        val text = snapshot.composer.draft.trim()
        if (snapshot.composer.posting || text.isBlank() || text.length > ReleaseComments.MAX_LENGTH) return
        commentsJob?.cancel()
        saved["pending:${topic.key}"] = true
        mutableState.update { it.copy(composer = it.composer.copy(posting = true, error = null), comments = it.comments.copy(busy = false)) }
        viewModelScope.launch {
            try {
                val id = repository.postDiscussionComment(topic, text, snapshot.composer.replyTo?.id)
                if (id <= 0) throw ApiFailure(FailureKind.InvalidResponse, "The server did not confirm the comment.")
                val comment = ReleaseComments.posted(id, author, text, System.currentTimeMillis() / 1000).copy(
                    quotes = snapshot.composer.replyTo?.let { listOf(CommentQuote(it.author, it.html, it.id)) }.orEmpty())
                confirmed[topic.key] = listOf(comment) + confirmed[topic.key].orEmpty()
                listOf("draft", "pending", "reply", "author", "quote").forEach { saved.remove<Any>("$it:${topic.key}") }
                mutableState.update { it.copy(composer = CommentComposerState(), notice = "Comment posted",
                    comments = it.comments.copy(items = listOf(comment) + it.comments.items),
                    topic = topic.copy(replies = topic.replies + 1),
                    topics = it.topics.copy(items = it.topics.items.map { row -> if (row.key == topic.key) row.copy(replies = row.replies + 1) else row })) }
                loadComments(refresh = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                val rejected = failure is ApiFailure && (failure.httpStatus in 400..499 ||
                    failure.kind in setOf(FailureKind.Credentials, FailureKind.Session, FailureKind.Permission, FailureKind.Dns))
                if (rejected) saved.remove<Boolean>("pending:${topic.key}")
                mutableState.update { it.copy(composer = it.composer.copy(posting = false,
                    error = if (rejected) failure.forScreen().message else UNCONFIRMED)) }
            }
        }
    }

    fun noticeShown() { mutableState.update { it.copy(notice = null) } }

    private companion object {
        const val UNCONFIRMED = "Posting could not be confirmed. Your draft is kept. Check the comments before trying again to avoid a duplicate."
    }
}
