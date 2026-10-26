package org.mobilism.material.ui

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.settings.DiagnosticLogStore

@RunWith(AndroidJUnit4::class)
class ReleaseReliabilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun commentAndQuoteRenderEmojiInNativeTextViews() {
        compose.setContent { MobilismTheme(reduceMotion = true) {
            ReleaseCommentCard(ReleaseComment(12726994, "Disk4mat", null, 0,
                "Taking a look now, very interesting. 😊", quotes = listOf(CommentQuote("Reader", "👍🏽"))),
                "https://forum.mobilism.org/", onImage = { _, _ -> })
        } }
        compose.waitForIdle()
        compose.runOnUiThread {
            fun texts(view: View): List<String> = when (view) {
                is TextView -> listOf(view.text.toString())
                is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
                else -> emptyList()
            }
            val visible = texts(compose.activity.window.decorView)
            assertTrue(visible.contains("Taking a look now, very interesting. 😊"))
            assertTrue(visible.contains("👍🏽"))
        }
        capture("comment-emoji")
    }

    @Test fun mobilismIsFirstPremiumOptionAndLogsArePubliclyReachable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val logs = DiagnosticLogStore(File(context.cacheDir, "ui-test-logs"), { true })
        // Compact configured cards let all provider positions be compared in the viewport.
        val state = DebridState(DebridProvider.entries.map { DebridAccount(it, configured = true, validated = true) })
        compose.setContent { MobilismTheme(reduceMotion = true) {
            SettingsScreen(state, DebridRepository(context), {}, {}, {}, diagnostics = logs)
        } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Premium", substring = false))
        val mobilism = compose.onNodeWithText("Mobilism Premium").fetchSemanticsNode().boundsInRoot.top
        val mega = compose.onNodeWithText("MegaDebrid").fetchSemanticsNode().boundsInRoot.top
        assertTrue(mobilism < mega)
        capture("premium-order")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Export logs"))
        compose.onNodeWithText("Export logs").assertIsDisplayed()
        capture("settings-diagnostics")
    }

    @Test fun loginOffersLogExportBeforeSignIn() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "login-test-logs")
        directory.deleteRecursively()
        val logs = DiagnosticLogStore(directory, { true })
        compose.setContent { MobilismTheme(reduceMotion = true) {
            LoginScreen(LoginState(), { _, _ -> }, {}, {}, {}, diagnostics = logs)
        } }
        compose.onNodeWithText("Export logs").performScrollTo().performClick()
        compose.onNodeWithText("No logs yet. Reproduce the issue and try again.").assertExists()
        capture("login-diagnostics")
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.waitUntil(5_000) { compose.activity.hasWindowFocus() }
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
