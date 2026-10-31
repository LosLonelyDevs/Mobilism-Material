package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CopyOnWriteArrayList
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.settings.DownloadPreferences
import org.mobilism.material.ui.DownloadGroups
import org.mobilism.material.ui.ReleaseContentParser
import org.mobilism.material.ui.ReleaseScreen
import org.mobilism.material.ui.ReleaseState
import org.mobilism.material.ui.CommentsState

@RunWith(AndroidJUnit4::class)
class WearReleaseTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun fallbackRendersDownloadVariantAndForumReply() = runBlocking<Unit> {
        val repository = Fixture().repository
        val release = repository.release(42)
        val comments = repository.comments(42, 1)
        compose.setContent { MobilismTheme(reduceMotion = true) {
            ReleaseScreen(ReleaseState(release, busy = false, comments = CommentsState(comments.items)),
                DebridState(), DownloadPreferences(), onBack = {}, onRetry = {}, onMoreComments = {},
                watched = false, downloaded = false, onWatch = {}, onDownloaded = {}, onLike = {}, onActionMessageShown = {})
        } }
        compose.onNodeWithText("Watch APK").performScrollTo().assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.waitUntil(5_000) { compose.activity.hasWindowFocus() }
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(instrumentation.targetContext.getExternalFilesDir(null), "wear-release.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        compose.onNodeWithTag("comment-81").performScrollTo().assertIsDisplayed()
    }

    @Test fun missingApiReleaseLoadsForumDownloadsCommentsAndLikeIdWithoutLeakingToken() = runBlocking {
        val fixture = Fixture()
        fixture.repository.signIn("Reader", "sample-password")
        val release = fixture.repository.release(42)
        assertEquals(1347L, release.categoryId)
        assertEquals(80L, release.likeId)
        val parsed = ReleaseContentParser.parse(release.contentHtml, release.downloadLinks, release.browserUrl!!)
        val group = DownloadGroups.group(parsed.downloads, DownloadPreferences()).single()
        assertEquals("Watch APK", group.title)
        assertEquals("https://files.example.org/watch.apk", group.links.single().url)
        val comments = fixture.repository.comments(42, 1)
        assertEquals(listOf(81L), comments.items.map { it.id })
        assertTrue(comments.canLoadMore)
        val next = fixture.repository.comments(42, 2)
        assertEquals(listOf(82L), next.items.map { it.id })
        assertFalse(next.canLoadMore)
        assertTrue(fixture.requests.any { it.url.queryParameter("start") == "10" })
        assertTrue(fixture.requests.filter { it.url.host == "forum.mobilism.org" }.all { it.header("X-Auth-Token") == null })
        assertEquals("sample-session", fixture.requests.single { it.url.encodedPath == "/api-new/release/42" }.header("X-Auth-Token"))
        assertTrue(fixture.repository.toggleLike(release))
        val form = okio.Buffer()
        fixture.requests.last().body!!.writeTo(form)
        assertEquals("id=80", form.readUtf8())
    }

    @Test fun successfulApiReleaseDoesNotFetchForum() = runBlocking {
        val fixture = Fixture(apiStatus = 200, apiBody = """{"response":{"id":42,"subject":"API release"}}""")
        assertEquals("API release", fixture.repository.release(42).title)
        assertEquals(1, fixture.requests.size)
        assertEquals("app.mobilism.org", fixture.requests.single().url.host)
    }

    @Test fun unrelatedApiErrorsDoNotTriggerForumFallback() = runBlocking {
        listOf(400 to "Invalid request.", 401 to "Authentication required.", 404 to "Not found.").forEach { (status, message) ->
            val fixture = Fixture(status, """{"error":{"text":"$message"}}""")
            val failure = runCatching { fixture.repository.release(42) }.exceptionOrNull()
            assertTrue(failure is ApiFailure)
            assertEquals(message, failure!!.message)
            assertEquals(1, fixture.requests.size)
        }
    }

    @Test fun forumChallengesRemainActionableFailures() = runBlocking {
        val fixture = Fixture(forumStatus = 403)
        val failure = runCatching { fixture.repository.release(42) }.exceptionOrNull() as ApiFailure
        assertEquals(FailureKind.Challenge, failure.kind)
        assertEquals(2, fixture.requests.size)
    }

    private class Fixture(
        private val apiStatus: Int = 400,
        private val apiBody: String = """{"error":{"text":"No records found."}}""",
        private val forumStatus: Int = 200,
    ) {
        val requests = CopyOnWriteArrayList<Request>()
        private val prefix = "wear-test-${System.nanoTime()}-"
        private val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(prefix + name, mode)
        }
        private val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            var status = 200
            val body = when (request.url.encodedPath) {
                "/api-new/auth/login" -> """{"access_token":"sample-session"}"""
                "/api-new/release/42" -> { status = apiStatus; apiBody }
                "/api-new/post/like" -> """{"response":{"liked":true}}"""
                "/viewtopic.php" -> {
                    assertEquals("forum.mobilism.org", request.url.host)
                    assertEquals("42", request.url.queryParameter("t"))
                    status = forumStatus
                    if (status == 403) "<html>cf-chl-verification</html>" else topic(request.url.queryParameter("start"))
                }
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Fixture")
                .body(body.toResponseBody()).build()
        }.build()
        val repository = DefaultMobilismRepository(context, client = client)

        private fun topic(start: String?): String = """
            <div class='page-category-topic'><a href='viewtopic.php?f=1347&amp;t=42'>Wear example v1.0</a></div>
            <div class='pagination'><a href='viewforum.php?f=402'>Apps</a></div>
        """ + if (start == "10") post(82, "Older reply") else
            post(80, """Wear description<br><span style='text-decoration: underline'>Download Instructions:</span><br>
                <span style='font-weight: bold'>Watch APK:</span><br><a href='https://files.example.org/watch.apk'>File</a>""") +
                post(81, "New reply") + "<div class='pagination'><a href='viewtopic.php?t=42&amp;sd=d&amp;start=10'>2</a></div>"

        private fun post(id: Long, content: String) = """<div id='p$id'><article>
            <div class='user-profile-username'>Author</div><div class='postbody'><div class='content'>$content</div></div>
        </article></div>"""
    }
}
