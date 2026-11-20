package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.DefaultMobilismRepository
import org.mobilism.material.data.ChallengeRecovery
import org.mobilism.material.data.ProfileReference
import org.junit.Assume.assumeTrue

@RunWith(AndroidJUnit4::class)
class NativeCookieContinuityTest {
    private fun isolatedContext(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "cookie-continuity-${System.nanoTime()}"
        return object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("$prefix-$name", mode)
        }
    }

    @Test fun nativeResponseRefreshesBrowserCookieBeforeTheNextRead() = runBlocking {
        val url = "https://app.mobilism.org/"
        val name = "continuity_fixture"
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        manager.setCookie(url, "$name=old; Path=/; Secure; HttpOnly")
        val requests = mutableListOf<Request>()
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "cookie-continuity-${System.nanoTime()}"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("$prefix-$name", mode)
        }
        val repository = DefaultMobilismRepository(context, client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("Fixture")
                .code(200).header("Set-Cookie", "$name=new; Path=/; Secure; HttpOnly; Max-Age=3600")
                .body("""{"response":[]}""".toResponseBody()).build()
        }.build())
        try {
            repository.comments(410, 1)
            repository.comments(411, 1)
            assertEquals("Cookie preservation must not add a request", 2, requests.size)
            assertTrue(requests.first().header("Cookie").orEmpty().contains("$name=old"))
            assertTrue("The second native read must use the cookie from the first response",
                requests.last().header("Cookie").orEmpty().contains("$name=new"))
            assertTrue(manager.getCookie(url).orEmpty().contains("$name=new"))
            assertEquals(requests.first().header("User-Agent"), requests.last().header("User-Agent"))
            assertNull(manager.getCookie("https://forum.mobilism.org/")?.split(';')?.find { it.trim().startsWith("$name=") })
        } finally { manager.setCookie(url, "$name=; Path=/; Secure; Max-Age=0"); manager.flush() }
    }

    @Test fun challengeCookiesReachRecoveryAndTheSingleRetry() = runBlocking {
        val url = "https://app.mobilism.org/"
        val name = "challenge_cookie_fixture"
        val manager = CookieManager.getInstance()
        val requests = mutableListOf<Request>()
        val repository = DefaultMobilismRepository(isolatedContext(), challengeRecovery = ChallengeRecovery {
            assertTrue(manager.getCookie(url).orEmpty().contains("$name=renewed"))
            true
        }, client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            val blocked = requests.size == 1
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("Fixture")
                .code(if (blocked) 403 else 200)
                .apply { if (blocked) {
                    header("cf-mitigated", "challenge")
                    header("Set-Cookie", "$name=renewed; Path=/; Secure; Max-Age=3600")
                } }
                .body((if (blocked) "<html>challenge-platform</html>" else """{"response":[]}""").toResponseBody()).build()
        }.build())
        try {
            repository.comments(412, 1)
            assertEquals(2, requests.size)
            assertTrue(requests.last().header("Cookie").orEmpty().contains("$name=renewed"))
        } finally { manager.setCookie(url, "$name=; Path=/; Secure; Max-Age=0"); manager.flush() }
    }

    @Test fun forumCookiesRotateAndDeleteWithoutReceivingApiCredentials() = runBlocking {
        val url = "https://forum.mobilism.org/"
        val name = "forum_cookie_fixture"
        val manager = CookieManager.getInstance()
        val requests = mutableListOf<Request>()
        val repository = DefaultMobilismRepository(isolatedContext(), client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host == "app.mobilism.org") {
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).message("Fixture").code(200)
                    .body("""{"access_token":"fixture-token"}""".toResponseBody()).build()
            } else {
                requests += request
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).message("Fixture").code(200)
                    .header("Set-Cookie", if (requests.size == 1) "$name=forum; Path=/; Secure; HttpOnly; Max-Age=3600"
                        else "$name=; Path=/; Secure; Max-Age=0")
                    .addHeader("Set-Cookie", "$name=foreign; Domain=example.org; Path=/; Secure")
                    .body("<div id='viewprofile'><ul class='reset-list'><li><b>Username:</b><span>Fixture</span></li></ul></div>".toResponseBody()).build()
            }
        }.build())
        try {
            repository.signIn("fixture", "fixture")
            repeat(3) { repository.profile(ProfileReference(username = "Fixture")) }
            assertEquals(3, requests.size)
            assertTrue(requests[1].header("Cookie").orEmpty().contains("$name=forum"))
            assertFalse(requests[2].header("Cookie").orEmpty().contains("$name="))
            assertTrue(requests.all { it.header("X-Auth-Token") == null })
            assertFalse(manager.getCookie("https://example.org/").orEmpty().contains("$name="))
        } finally { manager.setCookie(url, "$name=; Path=/; Secure; Max-Age=0"); manager.flush() }
    }

    @Test fun nativeCookieSurvivesProcessRestart() = runBlocking {
        val phase = InstrumentationRegistry.getArguments().getString("cookieContinuityPhase")
        assumeTrue(phase == "write" || phase == "read")
        val url = "https://app.mobilism.org/"
        val name = "persistent_cookie_fixture"
        val forum = ProfileReference(username = "Fixture")
        val forumCookie = "forum_restart_fixture"
        val manager = CookieManager.getInstance()
        val requests = mutableListOf<Request>()
        val repository = DefaultMobilismRepository(isolatedContext(), client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("Fixture").code(200)
                .apply { if (phase == "write" && chain.request().url.host == "app.mobilism.org")
                    header("Set-Cookie", "$name=persisted; Path=/; Secure; HttpOnly; Max-Age=3600") }
                .body((if (chain.request().url.host == "forum.mobilism.org")
                    "<div id='viewprofile'><ul class='reset-list'><li>Username: Fixture</li></ul></div>"
                    else """{"response":[]}""").toResponseBody()).build()
        }.build())
        if (phase == "write") {
            manager.setCookie(url, "$name=; Path=/; Secure; Max-Age=0")
            manager.setCookie(forum.url, "$forumCookie=browser; Path=/; Secure; HttpOnly; Max-Age=3600")
            manager.flush()
        }
        try {
            repository.comments(413, 1)
            repository.profile(forum)
            assertEquals(2, requests.size)
            assertTrue(requests.last().header("Cookie").orEmpty().contains("$forumCookie=browser"))
            assertFalse(requests.first().header("Cookie").orEmpty().contains(forumCookie))
            if (phase == "read") assertTrue("A fresh process must send the stored native-response cookie",
                requests.first().header("Cookie").orEmpty().contains("$name=persisted"))
        } finally {
            if (phase == "read") {
                manager.setCookie(url, "$name=; Path=/; Secure; Max-Age=0")
                manager.setCookie(forum.url, "$forumCookie=; Path=/; Secure; Max-Age=0")
                manager.flush()
            }
        }
    }
}
