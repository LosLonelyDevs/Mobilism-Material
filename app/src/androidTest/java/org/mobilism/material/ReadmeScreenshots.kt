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
import androidx.compose.runtime.collectAsState
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

    @Test fun singleImageStillOpensTheFullSizeViewer() {
        releaseScreen(singleImage = true)
        awaitImages(listOf(asset("tizen-1")))
        capture("release-single-image")
        compose.onNodeWithContentDescription("Release image 1 of 1").performClick()
        compose.onNodeWithContentDescription("Close image viewer").assertExists()
    }

    @Test fun appearance() {
        screen { AppearanceScreen(AppearanceState(), AppearanceStore(compose.activity), {}) }
        capture("appearance")
    }

    @Test fun debridAccounts() {
        val accounts = DebridState(listOf(
            DebridAccount(DebridProvider.RealDebrid, configured = true, validated = true, hostsVerified = true,
                hosts = listOf(SupportedHost("rapidgator.net", "Rapidgator"), SupportedHost("send.now", "send.now"))),
            DebridAccount(DebridProvider.MegaDebrid, configured = true, validated = true, hostsVerified = true,
                hosts = listOf(SupportedHost("rapidgator.net", "Rapidgator"))),
        ))
        screen { SettingsScreen(accounts, DebridRepository(compose.activity), {}, {}, {},
            developer = org.mobilism.material.settings.DeveloperPreferences(enabled = true, debridEnabled = true)) }
        compose.onNodeWithText("Premium").performScrollTo()
        capture("debrid")
        compose.onNodeWithText("2 Mobilism hosts").performClick()
        compose.onNodeWithText("Supported Mobilism hosts").assertIsDisplayed()
        compose.onNodeWithText("send.now").assertIsDisplayed()
    }

    @Test fun adaptiveIcon() {
        val icon = compose.activity.packageManager.getApplicationIcon(compose.activity.packageName)
        assertTrue(icon is AdaptiveIconDrawable)
        if (android.os.Build.VERSION.SDK_INT >= 33) assertNotNull((icon as AdaptiveIconDrawable).monochrome)
    }

    @Test fun about() {
        screen { AboutScreen(onBack = {}) }
        compose.onNodeWithText("v${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        capture("about")
    }

    @Test fun searchSortTabPreservesFiltersAndResetsToNewest() {
        var applied: FeedFilters? = null
        val initial = FeedFilters(setOf(10), java.time.LocalDate.of(2026, 10, 1))
        screen {
            CategoryFilterSheet(CategoryState(items = listOf(ReleaseCategory(10, "Sample category"))),
                initial, {}, { applied = it }, {}, showSearchSort = true)
        }
        compose.onNodeWithText("Sort", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Oldest posts").performClick()
        compose.onNode(hasText("Filters") and hasClickAction()).performClick()
        compose.onNodeWithText("Post date").assertExists()
        compose.onNodeWithText("Sort", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Apply filters").performClick()
        compose.runOnIdle { assertEquals(initial.copy(searchSort = SearchSort.Oldest), applied) }
        compose.onNodeWithText("Reset").performClick()
        compose.onNodeWithText("Apply filters").performClick()
        compose.runOnIdle { assertEquals(FeedFilters(), applied) }
    }

    @Test fun dateFiltersValidateResetAndUseCalendarDialog() {
        val start = java.time.LocalDate.of(2026, 10, 10)
        var applied: FeedFilters? = null
        screen {
            CategoryFilterSheet(CategoryState(items = listOf(ReleaseCategory(10, "Sample category"))),
                FeedFilters(setOf(10), start, start.minusDays(1)), {}, { applied = it }, {})
        }
        compose.onNodeWithText("Apply filters").assertIsNotEnabled()
        compose.onNodeWithText("On or after").performClick()
        compose.onNodeWithText("Set date").assertExists()
        compose.onAllNodesWithText("Cancel").onLast().performClick()
        compose.onNodeWithText("Reset").performClick()
        compose.onNodeWithText("On or after").assertDoesNotExist()
        compose.onNodeWithText("On or before").assertDoesNotExist()
        compose.onNodeWithText("Apply filters").assertIsEnabled().performClick()
        compose.waitUntil { applied != null }
        assertEquals(FeedFilters(), applied)
    }

    @Test fun dateFiltersStartCollapsedWithoutActiveDates() {
        screen {
            CategoryFilterSheet(CategoryState(items = listOf(ReleaseCategory(10, "Sample category"))),
                FeedFilters(categoryIds = setOf(10)), {}, {}, {})
        }
        compose.onNodeWithText("On or after").assertDoesNotExist()
        compose.onNodeWithText("Post date").performClick()
        compose.onNodeWithText("On or after").assertIsDisplayed()
        compose.onNodeWithText("On or before").assertIsDisplayed()
        compose.onNodeWithText("Post date").performClick()
        compose.onNodeWithText("On or after").assertDoesNotExist()
    }

    @Test fun hostOpenSignalsDownloadButCopyDoesNot() {
        var downloads = 0
        releaseScreen(onDownloaded = { if (it) downloads++ })
        compose.onNode(verticalList).performScrollToNode(hasText("Downloads"))
        compose.onAllNodesWithContentDescription("Copy Rapidgator link").onFirst().performClick()
        compose.runOnIdle { assertEquals(0, downloads) }
        // The copy snackbar can cover a host button on shorter viewports.
        compose.mainClock.advanceTimeBy(6_000)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Link copied").fetchSemanticsNodes().isEmpty() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(IntentFilter(Intent.ACTION_VIEW).apply {
            addDataScheme("https")
            addCategory(Intent.CATEGORY_BROWSABLE)
        },
            Instrumentation.ActivityResult(Activity.RESULT_OK, null), true)
        try {
            compose.onAllNodesWithText("Rapidgator").onFirst().performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(1, downloads) }
            assertEquals(1, monitor.hits)
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test fun hideAdultContentDefaultsOffAndPersistsThroughSettingsToggle() {
        val context = object : ContextWrapper(compose.activity) {
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("screenshot-test-$name", mode)
        }
        val preferences = context.getSharedPreferences("content-preferences", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val store = ContentPreferencesStore(context)
            screen { ContentVisibilitySetting(store.hideAdult.collectAsState().value, store::hideAdult) }
            compose.onNodeWithText("Hide 18+ content").assertIsOff().performClick().assertIsOn()
            assertTrue(ContentPreferencesStore(context).hideAdult.value)
            compose.onNodeWithText("Hide 18+ content").performClick().assertIsOff()
            assertFalse(ContentPreferencesStore(context).hideAdult.value)
        } finally {
            preferences.edit().clear().commit()
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

    private fun releaseScreen(singleImage: Boolean = false, onDownloaded: (Boolean) -> Unit = {}) = screen {
        ReleaseScreen(
            state = ReleaseState(release = sample(1, "TizenTube v1.3.2 [Open Source]", "tizen-1").copy(
                images = if (singleImage) listOf(asset("tizen-1")) else listOf(asset("tizen-1"), asset("tizen-2")),
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
                listOf(SupportedHost("rapidgator.net", "Rapidgator")), validated = true, hostsVerified = true)), enabled = true),
            preferences = DownloadPreferences(), onBack = {}, onRetry = {}, onMoreComments = {},
            watched = true, downloaded = false, onWatch = {}, onDownloaded = onDownloaded,
            onLike = {}, onActionMessageShown = {},
            onResolveDownload = { "https://example.org/sample-download.apk" },
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
        id, title, "Sample release", asset(image), 1792497600, 8, 2048, section = ContentSection.Apps,
    )
}
