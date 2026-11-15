package org.mobilism.material.ui

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewFeature
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
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme

class QuietConnectionRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val loaded = CopyOnWriteArrayList<String>()
    private val browsers = CopyOnWriteArrayList<WebView>()
    private val success = """<html><body><pre>{"app":[],"games":[],"books":[]}</pre></body></html>"""
    private val noExpiry: (VerificationSite) -> ClearanceExpiry = { ClearanceExpiry(null, false) }

    private fun context(): Context {
        val prefix = "quiet-connection-${UUID.randomUUID()}"
        return object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("$prefix-$name", mode)
        }
    }

    private fun browser(complete: Boolean): (Context) -> WebView = { context ->
        object : WebView(context) {
            override fun loadUrl(url: String) {
                loaded += url
                if (complete) CookieManager.getInstance().setCookie(url, "quiet_test_clearance=fixture; Path=/; Secure")
                super.loadDataWithBaseURL(url, if (complete) success else "<html><body id='cf-chl-widget'>Check fixture</body></html>",
                    "text/html", "UTF-8", url)
            }
        }.also { browsers += it }
    }

    @After fun cleanup() {
        compose.runOnIdle {
            CookieManager.getInstance().setCookie(VerificationSite.App.url, "quiet_test_clearance=; Path=/; Secure; Max-Age=0")
            CookieManager.getInstance().setCookie(VerificationSite.App.url, "cf_clearance=; Path=/api-new/release/category; Secure; Max-Age=0")
            CookieManager.getInstance().flush()
        }
    }

    @Test fun challengedFeedResumesWithoutOpeningVerificationOrPressingContinue() {
        val controller = ConnectionRecoveryController()
        val requests = CopyOnWriteArrayList<Request>()
        val repository = DefaultMobilismRepository(context(), challengeRecovery = controller,
            client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                requests += request
                val cleared = requests.size > 1 && request.header("Cookie").orEmpty().contains("quiet_test_clearance=fixture")
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).message("Fixture")
                    .code(if (cleared) 200 else 403)
                    .apply { if (!cleared) header("cf-mitigated", "challenge") }
                    .body((if (cleared) """{"response":[]}""" else "<html>challenge-platform</html>").toResponseBody()).build()
            }.build())
        var state by mutableStateOf(FeedState(busy = true))
        compose.setContent { MobilismTheme(reduceMotion = true) {
            ConnectionRecoveryHost(controller, browser(true), noExpiry) {
                LaunchedEffect(Unit) {
                    try {
                        val page = repository.releases(ContentSection.Apps, FeedOrder.Latest, 1)
                        state = FeedState(items = page.items, canLoadMore = page.canLoadMore)
                    } catch (failure: Exception) { state = FeedState(error = failure.forScreen()) }
                }
                FeedScreen(state, AuthState.Guest, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, CategoryState(), {}, {})
            }
        } }
        compose.waitUntil(15_000) { requests.size == 2 && !state.busy }
        assertNull(state.error)
        assertEquals(1, loaded.size)
        assertEquals(requests.first().url, requests.last().url)
        compose.onNodeWithText("No releases found.").assertIsDisplayed()
        compose.onNodeWithText("Verify connection").assertDoesNotExist()
        compose.onNodeWithText("Continue").assertDoesNotExist()
        capture("quiet-feed-recovery")
    }

    @Test fun upcomingExpiryStartsOneQuietCheckWithoutInterruptingBrowsing() {
        val controller = ConnectionRecoveryController()
        val expiresAt = System.currentTimeMillis() + 90_000
        compose.setContent { MobilismTheme {
            ConnectionRecoveryHost(controller, browser(true), {
                if (it == VerificationSite.App) ClearanceExpiry(expiresAt, true) else ClearanceExpiry(null, false)
            }, pollMillis = 100) { Text("Keep browsing") }
        } }
        compose.waitUntil(10_000) { controller.completed.value[VerificationSite.App] != null }
        compose.onNodeWithText("Keep browsing").assertIsDisplayed()
        compose.onNodeWithText("Verify connection").assertDoesNotExist()
        android.os.SystemClock.sleep(350)
        assertEquals(listOf(VerificationSite.App.url), loaded.toList())
    }

    @Test fun existingChallengeErrorResumesAfterVerificationWithoutAnotherRetryTap() {
        val controller = ConnectionRecoveryController()
        val error = ScreenError("Cloudflare needs a browser check.", verificationSite = VerificationSite.App)
        var retries by mutableIntStateOf(0)
        compose.setContent { MobilismTheme {
            ConnectionRecoveryHost(controller, browser(true), noExpiry) {
                if (retries == 0) ErrorPanel(error, onRetry = { retries++ })
                else Text("Loading resumed")
            }
        } }
        compose.onNodeWithText("Verify connection").performClick()
        compose.waitUntil(10_000) { retries == 1 }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Loading resumed").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithText("Cloudflare needs a browser check.").assertDoesNotExist()
        compose.onNodeWithText("Continue").assertDoesNotExist()
        assertEquals(1, loaded.size)
    }

    @Test fun knownCookieExpiryIsReadFromWebViewAndAvailableAfterReaderRecreation() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO))
        val context = context()
        compose.runOnIdle {
            val now = System.currentTimeMillis()
            val cookies = CookieManager.getInstance()
            cookies.setCookie(VerificationSite.App.url, "cf_clearance=expiry-fixture; Path=/api-new/release/category; Secure; Max-Age=90")
            cookies.flush()
            val expiry = ClearanceCookies(context).read(VerificationSite.App)
            assertTrue(expiry.present)
            assertTrue(checkNotNull(expiry.expiresAt) in (now + 80_000)..(now + 100_000))
            assertEquals(expiry, ClearanceCookies(context).read(VerificationSite.App))
        }
    }

    @Test fun interactiveFallbackKeepsTheSameBrowserAndAutomaticallyResumes() {
        val controller = ConnectionRecoveryController()
        var recovered by mutableStateOf<Boolean?>(null)
        compose.setContent { MobilismTheme {
            ConnectionRecoveryHost(controller, browser(false), noExpiry, quietTimeoutMillis = 1_500) {
                LaunchedEffect(Unit) { recovered = controller.recover(VerificationSite.App) }
                Text(if (recovered == true) "Loading resumed" else "Loading")
            }
        } }
        compose.waitUntil(10_000) { controller.attempt.value?.interactive == true }
        compose.onNodeWithText("Verify connection").assertIsDisplayed()
        compose.onNodeWithText("Complete the browser check. Loading will resume automatically.").assertIsDisplayed()
        assertEquals(1, browsers.size)
        assertEquals(1, loaded.size)
        capture("interactive-fallback")
        compose.runOnIdle {
            browsers.single().loadDataWithBaseURL(VerificationSite.App.url, success, "text/html", "UTF-8", VerificationSite.App.url)
        }
        compose.waitUntil(10_000) { recovered != null }
        assertEquals(true, recovered)
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Loading resumed").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithText("Loading resumed").assertIsDisplayed()
        compose.onNodeWithText("Verify connection").assertDoesNotExist()
        assertEquals(1, browsers.size)
    }

    @Test fun backgroundingStopsQuietVerificationAndDoesNotOpenADialog() {
        val controller = ConnectionRecoveryController()
        var recovered: Boolean? = null
        compose.setContent { MobilismTheme {
            ConnectionRecoveryHost(controller, browser(false), noExpiry, quietTimeoutMillis = 60_000) {
                LaunchedEffect(Unit) { recovered = controller.recover(VerificationSite.App) }
                Text("Browsing")
            }
        } }
        compose.waitUntil(10_000) { loaded.isNotEmpty() }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitUntil(10_000) { controller.attempt.value == null }
        assertEquals(false, recovered)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("Verify connection").assertDoesNotExist()
    }

    @Test fun cancelLeavesTheReadUnrecoveredAndClosesVerification() {
        val controller = ConnectionRecoveryController()
        var recovered: Boolean? = null
        compose.setContent { MobilismTheme {
            ConnectionRecoveryHost(controller, browser(false), noExpiry, quietTimeoutMillis = 100) {
                LaunchedEffect(Unit) { recovered = controller.recover(VerificationSite.App) }
                Text("Browsing")
            }
        } }
        compose.waitUntil(10_000) { controller.attempt.value?.interactive == true }
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil { recovered != null }
        assertEquals(false, recovered)
        compose.onNodeWithText("Browsing").assertIsDisplayed()
        assertNull(controller.attempt.value)
    }

    @Test fun liveQuietVerificationAllowsNativeCategoriesAndLatest() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveConnectionVerification") == "true")
        val controller = ConnectionRecoveryController()
        var recovered: Boolean? = null
        compose.setContent { MobilismTheme {
            ConnectionRecoveryHost(controller, readExpiry = noExpiry) {
                LaunchedEffect(Unit) { recovered = controller.recover(VerificationSite.App) }
                Text("Browsing continues")
            }
        } }
        compose.waitUntil(45_000) { recovered != null || controller.attempt.value?.interactive == true }
        assertEquals("Live check must finish without user interaction", true, recovered)
        compose.onNodeWithText("Verify connection").assertDoesNotExist()
        val repository = DefaultMobilismRepository(context(), challengeRecovery = controller)
        assertTrue(repository.categories(ContentSection.Apps).isNotEmpty())
        assertTrue(repository.releases(ContentSection.Apps, FeedOrder.Latest, 1).items.isNotEmpty())
        capture("live-quiet-verification")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(500)
        val screenshot = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory = File(compose.activity.getExternalFilesDir(null), "quiet-connection-recovery")
        check(directory.mkdirs() || directory.isDirectory)
        File(directory, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
