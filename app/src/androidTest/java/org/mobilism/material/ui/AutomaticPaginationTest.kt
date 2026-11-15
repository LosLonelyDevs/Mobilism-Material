package org.mobilism.material.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme

class AutomaticPaginationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val release = Release(42, "Example v2.0", "Author", null, 1, 0, 0, section = ContentSection.Apps)

    @Test fun customCategoryAutomaticallyContinuesEmptyBatchesUntilAReleaseAppears() {
        var state by mutableStateOf(FeedState(page = 1, awaitingMore = true,
            filters = FeedFilters(customCategoryIds = setOf("favorites"))))
        var calls = 0
        compose.setContent { MobilismTheme(reduceMotion = true) {
            FeedScreen(state, AuthState.Guest, {}, {}, {}, {
                calls++
                state = state.copy(page = state.page + 1, awaitingMore = calls < 3, canLoadMore = calls < 3,
                    items = if (calls < 3) emptyList() else listOf(release))
            }, {}, {}, {}, {}, {}, {}, CategoryState(), {}, {})
        } }
        compose.waitUntil { calls == 3 }
        compose.onNodeWithText("Example", substring = false).assertIsDisplayed()
        compose.onNodeWithText("No matches in the posts just checked.").assertDoesNotExist()
        compose.onNodeWithText("Keep looking").assertDoesNotExist()
        compose.runOnIdle { assertEquals(3, calls) }
    }

    @Test fun searchWaitsForEachExplicitPageAndStopsWhenTheSourceEnds() {
        var state by mutableStateOf(SearchState(query = "Example", submitted = "Example", page = 1, awaitingMore = true))
        var calls = 0
        compose.setContent { MobilismTheme(reduceMotion = true) {
            SearchScreen(state, {}, {}, {}, {
                calls++
                state = state.copy(page = state.page + 1, awaitingMore = calls < 3, canLoadMore = calls < 3)
            }, {}, {}, CategoryState(), {}, {})
        } }
        compose.runOnIdle { assertEquals(0, calls) }
        repeat(3) {
            compose.onNodeWithText("Load more").performClick()
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(it + 1, calls) }
        }
        compose.onNodeWithText("Keep looking").assertDoesNotExist()
        compose.onNodeWithText("No matches in the posts just checked.").assertDoesNotExist()
        compose.onNodeWithText("No releases found for “Example”.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(3, calls) }
    }

    @Test fun continuationWaitsWhileBusyAndStopsAtAnError() {
        var state by mutableStateOf(SearchState(query = "Example", submitted = "Example", page = 1,
            busy = true, awaitingMore = true))
        var calls = 0
        compose.setContent { MobilismTheme(reduceMotion = true) {
            SearchScreen(state, {}, {}, {}, { calls++; state = state.copy(busy = true) }, {}, {}, CategoryState(), {}, {})
        } }
        compose.onNodeWithText("Searching…").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, calls); state = state.copy(busy = false) }
        compose.onNodeWithText("Load more").performClick()
        compose.waitUntil { calls == 1 }
        compose.runOnIdle { state = state.copy(busy = false, error = ApiFailure(FailureKind.Connection, "Offline").forScreen()) }
        compose.onNodeWithText("Keep looking").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, calls) }
    }
}
