package org.mobilism.material

import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.settings.*
import org.mobilism.material.ui.*

@RunWith(AndroidJUnit4::class)
class ReadmeScreenshots {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val loaded = ConcurrentHashMap.newKeySet<String>()

    @OptIn(coil3.annotation.DelicateCoilApi::class)
    @Before fun imageLoader() {
        SingletonImageLoader.setUnsafe(ImageLoader.Builder(compose.activity)
            .allowHardware(false)
            .eventListener(object : EventListener() {
                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    loaded.add(request.data.toString())
                }
            }).build())
    }

    @Test fun apps() {
        val releases = listOf(
            sample(1, "TizenTube v1.3.2 [Open Source]", "tizen-1"),
            sample(2, "Windfinder Pro v3.40.0 [Paid]", "windfinder"),
            sample(3, "Flud+ v2.2.2 build 100202352 [Paid] [Patched]", "flud"),
            sample(4, "Aloha Browser v9.2.0 [Premium]", "aloha"),
        )
        screen {
            FeedScreen(FeedState(items = releases, canLoadMore = false), AuthState.Guest,
                {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, CategoryState(), {}, {})
        }
        awaitImages(releases.mapNotNull { it.thumbnail })
        capture("apps")
    }

    @Test fun release() {
        releaseScreen()
        awaitImages(listOf(asset("tizen-1"), asset("tizen-2")))
        capture("release")
        compose.onNode(hasScrollToIndexAction() and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("Release image 2 of 2").performClick()
        compose.onNodeWithContentDescription("Close image viewer").assertExists()
    }

    @Test fun downloads() {
        releaseScreen()
        compose.onNode(verticalList).performScrollToNode(hasText("Downloads"))
        compose.onAllNodesWithText("Rapidgator").onFirst().assertExists()
        compose.onNodeWithText("Uploadrar").assertExists()
        capture("downloads")
    }

    @Test fun appearance() {
        screen { AppearanceScreen(AppearanceState(), AppearanceStore(compose.activity), {}) }
        capture("appearance")
    }

    @Test fun adaptiveIcon() {
        val icon = compose.activity.packageManager.getApplicationIcon(compose.activity.packageName)
        assertTrue(icon is AdaptiveIconDrawable)
        if (android.os.Build.VERSION.SDK_INT >= 33) assertNotNull((icon as AdaptiveIconDrawable).monochrome)
    }

    @Test fun hostOpenSignalsDownloadButCopyDoesNot() {
        var downloads = 0
        releaseScreen(onDownloaded = { if (it) downloads++ })
        compose.onNode(verticalList).performScrollToNode(hasText("Downloads"))
        compose.onAllNodesWithContentDescription("Copy Rapidgator link").onFirst().performClick()
        compose.runOnIdle { assertEquals(0, downloads) }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(IntentFilter(Intent.ACTION_VIEW).apply {
            addDataScheme("https")
            addCategory(Intent.CATEGORY_BROWSABLE)
        },
            Instrumentation.ActivityResult(Activity.RESULT_OK, null), true)
        try {
            compose.onAllNodesWithText("Rapidgator").onFirst().performClick()
            compose.runOnIdle { assertEquals(1, downloads) }
            assertEquals(1, monitor.hits)
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test fun autoWatchPreferenceDefaultsOnAndPersists() {
        val context = object : ContextWrapper(compose.activity) {
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("screenshot-test-$name", mode)
        }
        val preferences = context.getSharedPreferences("material-downloads", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            assertTrue(DownloadPreferencesStore(context).state.value.autoWatchDownloads)
            DownloadPreferencesStore(context).autoWatchDownloads(false)
            assertFalse(DownloadPreferencesStore(context).state.value.autoWatchDownloads)
            DownloadPreferencesStore(context).autoWatchDownloads(true)
            assertTrue(DownloadPreferencesStore(context).state.value.autoWatchDownloads)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    private fun releaseScreen(onDownloaded: (Boolean) -> Unit = {}) = screen {
        ReleaseScreen(
            state = ReleaseState(release = sample(1, "TizenTube v1.3.2 [Open Source]", "tizen-1").copy(
                images = listOf(asset("tizen-1"), asset("tizen-2")),
                contentHtml = """
                    <p>A YouTube client for Android TV, with playback controls and a simple layout.</p>
                    <p>What's New:</p><p>Playback fixes and navigation improvements.</p>
                    <p>Mod Info:</p><p>Sample release details for the README.</p>
                    <p>Download Instructions:</p><p>arm64-v8a</p>
                    <a href="https://rapidgator.net/file/sample">Download</a><br>
                    <a href="https://uploadrar.com/sample">Mirror</a><br>
                    <p>Universal</p><a href="https://rapidgator.net/file/universal">Download</a>
                """.trimIndent(), liked = false), busy = false,
                comments = CommentsState(canLoadMore = false)),
            debrid = DebridState(listOf(DebridAccount(DebridProvider.entries.first(), true,
                listOf(SupportedHost("rapidgator.net", "Rapidgator"))))),
            preferences = DownloadPreferences(), onBack = {}, onRetry = {}, onMoreComments = {},
            watched = true, downloaded = false, onWatch = {}, onDownloaded = onDownloaded,
            onLike = {}, onActionMessageShown = {},
        )
    }

    private fun screen(content: @Composable () -> Unit) {
        compose.setContent { MobilismTheme(reduceMotion = true, content = content) }
    }

    private fun awaitImages(images: List<String>) {
        compose.waitUntil(15_000) { loaded.containsAll(images) }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // Capture only Compose content. System notifications and keyboard never enter the PNG.
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(compose.activity.getExternalFilesDir(null), "readme-screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun asset(name: String) = "file:///android_asset/readme/$name.png"

    private val verticalList = hasScrollToIndexAction() and
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private fun sample(id: Long, title: String, image: String) = Release(
        id, title, "Sample release", asset(image), 0, 8, 2048, section = ContentSection.Apps,
    )
}
