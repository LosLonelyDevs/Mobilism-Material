package org.mobilism.material.ui

import android.content.Context
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.members.MemberIdentityCache
import org.mobilism.material.members.MemberIdentityStore

class ProfileRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val reference = ProfileReference(username = "Fixture")
    private val homepage = "<html><a href='viewforum.php?f=1'>Forum</a><a href='ucp.php?mode=login'>Login</a></html>"
    private val member = """<div id='viewprofile'><div class='span2'><ul class='unstyled'>
        <li><img src='./fixture.png'></li><li>Major Android Releaser</li></ul></div>
        <ul class='reset-list'><li>Username: Fixture</li></ul></div>"""
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun optionalIdentityDoesNotStartVerificationOrRetry() = exercise(optional = true)
    @Test fun readStillChallengedAfterHomepageIsNotRecovered() = exercise()
    @Test fun homepageInPlaceOfAProfileIsNotRecovered() = exercise(secondBody = homepage)
    @Test fun anotherMemberIsNotRecovered() = exercise(secondBody = member.replace("Username: Fixture", "Username: Another"))
    @Test fun realProfileRetryPublishesIdentityAndRecoveryTogether() = exercise(secondBody = member)

    private fun exercise(optional: Boolean = false, secondBody: String? = null) {
        val controller = ConnectionRecoveryController()
        val requests = CopyOnWriteArrayList<String>()
        val browsers = AtomicInteger()
        val done = AtomicBoolean()
        val repository = DefaultMobilismRepository(context, challengeRecovery = controller,
            client = OkHttpClient.Builder().addInterceptor { chain ->
                requests += chain.request().url.toString()
                val body = if (requests.size == 2) secondBody else null
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("Fixture")
                    .code(if (body == null) 403 else 200)
                    .apply { if (body == null) header("cf-mitigated", "challenge") }
                    .body((body ?: "<html>challenge-platform</html>").toResponseBody()).build()
            }.build())
        val store = MemberIdentityStore(repository::profileForIdentity)
        compose.setContent { MobilismTheme {
            ConnectionRecoveryHost(controller, createBrowser = { ctx ->
                browsers.incrementAndGet()
                object : WebView(ctx) {
                    override fun loadUrl(url: String) {
                        settings.blockNetworkLoads = true
                        super.loadDataWithBaseURL(url, homepage, "text/html", "UTF-8", url)
                    }
                }
            }, readExpiry = { ClearanceExpiry(null, false) }, quietTimeoutMillis = 60_000) {
                LaunchedEffect(Unit) {
                    if (optional) {
                        store.request("Fixture")
                        store.request("Another")
                    } else {
                        try { store.remember(repository.profile(reference)) } catch (_: ApiFailure) { }
                    }
                    done.set(true)
                }
                Text("Reading release")
            }
        } }
        compose.waitUntil(90_000) { done.get() }
        compose.onNodeWithText("Reading release").assertIsDisplayed()
        compose.onNodeWithText("Verify connection").assertDoesNotExist()
        assertEquals(List(if (optional) 1 else 2) { reference.url }, requests.toList())
        assertEquals(if (optional) 0 else 1, browsers.get())
        val expected = secondBody == member
        assertEquals(expected, VerificationSite.Forum in controller.completed.value)
        assertEquals(expected, store.identities.value["fixture"]?.majorAndroidReleaser == true)
    }

    @Test fun identityCacheSurvivesProcessRestart() = runBlocking {
        val phase = InstrumentationRegistry.getArguments().getString("identityCachePhase")
        assumeTrue(phase == "seed" || phase == "restore")
        val prefs = context.getSharedPreferences("restart-identity-fixture", Context.MODE_PRIVATE)
        val cache = MemberIdentityCache(prefs)
        val loads = AtomicInteger()
        val store = MemberIdentityStore({ loads.incrementAndGet(); error("Offline fixture") }, cache = cache)
        if (phase == "seed") {
            prefs.edit().clear().commit()
            store.remember(ForumProfiles.parse(member, reference))
            assertTrue(prefs.edit().putInt("test-process", android.os.Process.myPid()).commit())
        } else {
            try {
                assertNotEquals(prefs.getInt("test-process", -1), android.os.Process.myPid())
                store.request("Missing")
                store.request("FIXTURE")
                val identity = store.identities.value.getValue("fixture")
                assertTrue(identity.majorAndroidReleaser)
                assertEquals("https://forum.mobilism.org/fixture.png", identity.avatar)
                assertEquals(1, loads.get())
            } finally { prefs.edit().clear().commit() }
        }
    }

    @Test fun restoringAnIdentityDoesNotExtendItsExpiry() = runBlocking {
        var wallTime = 10_000L
        val ttl = ApiRequestLimits.Defaults.memberCacheTtlMs
        val prefs = context.getSharedPreferences("expiry-identity-fixture", Context.MODE_PRIVATE)
        val cache = MemberIdentityCache(prefs) { wallTime }
        try {
            prefs.edit().clear().commit()
            val store = MemberIdentityStore({ error("Offline fixture") }, cache = cache)
            store.remember(ForumProfiles.parse(member, reference))
            wallTime += ttl - 1
            val restored = MemberIdentityStore({ error("Offline fixture") }, now = { wallTime }, cache = cache)
            restored.request("Fixture")
            assertTrue(restored.identities.value.getValue("fixture").majorAndroidReleaser)
            wallTime++
            restored.request("Fixture")
            assertTrue(restored.identities.value.isEmpty())
        } finally { prefs.edit().clear().commit() }
    }
}
