package org.mobilism.material.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mobilism.material.data.*

@OptIn(ExperimentalCoroutinesApi::class)
class DiscussionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun paginationDeduplicatesAndFailedRefreshRetriesFirstPage() = runTest(dispatcher) {
        val repo = Repository()
        val model = model(repo)
        model.openCategory(category)
        advanceUntilIdle()
        model.loadTopics()
        advanceUntilIdle()
        assertEquals(1, model.state.value.topics.items.size)
        assertFalse(model.state.value.topics.canLoadMore)
        repo.fail = true
        model.loadTopics(true)
        advanceUntilIdle()
        assertTrue(model.state.value.topics.refreshFailed)
        assertEquals(1, model.state.value.topics.items.size)
        repo.fail = false
        model.loadTopics()
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 1, 1), repo.pages)
        assertNull(model.state.value.topics.error)
    }

    @Test fun newerCategoryAndSearchCancelStaleLoads() = runTest(dispatcher) {
        val repo = Repository()
        repo.wait = CompletableDeferred()
        val model = model(repo)
        model.openCategory(category)
        runCurrent()
        val old = repo.wait!!
        repo.wait = null
        model.openCategory(category.copy(id = 66, name = "Second"))
        advanceUntilIdle()
        old.complete(DiscussionPage(listOf(topic.copy(id = 999)), false))
        advanceUntilIdle()
        assertEquals(66L, model.state.value.category?.id)
        assertEquals(80L, model.state.value.topics.items.single().id)
        model.editQuery("  help  ")
        model.search()
        advanceUntilIdle()
        assertEquals("help", repo.queries.last())
        model.clearSearch()
        advanceUntilIdle()
        assertEquals("", repo.queries.last())
    }

    @Test fun draftsAndReplyTargetsBelongToTheirTopicAndSurviveViewModelRecreation() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val repo = Repository()
        val model = model(repo, saved = saved)
        model.openTopic(topic)
        model.openComposer(comment)
        model.editDraft("First draft")
        model.closeComposer()
        model.back()
        model.openTopic(topic.copy(id = 81))
        assertEquals("", model.state.value.composer.draft)
        model.editDraft("Second draft")
        val restored = model(repo, saved = saved)
        restored.openTopic(topic)
        assertEquals("First draft", restored.state.value.composer.draft)
        assertEquals(44L, restored.state.value.composer.replyTo?.id)
        restored.removeReply()
        assertNull(restored.state.value.composer.replyTo)
        advanceUntilIdle()
    }

    @Test fun guestCannotPostAndDoubleTapCannotDuplicateARequest() = runTest(dispatcher) {
        val repo = Repository()
        val guest = model(repo, username = null)
        guest.openTopic(topic)
        guest.editDraft("Guest text")
        guest.openComposer()
        guest.post()
        advanceUntilIdle()
        assertEquals(0, repo.posts)
        assertFalse(guest.state.value.composer.open)
        val member = model(repo)
        member.openTopic(topic)
        member.openComposer(comment)
        member.editDraft("Reply")
        member.post()
        member.post()
        runCurrent()
        assertTrue(member.back())
        assertNotNull(member.state.value.topic)
        assertEquals(1, repo.posts)
        assertEquals(44L, repo.replyTo)
        repo.postResult.complete(101)
        advanceUntilIdle()
        assertEquals("", member.state.value.composer.draft)
        assertEquals(101L, member.state.value.comments.items.first().id)
        assertEquals("Comment posted", member.state.value.notice)
    }

    @Test fun uncertainPostKeepsDraftAndWarningWhenReopened() = runTest(dispatcher) {
        val repo = Repository()
        repo.postResult.completeExceptionally(ApiFailure(FailureKind.Connection, "Disconnected"))
        val saved = SavedStateHandle()
        val model = model(repo, saved = saved)
        model.openTopic(topic)
        model.editDraft("Keep this")
        model.post()
        advanceUntilIdle()
        assertEquals("Keep this", model.state.value.composer.draft)
        assertTrue(model.state.value.composer.error!!.contains("could not be confirmed"))
        assertNull(model.state.value.notice)
        val restored = model(repo, saved = saved)
        restored.openTopic(topic)
        assertTrue(restored.state.value.composer.error!!.contains("avoid a duplicate"))
        advanceUntilIdle()
    }

    @Test fun commentRefreshKeepsConfirmedPostAndBackIgnoresLateResponse() = runTest(dispatcher) {
        val repo = Repository()
        val model = model(repo)
        model.openCategory(category)
        model.openTopic(topic)
        advanceUntilIdle()
        model.loadComments()
        advanceUntilIdle()
        assertFalse(model.state.value.comments.canLoadMore)
        model.back()
        assertNull(model.state.value.topic)
        assertNotNull(model.state.value.category)
        assertEquals(1, model.state.value.topics.items.size)
        model.back()
        assertNull(model.state.value.category)
        assertFalse(model.back())
    }

    private fun model(repo: Repository, username: String? = "Reader", saved: SavedStateHandle = SavedStateHandle()) =
        DiscussionsViewModel(repo, username, saved)

    private class Repository : DiscussionsRepository {
        val pages = mutableListOf<Int>()
        val queries = mutableListOf<String>()
        var posts = 0
        var fail = false
        var replyTo: Long? = null
        var wait: CompletableDeferred<DiscussionPage>? = null
        val postResult = CompletableDeferred<Long>()
        override suspend fun discussionCategories(area: DiscussionArea) = listOf(category.copy(area = area))
        override suspend fun discussionTopics(category: DiscussionCategory, page: Int, query: String): DiscussionPage {
            pages += page; queries += query
            if (fail) throw ApiFailure(FailureKind.Connection, "Offline")
            return wait?.await() ?: DiscussionPage(listOf(topic), true)
        }
        override suspend fun discussionComments(topic: DiscussionTopic, page: Int) = CommentPage(listOf(comment), true)
        override suspend fun postDiscussionComment(topic: DiscussionTopic, text: String, replyTo: Long?): Long {
            posts++; this.replyTo = replyTo
            return postResult.await()
        }
    }

    companion object {
        private val category = DiscussionCategory(500, "Help", DiscussionArea.Apps)
        private val topic = DiscussionTopic(80, "Topic", "Author", "Original post", DiscussionKind.Discussion, 500)
        private val comment = ReleaseComment(44, "Author", null, 0, "Reply text", emptyList())
    }
}
