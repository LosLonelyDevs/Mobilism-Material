package org.mobilism.material.ui

import android.content.Context
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    @Test fun optionalMemberLookupsStopAtTheChallenge() = runBlocking {
        val calls = AtomicInteger()
        val store = MemberIdentityStore(challengedRepository { calls.incrementAndGet() }::profile)
        store.request("Fixture")
        store.request("Another")
        assertEquals(1, calls.get())
        assertTrue(store.identities.value.isEmpty())
    }

    @Test fun explicitVisitOpensThePageAndReturnsWithTheMemberIdentity() = exercise(member)
    @Test fun anotherMemberKeepsTheCheckOpenUntilCancelled() =
        exercise(member.replace("Username: Fixture", "Username: Another"))
    @Test fun forumHomepageDoesNotCountAsTheRequestedProfile() = exercise(homepage)
    @Test fun plainForbiddenResponseDoesNotAutomaticallyOpenVerification() = exercise("", challenge = false)
    @Test fun explicitHelpVisitReturnsDirectlyToTheArticle() = exercise(
        "<h2 class='topic-title'><a href='${DownloadHelp.URL}'>Fixture download instructions</a></h2>" +
            "<div class='post' id='p1'><div class='postbody'><div class='content'>Fixture steps</div></div></div>",
        help = true)

    private fun challengedRepository(challenge: Boolean = true, onRead: (String) -> Unit) =
        DefaultMobilismRepository(context, client = OkHttpClient.Builder().addInterceptor { chain ->
            onRead(chain.request().url.toString())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("Fixture").code(403)
                .apply { if (challenge) header("cf-mitigated", "challenge") }
                .body("<html>Forbidden</html>".toResponseBody()).build()
        }.build())

    private fun exercise(page: String, challenge: Boolean = true, help: Boolean = false) {
        val url = if (help) DownloadHelp.URL else reference.url
        val reads = CopyOnWriteArrayList<String>()
        val pages = CopyOnWriteArrayList<String>()
        val inspected = AtomicBoolean()
        val repository = challengedRepository(challenge) { reads.add(it) }
        val store = MemberIdentityStore(repository::profile)
        compose.setContent { MobilismTheme(reduceMotion = true) {
            CompositionLocalProvider(LocalMemberIdentities provides store, LocalForumVerificationBrowser provides { ctx ->
                object : WebView(ctx) {
                    override fun loadUrl(url: String) {
                        pages.add(url)
                        settings.blockNetworkLoads = true
                        loadDataWithBaseURL(url, page, "text/html", "UTF-8", url)
                    }
                    override fun evaluateJavascript(script: String, callback: android.webkit.ValueCallback<String>?) {
                        super.evaluateJavascript(script) { result -> callback?.onReceiveValue(result); inspected.set(true) }
                    }
                }
            }) {
                if (help) DownloadHelpScreen(repository::downloadHelp, onBack = {}, onRelease = {})
                else UserProfileScreen(reference, repository::profile, onBack = {})
            }
        } }
        if (challenge) {
            compose.waitUntil(15_000) { inspected.get() }
            if (help) {
                compose.onNodeWithText("Fixture download instructions").assertIsDisplayed()
                compose.onNodeWithText("Cancel").assertDoesNotExist()
            } else if (page == member) {
                compose.waitUntil(10_000) { store.identities.value["fixture"]?.majorAndroidReleaser == true }
                compose.onNodeWithText("Verify forum access").assertDoesNotExist()
                assertEquals("https://forum.mobilism.org/fixture.png", store.identities.value.getValue("fixture").avatar)
            } else {
                compose.onNodeWithText("Cancel").performClick()
                compose.onNodeWithText("Cancel").assertDoesNotExist()
                assertTrue(store.identities.value.isEmpty())
            }
        } else {
            compose.waitUntil(10_000) { reads.isNotEmpty() }
            compose.onNodeWithText("Cancel").assertDoesNotExist()
        }
        assertEquals(listOf(url), reads.toList())
        assertEquals(if (challenge) listOf(url) else emptyList(), pages.toList())
    }

    @Test fun identityCacheSurvivesProcessRestart() = runBlocking {
        val phase = InstrumentationRegistry.getArguments().getString("identityCachePhase")
        assumeTrue(phase == "seed" || phase == "restore")
        val prefs = context.getSharedPreferences("restart-identity-fixture", Context.MODE_PRIVATE)
        val avatar = if (phase == "seed") "https://image.example.invalid/${java.util.UUID.randomUUID()}.png"
            else requireNotNull(prefs.getString("test-avatar", null))
        val day = 24 * 60 * 60_000L
        val cache = MemberIdentityCache(prefs) { if (phase == "restore") day * 2 else day }
        val loads = AtomicInteger()
        val store = MemberIdentityStore({ loads.incrementAndGet(); error("Offline fixture") }, cache = cache)
        if (phase == "seed") {
            prefs.edit().clear().commit()
            store.remember(ForumProfiles.parse(member, reference).copy(avatar = avatar,
                roles = setOf(AccountRole.Administrator, AccountRole.Developer)))
            assertTrue(prefs.edit().putInt("test-process", android.os.Process.myPid()).putString("test-avatar", avatar).commit())
        } else {
            try {
                assertNotEquals(prefs.getInt("test-process", -1), android.os.Process.myPid())
                assertFalse(store.restoreCached("Missing"))
                assertTrue(store.restoreCached("Fixture"))
                store.request("FIXTURE")
                val identity = store.identities.value.getValue("fixture")
                assertTrue(identity.majorAndroidReleaser)
                assertTrue(identity.administrator)
                assertTrue(org.mobilism.material.members.MemberBadge.Developer in identity.badges)
                assertEquals(avatar, identity.avatar)
                assertEquals(0, loads.get())
            } finally { prefs.edit().clear().commit() }
        }
        val fetches = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            fetches.incrementAndGet()
            check(phase == "seed" && chain.request().url.toString() == avatar)
            val bytes = java.io.ByteArrayOutputStream()
            val bitmap = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes)
            bitmap.recycle()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("Fixture").code(200)
                .header("Content-Type", "image/png").body(bytes.toByteArray().toResponseBody()).build()
        }.build()
        val loader = org.mobilism.material.images.appImageLoader(context, client)
        try {
            val request = coil3.request.ImageRequest.Builder(context).data(avatar).size(16, 16).build()
            val result = loader.execute(request) as coil3.request.SuccessResult
            assertEquals(if (phase == "seed") coil3.decode.DataSource.NETWORK else coil3.decode.DataSource.DISK, result.dataSource)
            assertEquals(if (phase == "seed") 1 else 0, fetches.get())
        } finally { loader.shutdown() }
    }

    @Test fun equivalentForumImagesShareMemoryAndDiskBeforeNetworkFetch() = runBlocking {
        val path = "forum.mobilism.org/images/fixture-${java.util.UUID.randomUUID()}.png"
        val fetches = AtomicInteger()
        val outcomes = mutableListOf<org.mobilism.material.data.CacheOutcome>()
        val logger = org.mobilism.material.data.DiagnosticLogger { event -> event.cache?.outcome?.let(outcomes::add) }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("https://$path", chain.request().url.toString())
            fetches.incrementAndGet()
            val bytes = java.io.ByteArrayOutputStream()
            val bitmap = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes)
            bitmap.recycle()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("Fixture").code(200)
                .header("Content-Type", "image/png").body(bytes.toByteArray().toResponseBody()).build()
        }.build()
        val loader = org.mobilism.material.images.appImageLoader(context, client, logger)
        try {
            suspend fun load(scheme: String) = loader.execute(coil3.request.ImageRequest.Builder(context)
                .data("$scheme://$path").size(16, 16).build()) as coil3.request.SuccessResult
            assertEquals(coil3.decode.DataSource.NETWORK, load("http").dataSource)
            assertEquals(coil3.decode.DataSource.MEMORY_CACHE, load("https").dataSource)
            loader.memoryCache?.clear()
            assertEquals(coil3.decode.DataSource.DISK, load("https").dataSource)
            assertEquals(1, fetches.get())
            assertEquals(listOf("Miss", "MemoryHit", "DiskHit"), outcomes.map { it.name })
        } finally { loader.shutdown() }
    }

    @Test fun restoringAnIdentityDoesNotExtendItsExpiry() = runBlocking {
        var wallTime = 10_000L
        val ttl = ApiRequestLimits.Defaults.memberCacheTtlMs
        val displayLifetime = 30L * 24 * 60 * 60_000
        val prefs = context.getSharedPreferences("expiry-identity-fixture", Context.MODE_PRIVATE)
        val cache = MemberIdentityCache(prefs) { wallTime }
        try {
            prefs.edit().clear().commit()
            val store = MemberIdentityStore({ error("Offline fixture") }, cache = cache)
            store.remember(ForumProfiles.parse(member, reference))
            assertEquals(displayLifetime, cache.read("fixture", ttl)?.remainingMillis)
            wallTime += displayLifetime - 1
            val restored = MemberIdentityStore({ error("Offline fixture") }, now = { wallTime }, cache = cache)
            restored.request("Fixture")
            assertTrue(restored.identities.value.getValue("fixture").majorAndroidReleaser)
            wallTime++
            restored.request("Fixture")
            assertTrue(restored.identities.value.isEmpty())
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun anExistingInstallationReusesItsOldMemberRecord() = runBlocking {
        val day = 24 * 60 * 60_000L
        val prefs = context.getSharedPreferences("legacy-identity-fixture", Context.MODE_PRIVATE)
        try {
            prefs.edit().putString("fixture",
                """{"saved":1000,"ttl":3600000,"avatar":"https://image.example.invalid/member.png","admin":true,"major":true}""").commit()
            val loads = AtomicInteger()
            val store = MemberIdentityStore({ loads.incrementAndGet(); error("Offline") },
                cache = MemberIdentityCache(prefs) { 1000L + day })
            store.request(" Fixture ")
            val identity = store.identities.value.getValue("fixture")
            assertTrue(identity.administrator && identity.majorAndroidReleaser)
            assertEquals("https://image.example.invalid/member.png", identity.avatar)
            assertEquals(0, loads.get())
        } finally { prefs.edit().clear().commit() }
    }
}
