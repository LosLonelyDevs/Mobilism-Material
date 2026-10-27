package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
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
import org.mobilism.material.designsystem.SolarizedDark
import org.mobilism.material.settings.DownloadPreferences
import org.mobilism.material.ui.*

@RunWith(AndroidJUnit4::class)
class ReleaseActionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun likeUsesCurrentForumPostAndRestoresConfirmedState() = runBlocking {
        val requests = CopyOnWriteArrayList<Request>()
        var liked = false
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("like-test-$name", mode)
        }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = when (request.url.encodedPath.removePrefix("/api-new/")) {
                "auth/login" -> """{"access_token":"sample-session"}"""
                "release/6437968" -> """{"response":{"id":6437968,"subject":"Mobilism Material v0.1.8","name":"Author","section":"app"}}"""
                "/viewtopic.php" -> {
                    assertEquals("forum.mobilism.org", request.url.host)
                    assertEquals("6437968", request.url.queryParameter("t"))
                    assertNull(request.header("X-Auth-Token"))
                    """<div id="p12725286" class="row-fluid blue-bar"><article role="article">
                        <div class="postbody"><div class="content">Release</div></div></article></div>
                        <div id="p12726994" class="row-fluid blue-bar"><article><div class="postbody">Reply</div></article></div>"""
                }
                "post/like" -> {
                    requests += request
                    assertEquals("POST", request.method)
                    assertEquals("sample-session", request.header("X-Auth-Token"))
                    assertEquals("id=12725286", okio.Buffer().also(checkNotNull(request.body)::writeTo).readUtf8())
                    liked = !liked
                    """{"response":{"liked":$liked}}"""
                }
                else -> {
                    check(request.url.encodedPath.startsWith("/api-new/release/6437968/comments/"))
                    """{"response":[]}"""
                }
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body(body.toResponseBody()).build()
        }.build()
        val repository = DefaultMobilismRepository(context, client = client)
        repository.signIn("Like Reader", "sample-password")
        try {
            lateinit var model: ReleaseViewModel
            compose.runOnUiThread { model = ReleaseViewModel(6437968, repository, SavedStateHandle()) }
            compose.setContent { MobilismTheme(theme = SolarizedDark, reduceMotion = true) {
                val state by model.state.collectAsState()
                ReleaseScreen(state, DebridState(), DownloadPreferences(), onBack = {}, onRetry = model::load,
                    onMoreComments = model::loadComments, watched = true, downloaded = true, onWatch = {}, onDownloaded = {},
                    onLike = model::toggleLike, onActionMessageShown = model::actionMessageShown,
                    auth = AuthState.SignedIn("Like Reader"))
            } }
            compose.waitUntil(10_000) { !model.state.value.busy }
            compose.onNodeWithContentDescription("Thumbs up").performClick()
            compose.waitUntil(10_000) { model.state.value.release?.liked == true && !model.state.value.likeBusy }
            compose.onNodeWithContentDescription("Remove thumbs up").assertIsOn()
            assertEquals(true, repository.release(6437968).liked)
            val watch = compose.onNodeWithContentDescription("Unwatch app").fetchSemanticsNode().boundsInRoot
            val download = compose.onNodeWithContentDescription("Clear downloaded mark").fetchSemanticsNode().boundsInRoot
            assertTrue("Selected action circles need a visible gap", download.left > watch.right)
            val directory = File(compose.activity.getExternalFilesDir(null), "download-polish")
            check(directory.mkdirs() || directory.isDirectory)
            File(directory, "release-actions.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithContentDescription("Remove thumbs up").performClick()
            compose.waitUntil(10_000) { model.state.value.release?.liked == false && !model.state.value.likeBusy }
            compose.onNodeWithContentDescription("Thumbs up").assertIsOff()
            assertEquals(false, repository.release(6437968).liked)
            assertEquals(2, requests.size)
        } finally { repository.signOut() }
    }
}
