package org.mobilism.material.ui

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.DefaultMobilismRepository
import org.mobilism.material.data.DebridState
import org.mobilism.material.data.ReleaseComment
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.settings.DownloadPreferences

/** Opt-in, read-only verification of a real three-level forum reply chain. */
@RunWith(AndroidJUnit4::class)
class LiveCommentThreadingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun currentReleaseRepliesNestOnceAcrossLivePages() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveComments") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefix = "live-comments-${System.nanoTime()}-"
        val context = object : ContextWrapper(instrumentation.targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(prefix + name, mode)
        }
        val repository = DefaultMobilismRepository(context)
        val release = repository.release(6442407)
        val comments = mutableListOf<ReleaseComment>()
        for (page in 1..6) {
            val result = repository.comments(release.id, page)
            comments += result.items
            val partial = CommentTree.flatten(CommentTree.build(comments), emptySet())
            assertEquals("Each fetched post appears once", comments.map { it.id }.distinct().size,
                partial.count { !it.placeholder })
            if (!result.canLoadMore || result.items.isEmpty()) break
        }
        val roots = CommentTree.build(comments)
        assertEquals(roots.map { it.latestActivity }.sortedDescending(), roots.map { it.latestActivity })
        val rows = CommentTree.flatten(roots, emptySet())
        val parent = rows.single { it.comment.id == 12734265L }
        val reply = parent.replies.single { it.comment.id == 12734268L }
        val nested = reply.replies.single { it.comment.id == 12734281L }
        assertFalse(parent.placeholder)
        assertEquals(parent.depth + 1, reply.depth)
        assertEquals(reply.depth + 1, nested.depth)
        assertTrue("Parent quote is already visible in the thread", reply.content.quotes.isEmpty())
        assertTrue("Nested quote is already visible in the thread", nested.content.quotes.isEmpty())
        assertTrue(nested.content.html.contains("ty much"))
        assertFalse(nested.content.html.contains("Menu"))
        rows.forEach { row ->
            assertEquals(row.replies.map { it.comment.publishedAt }.sorted(), row.replies.map { it.comment.publishedAt })
        }
        compose.setContent { MobilismTheme(reduceMotion = true) {
            ReleaseScreen(ReleaseState(release, busy = false, comments = CommentsState(comments)),
                DebridState(), DownloadPreferences(), onBack = {}, onRetry = {}, onMoreComments = {},
                watched = false, downloaded = false, onWatch = {}, onDownloaded = {}, onLike = {}, onActionMessageShown = {})
        } }
        compose.onNodeWithTag("release-content").performScrollToNode(hasTestTag("comment-12734281"))
        compose.onNodeWithTag("comment-12734281").assertIsDisplayed()
        compose.onAllNodesWithTag("comment-12734281").assertCountEquals(1)
        compose.onNodeWithText("Show quote").assertDoesNotExist()
        compose.waitUntil(5_000) { compose.activity.hasWindowFocus() }
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(context.getExternalFilesDir(null), "live-comment-thread.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
