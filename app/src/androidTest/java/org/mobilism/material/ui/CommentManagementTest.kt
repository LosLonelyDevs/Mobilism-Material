package org.mobilism.material.ui

import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.test.filters.SdkSuppress
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.settings.DownloadPreferences

@SdkSuppress(minSdkVersion = 29)
class CommentManagementTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun ownReleaseCommentOffersEditAndRefreshesAfterClosingTheForm() {
        var refreshes = 0
        compose.setContent { MobilismTheme(reduceMotion = true) {
            ReleaseScreen(releaseState(), DebridState(), DownloadPreferences(), onBack = {}, onRetry = {},
                onMoreComments = {}, watched = false, downloaded = false, onWatch = {}, onDownloaded = {},
                onLike = {}, onActionMessageShown = {}, auth = AuthState.SignedIn("Reader"),
                onRefreshComments = { refreshes++ })
        } }
        openCommentOptions()
        compose.onNodeWithText("Edit comment").performClick()
        compose.onNodeWithTag("forum-comment-editor").assertExists()
        assertEquals("https://forum.mobilism.org/posting.php?mode=edit&p=123", captureFormRequest())
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("forum-comment-editor").assertDoesNotExist()
        assertEquals(1, refreshes)
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

    @Test fun discussionDeleteOpensTheConfirmationFormAndRefreshesOnClose() {
        val repository = Repository()
        lateinit var model: DiscussionsViewModel
        compose.runOnUiThread { model = DiscussionsViewModel(repository, "Reader", SavedStateHandle(), initialTopic = topic) }
        compose.setContent { MobilismTheme(reduceMotion = true) {
            DiscussionsScreen(model, AuthState.SignedIn("Reader"), {}, { _, _ -> })
        } }
        compose.waitUntil { !model.state.value.comments.busy && model.state.value.comments.items.isNotEmpty() }
        openCommentOptions()
        compose.onNodeWithText("Delete comment").performClick()
        compose.onNodeWithTag("forum-comment-editor").assertExists()
        assertEquals("https://forum.mobilism.org/posting.php?mode=delete&p=123", captureFormRequest())
        assertEquals(0, repository.posts)
        val reads = repository.reads
        compose.onNodeWithText("Close").performClick()
        compose.waitUntil { repository.reads > reads && !model.state.value.comments.busy }
        assertEquals(listOf(comment), model.state.value.comments.items)
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

    private fun captureFormRequest(): String {
        val requested = AtomicReference<String>()
        compose.runOnIdle {
            val view = browser()
            view.stopLoading()
            val client = view.webViewClient
            view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                    client.shouldOverrideUrlLoading(view, request)
                override fun onPageFinished(view: WebView, url: String) = client.onPageFinished(view, url)
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                    if (request.isForMainFrame) requested.set(request.url.toString())
                    return WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream("<html>Comment form</html>".toByteArray()))
                }
            }
        }
        compose.onNodeWithText("Open comment form").performClick()
        compose.waitUntil(10_000) { requested.get() != null }
        return requested.get()
    }

    private fun browser(): WebView = WindowInspector.getGlobalWindowViews()
        .asSequence().flatMap(::descendants).filterIsInstance<WebView>().single()

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
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

    private fun releaseState() = ReleaseState(Release(42, "Sample", "Author", null, 0, 1, 0), busy = false,
        comments = CommentsState(listOf(comment), canLoadMore = false))

    private companion object {
        val topic = DiscussionTopic(42, "Discussion", "Author", "Original post", DiscussionKind.Discussion, 500)
        val comment = ReleaseComment(123, "Reader", null, 0, "My comment")
    }
}
