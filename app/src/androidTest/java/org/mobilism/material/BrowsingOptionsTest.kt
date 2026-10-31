package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.AuthState
import org.mobilism.material.data.ContentSection
import org.mobilism.material.data.Release
import org.mobilism.material.designsystem.LocalAppTheme
import org.mobilism.material.designsystem.MaterialYou
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.designsystem.ThemeDefinition
import org.mobilism.material.settings.AppearanceStore
import org.mobilism.material.settings.NewReleaseSession
import org.mobilism.material.ui.AppearanceScreen
import org.mobilism.material.ui.CategoryState
import org.mobilism.material.ui.FeedScreen
import org.mobilism.material.ui.FeedState

@RunWith(AndroidJUnit4::class)
class BrowsingOptionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var context: Context

    @Before fun isolatePreferences() {
        context = object : ContextWrapper(compose.activity) {
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("browsing-test-$name", mode)
        }
        clearPreferences()
    }

    @After fun clearPreferences() {
        listOf("material-appearance", "material-new-releases").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun compactListsKeepNewBadgesAndPersistAcrossTabs() = runBlocking<Unit> {
        val store = AppearanceStore(context)
        store.load()
        val release = Release(12, "Sample app v2.0 [Premium]", "Sample author", null, 1700000000, 3, 100)
        val feed = mutableStateOf(FeedState(items = listOf(release), newReleaseIds = setOf(12), canLoadMore = false))
        compose.setContent {
            val appearance by store.state.collectAsState()
            MobilismTheme(reduceMotion = true) {
                FeedScreen(feed.value, AuthState.Guest, onSection = {},
                    onOrder = { feed.value = feed.value.copy(order = it) }, onRefresh = {}, onMore = {},
                    onRelease = {}, onSettings = {}, onSearch = {}, onLibrary = {}, onFollowing = {}, onSignOut = {},
                    categories = CategoryState(), onLoadCategories = {}, onFilters = {},
                    compact = appearance.compactFeed, onCompact = store::compactFeed)
            }
        }
        compose.onNodeWithText("Sample author").assertIsDisplayed()
        val detailed = compose.onNodeWithTag("release-12").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("new-release-12", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Use compact list").performClick()
        compose.onNodeWithText("Sample author").assertDoesNotExist()
        compose.onNodeWithText("3 replies").assertDoesNotExist()
        compose.onNodeWithTag("new-release-12", useUnmergedTree = true).assertIsDisplayed()
        val compact = compose.onNodeWithTag("release-12").fetchSemanticsNode().boundsInRoot.height
        assertTrue(compact < detailed)
        capture("compact-new-releases")
        compose.onNodeWithText("Top").performClick()
        compose.onNodeWithContentDescription("Use detailed list").assertIsDisplayed()
        compose.onNodeWithTag("new-release-12", useUnmergedTree = true).assertDoesNotExist()
        val restored = AppearanceStore(context)
        restored.load()
        assertTrue(restored.state.value.compactFeed)
        compose.onNodeWithContentDescription("Use detailed list").performClick()
        compose.onNodeWithText("Sample author").assertIsDisplayed()
    }

    @Test fun materialYouPickerPersistsAndTracksSystemLightAndDarkColors() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        val store = AppearanceStore(context)
        store.load()
        val configuration = mutableStateOf(Configuration(compose.activity.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        })
        var rendered: ThemeDefinition? = null
        compose.setContent {
            val appearance by store.state.collectAsState()
            CompositionLocalProvider(LocalConfiguration provides configuration.value) {
                MobilismTheme(appearance.theme, reduceMotion = true) {
                    rendered = LocalAppTheme.current
                    AppearanceScreen(appearance, store, {})
                }
            }
        }
        compose.onNodeWithContentDescription("Choose theme").performClick()
        compose.onNodeWithContentDescription("Material You").performClick()
        compose.runOnIdle {
            assertEquals(MaterialYou.id, store.state.value.themeId)
            assertFalse(checkNotNull(rendered).isDark)
            assertEquals(dynamicLightColorScheme(compose.activity).primary, checkNotNull(rendered).colors.primary)
        }
        capture("material-you-light")
        compose.runOnIdle {
            configuration.value = Configuration(configuration.value).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
        }
        compose.runOnIdle {
            assertTrue(checkNotNull(rendered).isDark)
            assertEquals(dynamicDarkColorScheme(compose.activity).primary, checkNotNull(rendered).colors.primary)
        }
        capture("material-you-dark")
        val restored = AppearanceStore(context)
        restored.load()
        assertEquals(MaterialYou.id, restored.state.value.themeId)
        compose.onNodeWithContentDescription("Compact lists").performClick()
        restored.load()
        assertTrue(restored.state.value.compactFeed)
    }

    @Test fun newReleaseMarkersPersistWithoutChangingTheCurrentSession() {
        fun releases(vararg ids: Long) = ids.map { Release(it, "Release $it", "Member", null, 0, 0, 0) }
        val session = NewReleaseSession(context)
        assertTrue(session.observe(ContentSection.Apps, releases(10)).isEmpty())
        assertEquals(setOf(11L), session.observe(ContentSection.Apps, releases(11, 10)))
        assertTrue(session.observe(ContentSection.Books, releases(30)).isEmpty())
        assertEquals(setOf(11L), session.observe(ContentSection.Apps, releases(11, 10)))
        val reopened = NewReleaseSession(context)
        assertTrue(reopened.observe(ContentSection.Apps, releases(11, 10)).isEmpty())
        assertEquals(setOf(12L), reopened.observe(ContentSection.Apps, releases(12, 11)))
        assertTrue(reopened.observe(ContentSection.Books, releases(30)).isEmpty())
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val file = File(compose.activity.getExternalFilesDir(null), "browsing-checks/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
