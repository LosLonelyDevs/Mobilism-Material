package org.mobilism.material

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.DebridProvider
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LiveDebridTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun settingsToReleaseToBrowserWithEachLiveProvider() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveDebrid") == "true")
        val context = instrumentation.targetContext
        val credentialsFile = File(context.filesDir, "debrid-live.json")
        assumeTrue(credentialsFile.isFile)
        val credentials = JSONObject(credentialsFile.readText())
        val app = context.applicationContext as MobilismApplication
        val results = File(context.getExternalFilesDir(null), "live-debrid")
        results.mkdirs()
        context.getSharedPreferences("material-navigation", 0).edit().putString("section", "Apps").commit()
        context.getSharedPreferences("material-downloads", 0).edit().putString("architecture", "Arm64")
            .putBoolean("auto-watch-downloads", false).commit()
        app.developer.unlock()
        app.developer.setDebridEnabled(true)
        app.developer.setVerboseLogging(false)
        for (provider in DebridProvider.entries) app.debrid.removeKey(provider)
        app.debrid.load()
        val opened = AtomicReference<Uri?>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action == Intent.ACTION_VIEW && intent.data?.scheme == "https" &&
                    intent.data?.host != "forum.mobilism.org") opened.set(intent.data)
                return null
            }
        }
        instrumentation.addMonitor(monitor)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var launchIntent: Intent
        scenario.onActivity { launchIntent = Intent(it.intent) }
        var failure: Throwable? = null
        try {
            compose.waitUntil(30_000) {
                exists(hasText("Continue as guest")) || exists(hasContentDescription("Open navigation drawer"))
            }
            if (exists(hasText("Continue as guest"))) compose.onNodeWithText("Continue as guest").performClick()
            for (provider in DebridProvider.entries) {
                compose.waitUntil(30_000) { exists(hasContentDescription("Open navigation drawer")) }
                compose.onNodeWithContentDescription("Open navigation drawer").performClick()
                compose.onNodeWithText("Settings", useUnmergedTree = false).performClick()
                if (provider == DebridProvider.RealDebrid) {
                    scrollTo("Real-Debrid API key")
                    compose.onNodeWithText("Real-Debrid API key").performTextInput(credentials.getString("realToken"))
                    compose.onNodeWithText("Verify and save").performScrollTo().performClick()
                } else if (provider == DebridProvider.MegaDebrid) {
                    scrollTo("MegaDebrid username")
                    compose.onNodeWithText("MegaDebrid username").performTextInput(credentials.getString("megaUsername"))
                    compose.onNodeWithText("MegaDebrid password").performScrollTo().performTextInput(credentials.getString("megaPassword"))
                    compose.onNodeWithText("Sign in to MegaDebrid").performScrollTo().performClick()
                } else {
                    scrollTo("Mobilism username")
                    compose.onNodeWithText("Mobilism username").performTextInput(credentials.getString("mobilismUsername"))
                    compose.onNodeWithText("Mobilism Premium password").performScrollTo().performTextInput(credentials.getString("mobilismPassword"))
                    compose.onNodeWithText("Sign in to Mobilism Premium").performScrollTo().performClick()
                }
                compose.waitUntil(75_000) {
                    app.debrid.state.value.accounts.single { it.provider == provider }.let { it.validated && it.hostsVerified && !it.busy }
                }
                assertEquals(listOf(provider), app.debrid.state.value.supporting(credentials.getString("sourceUrl")).map { it.provider })
                compose.onNodeWithContentDescription("Back").performClick()
                opened.set(null)
                scenario.onActivity { activity ->
                    activity.startActivity(Intent(activity, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW).setData(Uri.parse(credentials.getString("releaseUrl")))
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                }
                val target = SemanticsMatcher("requested download") {
                    it.config.getOrElse(SemanticsProperties.TestTag) { "" }.let { tag ->
                        tag.startsWith("download:") && tag.contains(credentials.getString("fileId"))
                    }
                }
                compose.waitUntil(75_000) {
                    runCatching { compose.onNodeWithTag("release-content").performScrollToNode(target) }.isSuccess
                }
                compose.onNode(target).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                    "Download through ${provider.label}"))
                File(results, "${provider.name}-release.png").outputStream().use {
                    compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                compose.onNode(target).performClick()
                compose.waitUntil(75_000) { opened.get() != null }
                val direct = checkNotNull(opened.get())
                assertEquals("https", direct.scheme)
                assertFalse(direct.host.orEmpty().contains("rapidgator"))
                val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
                client.newCall(Request.Builder().url(direct.toString()).header("Range", "bytes=0-3").build()).execute().use { response ->
                    assertTrue("Resolved download must be readable", response.isSuccessful)
                    val bytes = checkNotNull(response.body).source().readByteArray(4)
                    assertTrue("Resolved file must have an APK ZIP header", bytes.contentEquals(byteArrayOf(0x50, 0x4b, 0x03, 0x04)))
                }
                File(results, "${provider.name}-result.json").writeText(JSONObject()
                    .put("provider", provider.label).put("settingsVerified", true).put("releaseButtonVerified", true)
                    .put("browserIntentVerified", true).put("downloadHeaderVerified", true).toString())
                app.debrid.removeKey(provider)
                instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                compose.waitUntil(15_000) { exists(hasContentDescription("Back")) }
                compose.onNodeWithContentDescription("Back").performClick()
            }
        } catch (cause: Throwable) {
            failure = cause
            throw cause
        } finally {
            credentialsFile.delete()
            instrumentation.removeMonitor(monitor)
            for (provider in DebridProvider.entries) app.debrid.removeKey(provider)
            app.developer.disable()
            try {
                // ActivityScenario matches lifecycle events against the original launch intent.
                scenario.onActivity { it.intent = launchIntent }
                scenario.close()
            } catch (cleanup: Throwable) {
                if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
            }
        }
    }

    private fun exists(matcher: SemanticsMatcher): Boolean = compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun scrollTo(text: String) = compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
}
