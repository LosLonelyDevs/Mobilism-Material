package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import java.net.Socket
import java.io.DataInputStream
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.settings.DownloadPreferences
import org.mobilism.material.ui.ReleaseScreen
import org.mobilism.material.ui.ReleaseViewModel

@SdkSuppress(minSdkVersion = 29)
class LiveReleaseActionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun realReactionsSurviveBlockedLookupAndRepositoryRestart() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("liveReleaseActions") == "true")
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val port = checkNotNull(arguments.getString("localCredentialsPort")?.toIntOrNull())
        val credentials = Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 10_000
            val input = DataInputStream(socket.getInputStream())
            val size = input.readInt()
            require(size in 1..16_384)
            val bytes = ByteArray(size)
            input.readFully(bytes)
            JSONObject(bytes.toString(Charsets.UTF_8))
        }
        var activeModel by mutableStateOf<ReleaseViewModel?>(null)
        compose.setContent { MobilismTheme(reduceMotion = true) {
            activeModel?.let { model ->
                val state by model.state.collectAsState()
                ReleaseScreen(state, DebridState(), DownloadPreferences(), onBack = {}, onRetry = model::load,
                    onMoreComments = model::loadComments, watched = false, downloaded = false,
                    onWatch = {}, onDownloaded = {}, onLike = model::toggleLike,
                    onActionMessageShown = model::actionMessageShown, auth = AuthState.SignedIn("Live test"),
                    onLikePostVerified = model::likePostVerified, onDismissLikeVerification = model::dismissLikeVerification)
            }
        } }
        val scenarios = when (arguments.getString("likeLookupMode")) {
            "blocked" -> listOf(true)
            "normal" -> listOf(false)
            else -> listOf(false, true)
        }
        for (blockedLookup in scenarios) {
            val prefix = "live-likes-${System.nanoTime()}-"
            val preferences = java.util.concurrent.CopyOnWriteArraySet<String>()
            val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
                override fun getApplicationContext(): Context = this
                override fun getSharedPreferences(name: String, mode: Int) =
                    super.getSharedPreferences(prefix + name, mode).also { preferences += prefix + name }
            }
            val sent = AtomicInteger()
            val forumReads = AtomicInteger()
            val confirmed = CopyOnWriteArrayList<Boolean>()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                if (request.url.host == "forum.mobilism.org" && request.url.encodedPath == "/viewtopic.php") {
                    forumReads.incrementAndGet()
                    assertNull(request.header("X-Auth-Token"))
                    if (blockedLookup) return@addInterceptor Response.Builder().request(request)
                        .protocol(Protocol.HTTP_1_1).code(403).message("Forbidden")
                        .header("cf-mitigated", "challenge")
                        .body("<html><title>Just a moment...</title></html>".toResponseBody()).build()
                }
                val like = request.url.encodedPath == "/api-new/post/like"
                if (like) {
                    assertEquals("POST", request.method)
                    assertEquals("id=12725286", okio.Buffer().also(checkNotNull(request.body)::writeTo).readUtf8())
                    sent.incrementAndGet()
                }
                chain.proceed(request).also { response ->
                    if (like && response.isSuccessful) {
                        val value = JSONObject(response.peekBody(64 * 1024).string()).optJSONObject("response")?.opt("liked")
                        when (value) {
                            true, 1, "1", "true" -> confirmed += true
                            false, 0, "0", "false" -> confirmed += false
                        }
                    }
                }
            }.build()
            val repository = DefaultMobilismRepository(context, client = client)
            try {
                repository.signIn(credentials.getString("username"), credentials.getString("password"))
                lateinit var first: ReleaseViewModel
                compose.runOnUiThread {
                    first = ReleaseViewModel(6437968, repository, SavedStateHandle())
                    activeModel = first
                }
                awaitRelease(first)
                clickAndConfirm(first, confirmed)
                assertEquals(1, confirmed.size)
                val originalState = !confirmed.single()
                assertEquals(1, forumReads.get())

                val restored = DefaultMobilismRepository(context, client = client)
                restored.restoreSession()
                lateinit var second: ReleaseViewModel
                compose.runOnUiThread {
                    second = ReleaseViewModel(6437968, restored, SavedStateHandle())
                    activeModel = second
                }
                awaitRelease(second)
                assertEquals(confirmed.single(), second.state.value.release!!.liked)
                clickAndConfirm(second, confirmed)
                assertEquals(originalState, confirmed.last())
                assertEquals("A restart must use the saved original post ID", 1, forumReads.get())
                assertEquals("Each tap must send exactly one mutation", 2, sent.get())
                println("Live thumbs-up: blockedLookup=$blockedLookup; first=${confirmed.first()}; restored=${confirmed.last()}; writes=${sent.get()}; forumReads=${forumReads.get()}")
            } finally {
                try {
                    // Never retry an unconfirmed mutation: it may already have changed the reaction.
                    if (confirmed.size % 2 == 1 && sent.get() == confirmed.size) {
                        val originalState = !confirmed.first()
                        val restored = repository.toggleLike(repository.release(6437968))
                        assertEquals("Cleanup must restore the starting reaction", originalState, restored)
                    }
                    assertEquals("An unconfirmed write needs a manual reaction check", sent.get(), confirmed.size)
                } finally {
                    repository.signOut()
                    preferences.forEach(context::deleteSharedPreferences)
                }
            }
        }
    }

    private fun awaitRelease(model: ReleaseViewModel) {
        compose.waitUntil(45_000) { !model.state.value.busy }
        assertNull(model.state.value.error)
        assertNotNull(model.state.value.release)
    }

    private fun clickAndConfirm(model: ReleaseViewModel, confirmed: List<Boolean>) {
        val before = confirmed.size
        fun click() = compose.onNodeWithContentDescription(
            if (model.state.value.release?.liked == true) "Remove thumbs up" else "Thumbs up").performClick()
        click()
        compose.waitUntil(60_000) {
            model.state.value.let { it.likeNeedsVerification || confirmed.size > before || !it.likeBusy && it.actionMessage != null }
        }
        if (confirmed.size == before) {
            assertTrue("Expected browser verification: ${model.state.value.actionMessage}",
                model.state.value.likeNeedsVerification || model.state.value.release?.likeId != null)
            try {
                compose.waitUntil(60_000) { !model.state.value.likeNeedsVerification && model.state.value.release?.likeId != null }
            } catch (failure: Throwable) {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "live-release")
                check(directory.mkdirs() || directory.isDirectory)
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    File(directory, "verification-timeout.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                compose.runOnIdle {
                    WindowInspector.getGlobalWindowViews().asSequence().flatMap(::descendants)
                        .filterIsInstance<WebView>().forEach { browser ->
                            println("Verification page: title=${browser.title}; progress=${browser.progress}")
                            browser.evaluateJavascript("JSON.stringify({ready:document.readyState,posts:document.querySelectorAll('div.post[id],div[id] article .postbody').length})") {
                                println("Verification document: $it")
                            }
                        }
                }
                throw failure
            }
            assertEquals(12725286L, model.state.value.release!!.likeId)
            click()
        }
        compose.waitUntil(45_000) { confirmed.size > before && !model.state.value.likeBusy }
        val liked = confirmed.last()
        assertEquals(liked, model.state.value.release!!.liked)
        if (liked) compose.onNodeWithContentDescription("Remove thumbs up").assertIsOn()
        else compose.onNodeWithContentDescription("Thumbs up").assertIsOff()
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
