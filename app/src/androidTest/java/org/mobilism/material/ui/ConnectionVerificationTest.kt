package org.mobilism.material.ui

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme

@SdkSuppress(minSdkVersion = 29)
class ConnectionVerificationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class Fixture {
        val requests = CopyOnWriteArrayList<Request>()
        private val prefix = "verification-${UUID.randomUUID()}"
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("$prefix-$name", mode)
        }
        val repository = DefaultMobilismRepository(context, client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val forum = request.url.host == "forum.mobilism.org"
            val cleared = request.header("Cookie").orEmpty().contains("connection_test_clearance=fixture")
            val body = when {
                !cleared || forum -> "<html><div id='cf-chl-widget'>Browser check</div></html>"
                request.url.encodedPath.endsWith("auth/login") -> """{"access_token":"sample-session"}"""
                request.url.encodedPath.endsWith("release/category") -> """{"app":[],"games":[],"books":[]}"""
                request.url.encodedPath.endsWith("search") -> """{"releases":[]}"""
                else -> """{"response":[]}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (cleared && !forum) 200 else 403).message("Fixture")
                .apply { if (!cleared || forum) header("cf-mitigated", "challenge") }
                .body(body.toResponseBody()).build()
        }.build())
    }

    private fun cookie(site: String, value: String) {
        compose.runOnIdle {
            CookieManager.getInstance().setCookie("https://$site.mobilism.org/", value)
            CookieManager.getInstance().flush()
        }
    }

    private fun clearFixtureCookies() {
        cookie("app", "connection_test_clearance=; Path=/; Secure; Max-Age=0")
        cookie("forum", "connection_test_forum=; Path=/; Secure; Max-Age=0")
    }

    @Test fun feedRecoversThroughBrowserVerificationAndRetriesOnlyOnContinue() = runBlocking {
        clearFixtureCookies()
        val fixture = Fixture()
        val failure = runCatching { fixture.repository.releases(ContentSection.Apps, FeedOrder.Latest, 1) }.exceptionOrNull()!!
        var state by mutableStateOf(FeedState(error = (failure as ApiFailure).forScreen()))
        compose.setContent { MobilismTheme(reduceMotion = true) {
            val scope = rememberCoroutineScope()
            FeedScreen(state, AuthState.Guest, {}, {}, {
                scope.launch {
                    state = state.copy(busy = true, error = null)
                    try {
                        val page = fixture.repository.releases(ContentSection.Apps, FeedOrder.Latest, 1)
                        state = state.copy(busy = false, items = page.items, canLoadMore = page.canLoadMore)
                    } catch (error: Exception) { state = state.copy(busy = false, error = error.forScreen()) }
                }
            }, {}, {}, {}, {}, {}, {}, {}, CategoryState(), {}, {})
        } }
        capture("blocked-feed")
        compose.onNodeWithText("Verify connection").performClick()
        compose.onNodeWithText("Continue").assertIsDisplayed()
        compose.onNodeWithText("Reload").assertIsDisplayed()
        compose.onNodeWithText("Use forum in browser").assertIsDisplayed()
        compose.waitUntil(15_000) { compose.runOnIdle { browser().originalUrl != null } }
        compose.runOnIdle {
            browser().apply {
                assertEquals(VerificationSite.App.url, originalUrl)
                assertEquals(WebSettings.getDefaultUserAgent(fixture.context), settings.userAgentString)
                assertTrue(settings.javaScriptEnabled)
                assertTrue(settings.domStorageEnabled)
                assertTrue(CookieManager.getInstance().acceptThirdPartyCookies(this))
                assertFalse(settings.allowFileAccess)
                stopLoading()
                loadDataWithBaseURL(VerificationSite.App.url, """<html><body><pre>{"app":[],"games":[],"books":[]}</pre></body></html>""",
                    "text/html", "UTF-8", VerificationSite.App.url)
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Browser check finished. Tap Continue to try again.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { assertEquals(View.INVISIBLE, browser().visibility) }
        capture("verification")
        assertEquals(1, fixture.requests.size)
        cookie("app", "connection_test_clearance=fixture; Path=/; Secure")
        try {
            compose.onNodeWithTag("continue-connection-verification").performClick()
            compose.waitUntil(10_000) { fixture.requests.size == 2 && !state.busy }
            compose.onNodeWithText("No releases found.").assertIsDisplayed()
            compose.onNodeWithText("Verify connection").assertDoesNotExist()
            assertNull(state.error)
            capture("recovered-feed")
            assertEquals(fixture.requests.first().url, fixture.requests.last().url)
            assertTrue(fixture.requests.last().header("Cookie").orEmpty().contains("connection_test_clearance=fixture"))
        } finally { clearFixtureCookies() }
    }

    @Test fun allApiReadsAndLoginReuseClearanceWithoutForumCookies() = runBlocking {
        clearFixtureCookies()
        cookie("app", "connection_test_clearance=fixture; Path=/; Secure")
        cookie("forum", "connection_test_forum=fixture; Path=/; Secure")
        val fixture = Fixture()
        try {
            fixture.repository.signIn("Reader", "sample-password")
            ContentSection.entries.forEach { section ->
                FeedOrder.entries.forEach { order -> fixture.repository.releases(section, order, 1) }
                fixture.repository.categories(section)
            }
            fixture.repository.search("sample", SearchScope.All, 1)
            fixture.repository.comments(42, 1)
            val appRequests = fixture.requests.toList()
            assertTrue(appRequests.size >= 9)
            appRequests.forEach { request ->
                assertTrue(request.header("Cookie").orEmpty().contains("connection_test_clearance=fixture"))
                assertFalse(request.header("Cookie").orEmpty().contains("connection_test_forum"))
                assertEquals(WebSettings.getDefaultUserAgent(fixture.context), request.header("User-Agent"))
            }
            val failure = runCatching { fixture.repository.profile(ProfileReference(username = "Reader")) }.exceptionOrNull() as ApiFailure
            assertEquals(VerificationSite.Forum, failure.verificationSite)
            val forumRequest = fixture.requests.last()
            assertNull(forumRequest.header("X-Auth-Token"))
            assertTrue(forumRequest.header("Cookie").orEmpty().contains("connection_test_forum=fixture"))
            assertFalse(forumRequest.header("Cookie").orEmpty().contains("connection_test_clearance"))
        } finally { fixture.repository.signOut(); clearFixtureCookies() }
    }

    @Test fun cancelDoesNotRetryOrClearTheError() {
        var calls = 0
        compose.setContent { MobilismTheme {
            ErrorPanel(ApiFailure(FailureKind.Challenge, "Browser check").forScreen(), onRetry = { calls++ })
        } }
        compose.onNodeWithText("Verify connection").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Browser check").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun loginErrorsCanVerifyWithoutResubmittingCredentials() {
        compose.setContent { MobilismTheme { ErrorPanel(ApiFailure(FailureKind.Challenge, "Browser check").forScreen()) } }
        compose.onNodeWithText("Verify connection").performClick()
        compose.onNodeWithTag("continue-connection-verification").performClick()
        compose.onNodeWithText("Browser check").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertDoesNotExist()
    }

    @Test fun ordinaryErrorsDoNotOfferBrowserVerification() {
        compose.setContent { MobilismTheme { ErrorPanel(ApiFailure(FailureKind.Server, "Unavailable").forScreen()) } }
        compose.onNodeWithText("Verify connection").assertDoesNotExist()
    }

    @Test fun liveBrowserVerificationReturnsToNativePublicReads() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveConnectionVerification") == "true")
        var continued = false
        compose.setContent { MobilismTheme {
            ConnectionVerification(VerificationSite.App, {}, { continued = true })
        } }
        compose.waitUntil(45_000) {
            compose.onAllNodesWithText("Browser check finished. Tap Continue to try again.").fetchSemanticsNodes().isNotEmpty()
        }
        capture("live-verification")
        compose.onNodeWithTag("continue-connection-verification").performClick()
        assertTrue(continued)
        val repository = DefaultMobilismRepository(Fixture().context)
        assertTrue(repository.categories(ContentSection.Apps).isNotEmpty())
        assertTrue(repository.releases(ContentSection.Apps, FeedOrder.Latest, 1).items.isNotEmpty())
    }

    @Test fun verificationStaysOnPublicReadsAndTheFailedOrigin() {
        fun accepts(url: String, site: VerificationSite = VerificationSite.App) =
            acceptsConnectionVerificationUrl(Uri.parse(url), site)
        assertTrue(accepts(VerificationSite.App.url))
        assertTrue(accepts("${VerificationSite.App.url}?__cf_chl_rt_tk=fixture"))
        assertTrue(accepts("https://app.mobilism.org/cdn-cgi/challenge-platform/h/g/orchestrate/chl_page/v1"))
        assertTrue(accepts(VerificationSite.Forum.url, VerificationSite.Forum))
        assertTrue(accepts("https://forum.mobilism.org/index.php", VerificationSite.Forum))
        assertTrue(isConnectionVerificationPageUrl(Uri.parse("https://forum.mobilism.org/index.php"), VerificationSite.Forum))
        assertFalse(isConnectionVerificationPageUrl(Uri.parse("https://forum.mobilism.org/cdn-cgi/challenge-platform/check"), VerificationSite.Forum))
        listOf("https://forum.mobilism.org/ucp.php?mode=login", "https://forum.mobilism.org/posting.php",
            "https://forum.mobilism.org.evil.example/index.php", "http://forum.mobilism.org/index.php",
            "https://user@forum.mobilism.org/index.php", "https://forum.mobilism.org:444/index.php")
            .forEach { assertFalse(it, accepts(it, VerificationSite.Forum)) }
        listOf("https://app.mobilism.org/api-new/message/reply/send", "https://app.mobilism.org/api-new/auth/login",
            "https://forum.mobilism.org/", "https://app.mobilism.org.evil.example/api-new/release/category",
            "https://user@app.mobilism.org/api-new/release/category", "https://app.mobilism.org:444/api-new/release/category",
            "http://app.mobilism.org/api-new/release/category").forEach { assertFalse(it, accepts(it)) }
        assertFalse(accepts(VerificationSite.App.url, VerificationSite.Forum))
    }

    private fun browser(): WebView = WindowInspector.getGlobalWindowViews()
        .asSequence().flatMap(::descendants).filterIsInstance<WebView>().single()

    private fun capture(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(500)
        val screenshot = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory = File(compose.activity.getExternalFilesDir(null), "connection-verification")
        check(directory.mkdirs() || directory.isDirectory)
        File(directory, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
