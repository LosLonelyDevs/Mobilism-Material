package org.mobilism.material

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.*
import org.mobilism.material.ui.*

@RunWith(AndroidJUnit4::class)
class DiscussionDesignTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun previewsMenusAndPullRefreshWorkAtEveryDiscussionLevel() {
        val repository = Repository()
        lateinit var model: DiscussionsViewModel
        var theme by mutableStateOf(CatppuccinLatte)
        compose.runOnUiThread { model = DiscussionsViewModel(repository, null, SavedStateHandle()) }
        compose.setContent { MobilismTheme(theme = theme, reduceMotion = true) {
            DiscussionsScreen(model, AuthState.Guest, {}, { _, _ -> })
        } }
        compose.waitUntil { model.state.value.categories.isNotEmpty() }
        compose.waitUntil { model.state.value.categoryActivity.isNotEmpty() }
        compose.onNodeWithText("Reviews").assertIsDisplayed()
        compose.onNodeWithTag("discussion-activity:Apps:500", useUnmergedTree = true).assertIsDisplayed().assertTextContains("ago", substring = true)
        compose.onNodeWithText("Apps discussions").assertDoesNotExist()
        compose.onNodeWithText("Find help, share ideas, and join a conversation.").assertDoesNotExist()
        capture("categories-light")
        compose.runOnIdle { theme = SolarizedDark }
        capture("categories-solarized")
        val categoriesBefore = repository.categoryLoads
        pull("discussion-categories")
        compose.waitUntil { repository.categoryLoads > categoriesBefore }
        compose.waitUntil { repository.activityLoads >= 6 }
        compose.onNodeWithText("Reviews").performClick()
        compose.waitUntil { model.state.value.topics.items.isNotEmpty() }
        val preview = compose.onNodeWithTag("discussion-preview:discussion:80", useUnmergedTree = true)
        preview.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        preview.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        assertTrue(layouts.single().isLineEllipsized(0))
        assertFalse(layouts.single().layoutInput.text.text.contains("<p>"))
        val titleLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("discussion-title:discussion:80", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(titleLayouts) }
        assertEquals(1, titleLayouts.single().lineCount)
        compose.onNodeWithText("Latest activity · pinned topics included").assertDoesNotExist()
        val pinned = compose.onNodeWithText("Pinned", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val replies = compose.onNodeWithText("12 replies", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val views = compose.onNodeWithText("240 views", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(replies.center.y, pinned.center.y, 2f)
        assertEquals(views.center.y, pinned.center.y, 2f)
        assertTrue(pinned.left > views.right)
        capture("topics-solarized")
        val topicsBefore = repository.topicLoads
        pull("discussion-topics")
        compose.waitUntil { repository.topicLoads > topicsBefore }
        compose.onNodeWithText("A discussion with a longer first post").performClick()
        compose.waitUntil { !model.state.value.comments.busy }
        compose.onNodeWithContentDescription("Refresh").assertDoesNotExist()
        compose.onNodeWithContentDescription("Discussion options").performClick()
        compose.onNodeWithText("Open in browser").assertIsDisplayed()
        compose.onNodeWithText("Copy URL").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(repository.topic.browserUrl, clipboard.primaryClip?.getItemAt(0)?.text.toString())
        }
        val commentsBefore = repository.commentLoads
        pull("discussion-reader")
        compose.waitUntil { repository.commentLoads > commentsBefore }
        assertEquals(1, repository.lastCommentPage)
        capture("reader-solarized")
    }

    @Test fun nestedQuoteContextAndCollapseRemainIndependentInDarkTheme() {
        val comment = ReleaseComment(91, "Newest reader", null, 1700000000, """
            <blockquote><cite>Parent author wrote:</cite>
              <blockquote><cite>Earlier reader wrote:</cite><p>Original question</p></blockquote>
              <p>Parent answer</p>
            </blockquote><p>My own reply stays separate.</p>
        """.trimIndent())
        compose.setContent { MobilismTheme(theme = AmoledBlack, reduceMotion = true) {
            Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                Column(Modifier.padding(16.dp)) {
                    ReleaseCommentCard(comment, "https://forum.mobilism.org/", onReply = {}, onImage = { _, _ -> })
                }
            }
        } }
        compose.onNodeWithText("Replying to Parent author").assertIsDisplayed()
        compose.onNodeWithText("Replying to Earlier reader").assertDoesNotExist()
        compose.onNodeWithText("Show quote").performClick()
        compose.onNodeWithText("Replying to Earlier reader").assertIsDisplayed()
        compose.onNodeWithText("Show quote").performClick()
        compose.onAllNodesWithText("Hide quote")[0].performClick()
        compose.onNodeWithText("Show quote").performClick()
        compose.onAllNodesWithText("Hide quote").assertCountEquals(2)
        capture("nested-context-dark")
        compose.onNodeWithContentDescription("Collapse comment").performClick()
        compose.onNodeWithTag("comment-body-91").assertDoesNotExist()
        compose.onNodeWithContentDescription("Expand comment").performClick()
        compose.onNodeWithText("Replying to Earlier reader").assertIsDisplayed()
        compose.onNodeWithText("Reply", substring = false).assertIsDisplayed()
    }

    private fun pull(tag: String) {
        compose.onNodeWithTag(tag).performTouchInput {
            swipe(Offset(centerX, top + 12f), Offset(centerX, bottom - 12f), 700)
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = java.io.File(compose.activity.getExternalFilesDir(null), "discussion-redesign")
        check(directory.mkdirs() || directory.isDirectory)
        java.io.File(directory, "$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    private class Repository : DiscussionsRepository {
        var categoryLoads = 0
        var topicLoads = 0
        var commentLoads = 0
        var activityLoads = 0
        var lastCommentPage = 0
        val category = DiscussionCategory(500, "Reviews", DiscussionArea.Apps, topics = 78)
        val topic = DiscussionTopic(80, "A discussion with a longer first post", "Community guide",
            "<p>" + "A first post preview with helpful details &amp; links. ".repeat(12) + "</p>",
            DiscussionKind.Discussion, 500, activityAt = 1700000500, replies = 12, views = 240, pinned = true)
        override suspend fun discussionCategories(area: DiscussionArea): List<DiscussionCategory> {
            categoryLoads++
            return listOf(category, category.copy(id = 501, name = "Best Of..."), category.copy(id = 502, name = "General Talk"))
        }
        override suspend fun discussionLastActivity(category: DiscussionCategory): Long {
            activityLoads++
            return System.currentTimeMillis() / 1000 - 7200
        }
        override suspend fun discussionTopics(category: DiscussionCategory, page: Int, query: String): DiscussionPage {
            topicLoads++
            return DiscussionPage(listOf(topic), false)
        }
        override suspend fun discussionComments(topic: DiscussionTopic, page: Int): CommentPage {
            commentLoads++
            lastCommentPage = page
            return CommentPage(emptyList(), false)
        }
        override suspend fun postDiscussionComment(topic: DiscussionTopic, text: String, replyTo: Long?): Long = error("No writes expected")
    }
}
