package org.mobilism.material.ui

import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.text.Spanned
import android.text.style.URLSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.data.*
import org.mobilism.material.settings.DownloadPreferences

class ForumLinkActionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun announcementLinkInReleaseCommentOpensTheNativeReaderAndBackReturnsToComment() {
        val topicId = 6464350L
        val page = "page/1/limit/20/orderby"
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.encodedPath.removePrefix("/api-new/")
            requests += path
            val body = when (path) {
                "other/category/1/$page/topic_last_post_time/desc/1" ->
                    """{"response":[{"id":$topicId,"subject":"Forum announcement","name":"Moderator","forum_id":1,"content":"Announcement body"}]}"""
                "other/$topicId/comments/$page/date/desc" -> """{"response":[]}"""
                else -> error("Unexpected request: $path")
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build()
        val repository = DefaultMobilismRepository(compose.activity, client = client)
        val url = "https://forum.mobilism.org/viewtopic.php?f=1&t=$topicId"
        compose.setContent { MobilismTheme(reduceMotion = true) {
            val nav = rememberNavController()
            androidx.compose.runtime.CompositionLocalProvider(LocalAnnouncementLinkHandler provides { id, forum ->
                nav.openAnnouncement(id, forum, repository)
            }) {
                NavHost(nav, startDestination = "comment") {
                    composable("comment") {
                        ReleaseCommentCard(ReleaseComment(42, "Reader", null, 0, "<a href='$url'>See the announcement</a>"),
                            "https://forum.mobilism.org/viewtopic.php?t=6451572", onRelease = { error("Opened a release") },
                            onImage = { _, _ -> })
                    }
                    composable("announcement/{forum}/{id}", arguments = listOf(navArgument("forum") { type = NavType.LongType },
                        navArgument("id") { type = NavType.LongType })) { entry ->
                        val args = requireNotNull(entry.arguments)
                        val reader = viewModel<DiscussionsViewModel> { DiscussionsViewModel(repository, null, SavedStateHandle(),
                            announcementsOnly = true, linkedAnnouncement = { repository.announcement(args.getLong("id"), args.getLong("forum")) }) }
                        DiscussionsScreen(reader, AuthState.Guest, { nav.popBackStack() }, { _, _ -> }, linkedUrl = url)
                    }
                }
            }
        } }
        val link = textView()
        touch(link, MotionEvent.ACTION_DOWN)
        touch(link, MotionEvent.ACTION_UP)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Forum announcement").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Announcement").assertIsDisplayed()
        compose.onNodeWithText("Forum announcement").assertIsDisplayed()
        compose.waitUntil(10_000) { requests.size == 2 }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("comment-42").assertIsDisplayed()
        assertEquals(2, requests.size)
    }

    @Test fun linkedReleaseOpensItsOwnDetailAndBackRestoresTheSource() {
        val source = 6462354L
        val linked = 6448504L
        val url = "https://forum.mobilism.org/viewtopic.php?f=1283&t=$linked&p=12747480#p12747480"
        val visited = mutableListOf<Long>()
        val commentPages = mutableListOf<Pair<Long, Int>>()
        val repository = object : MobilismRepository by DefaultMobilismRepository(compose.activity) {
            override suspend fun release(id: Long): Release {
                visited += id
                return Release(id, if (id == source) "Current build" else "Previous build", "Uploader", null,
                    1_793_188_800L, 0, 0, contentHtml = "<p><a href='$url'>$url</a></p>")
            }
            override suspend fun comments(id: Long, page: Int, fresh: Boolean): CommentPage {
                commentPages += id to page
                return CommentPage(emptyList(), false)
            }
        }
        compose.setContent { MobilismTheme(reduceMotion = true) {
            val nav = rememberNavController()
            NavHost(nav, startDestination = "release/$source") {
                composable("release/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    val id = entry.arguments!!.getLong("id")
                    val detail = viewModel<ReleaseViewModel> { ReleaseViewModel(id, repository, SavedStateHandle()) }
                    val state by detail.state.collectAsStateWithLifecycle()
                    ReleaseScreen(state, DebridState(), DownloadPreferences(), onBack = { nav.popBackStack() },
                        onRetry = {}, onMoreComments = {}, watched = false, downloaded = false, onWatch = {},
                        onDownloaded = {}, onLike = {}, onActionMessageShown = {}, descriptionExpanded = true,
                        onRelease = nav::openReleaseTopic)
                }
            }
        } }
        compose.onNodeWithText("Current build").assertIsDisplayed()
        val localTime = android.text.format.DateFormat.getTimeFormat(compose.activity).format(java.util.Date(1_793_188_800_000L))
        compose.onNodeWithText(" · $localTime", substring = true).assertIsDisplayed()
        val target = textView()
        touch(target, MotionEvent.ACTION_DOWN)
        touch(target, MotionEvent.ACTION_UP)
        compose.onNodeWithText("Previous build").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Current build").assertIsDisplayed()
        assertEquals(listOf(source, linked), visited)
        assertEquals(listOf(source to 1, linked to 1), commentPages)
    }

    @Test fun nativeDownloadHelpActionKeepsLongPressMenuAndTapOpensReader() {
        var helpOpened = false
        compose.setContent { MobilismTheme(reduceMotion = true) {
            DownloadHelpAction { helpOpened = true }
        } }
        compose.onNodeWithTag("download-help-action").performTouchInput { longClick() }
        compose.onNodeWithText("Link options").assertIsDisplayed()
        compose.onNodeWithText("Open in browser").assertIsDisplayed()
        compose.runOnIdle { assertFalse(helpOpened) }
        compose.onNodeWithText("Copy to clipboard").performClick()
        compose.waitUntil { compose.onAllNodesWithText("Link options").fetchSemanticsNodes().isEmpty() }
        compose.runOnUiThread {
            val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(org.mobilism.material.data.DownloadHelp.URL, clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        compose.onNodeWithTag("download-help-action").performClick()
        compose.runOnIdle { assertTrue(helpOpened) }
    }

    @Test fun downloadSheetDismissesBeforeOpeningNativeHelp() {
        var dismissed = false
        var helpOpened = false
        compose.setContent { MobilismTheme(reduceMotion = true) {
            androidx.compose.runtime.CompositionLocalProvider(LocalDownloadHelpHandler provides {
                assertTrue(dismissed)
                helpOpened = true
            }) {
                ReleaseDownloadsSheet(42, "Example", emptyList(),
                    "Trouble downloading? Read <a href='${org.mobilism.material.data.DownloadHelp.URL}'>This</a>.",
                    "https://forum.mobilism.org/", org.mobilism.material.data.DebridState(), DownloadRequestTracker(),
                    onResolve = { it }, onOpened = {}, onRelease = { fail("Help must not open as a release") },
                    onDismiss = { dismissed = true })
            }
        } }
        compose.onNodeWithTag("download-help-action").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(helpOpened) }
    }

    @Test fun downloadHelpLinkUsesItsNativeReaderInsteadOfReleaseOrBrowserRouting() {
        var helpOpened = false
        var releaseOpened = false
        var browserOpened = false
        compose.setContent { MobilismTheme(reduceMotion = true) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalDownloadHelpHandler provides { helpOpened = true },
                LocalForumLinkHandler provides { browserOpened = true },
            ) {
                ReleaseBody("Trouble downloading? Read <a href='${org.mobilism.material.data.DownloadHelp.URL}'>This</a>.",
                    "https://forum.mobilism.org/", onRelease = { releaseOpened = true })
            }
        } }
        val target = textView()
        touch(target, MotionEvent.ACTION_DOWN)
        touch(target, MotionEvent.ACTION_UP)
        compose.runOnIdle { assertTrue(helpOpened); assertFalse(releaseOpened); assertFalse(browserOpened) }
    }

    @Test fun holdingNamedLinkCopiesDestinationWithoutOpeningItAndTapStillNavigates() {
        var opened: Long? = null
        compose.setContent { MobilismTheme(reduceMotion = true) {
            ReleaseBody("<p><a href='/viewtopic.php?t=42'>Read this topic</a></p>", "https://forum.mobilism.org/",
                onRelease = { opened = it })
        } }
        val target = textView()
        touch(target, MotionEvent.ACTION_DOWN)
        SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout().toLong() + 150)
        touch(target, MotionEvent.ACTION_UP)
        compose.onNodeWithText("Link options").assertIsDisplayed()
        compose.onNodeWithText("Open in browser").assertIsDisplayed()
        assertNull(opened)
        compose.onNodeWithText("Copy to clipboard").performClick()
        compose.waitUntil { compose.onAllNodesWithText("Link options").fetchSemanticsNodes().isEmpty() }
        compose.runOnUiThread {
            val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals("https://forum.mobilism.org/viewtopic.php?t=42", clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        touch(target, MotionEvent.ACTION_DOWN)
        touch(target, MotionEvent.ACTION_UP)
        compose.runOnIdle { assertEquals(42L, opened) }
    }

    @Test fun cancelledLinkGestureDoesNotOpenMenuOrNavigate() {
        var opened = false
        compose.setContent { MobilismTheme(reduceMotion = true) {
            ReleaseBody("https://forum.mobilism.org/viewtopic.php?t=42", "https://forum.mobilism.org/", onRelease = { opened = true })
        } }
        val target = textView()
        touch(target, MotionEvent.ACTION_DOWN)
        touch(target, MotionEvent.ACTION_CANCEL)
        SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout().toLong() + 100)
        compose.onNodeWithText("Link options").assertDoesNotExist()
        assertFalse(opened)
    }

    private fun textView(): TextView {
        compose.waitForIdle()
        fun find(view: View): TextView? = if (view is TextView && view.text is Spanned) view
            else if (view is ViewGroup) (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) } else null
        return checkNotNull(find(compose.activity.window.decorView))
    }

    private fun touch(view: TextView, action: Int) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val text = view.text as Spanned
            val span = text.getSpans(0, text.length, URLSpan::class.java).first()
            val offset = text.getSpanStart(span) + 1
            val line = view.layout.getLineForOffset(offset)
            val x = view.layout.getPrimaryHorizontal(offset) + view.totalPaddingLeft
            val y = (view.layout.getLineTop(line) + view.layout.getLineBottom(line)) / 2f + view.totalPaddingTop
            val now = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(now, now, action, x, y, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }
}
