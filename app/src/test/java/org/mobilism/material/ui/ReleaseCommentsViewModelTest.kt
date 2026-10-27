package org.mobilism.material.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
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
class ReleaseCommentsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMain() = Dispatchers.resetMain()

    @Test fun postIsExplicitAndDuplicateTapsOnlySendOnce() = runTest(dispatcher) {
        val repo = Repository()
        val model = ReleaseViewModel(42, repo, SavedStateHandle())
        advanceUntilIdle()
        model.openComposer()
        model.editComment("  Thanks & hello\nSecond line  ")
        assertTrue(repo.posts.isEmpty())
        model.postComment()
        model.postComment()
        runCurrent()
        assertEquals(listOf(42L to "Thanks & hello\nSecond line"), repo.posts)
        assertTrue(model.state.value.composer.posting)
        model.refresh()
        assertEquals(1, repo.releaseRequests)
        model.editComment("changed while posting")
        model.closeComposer()
        assertTrue(model.state.value.composer.open)
        repo.result.complete(99)
        advanceUntilIdle()
        assertEquals("", model.state.value.composer.draft)
        assertFalse(model.state.value.composer.open)
        assertEquals("Comment posted", model.state.value.actionMessage)
        assertEquals(1, model.state.value.release!!.replies)
        assertEquals(99L, model.state.value.comments.items.first().id)
        assertEquals("Thanks &amp; hello<br>Second line", model.state.value.comments.items.first().contentHtml)
        assertEquals(listOf(1, 1), repo.pages)
    }

    @Test fun blankOverlongAndGuestCommentsCannotPost() = runTest(dispatcher) {
        val repo = Repository()
        val model = ReleaseViewModel(42, repo, SavedStateHandle())
        advanceUntilIdle()
        model.editComment(" \n ")
        model.postComment()
        model.editComment("x".repeat(ReleaseComments.MAX_LENGTH + 1))
        model.postComment()
        repo.auth.value = AuthState.Guest
        model.editComment("Guest text")
        model.openComposer()
        model.postComment()
        advanceUntilIdle()
        assertTrue(repo.posts.isEmpty())
        assertFalse(model.state.value.composer.open)
    }

    @Test fun uncertainDeliveryKeepsDraftAndNeverRetriesAutomatically() = runTest(dispatcher) {
        val repo = Repository()
        val model = ReleaseViewModel(42, repo, SavedStateHandle())
        advanceUntilIdle()
        model.openComposer()
        model.editComment("Keep this draft")
        model.postComment()
        repo.result.completeExceptionally(ApiFailure(FailureKind.Connection, "timeout"))
        advanceUntilIdle()
        assertEquals("Keep this draft", model.state.value.composer.draft)
        assertTrue(model.state.value.composer.error!!.contains("Check the comments"))
        assertTrue(model.state.value.composer.open)
        assertFalse(model.state.value.composer.posting)
        model.closeComposer()
        model.refreshComments()
        model.openComposer()
        advanceUntilIdle()
        assertEquals("Keep this draft", model.state.value.composer.draft)
        assertEquals(1, repo.posts.size)
    }

    @Test fun serverValidationShowsItsMessageAndKeepsDraft() = runTest(dispatcher) {
        for (status in listOf(200, 429)) {
            val repo = Repository()
            val model = ReleaseViewModel(42, repo, SavedStateHandle())
            advanceUntilIdle()
            model.editComment("Please keep me")
            model.postComment()
            repo.result.completeExceptionally(ApiFailure(FailureKind.Server, "Please wait before posting again.", status))
            advanceUntilIdle()
            assertEquals("Please wait before posting again.", model.state.value.composer.error)
            assertEquals("Please keep me", model.state.value.composer.draft)
            assertEquals(0, model.state.value.release!!.replies)
        }
    }

    @Test fun confirmedPostSurvivesRefreshFailureAndCannotBeResent() = runTest(dispatcher) {
        val repo = Repository()
        val model = ReleaseViewModel(42, repo, SavedStateHandle())
        advanceUntilIdle()
        model.editComment("Accepted")
        repo.commentFailure = ApiFailure(FailureKind.Connection, "Offline")
        model.postComment()
        repo.result.complete(99)
        advanceUntilIdle()
        model.postComment()
        advanceUntilIdle()
        assertEquals(99L, model.state.value.comments.items.single().id)
        assertNotNull(model.state.value.comments.error)
        assertEquals("", model.state.value.composer.draft)
        assertEquals(1, repo.posts.size)
    }

    @Test fun refreshDeduplicatesConfirmedCommentAndRestartsPagination() = runTest(dispatcher) {
        val repo = Repository()
        val model = ReleaseViewModel(42, repo, SavedStateHandle())
        advanceUntilIdle()
        model.editComment("Accepted")
        repo.comments = CommentPage(listOf(ReleaseComments.posted(99, "Reader", "Server content", 1)), true)
        model.postComment()
        repo.result.complete(99)
        advanceUntilIdle()
        assertEquals(1, model.state.value.comments.items.size)
        assertEquals("Server content", model.state.value.comments.items.single().contentHtml)
        model.loadComments()
        advanceUntilIdle()
        assertEquals(listOf(1, 1, 2), repo.pages)
        assertFalse(model.state.value.comments.canLoadMore)
    }

    @Test fun processRestoreKeepsDraftAndFlagsInterruptedSubmissionWithoutResending() = runTest(dispatcher) {
        val saved = SavedStateHandle(mapOf("comment-draft" to "Interrupted draft", "comment-open" to true,
            "comment-pending" to true))
        val repo = Repository()
        val model = ReleaseViewModel(42, repo, saved)
        advanceUntilIdle()
        assertEquals("Interrupted draft", model.state.value.composer.draft)
        assertTrue(model.state.value.composer.open)
        assertTrue(model.state.value.composer.error!!.contains("Check the comments"))
        assertFalse(model.state.value.composer.posting)
        assertTrue(repo.posts.isEmpty())
    }

    @Test fun oldPaginationCannotOverwriteThePostRefresh() = runTest(dispatcher) {
        val repo = Repository().apply {
            comments = CommentPage(listOf(ReleaseComments.posted(1, "Reader", "Older", 1)), true)
            pendingPageTwo = CompletableDeferred()
        }
        val model = ReleaseViewModel(42, repo, SavedStateHandle())
        advanceUntilIdle()
        model.loadComments()
        runCurrent()
        model.editComment("Newest")
        model.postComment()
        repo.comments = CommentPage(listOf(ReleaseComments.posted(99, "Reader", "Newest", 10)), false)
        repo.result.complete(99)
        advanceUntilIdle()
        repo.pendingPageTwo!!.complete(CommentPage(listOf(ReleaseComments.posted(888, "Reader", "Stale", 2)), true))
        advanceUntilIdle()
        assertEquals(listOf(99L), model.state.value.comments.items.map { it.id })
        assertEquals(1, model.state.value.comments.page)
        assertEquals(listOf(1, 2, 1), repo.pages)
    }

    @Test fun replyTargetSurvivesCloseAndRestoreAndPostsTheCommentId() = runTest(dispatcher) {
        val repo = Repository()
        val saved = SavedStateHandle()
        val model = ReleaseViewModel(42, repo, saved)
        advanceUntilIdle()
        model.editComment("My draft")
        model.replyToComment(ReleaseComments.posted(77, "Author", "Original text", 1))
        model.closeComposer()
        val restored = ReleaseViewModel(42, repo, saved)
        advanceUntilIdle()
        assertEquals(77L, restored.state.value.composer.replyTo!!.id)
        assertEquals("My draft", restored.state.value.composer.draft)
        restored.openComposer()
        restored.postComment()
        runCurrent()
        assertEquals(listOf(77L), repo.replyTargets)
        repo.result.complete(99)
        advanceUntilIdle()
        assertNull(restored.state.value.composer.replyTo)
        assertEquals("Author", restored.state.value.comments.items.first().quotes.single().author)
        assertNull(saved.get<Long>("comment-reply-id"))
    }

    @Test fun removingReplyPreservesDraftAndGuestCannotSelectTarget() = runTest(dispatcher) {
        val repo = Repository()
        val model = ReleaseViewModel(42, repo, SavedStateHandle())
        advanceUntilIdle()
        val comment = ReleaseComments.posted(77, "Author", "Original", 1)
        model.editComment("Keep this")
        model.replyToComment(comment)
        model.clearCommentReply()
        assertNull(model.state.value.composer.replyTo)
        assertEquals("Keep this", model.state.value.composer.draft)
        repo.auth.value = AuthState.Guest
        model.replyToComment(comment)
        assertNull(model.state.value.composer.replyTo)
    }

    @Test fun refreshReloadsReleaseAndFirstCommentPageWithoutLosingDraftOrAcceptingStalePagination() = runTest(dispatcher) {
        val repo = Repository()
        val original = ReleaseComments.posted(77, "Author", "Original", 1)
        repo.comments = CommentPage(listOf(original), true)
        val model = ReleaseViewModel(42, repo, SavedStateHandle())
        advanceUntilIdle()
        model.editComment("Unfinished reply")
        model.replyToComment(original)
        repo.pendingPageTwo = CompletableDeferred()
        model.loadComments()
        runCurrent()
        val fresh = ReleaseComments.posted(88, "Reader", "New comment", 2)
        repo.comments = CommentPage(listOf(fresh), false)
        repo.releaseTitle = "Updated release"
        model.refresh()
        model.refresh()
        advanceUntilIdle()
        assertEquals(2, repo.releaseRequests)
        assertEquals("Updated release", model.state.value.release!!.title)
        assertEquals(listOf(1, 2, 1), repo.pages)
        assertEquals(listOf(fresh), model.state.value.comments.items)
        assertEquals(1, model.state.value.comments.page)
        assertEquals("Unfinished reply", model.state.value.composer.draft)
        assertEquals(77L, model.state.value.composer.replyTo!!.id)
        repo.pendingPageTwo!!.complete(CommentPage(listOf(original), false))
        advanceUntilIdle()
        assertEquals(listOf(fresh), model.state.value.comments.items)
    }

    private class Repository : MobilismRepository {
        override val auth = MutableStateFlow<AuthState>(AuthState.SignedIn("Reader"))
        val posts = mutableListOf<Pair<Long, String>>()
        val replyTargets = mutableListOf<Long?>()
        val pages = mutableListOf<Int>()
        val result = CompletableDeferred<Long>()
        var comments = CommentPage(emptyList(), false)
        var commentFailure: Exception? = null
        var pendingPageTwo: CompletableDeferred<CommentPage>? = null
        var releaseRequests = 0
        var releaseTitle = "Sample release"
        override suspend fun postComment(id: Long, text: String, replyTo: Long?): Long {
            posts += id to text
            replyTargets += replyTo
            return result.await()
        }
        override suspend fun comments(id: Long, page: Int): CommentPage {
            pages += page
            commentFailure?.let { throw it }
            if (page == 2) pendingPageTwo?.let { return it.await() }
            return comments
        }
        override suspend fun release(id: Long): Release {
            releaseRequests++
            return Release(id, releaseTitle, "Author", null, 1, 0, 0)
        }
        override suspend fun toggleLike(release: Release) = true
        override suspend fun restoreSession() = Unit
        override suspend fun signIn(username: String, password: String) = Unit
        override suspend fun continueAsGuest() = Unit
        override suspend fun signOut() = Unit
        override suspend fun register(username: String, email: String, password: String) = Unit
        override suspend fun restorePassword(email: String) = Unit
        override suspend fun categories(section: ContentSection) = emptyList<ReleaseCategory>()
        override suspend fun releases(section: ContentSection, order: FeedOrder, page: Int, filters: FeedFilters): ReleasePage = error("Unused")
        override suspend fun search(query: String, scope: SearchScope, page: Int, filters: FeedFilters): ReleasePage = error("Unused")
    }
}
