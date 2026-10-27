package org.mobilism.material

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.settings.DownloadPreferences
import org.mobilism.material.ui.*

@RunWith(AndroidJUnit4::class)
class BrowseScrollTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val releases = (1L..60L).map { Release(it, "Sample app $it v1.0", "Author", null, 1, 0, 0) }

    @Test fun feedBackAndStateRestorationKeepPositionWhileChangedFiltersOrderAndSectionResetIt() {
        var feed by mutableStateOf(FeedState(items = releases, order = FeedOrder.Top, canLoadMore = false,
            filters = FeedFilters(categoryIds = setOf(7))))
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MobilismTheme(reduceMotion = true) {
            val nav = rememberNavController()
            NavHost(nav, startDestination = "feed") {
                composable("feed") {
                    FeedScreen(feed, AuthState.Guest, {}, {}, {}, {}, { nav.navigate("release/$it") },
                        {}, {}, {}, {}, {}, CategoryState(), {}, {})
                }
                composable("release/{id}") { entry ->
                    Detail(entry.arguments!!.getString("id")!!.toLong()) { nav.popBackStack() }
                }
            }
        } }
        val top = scrollToPartialCard("feed-list")
        compose.onNodeWithText("Sample app 26").performClick()
        compose.onNodeWithTag("feed-list").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        assertCardPosition(top)
        compose.onNodeWithText("Top", substring = false).assertIsSelected()
        compose.runOnIdle { assertEquals(setOf(7L), feed.filters.categoryIds) }
        restoration.emulateSavedInstanceStateRestore()
        assertCardPosition(top)

        compose.runOnIdle { feed = feed.copy(filters = FeedFilters()) }
        assertAtStart()
        scrollToPartialCard("feed-list")
        compose.runOnIdle { feed = feed.copy(order = FeedOrder.Latest) }
        assertAtStart()
        scrollToPartialCard("feed-list")
        compose.runOnIdle { feed = feed.copy(section = ContentSection.Games) }
        assertAtStart()
    }

    @Test fun searchBackAndStateRestorationKeepPositionWhileChangedQueryScopeAndFiltersResetIt() {
        var search by mutableStateOf(SearchState(query = "sample", submitted = "sample", scope = SearchScope.Apps,
            items = releases, canLoadMore = false, filters = FeedFilters(categoryIds = setOf(7))))
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MobilismTheme(reduceMotion = true) {
            val nav = rememberNavController()
            NavHost(nav, startDestination = "search") {
                composable("search") {
                    SearchScreen(search, {}, {}, {}, {}, { nav.navigate("release/$it") }, {}, CategoryState(), {}, {})
                }
                composable("release/{id}") { entry ->
                    Detail(entry.arguments!!.getString("id")!!.toLong()) { nav.popBackStack() }
                }
            }
        } }
        val top = scrollToPartialCard("search-results")
        compose.onNodeWithText("Sample app 26").performClick()
        compose.onNodeWithTag("search-results").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        assertCardPosition(top)
        compose.onNode(hasSetTextAction()).assertTextContains("sample")
        compose.runOnIdle { assertEquals(setOf(7L), search.filters.categoryIds) }
        restoration.emulateSavedInstanceStateRestore()
        assertCardPosition(top)

        compose.runOnIdle { search = search.copy(query = "another", submitted = "another") }
        assertAtStart()
        scrollToPartialCard("search-results")
        compose.runOnIdle { search = search.copy(scope = SearchScope.Games) }
        assertAtStart()
        scrollToPartialCard("search-results")
        compose.runOnIdle { search = search.copy(filters = FeedFilters()) }
        assertAtStart()
    }

    private fun scrollToPartialCard(tag: String): Float {
        compose.onNodeWithTag(tag).performScrollToIndex(24)
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 73f) }
        compose.onNodeWithText("Sample app 26").assertIsDisplayed()
        return compose.onNodeWithText("Sample app 26").fetchSemanticsNode().boundsInRoot.top
    }

    private fun assertCardPosition(top: Float) {
        compose.onNodeWithText("Sample app 26").assertIsDisplayed()
        assertEquals(top, compose.onNodeWithText("Sample app 26").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    private fun assertAtStart() { compose.onNodeWithText("Sample app 1").assertIsDisplayed() }

    @androidx.compose.runtime.Composable
    private fun Detail(id: Long, onBack: () -> Unit) {
        ReleaseScreen(ReleaseState(release = releases.first { it.id == id }, busy = false,
            comments = CommentsState(canLoadMore = false)), DebridState(), DownloadPreferences(), onBack,
            onRetry = {}, onMoreComments = {}, watched = false, downloaded = false, onWatch = {}, onDownloaded = {},
            onLike = {}, onActionMessageShown = {})
    }
}
