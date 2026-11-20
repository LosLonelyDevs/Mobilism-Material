package org.mobilism.material.ui

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.DebridState
import org.mobilism.material.data.ContentSection
import org.mobilism.material.data.Release
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.settings.DeveloperPreferencesStore
import org.mobilism.material.settings.DownloadPreferences

@RunWith(AndroidJUnit4::class)
class ForumFormattingPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val base = "https://forum.mobilism.org/"

    @Test fun nativeRendererKeepsListsLinksCodeAndSafeMarkdown() {
        val text = forumText("# Heading<br><br>**Bold**<br><br>- One<br>- Two<br><br>" +
            "3. Three<br>4. Four<br><br>[Guide](https://example.org/a)<br><br>" +
            "[unsafe](javascript:alert(1))<br><br>```<br>**literal**<br>```", base, ForumFormattingOptions(markdown = true))
        assertTrue(text.toString().contains("Heading"))
        assertTrue(text.toString().contains("3. Three"))
        assertTrue(text.toString().contains("4. Four"))
        assertTrue(text.toString().contains("**literal**"))
        assertEquals(2, text.getSpans(0, text.length, ForumBulletSpan::class.java).size)
        assertTrue(text.getSpans(0, text.length, StyleSpan::class.java).isNotEmpty())
        assertEquals(listOf("https://example.org/a"), text.getSpans(0, text.length, URLSpan::class.java).map { it.url })
        val literal = forumText("**Still literal**<br>-One<br>-Two", base)
        assertTrue(literal.toString().contains("**Still literal**"))
        assertEquals(0, literal.getSpans(0, literal.length, BulletSpan::class.java).size)
        val loose = forumText("3. Three<br><br>4. Four<br>   1. Child<br><br>```<br>  indented<br><br><br>  kept<br>```",
            base, ForumFormattingOptions(markdown = true))
        assertTrue(loose.toString().contains("3. Three"))
        assertTrue(loose.toString().contains("4. Four"))
        assertTrue(loose.toString().contains("1. Child"))
        assertTrue(loose.toString().replace("\n", "\\n"),
            loose.toString().replace('\u00a0', ' ').contains("  indented\n\n\n  kept"))
    }

    @Test fun cleanupGivesRequirementsAndHeadingsDistinctStylesWithoutColoringProse() {
        val source = "<b>Example App</b><br><u>Requirements:</u> Android 9+<br><b>Choose a Site</b><br>" +
            "A normal paragraph mentions requirements: storage.<pre>Requirements: literal code</pre>"
        val heading = android.graphics.Color.rgb(182, 188, 216)
        val subtitle = android.graphics.Color.rgb(150, 164, 178)
        for (markdown in listOf(false, true)) {
            val text = forumText(source, base, ForumFormattingOptions(true, markdown),
                headingColor = heading, requirementsColor = subtitle)
            fun colorsAt(word: String): List<Int> {
                val index = text.indexOf(word)
                assertTrue(word, index >= 0)
                return text.getSpans(index, index + 1, ForegroundColorSpan::class.java).map { it.foregroundColor }
            }
            val start = text.indexOf("Android 9+")
            assertTrue(text.toString(), text.startsWith("Example App\nRequirements: Android 9+\n\nChoose a Site"))
            assertEquals(listOf(subtitle), colorsAt("Android 9+"))
            assertTrue(text.getSpans(start, start + 1, RelativeSizeSpan::class.java).any { it.sizeChange < 1f })
            assertEquals(listOf(heading), colorsAt("Choose a Site"))
            assertTrue(colorsAt("normal paragraph").isEmpty())
            assertTrue(colorsAt("literal").isEmpty())
        }
        val original = forumText(source, base)
        assertTrue(original.getSpans(0, original.length, ForegroundColorSpan::class.java).isEmpty())
    }

    @Test fun captureOriginalCleanupMarkdownAndCombinedReleaseCards() {
        val options = mutableStateOf(ForumFormattingOptions())
        val content = mutableStateOf(releaseFixture)
        compose.setContent {
            MobilismTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalForumFormatting provides options.value) {
                    val release = Release(1, "MyPGallery v1.1.3 b370", "spydog", null, 0, 0, 0,
                        section = ContentSection.Apps, contentHtml = content.value + "<br><b>What's new:</b><br>" + changelogFixture)
                    ReleaseScreen(ReleaseState(release, busy = false), DebridState(), DownloadPreferences(),
                        onBack = {}, onRetry = {}, onMoreComments = {}, watched = false, downloaded = false,
                        onWatch = {}, onDownloaded = {}, onLike = {}, onActionMessageShown = {}, descriptionExpanded = true)
                }
            }
        }
        val list = compose.onNodeWithTag("release-content")
        list.performScrollToKey(ReleaseSection.Description)
        capture("01-original")
        compose.runOnIdle { options.value = ForumFormattingOptions(cleanUpReleaseText = true) }
        capture("02-cleanup")
        compose.runOnIdle { content.value = markdownFixture; options.value = ForumFormattingOptions(markdown = true) }
        capture("03-markdown")
        compose.runOnIdle { options.value = ForumFormattingOptions(true, true) }
        capture("04-combined")
        compose.runOnIdle {
            content.value = releaseFixture
            options.value = ForumFormattingOptions(cleanUpReleaseText = true)
        }
        list.performScrollToKey(ReleaseSection.Changes)
        capture("05-changelog")
    }

    @Test fun developerSwitchesPersistIndependentlyAndDisableWithDeveloperMode() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "formatting-test-${System.nanoTime()}"
        val context = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(ignored: String, mode: Int) = super.getSharedPreferences(name, mode)
        }
        try {
            val store = DeveloperPreferencesStore(context)
            store.setReleaseTextCleanup(true)
            store.setMarkdownSyntax(true)
            assertFalse(store.state.value.releaseTextCleanup)
            assertFalse(store.state.value.markdownSyntax)
            store.unlock()
            compose.setContent { MobilismTheme(reduceMotion = true) {
                val state by store.state.collectAsState()
                DeveloperOptionsScreen(state, store::setVerboseLogging, store::disable, {},
                    onReleaseTextCleanup = store::setReleaseTextCleanup, onMarkdownSyntax = store::setMarkdownSyntax)
            } }
            compose.onNodeWithText("Clean up release text").performScrollTo().performClick()
            assertTrue(store.state.value.releaseTextCleanupActive)
            assertFalse(store.state.value.markdownSyntaxActive)
            compose.onNodeWithText("Enable Markdown syntax").performScrollTo().performClick()
            compose.onNodeWithText("Enable Markdown syntax").assertIsDisplayed()
            assertTrue(DeveloperPreferencesStore(context).state.value.markdownSyntaxActive)
            assertTrue(DeveloperPreferencesStore(context).state.value.releaseTextCleanupActive)
            capture("06-developer-options")
            compose.runOnIdle { store.disable() }
            assertFalse(DeveloperPreferencesStore(context).state.value.releaseTextCleanupActive)
            assertFalse(DeveloperPreferencesStore(context).state.value.markdownSyntaxActive)
        } finally { target.deleteSharedPreferences(name) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val window = compose.activity.window
        val decor = window.decorView
        if (Build.VERSION.SDK_INT >= 29 && decor.isHardwareAccelerated) {
            val drawn = CountDownLatch(1)
            compose.runOnIdle {
                decor.viewTreeObserver.registerFrameCommitCallback { drawn.countDown() }
                decor.invalidate()
            }
            assertTrue("Timed out drawing $name", drawn.await(30, TimeUnit.SECONDS))
        }
        val image = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val copied = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        PixelCopy.request(window, image, { code -> result = code; copied.countDown() }, Handler(Looper.getMainLooper()))
        assertTrue("Timed out copying $name", copied.await(30, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, result)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "formatting-previews").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    private val releaseFixture = "<b>MyPGallery v1.1.3 b370</b><br>Requirements: Limited 9.0+ Mobile + TV Devices<br>" +
        "MyPGallery is a video and gallery app to browse 35+ adult content sites, watch videos, explore photo galleries and download what you want to keep.<br>" +
        "<b>How does MyPGallery display content?</b><br>" +
        "MyPGallery lets you browse videos and photo galleries from supported sites. When you open something, your device loads it from the original site or its video host.<br>" +
        "<b>Choose a site</b><br>Each supported site has a dedicated plugin that lets you search and browse its content within the app.<br>" +
        "<b>The plugin reads the content details</b><br>The app displays videos, images and tags in its own interface.<br>" +
        "<b>Watch and browse in the app</b><br>Watch videos in the player and open images in the gallery."

    private val changelogFixture = "VERSION 1.1.3 b370<br>-Set app version to 1.1.3<br>" +
        "-Fixing of tests that failed after midnight on old layouts<br>-Fixing of JavGuru videos and some video hosts<br>" +
        "-Added 7 new video sites<br>-Share menu handle stays still<br>-Fixing of the player settings button and its menu<br>" +
        "-Library tabs and filters hide while scrolling<br>-Batch search lists finished sites first<br>" +
        "-Descriptions show 4 lines and open on tap<br>-Back swipe from a tab slides to home on Android<br>*every update has a changelog"

    private val markdownFixture = "## Release notes<br><br>**Markdown** works beside <b>existing forum formatting</b>.<br><br>" +
        "### Features<br>- Clear headings and *emphasis*<br>- Nested lists<br>  - A child item stays indented<br><br>" +
        "3. Open the app<br>4. Choose a release<br><br>&gt; Original words stay intact.<br><br>" +
        "`version_code = 24`<br><br>[Release link](https://forum.mobilism.org/viewtopic.php?t=6460689)<br><br>" +
        "-Compact release bullet<br>-Another compact bullet"
}
