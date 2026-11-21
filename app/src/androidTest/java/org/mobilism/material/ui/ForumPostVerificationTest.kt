package org.mobilism.material.ui

import android.content.Context
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.mobilism.material.designsystem.MobilismTheme

@SdkSuppress(minSdkVersion = 29)
class ForumPostVerificationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun openingIndicatorYieldsToTheFirstVisiblePageBeforeVerificationCompletes() {
        val url = "https://forum.mobilism.org/viewtopic.php?t=42"
        var loads = 0
        lateinit var browser: WebView
        compose.setContent { MobilismTheme(reduceMotion = true) {
            ForumPostVerification(42, { fail("An unverified page cannot complete the check") }, {}, { context ->
                object : WebView(context) {
                    override fun loadUrl(url: String) { loads++ }
                }.also { browser = it }
            })
        } }
        compose.onNodeWithText("Opening verification…").assertIsDisplayed()
        compose.runOnIdle { browser.webViewClient.onPageCommitVisible(browser, url) }
        compose.onNodeWithText("Opening verification…").assertDoesNotExist()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.runOnIdle { assertTrue(browser.isShown); assertEquals(1, loads) }
    }

    @Test fun verifiedTopicReturnsOriginalPostAndIgnoresReplyIds() {
        var postId: Long? = null
        val page = fixture("""
            <div id="p123"><article><div class="postbody">Original</div></article></div>
            <div id="p456"><article><div class="postbody">Reply</div></article></div>
        """.trimIndent())
        compose.setContent { MobilismTheme { ForumPostVerification(42, { postId = it }, {}, page) } }
        compose.onNodeWithText("Verify forum connection").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsDisplayed()
        compose.waitUntil(10_000) { postId != null }
        assertEquals(123L, postId)
    }

    @Test fun anotherTopicCannotSupplyTheReactionId() {
        var postId: Long? = null
        val page = fixture("""<div id="p456" class="post">Wrong topic</div>""", topicId = 99)
        lateinit var browser: WebView
        compose.setContent { MobilismTheme { ForumPostVerification(42, { postId = it }, {},
            createBrowser = { page(it).also { view -> browser = view } }) } }
        compose.waitUntil(10_000) { compose.runOnIdle { browser.progress == 100 } }
        compose.runOnIdle { assertNull(postId) }
    }

    @Test fun readsPostAddedAfterPageFinishedWithoutAnotherNavigation() {
        var postId: Long? = null
        val page = fixture("""
            <body>Checking connection</body>
            <script>setTimeout(function() {
                document.body.innerHTML = '<div id="p123" class="post">Original</div>';
            }, 1000);</script>
        """.trimIndent())
        compose.setContent { MobilismTheme { ForumPostVerification(42, { postId = it }, {}, page) } }
        compose.waitUntil(10_000) { postId != null }
        assertEquals(123L, postId)
    }

    @Test fun liveForumReturnsOriginalPostWithoutChangingReaction() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveForumVerification") == "true")
        var postId: Long? = null
        compose.setContent { MobilismTheme { ForumPostVerification(6437968, { postId = it }, {}) } }
        compose.waitUntil(60_000) { postId != null }
        assertEquals(12725286L, postId)
    }

    private fun fixture(html: String, topicId: Long = 42): (Context) -> WebView = { context ->
        object : WebView(context) {
            override fun loadUrl(url: String) {
                val location = "https://forum.mobilism.org/viewtopic.php?t=$topicId"
                loadDataWithBaseURL(location, html, "text/html", "UTF-8", location)
            }
        }
    }
}
