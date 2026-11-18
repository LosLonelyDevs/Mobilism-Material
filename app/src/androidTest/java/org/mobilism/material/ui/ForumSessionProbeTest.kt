package org.mobilism.material.ui

import android.webkit.CookieManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.MobilismApplication
import org.mobilism.material.data.ChallengeRecovery
import org.mobilism.material.data.DefaultMobilismRepository
import org.mobilism.material.data.ProfileReference
import org.mobilism.material.designsystem.MobilismTheme

/** Opt-in public read probe. Never include this in a live run without an approved request budget. */
@RunWith(AndroidJUnit4::class)
class ForumSessionProbeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun publicProfileAndCookieContinuity() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("liveForumSessionProbe") == "true")
        val phase = arguments.getString("phase") ?: "verify"
        require(phase in setOf("verify", "restart"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".formattingpreview"))
        check(context.applicationContext !is MobilismApplication)
        val controller = ConnectionRecoveryController()
        val attempts = AtomicInteger()
        val completed = AtomicBoolean()
        val failure = AtomicReference<Throwable?>()
        val userAgent = AtomicReference<String?>()
        val budget = if (phase == "verify") 2 else 1
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun report(event: String) = instrumentation.sendStatus(2, Bundle().apply {
            putString("forumProbe", "$phase $event")
        })
        fun hasClearance(header: String?) = header.orEmpty().split(';').any { it.trim().startsWith("cf_clearance=") }
        report("pid=${android.os.Process.myPid()} storedClearance=${hasClearance(CookieManager.getInstance().getCookie("https://forum.mobilism.org/"))}")
        val client = OkHttpClient.Builder().addNetworkInterceptor { chain ->
            val request = chain.request()
            if (request.url.host != "forum.mobilism.org" || request.url.encodedPath != "/memberlist.php" ||
                request.method != "GET" || attempts.incrementAndGet() > budget) throw IOException("Probe request budget reached")
            userAgent.set(request.header("User-Agent"))
            report("read=${attempts.get()} clearanceSent=${hasClearance(request.header("Cookie"))}")
            chain.proceed(request).also { response ->
                report("http=${response.code} challengeHeader=${response.header("cf-mitigated") == "challenge"}")
            }
        }.build()
        val repository = DefaultMobilismRepository(context, client = client,
            challengeRecovery = if (phase == "verify") controller else ChallengeRecovery.None)
        compose.setContent { MobilismTheme(reduceMotion = true) {
            ConnectionRecoveryHost(controller, readExpiry = { ClearanceExpiry(null, false) }) {
                Text("Checking public forum access")
                LaunchedEffect(controller) {
                    controller.attempt.collect { attempt ->
                        if (attempt != null) report("browser=${if (attempt.interactive) "visible" else "quiet"}")
                    }
                }
                LaunchedEffect(Unit) {
                    try {
                        val profile = repository.profile(ProfileReference(username = "spydog"))
                        check(profile.username.equals("spydog", true))
                        report("profileMatched=true avatarUrlPresent=${!profile.avatar.isNullOrBlank()} releaserBadge=${profile.majorAndroidReleaser}")
                    } catch (error: Exception) { failure.set(error) }
                    finally { completed.set(true) }
                }
            }
        } }
        compose.waitUntil(60_000) { completed.get() }
        failure.get()?.let { throw AssertionError("Public profile did not load: ${it.message}", it) }
        assertTrue(attempts.get() <= budget)
        val preferences = context.getSharedPreferences("forum-session-probe", 0)
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(userAgent.get().orEmpty().toByteArray())
            .joinToString("") { "%02x".format(it) }
        if (phase == "verify") preferences.edit().putString("user-agent-hash", hash).commit()
        else assertEquals("Request identity changed across processes", preferences.getString("user-agent-hash", null), hash)
        val clearance = CookieManager.getInstance().getCookie("https://forum.mobilism.org/").orEmpty()
            .split(';').any { it.trim().startsWith("cf_clearance=") }
        report("profile=ok nativeRequests=${attempts.get()} clearancePresent=$clearance")
    }
}
