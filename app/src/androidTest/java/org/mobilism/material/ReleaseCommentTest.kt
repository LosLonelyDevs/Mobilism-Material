package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.containsString
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.settings.DownloadPreferences
import org.mobilism.material.ui.*

@RunWith(AndroidJUnit4::class)
class ReleaseCommentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun repliesFollowTheirParentAndCollapsingHidesTheWholeThread() {
        val parent = ReleaseComment(1, "Parent", null, 1700000001, "Original comment")
        val reply = ReleaseComment(2, "Reply", null, 1700000002, "First reply",
            quotes = listOf(CommentQuote(parent.author, parent.contentHtml)))
        val unrelated = ReleaseComment(3, "Another thread", null, 1700000003, "Independent comment")
        val nested = ReleaseComment(4, "Nested reply", null, 1700000004, "Second reply",
            quotes = listOf(CommentQuote(reply.author, reply.contentHtml)))
        val release = Release(42, "Sample app", "Author", null, 1700000000, 4, 0)
        val state = ReleaseState(release = release, busy = false,
            comments = CommentsState(listOf(nested, unrelated, reply, parent), canLoadMore = false))
        compose.setContent { MobilismTheme(reduceMotion = true) {
            ReleaseScreen(state, DebridState(), DownloadPreferences(), onBack = {}, onRetry = {},
                onMoreComments = {}, watched = false, downloaded = false, onWatch = {}, onDownloaded = {},
                onLike = {}, onActionMessageShown = {})
        } }
        compose.onNode(hasScrollToIndexAction() and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasTestTag("comment-1"))
        val unrelatedBounds = compose.onNodeWithTag("comment-3").fetchSemanticsNode().boundsInRoot
        val parentBounds = compose.onNodeWithTag("comment-1").fetchSemanticsNode().boundsInRoot
        val replyBounds = compose.onNodeWithTag("comment-2").fetchSemanticsNode().boundsInRoot
        val nestedBounds = compose.onNodeWithTag("comment-4").fetchSemanticsNode().boundsInRoot
        assertTrue(nestedBounds.top < unrelatedBounds.top)
        assertTrue(parentBounds.top < replyBounds.top)
        assertTrue(replyBounds.top < nestedBounds.top)
        assertTrue(replyBounds.left > parentBounds.left)
        assertTrue(nestedBounds.left > replyBounds.left)
        compose.onAllNodesWithTag("comment-2").assertCountEquals(1)
        compose.onAllNodesWithTag("comment-4").assertCountEquals(1)
        onView(withText(containsString("Original comment"))).check(matches(isDisplayed()))
        onView(withText(containsString("First reply"))).check(matches(isDisplayed()))
        compose.onNodeWithText("Show quote").assertDoesNotExist()
        capture("threaded-comments")
        compose.onNodeWithTag("comment-toggle-1").performClick()
        compose.onNodeWithTag("comment-2").assertDoesNotExist()
        compose.onNodeWithTag("comment-4").assertDoesNotExist()
        compose.onNodeWithTag("comment-3").assertIsDisplayed()
        compose.onNodeWithTag("comment-toggle-1").performClick()
        compose.onNodeWithTag("comment-2").assertExists()
        compose.onNodeWithTag("comment-4").assertExists()
    }

    @Test fun overlappingApiAndInlineQuotesDisplayOnePreviewAndOneExpandedQuote() {
        val comment = ReleaseComment(77, "Reader", null, 1700000000, """
            <blockquote><cite><a href="#p1">Parent wrote:</a></cite><div>Hello there</div></blockquote>My answer
        """, quotes = listOf(CommentQuote("Parent", "<p>Hello there</p>")))
        compose.setContent { MobilismTheme(reduceMotion = true) {
            Surface(Modifier.safeDrawingPadding()) {
                ReleaseCommentCard(comment, "https://forum.mobilism.org/", onImage = { _, _ -> })
            }
        } }
        compose.onAllNodesWithText("Replying to Parent").assertCountEquals(1)
        compose.onAllNodesWithText("Hello there", substring = true).assertCountEquals(1)
        compose.onNodeWithText("Show quote").performClick()
        onView(withText(containsString("Hello there"))).check(matches(isDisplayed()))
        onView(withText(containsString("My answer"))).check(matches(isDisplayed()))
        capture("deduplicated-quote")
    }

    @Test fun commentsCollapseIndependentlyAndRestoreQuotedReplies() {
        val restoration = StateRestorationTester(compose)
        val comments = listOf(
            ReleaseComment(77, "Author", null, 1700000000, "A comment with a quoted reply and an attached image.",
                images = listOf("android.resource://cum.ahfu.mobi.material/drawable/ic_mobilism"),
                quotes = listOf(CommentQuote("Earlier author", "Earlier comment"))),
            ReleaseComment(78, "Another reader", null, 1700000000, "An independent comment."))
        restoration.setContent { MobilismTheme(reduceMotion = true) {
            Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("collapse-test")) {
                    items(comments, key = { it.id }) { comment ->
                        ReleaseCommentCard(comment, "https://forum.mobilism.org/", onReply = {}, onImage = { _, _ -> })
                    }
                }
            }
        } }
        compose.onNodeWithText("Show quote").performClick()
        compose.onNodeWithText("Hide quote").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open comment image 1").assertExists()
        compose.onNodeWithTag("comment-toggle-77").performClick()
        compose.onNodeWithTag("comment-toggle-77").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        compose.onNodeWithTag("comment-body-77").assertDoesNotExist()
        compose.onNodeWithText("Replying to Earlier author").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open comment image 1").assertDoesNotExist()
        compose.onNodeWithTag("comment-body-78").assertIsDisplayed()
        compose.onNodeWithTag("comment-toggle-77").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("comment-77").assertHeightIsEqualTo(48.dp)
        capture("collapsed-comment")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("comment-body-77").assertDoesNotExist()
        compose.onNodeWithTag("comment-toggle-77").performClick()
        compose.onNodeWithTag("comment-body-77").assertExists()
        compose.onNodeWithText("Hide quote").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open comment image 1").assertExists()
        val shortComment = compose.onNodeWithTag("comment-78").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertTrue("A short comment should fit within 136 dp", shortComment.height <= 136 * density + 1)
        capture("expanded-comment")
    }

    @Test fun composerKeepsDraftOnCloseAndFailureThenPostsThroughAuthenticatedApi() = runBlocking {
        val fixture = Fixture()
        fixture.repository.signIn("Reader", "sample-password")
        lateinit var model: ReleaseViewModel
        compose.runOnUiThread { model = ReleaseViewModel(42, fixture.repository, androidx.lifecycle.SavedStateHandle()) }
        compose.setContent { MobilismTheme(reduceMotion = true) {
            val state by model.state.collectAsState()
            ReleaseScreen(state, DebridState(), DownloadPreferences(), onBack = {}, onRetry = model::load,
                onMoreComments = model::loadComments, watched = false, downloaded = false, onWatch = {}, onDownloaded = {},
                onLike = model::toggleLike, onActionMessageShown = model::actionMessageShown,
                auth = AuthState.SignedIn("Reader"), onWriteComment = model::openComposer,
                onEditComment = model::editComment, onPostComment = model::postComment,
                onCloseComposer = model::closeComposer, onRefreshComments = model::refreshComments,
                onRefresh = model::refresh)
        } }
        compose.waitUntil(10_000) { !model.state.value.busy }
        compose.onNodeWithTag("write-comment").performScrollTo().performClick()
        compose.onNodeWithTag("post-comment").assertIsNotEnabled()
        compose.onNodeWithText("Posting publicly as Reader").assertIsDisplayed()
        compose.onNodeWithTag("comment-draft").performTextInput("Thanks & hello\nSecond line")
        assertTrue(fixture.posts.isEmpty())
        compose.activityRule.scenario.onActivity { it.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            .hideSoftInputFromWindow(it.window.decorView.windowToken, 0) }
        compose.onNodeWithText("Close", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Continue your comment").performScrollTo().performClick()
        compose.onNodeWithTag("comment-draft").assertTextContains("Thanks & hello\nSecond line")
        capture("comment-composer")
        fixture.failPost = true
        compose.onNodeWithTag("post-comment").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.state.value.composer.error != null }
        compose.onNodeWithText("Check comments").assertExists()
        assertEquals("Thanks & hello\nSecond line", model.state.value.composer.draft)
        assertEquals(1, fixture.posts.size)
        compose.onNodeWithText("Check comments").performScrollTo().performClick()
        compose.onNodeWithText("Continue your comment").performScrollTo().performClick()
        fixture.failPost = false
        compose.onNodeWithTag("post-comment").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.state.value.composer.draft.isEmpty() && !model.state.value.composer.open }
        assertEquals(2, fixture.posts.size)
        assertEquals(99L, model.state.value.comments.items.first().id)
        assertEquals("Comment posted", model.state.value.actionMessage)
        val request = fixture.posts.last()
        assertEquals("https://app.mobilism.org/api-new/release/comment", request.url.toString())
        assertEquals("sample-session", request.header("X-Auth-Token"))
        assertEquals("POST", request.method)
        val body = checkNotNull(request.body)
        assertTrue(body.isOneShot())
        val expected = FormBody.Builder().add("id", "42").add("comment", "Thanks & hello\nSecond line").build()
        assertEquals(okio.Buffer().also(expected::writeTo).readUtf8(), okio.Buffer().also(body::writeTo).readUtf8())
        fixture.repository.signOut()
    }

    @Test fun guestGetsSignInActionAndCannotPostThroughRepository() = runBlocking {
        val fixture = Fixture()
        fixture.repository.continueAsGuest()
        var signIn = false
        compose.setContent { MobilismTheme(reduceMotion = true) {
            ReleaseScreen(ReleaseState(release = Release(42, "Sample release", "Author", null, 1, 0, 0), busy = false,
                comments = CommentsState(canLoadMore = false)), DebridState(), DownloadPreferences(),
                onBack = {}, onRetry = {}, onMoreComments = {}, watched = false, downloaded = false,
                onWatch = {}, onDownloaded = {}, onLike = {}, onActionMessageShown = {}, onSignIn = { signIn = true })
        } }
        compose.onNodeWithText("Sign in to comment").performScrollTo().performClick()
        assertTrue(signIn)
        assertTrue(runCatching { fixture.repository.postComment(42, "Guest text") }.exceptionOrNull() is ApiFailure)
        assertTrue(fixture.posts.isEmpty())
        compose.onNodeWithTag("comment-draft").assertDoesNotExist()
        fixture.repository.signOut()
    }

    @Test fun missingConfirmationAndValidationErrorsCannotReportSuccess() = runBlocking {
        val fixture = Fixture()
        fixture.repository.signIn("Reader", "sample-password")
        fixture.postBody = """{"status":"success","response":{}}"""
        assertEquals(FailureKind.InvalidResponse,
            (runCatching { fixture.repository.postComment(42, "hello") }.exceptionOrNull() as ApiFailure).kind)
        fixture.postStatus = 429
        fixture.postBody = """{"status":"error","error":{"text":"Please wait before posting again."}}"""
        val failure = runCatching { fixture.repository.postComment(42, "hello") }.exceptionOrNull() as ApiFailure
        assertEquals(429, failure.httpStatus)
        assertEquals("Please wait before posting again.", failure.message)
        assertEquals(2, fixture.posts.size)
        fixture.repository.signOut()
    }

    @Test fun largeTextKeepsComposerActionsReachable() {
        org.junit.Assume.assumeTrue(compose.activity.resources.configuration.fontScale >= 1.8f)
        compose.setContent { MobilismTheme(reduceMotion = true) {
            ReleaseCommentComposer(CommentComposerState(draft = "A comment with larger text"),
                "Reader", "A longer release title that wraps on a compact screen",
                onEdit = {}, onPost = {}, onDismiss = {}, onCheckComments = {})
        } }
        compose.onNodeWithTag("post-comment").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Close").assertIsDisplayed()
        capture("comment-large-text")
    }

    @Test fun wideWindowUsesBoundedSheetAndThemeTokens() {
        org.junit.Assume.assumeTrue(compose.activity.resources.configuration.screenWidthDp >= 840)
        compose.setContent { MobilismTheme(reduceMotion = true) {
            ReleaseCommentComposer(CommentComposerState(draft = "A comment in a wider window"),
                "Reader", "Sample release", onEdit = {}, onPost = {}, onDismiss = {}, onCheckComments = {})
        } }
        compose.onNodeWithTag("post-comment").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Posting publicly as Reader").assertIsDisplayed()
        capture("comment-wide")
    }

    @Test fun postingDisablesSubmissionAndDismissal() {
        var dismissed = false
        compose.setContent { MobilismTheme(reduceMotion = true) {
            ReleaseCommentComposer(CommentComposerState(draft = "Sending", posting = true),
                "Reader", "Sample release", onEdit = {}, onPost = {},
                onDismiss = { dismissed = true }, onCheckComments = {})
        } }
        compose.onNodeWithTag("post-comment").assertIsNotEnabled()
        compose.onNodeWithText("Close").assertIsNotEnabled()
        compose.onNodeWithText("Posting comment…").assertIsDisplayed()
        assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
        compose.waitForIdle()
        compose.onNodeWithTag("comment-composer").assertExists()
        assertFalse(dismissed)
    }

    @Test fun replyButtonShowsQuotedContextAndRemovalKeepsDraft() {
        val comment = ReleaseComment(77, "Author", null, 1, "Original comment",
            quotes = listOf(CommentQuote("Earlier author", "Earlier comment")))
        var composer by androidx.compose.runtime.mutableStateOf(CommentComposerState())
        compose.setContent { MobilismTheme(reduceMotion = true) {
            if (composer.open) ReleaseCommentComposer(composer, "Reader", "Sample release",
                onEdit = { composer = composer.copy(draft = it) }, onPost = {},
                onDismiss = { composer = composer.copy(open = false) }, onCheckComments = {},
                onRemoveReply = { composer = composer.copy(replyTo = null) })
            ReleaseCommentCard(comment, "https://forum.mobilism.org/", onReply = {
                composer = composer.copy(open = true, replyTo = CommentReplyTarget(comment.id, comment.author, comment.contentHtml))
            }, onImage = { _, _ -> })
        } }
        compose.onNodeWithText("Replying to Earlier author").assertIsDisplayed()
        compose.onNodeWithText("Show quote").performClick()
        compose.onNodeWithText("Hide quote").assertIsDisplayed()
        compose.onNodeWithText("Reply", substring = false).performClick()
        compose.onNodeWithText("Reply to comment").assertIsDisplayed()
        compose.onNodeWithText("Replying to Author").assertIsDisplayed()
        compose.onNodeWithTag("comment-draft").performTextInput("My reply")
        capture("comment-reply")
        compose.onNodeWithText("Remove reply").performScrollTo().performClick()
        compose.onNodeWithTag("comment-draft").assertTextContains("My reply")
        compose.onNodeWithText("Post comment").assertExists()
        assertNull(composer.replyTo)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // The sheet owns a separate window; capture the composed display, including that window.
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory = File(compose.activity.getExternalFilesDir(null), "comment-screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private class Fixture {
        val posts = CopyOnWriteArrayList<Request>()
        @Volatile var failPost = false
        @Volatile var postBody = """{"status":"success","response":{"comment_id":99}}"""
        @Volatile var postStatus = 200
        private val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("comment-test-$name", mode)
        }
        private val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            var status = 200
            val body = when (request.url.encodedPath.removePrefix("/api-new/")) {
                "auth/login" -> """{"access_token":"sample-session"}"""
                "release/42" -> """{"response":{"id":42,"subject":"Sample release v1.0","name":"Author","section":"book"}}"""
                "release/comment" -> {
                    posts += request
                    if (failPost) throw IOException("Simulated lost response")
                    status = postStatus
                    postBody
                }
                else -> {
                    check(request.url.encodedPath.startsWith("/api-new/release/42/comments/"))
                    """{"response":[]}"""
                }
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Fixture")
                .body(body.toResponseBody()).build()
        }.build()
        val repository = DefaultMobilismRepository(context, client = client)
    }
}
