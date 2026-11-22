package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.settings.DownloadPreferencesStore
import org.mobilism.material.ui.DownloadSettingsScreen

@RunWith(AndroidJUnit4::class)
class PremiumAccessTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun separatePremiumLoginCannotAuthorizeAnotherForumUserOrUnsupportedHost() = runBlocking {
        val base = compose.activity
        val prefix = UUID.randomUUID().toString()
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
        }
        val resolutions = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val response = when {
                request.url.encodedPath.endsWith("/auth/login") -> """{"access_token":"fixture-token"}"""
                request.url.encodedPath.endsWith("/filehosts.xml") ->
                    """<supported-filehosts><host url="files.example"/></supported-filehosts>"""
                request.url.host == "mblservices.org" && request.url.queryParameter("dl") != null -> {
                    resolutions.incrementAndGet()
                    """<a download href="https://cdn.example/file.apk">Download</a>"""
                }
                request.url.host == "mblservices.org" ->
                    """<form name="transload"><input name="link"/><input name="premium_acc"/></form>"""
                else -> error("Unexpected fixture request")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body(response.toResponseBody()).build()
        }.build()
        val forum = DefaultMobilismRepository(context, client = client)
        val premium = DebridRepository(context, client)
        val access = PremiumDownloadAccess(context)
        val settings = DownloadPreferencesStore(context)
        val original = "https://files.example/original"
        context.getSharedPreferences("material-session", Context.MODE_PRIVATE).edit().putBoolean("guest", true).commit()
        forum.restoreSession()
        assertEquals(AuthState.SignedOut, forum.auth.value)
        forum.signIn("ForumAlice", "fixture")
        compose.setContent { MobilismTheme(reduceMotion = true) {
            DownloadSettingsScreen(settings.state.collectAsState().value, settings, premium, {}, forum.auth.collectAsState().value)
        } }
        compose.onNodeWithText("Internal downloader").assertDoesNotExist()
        premium.connectMobilism("DifferentPremiumLogin", "fixture-password")
        compose.onNodeWithText("Internal downloader").assertIsDisplayed()
        assertTrue(access.permits("ForumAlice", original))
        assertFalse(access.permits("ForumAlice", "https://unknown.example/file.apk"))
        assertEquals("https://cdn.example/file.apk", premium.resolve(original))
        forum.signOut()
        assertFalse(access.permits("ForumAlice", original))
        assertTrue(runCatching { premium.resolve(original) }.isFailure)
        forum.signIn("ForumBob", "fixture")
        compose.onNodeWithText("Internal downloader").assertDoesNotExist()
        assertFalse(access.permits("ForumBob", original))
        assertTrue(runCatching { premium.resolve(original) }.isFailure)
        assertEquals(1, resolutions.get())
    }
}
