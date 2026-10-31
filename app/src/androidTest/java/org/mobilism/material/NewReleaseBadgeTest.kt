package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.settings.NewReleaseSession
import org.mobilism.material.ui.*

@RunWith(AndroidJUnit4::class)
class NewReleaseBadgeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var context: Context
    private val models = ViewModelStore()

    @Before fun setup() {
        context = object : ContextWrapper(compose.activity) {
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("new-badge-test-$name", mode)
        }
        preferences().edit(commit = true) {
            clear()
            putLong(ContentSection.Apps.name, 10)
        }
    }

    @After fun cleanup() {
        compose.runOnUiThread { models.clear() }
        preferences().edit(commit = true) { clear() }
    }

    @Test fun prefetchedReleasesStayNewUntilLatestIsVisibleAndResumed() {
        val prefetched = NewReleaseSession(context)
        prefetched.observe(ContentSection.Apps, releases(12, 10))
        assertEquals(10L, viewed())
        val session = NewReleaseSession(context)
        val feed = mutableStateOf(FeedState(items = releases(12, 10), canLoadMore = false,
            newReleaseIds = session.observe(ContentSection.Apps, releases(12, 10))))
        val tab = mutableStateOf(BrowseTab.Home)
        compose.setContent { MobilismTheme(reduceMotion = true) {
            FeedScreen(feed.value, AuthState.Guest, onSection = {}, onOrder = {}, onRefresh = {}, onMore = {},
                onRelease = {}, onSettings = {}, onSearch = {}, onLibrary = {}, onFollowing = {}, onSignOut = {},
                categories = CategoryState(), onLoadCategories = {}, onFilters = {},
                tab = tab.value, onTab = { tab.value = it }, homeContent = { Text("Home fixture") },
                onReleasesViewed = session::markViewed)
        } }
        compose.onNodeWithTag("new-release-12", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(10L, viewed())
        compose.onNodeWithText("Latest").performClick()
        compose.onNodeWithTag("new-release-12", useUnmergedTree = true).assertIsDisplayed()
            .assertContentDescriptionEquals("New since your last visit")
        compose.onNodeWithText("NEW").assertDoesNotExist()
        compose.waitUntil(5_000) { viewed() == 12L }
        capture("latest-new-badge")

        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.runOnUiThread {
            val refreshed = releases(13, 12, 10)
            feed.value = feed.value.copy(items = refreshed,
                newReleaseIds = session.observe(ContentSection.Apps, refreshed))
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(12L, viewed())
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("new-release-13", useUnmergedTree = true).assertIsDisplayed()
        compose.waitUntil(5_000) { viewed() == 13L }
        compose.onNodeWithTag("new-release-13", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(NewReleaseSession(context).observe(ContentSection.Apps, releases(13, 12, 10)).isEmpty())
    }

    @Test fun homeLoaderRendersNewBadgesWithoutAcknowledgingAnOffscreenShelf() {
        val session = NewReleaseSession(context)
        val repository = object : HomeRepository {
            override suspend fun homeReleases(section: ContentSection, order: FeedOrder) =
                if (order == FeedOrder.Latest) releases(12, 10) else emptyList()
            override suspend fun homeCategories(section: ContentSection) = emptyList<ReleaseCategory>()
            override suspend fun homeReviews(section: ContentSection): HomeTopics? = null
            override suspend fun homeDiscussions(section: ContentSection) = HomeTopics(
                DiscussionCategory(1, "Discussions", DiscussionArea.Apps), emptyList())
        }
        lateinit var model: HomeViewModel
        compose.runOnUiThread {
            model = HomeViewModel(repository, ContentSection.Apps,
                observeNewReleases = { section, items -> session.observe(section, items) })
            models.put("home", model)
        }
        val shortViewport = mutableStateOf(true)
        compose.setContent { MobilismTheme(reduceMotion = true) {
            val state by model.state.collectAsState()
            Surface(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Box(if (shortViewport.value) Modifier.height(300.dp) else Modifier.fillMaxSize()) {
                        HomeScreen(state, onRetry = model::retry, onRelease = {}, onMore = {}, onCategory = {},
                            onCategories = {}, onTopic = { _, _ -> }, onForum = {}, onReleasesViewed = session::markViewed)
                    }
                }
            }
        } }
        compose.waitUntil(5_000) { model.state.value.latest.loaded }
        compose.waitForIdle()
        assertEquals(setOf(12L), model.state.value.newReleaseIds)
        assertEquals(10L, viewed())
        compose.runOnUiThread { shortViewport.value = false }
        compose.onNodeWithTag("home-Apps").performScrollToIndex(1)
        compose.onNodeWithTag("new-release-12", useUnmergedTree = true).assertIsDisplayed()
            .assertContentDescriptionEquals("New since your last visit")
        compose.onNodeWithText("NEW").assertDoesNotExist()
        compose.onNodeWithTag("new-release-10", useUnmergedTree = true).assertDoesNotExist()
        compose.waitUntil(5_000) { viewed() == 12L }
        capture("home-new-badge")
    }

    private fun preferences() = context.getSharedPreferences("material-new-releases", Context.MODE_PRIVATE)
    private fun viewed() = preferences().getLong(ContentSection.Apps.name, 0)
    private fun releases(vararg ids: Long) = ids.map {
        Release(it, "Release $it v1.0", "Sample author", null, 1793462400, 3, 100, section = ContentSection.Apps)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val image = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory = File(compose.activity.getExternalFilesDir(null), "new-badge-checks")
        check(directory.mkdirs() || directory.isDirectory)
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
