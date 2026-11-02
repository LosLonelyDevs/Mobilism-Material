package org.mobilism.material.ui

import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
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

    @Test fun verifiedTopicReturnsOriginalPostAndIgnoresReplyIds() {
        var postId: Long? = null
        compose.setContent { MobilismTheme { ForumPostVerification(42, { postId = it }, {}) } }
        compose.onNodeWithText("Verify forum connection").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsDisplayed()
        compose.runOnIdle {
            browser().apply {
                stopLoading()
                loadDataWithBaseURL("https://forum.mobilism.org/viewtopic.php?t=42", """
                    <div id="p123"><article><div class="postbody">Original</div></article></div>
                    <div id="p456"><article><div class="postbody">Reply</div></article></div>
                """.trimIndent(), "text/html", "UTF-8", "https://forum.mobilism.org/viewtopic.php?t=42")
            }
        }
        compose.waitUntil(10_000) { postId != null }
        assertEquals(123L, postId)
    }

    @Test fun anotherTopicCannotSupplyTheReactionId() {
        var postId: Long? = null
        compose.setContent { MobilismTheme { ForumPostVerification(42, { postId = it }, {}) } }
        compose.runOnIdle {
            browser().apply {
                stopLoading()
                loadDataWithBaseURL("https://forum.mobilism.org/viewtopic.php?t=99",
                    """<div id="p456" class="post">Wrong topic</div>""", "text/html", "UTF-8",
                    "https://forum.mobilism.org/viewtopic.php?t=99")
            }
        }
        compose.waitUntil(10_000) { compose.runOnIdle { browser().progress == 100 } }
        compose.runOnIdle { assertNull(postId) }
    }

    @Test fun readsPostAddedAfterPageFinishedWithoutAnotherNavigation() {
        var postId: Long? = null
        compose.setContent { MobilismTheme { ForumPostVerification(42, { postId = it }, {}) } }
        compose.runOnIdle {
            browser().apply {
                stopLoading()
                loadDataWithBaseURL("https://forum.mobilism.org/viewtopic.php?t=42", """
                    <body>Checking connection</body>
                    <script>setTimeout(function() {
                        document.body.innerHTML = '<div id="p123" class="post">Original</div>';
                    }, 1000);</script>
                """.trimIndent(), "text/html", "UTF-8", "https://forum.mobilism.org/viewtopic.php?t=42")
            }
        }
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

    private fun browser(): WebView = WindowInspector.getGlobalWindowViews()
        .asSequence().flatMap(::descendants).filterIsInstance<WebView>().single()

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
