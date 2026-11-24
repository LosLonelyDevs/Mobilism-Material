package org.mobilism.material.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.test.filters.SdkSuppress
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.settings.DownloadPreferences

@SdkSuppress(minSdkVersion = 29)
class CommentManagementTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun ownReleaseCommentSavesThroughNativeEditorAndRefreshesOnce() = runBlocking {
        val quote = "[quote=\"DEVIL18\"]add torbox support bro please[/quote]\n"
        val reply = "See https://example.org/issue"
        val html = "<blockquote>DEVIL18 wrote: add torbox support bro please</blockquote>See <a href='https://example.org/issue'>https://example.org/issue</a>"
        val fixture = CommentForumFixture().apply { source = quote + reply }
        fixture.signIn()
        var refreshes = 0
        compose.setContent { CompositionLocalProvider(LocalCommentGateway provides fixture.gateway) { MobilismTheme(reduceMotion = true) {
            ReleaseScreen(releaseState(comment.copy(contentHtml = html)), DebridState(), DownloadPreferences(), onBack = {}, onRetry = {},
                onMoreComments = {}, watched = false, downloaded = false, onWatch = {}, onDownloaded = {},
                onLike = {}, onActionMessageShown = {}, auth = AuthState.SignedIn("Reader"),
                onRefreshComments = { refreshes++ })
        } } }
        openCommentOptions()
        compose.onNodeWithText("Edit comment").performClick()
        compose.onNodeWithTag("forum-comment-editor").assertExists()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("edit-comment-text").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("edit-comment-text").assertTextContains(reply)
            .assert(!hasText("DEVIL18", substring = true)).performTextReplacement("An edited comment")
        assertTrue(fixture.formReads.isEmpty())
        compose.onNodeWithText("Update").performClick()
        compose.waitUntil(10_000) { refreshes == 1 }
        compose.onNodeWithTag("forum-comment-editor").assertDoesNotExist()
        assertEquals(1, refreshes)
        assertTrue(fixture.formReads.single().endsWith("mode=edit&p=123"))
        assertTrue(java.net.URLDecoder.decode(fixture.writes.single(), "UTF-8").contains("message=${quote}An edited comment"))
    }

    @Test fun guestAndOtherAuthorsDoNotGetCommentOptions() {
        val auth = mutableStateOf<AuthState>(AuthState.SignedIn("Someone else"))
        compose.setContent { MobilismTheme(reduceMotion = true) {
            ReleaseScreen(releaseState(), DebridState(), DownloadPreferences(), onBack = {}, onRetry = {},
                onMoreComments = {}, watched = false, downloaded = false, onWatch = {}, onDownloaded = {},
                onLike = {}, onActionMessageShown = {}, auth = auth.value)
        } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("comment-123"))
        compose.onNodeWithContentDescription("Comment options").assertDoesNotExist()
        compose.runOnIdle { auth.value = AuthState.Guest }
        compose.onNodeWithContentDescription("Comment options").assertDoesNotExist()
    }

    @Test fun discussionDeleteOnlyRefreshesAfterConfirmedSubmission() = runBlocking {
        val fixture = CommentForumFixture()
        fixture.signIn()
        val repository = Repository()
        lateinit var model: DiscussionsViewModel
        compose.runOnUiThread { model = DiscussionsViewModel(repository, "Reader", SavedStateHandle(), compose.activity::getString, initialTopic = topic) }
        compose.setContent { CompositionLocalProvider(LocalCommentGateway provides fixture.gateway) {
            MobilismTheme(reduceMotion = true) { DiscussionsScreen(model, AuthState.SignedIn("Reader"), {}, { _, _ -> }) }
        } }
        compose.waitUntil { !model.state.value.comments.busy && model.state.value.comments.items.isNotEmpty() }
        openCommentOptions()
        compose.onNodeWithText("Delete comment").performClick()
        compose.onNodeWithTag("forum-comment-editor").assertExists()
        assertEquals(0, repository.posts)
        val reads = repository.reads
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(reads, repository.reads)
        assertTrue(fixture.writes.isEmpty())
        openCommentOptions()
        compose.onNodeWithText("Delete comment").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Delete", substring = false).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Delete", substring = false).performClick()
        compose.waitUntil { repository.reads > reads && !model.state.value.comments.busy }
        assertEquals(1, fixture.writes.size)
    }

    @Test fun commentMenuStaysAvailableWhenCollapsedAndDisablesDuringPosting() {
        var edits = 0
        val enabled = mutableStateOf(true)
        compose.setContent { MobilismTheme(reduceMotion = true) {
            ReleaseCommentCard(comment, topic.browserUrl, collapsed = true, onEdit = { edits++ }, onDelete = {},
                actionsEnabled = enabled.value, onImage = { _, _ -> })
        } }
        compose.onNodeWithContentDescription("Comment options").performClick()
        compose.onNodeWithText("Edit comment").performClick()
        assertEquals(1, edits)
        compose.onNodeWithTag("comment-body-123").assertDoesNotExist()
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithContentDescription("Comment options").assertIsNotEnabled()
    }

    private fun openCommentOptions() {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("comment-123"))
        compose.onNodeWithContentDescription("Comment options").performClick()
    }


    private class Repository : DiscussionsRepository {
        var reads = 0
        var posts = 0
        override suspend fun discussionCategories(area: DiscussionArea) = emptyList<DiscussionCategory>()
        override suspend fun discussionLastActivity(category: DiscussionCategory): Long? = null
        override suspend fun discussionTopics(category: DiscussionCategory, page: Int, query: String) = DiscussionPage(listOf(topic), false)
        override suspend fun discussionComments(topic: DiscussionTopic, page: Int): CommentPage {
            reads++
            return CommentPage(listOf(comment), false)
        }
        override suspend fun postDiscussionComment(topic: DiscussionTopic, text: String, replyTo: Long?): Long {
            posts++
            error("The comment form must not create a new reply")
        }
    }

    private fun releaseState(selected: ReleaseComment = comment) = ReleaseState(Release(42, "Sample", "Author", null, 0, 1, 0), busy = false,
        comments = CommentsState(listOf(selected), canLoadMore = false))

    private companion object {
        val topic = DiscussionTopic(42, "Discussion", "Author", "Original post", DiscussionKind.Discussion, 500)
        val comment = ReleaseComment(123, "Reader", null, 0, "My comment")
    }
}
