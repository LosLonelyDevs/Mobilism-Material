package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.*
import org.mobilism.material.ui.*

@RunWith(AndroidJUnit4::class)
class DiscussionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun forumBrowserLinksExcludeOurReleaseLinkHandler() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val url = "https://forum.mobilism.org/viewtopic.php?t=80"
        val chooser = checkNotNull(forumBrowserIntent(context, url))
        assertEquals(android.content.Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val target = chooser.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)
        assertEquals(url, target?.dataString)
        @Suppress("DEPRECATION")
        val excluded = chooser.getParcelableArrayExtra(android.content.Intent.EXTRA_EXCLUDE_COMPONENTS)
        assertTrue(excluded!!.contains(android.content.ComponentName(context, MainActivity::class.java)))
        assertNull(forumBrowserIntent(context, "javascript:alert(1)"))
    }

    @Test fun categoriesTopicsSearchAndPostingUseTheLegacyRoutes() = runBlocking {
        val fixture = Fixture()
        fixture.repository.signIn("Reader", "sample-password")
        for (area in DiscussionArea.entries) {
            val category = fixture.repository.discussionCategories(area).first()
            val topic = fixture.repository.discussionTopics(category, 1).items.single()
            fixture.repository.discussionTopics(category, 2, "help & tips")
            fixture.repository.discussionComments(topic, 2)
            fixture.repository.postDiscussionComment(topic, "Reply & hello", 44)
        }
        val discussionPath = listOf("discussion", "category", "500", "page", "1", "limit", "20",
            "orderby", "topic_last_post_time", "desc", "1")
        assertTrue(fixture.requests.any { it.url.pathSegments.takeLast(discussionPath.size) == discussionPath })
        assertTrue(fixture.requests.any { it.url.encodedPath.endsWith("other/category/66/page/1/limit/20/orderby/topic_last_post_time/desc/1") })
        val searches = fixture.requests.filter { it.url.encodedPath.endsWith("/search") }.map(::body)
        assertTrue(searches.all { it.contains("isrelease=0") && it.contains("count=2") })
        assertTrue(searches.any { it.contains("isother=1") && it.contains("id=66") })
        assertTrue(searches.any { it.contains("isdiscussion=1") && it.contains("id=500") })
        val posts = fixture.requests.filter { it.url.encodedPath.endsWith("/comment") }
        assertEquals(4, posts.size)
        assertTrue(posts.all { it.body!!.isOneShot() && it.header("X-Auth-Token") == "sample-session" && body(it).contains("msg_id=44") })
        fixture.repository.signOut()
    }

    @Test fun categoryActivityUsesNewestTimestampRatherThanPinnedPosition() = runBlocking {
        val fixture = Fixture()
        val category = fixture.repository.discussionCategories(DiscussionArea.Apps).single()
        fixture.topicsBody = """{"response":[{"id":80,"status":1,"topic_last_post_time":10},{"id":81,"topic_last_post_time":500}]}"""
        assertEquals(500L, fixture.repository.discussionLastActivity(category))
        fixture.topicsBody = """{"response":[]}"""
        assertNull(fixture.repository.discussionLastActivity(category))
        fixture.topicsBody = """{"response":[{"id":80}]}"""
        assertNull(fixture.repository.discussionLastActivity(category))
    }

    @Test fun dedicatedGameForumsStayOutOfAppsWhenSwitchingTabs() {
        val fixture = Fixture()
        fixture.categoriesBody = """{"response":{"app|game":[
            {"id":500,"name":"Reviews"},{"id":502,"name":"Software"},
            {"id":503,"name":"Games"},{"id":504,"name":"Game Codes"}
        ],"books":[]}}"""
        lateinit var model: DiscussionsViewModel
        compose.runOnUiThread { model = DiscussionsViewModel(fixture.repository, null, SavedStateHandle()) }
        compose.setContent { MobilismTheme(theme = SolarizedDark, reduceMotion = true) {
            DiscussionsScreen(model, AuthState.Guest, {}, { _, _ -> })
        } }
        compose.waitUntil(10_000) { !model.state.value.loading && model.state.value.categories.isNotEmpty() }
        compose.onNodeWithText("Software").assertIsDisplayed()
        compose.onNodeWithText("Reviews").assertIsDisplayed()
        compose.onNodeWithText("Game Codes").assertDoesNotExist()
        compose.onNodeWithText("Games").performClick()
        compose.waitUntil(10_000) { model.state.value.area == DiscussionArea.Games && !model.state.value.loading }
        compose.onNodeWithText("Game Codes").assertIsDisplayed()
        compose.onNodeWithText("Reviews").assertIsDisplayed()
        compose.onNodeWithText("Software").assertDoesNotExist()
        compose.onNodeWithText("Apps").performClick()
        compose.waitUntil(10_000) { model.state.value.area == DiscussionArea.Apps && !model.state.value.loading }
        compose.onNodeWithText("Game Codes").assertDoesNotExist()
        compose.onNodeWithText("Software").assertIsDisplayed()
    }

    @Test fun failuresNeverReplayWritesAndExpiredSessionClearsAccount() = runBlocking {
        val fixture = Fixture()
        fixture.repository.signIn("Reader", "sample-password")
        val category = fixture.repository.discussionCategories(DiscussionArea.Apps).single()
        val topic = fixture.repository.discussionTopics(category, 1).items.single()
        fixture.postStatus = 503
        fixture.requests.clear()
        assertTrue(runCatching { fixture.repository.postDiscussionComment(topic, "Draft") }.isFailure)
        assertEquals(1, fixture.requests.size)
        fixture.postStatus = 200
        fixture.postBody = "{}"
        assertEquals(FailureKind.InvalidResponse, (runCatching { fixture.repository.postDiscussionComment(topic, "Draft") }.exceptionOrNull() as ApiFailure).kind)
        fixture.listStatus = 400
        assertTrue(fixture.repository.discussionTopics(category, 2).items.isEmpty())
        fixture.listStatus = 401
        assertEquals(FailureKind.Session, (runCatching { fixture.repository.discussionComments(topic, 1) }.exceptionOrNull() as ApiFailure).kind)
        assertEquals(AuthState.SignedOut, fixture.repository.auth.value)
        fixture.requests.clear()
        assertTrue(runCatching { fixture.repository.postDiscussionComment(topic, "Draft") }.isFailure)
        assertTrue(fixture.requests.isEmpty())
    }

    @Test fun browseSearchReadReplyAndReturnInLightAndAmoled() = runBlocking {
        val fixture = Fixture()
        fixture.repository.signIn("Reader", "sample-password")
        lateinit var model: DiscussionsViewModel
        compose.runOnUiThread { model = DiscussionsViewModel(fixture.repository, "Reader", SavedStateHandle()) }
        var dark by mutableStateOf(false)
        var reduced by mutableStateOf(false)
        compose.setContent { MobilismTheme(theme = if (dark) AmoledBlack else CatppuccinLatte, reduceMotion = reduced) {
            DiscussionsScreen(model, AuthState.SignedIn("Reader"), {}, { _, _ -> })
        } }
        compose.waitUntil(10_000) { model.state.value.categories.isNotEmpty() }
        compose.onNodeWithText("Help & support").assertIsDisplayed()
        capture("categories-light")
        compose.onNodeWithText("Help & support").performClick()
        compose.waitUntil(10_000) { model.state.value.topics.items.isNotEmpty() }
        compose.onNodeWithText("Pinned").assertIsDisplayed()
        compose.onNodeWithTag("discussion-search").performTextInput("missing")
        compose.onNodeWithTag("discussion-search").performImeAction()
        compose.waitUntil(10_000) { !model.state.value.topics.busy && model.state.value.submittedQuery == "missing" }
        compose.onNodeWithText("No matching topics").assertIsDisplayed()
        compose.onNodeWithText("Clear").performClick()
        compose.waitUntil(10_000) { model.state.value.topics.items.isNotEmpty() }
        compose.onNodeWithText("Getting started with Mobilism").assertIsDisplayed()
        capture("topics-light")
        compose.onNodeWithText("Getting started with Mobilism").performClick()
        compose.waitUntil(10_000) { model.state.value.comments.items.isNotEmpty() }
        compose.onNodeWithText("Original post").assertIsDisplayed()
        capture("topic-light")
        compose.onNodeWithTag("discussion-reader").performScrollToNode(hasText("Reply", substring = false))
        compose.onNodeWithText("Reply", substring = false).performClick()
        compose.onNodeWithTag("post-comment").assertIsNotEnabled()
        compose.onNodeWithTag("comment-draft").performTextInput("Thanks for the tips")
        compose.runOnUiThread { dark = true; reduced = true }
        capture("reply-amoled")
        compose.onNodeWithTag("post-comment").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.state.value.notice == "Comment posted" }
        assertEquals(1, fixture.requests.count { it.url.encodedPath.endsWith("/comment") })
        assertEquals("", model.state.value.composer.draft)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Getting started with Mobilism").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Community").performClick()
        compose.waitUntil(10_000) { model.state.value.area == DiscussionArea.Community && !model.state.value.loading }
        compose.onNodeWithText("General chat").assertIsDisplayed()
        capture("community-amoled")
        fixture.repository.signOut()
    }

    @Test fun guestGetsSignInActionAndOfflineForumCanRetry() {
        val fixture = Fixture()
        fixture.listStatus = 503
        lateinit var model: DiscussionsViewModel
        var requested: DiscussionTopic? = null
        compose.runOnUiThread { model = DiscussionsViewModel(fixture.repository, null, SavedStateHandle()) }
        compose.setContent { MobilismTheme(reduceMotion = true) {
            DiscussionsScreen(model, AuthState.Guest, {}, { topic, _ -> requested = topic })
        } }
        compose.waitUntil(10_000) { model.state.value.categories.isNotEmpty() }
        compose.onNodeWithText("Help & support").performClick()
        compose.waitUntil(10_000) { model.state.value.topics.error != null }
        compose.onNodeWithText("Try again").assertIsDisplayed()
        fixture.listStatus = 200
        compose.onNodeWithText("Try again").performClick()
        compose.waitUntil(10_000) { model.state.value.topics.items.isNotEmpty() }
        compose.onNodeWithText("Getting started with Mobilism").performClick()
        compose.onNodeWithText("Sign in to comment").performClick()
        assertEquals(80L, requested?.id)
        assertFalse(model.state.value.composer.open)
        assertTrue(fixture.requests.none { it.url.encodedPath.endsWith("/comment") })
    }

    @Test fun largeTextKeyboardFocusAndSheetDismissKeepControlsUsable() {
        val fixture = Fixture()
        lateinit var model: DiscussionsViewModel
        lateinit var inputMode: InputModeManager
        compose.runOnUiThread { model = DiscussionsViewModel(fixture.repository, "Reader", SavedStateHandle()) }
        compose.setContent { MobilismTheme(theme = AmoledBlack, reduceMotion = true) {
            inputMode = LocalInputModeManager.current
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                DiscussionsScreen(model, AuthState.SignedIn("Reader"), {}, { _, _ -> })
            }
        } }
        compose.waitUntil(10_000) { model.state.value.categories.isNotEmpty() }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        compose.onRoot().performKeyInput { pressKey(Key.Tab) }
        compose.onAllNodes(isFocused()).assertCountEquals(1)
        capture("focus-large-text")
        compose.onNodeWithText("Help & support").performClick()
        compose.waitUntil(10_000) { model.state.value.topics.items.isNotEmpty() }
        compose.onNodeWithText("Getting started with Mobilism").performClick()
        compose.onNodeWithText("Comment").performClick()
        compose.onNodeWithTag("comment-draft").performTextInput("Keep this draft")
        compose.onNodeWithText("Close", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Continue draft").assertIsDisplayed().performClick()
        compose.onNodeWithTag("comment-draft").assertTextContains("Keep this draft")
        compose.onNodeWithTag("post-comment").performScrollTo().assertIsDisplayed().assertIsEnabled()
        capture("composer-large-text")
        assertTrue(fixture.requests.none { it.url.encodedPath.endsWith("/comment") })
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val image = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory = java.io.File(compose.activity.getExternalFilesDir(null), "discussion-screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        java.io.File(directory, "$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    private class Fixture {
        val requests = CopyOnWriteArrayList<Request>()
        @Volatile var postStatus = 200
        @Volatile var postBody = """{"response":{"comment_id":99}}"""
        @Volatile var listStatus = 200
        @Volatile var topicsBody = """{"response":[$TOPIC]}"""
        @Volatile var categoriesBody = """{"response":{"app|game":[{"id":500,"name":"Help &amp; support","topics":42}],"books":[{"id":501,"name":"Book discussions"}]}}"""
        private val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("discussions-test-$name", mode)
        }
        private val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val path = request.url.encodedPath.removePrefix("/api-new/")
            var status = 200
            val response = when {
                path == "auth/login" -> """{"access_token":"sample-session","id":7}"""
                path == "discussion/categories" -> categoriesBody
                path == "other/category" -> """{"announcements":[],"contests":[],"talk":[{"id":66,"name":"General chat"}],"services":[],"feedback":[]}"""
                path.endsWith("/comment") -> { status = postStatus; postBody }
                path == "search" -> if (body(request).contains("missing")) """{"discussions":[],"others":[]}""" else """{"discussions":[$TOPIC],"others":[$TOPIC]}"""
                path.contains("/comments/") -> { status = listStatus; """{"response":[{"id":44,"name":"Helpful reader","date":1700000000,"content":"Welcome! Take a look at https://example.org/help for more details."}]}""" }
                path.contains("/category/") -> { status = listStatus; topicsBody }
                else -> error("Unexpected discussion fixture path: $path")
            }
            val payload = if (status == 400) """{"status":"error","error":{"text":"No records found."}}"""
                else if (status != 200) """{"status":"error","error":{"text":"Fixture unavailable"}}""" else response
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Fixture")
                .body(payload.toResponseBody()).build()
        }.build()
        val repository = DefaultMobilismRepository(context, client = client)
    }

    companion object {
        private fun body(request: Request) = okio.Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        private const val TOPIC = """{"id":80,"subject":"Getting started with Mobilism","name":"Community guide","content":"<p>A place to ask questions and share what works.</p><p>Read the guide at https://example.org/guide.</p>","date":1700000000,"topic_last_post_time":1700000500,"forum_id":500,"status":1,"replies":12,"views":240}"""
    }
}
