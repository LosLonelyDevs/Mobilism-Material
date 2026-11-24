package org.mobilism.material

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mobilism.material.data.ApiRequestLimits
import org.mobilism.material.data.AppLibraryStore
import org.mobilism.material.data.LibraryEntry
import org.mobilism.material.data.Release
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.following.*
import org.mobilism.material.ui.FollowingError
import org.mobilism.material.ui.FollowingScreen
import org.mobilism.material.ui.ScreenError

class FollowingScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun filterMatchesFollowedNamesLocallyAndChecksExpansionPersists() {
        val activity = compose.activity
        val settings = FollowingPreferences(activity)
        val original = settings.state.value.checksExpanded
        val library = AppLibraryStore(activity)
        val notifications = FollowingNotifications(activity)
        val entries = listOf("Reader v1.0", "Camera v2.0").mapIndexed { index, title ->
            LibraryEntry(Release(index.toLong() + 1, title, "Sample", null, 0, 0, 0), watched = true)
        }
        var refreshes = 0
        try {
            settings.checksExpanded(true)
            compose.setContent {
                val preferences by settings.state.collectAsState()
                MobilismTheme(reduceMotion = true) {
                    FollowingScreen(entries, emptyList(), FollowCheckState(), null, preferences, settings, notifications,
                        library, onRefresh = { refreshes++ }, onCheck = { error("Filter triggered a check") },
                        onLink = { _, _ -> }, onRelease = {}, onBack = {})
                }
            }
            compose.onNodeWithText(activity.getString(R.string.follow_update_checks)).performClick()
            compose.runOnIdle { assertEquals(false, FollowingPreferences(activity).state.value.checksExpanded) }
            val baseline = compose.runOnIdle { refreshes }
            val field = compose.onNodeWithText(activity.getString(R.string.follow_filter))
            field.performTextInput("  REad ")
            compose.onNodeWithText("Reader").assertExists()
            compose.onNodeWithText("Camera").assertDoesNotExist()
            field.performTextReplacement("v1.0")
            compose.onNodeWithText(activity.getString(R.string.follow_no_matches)).assertExists()
            field.performTextClearance()
            compose.onNodeWithText("Camera").assertExists()
            compose.runOnIdle { assertEquals(baseline, refreshes) }
        } finally {
            settings.checksExpanded(original)
        }
    }

    @Test fun updateCardHasNoManualButtonAndPullRespectsRemoteSwitch() {
        val activity = compose.activity
        val settings = FollowingPreferences(activity)
        val notifications = FollowingNotifications(activity)
        val library = AppLibraryStore(activity)
        val entry = LibraryEntry(Release(1, "Reader v1.0", "Sample", null, 0, 0, 0), watched = true)
        var pullEnabled by mutableStateOf(false)
        var check by mutableStateOf(FollowCheckState())
        var failure by mutableStateOf<FollowingError?>(null)
        var localRefreshes = 0
        var calls = 0
        compose.setContent {
            MobilismTheme(reduceMotion = true) {
                FollowingScreen(listOf(entry), emptyList(), check, failure, FollowPreferences(intervalHours = 6),
                    settings, notifications, library, onRefresh = { localRefreshes++ }, onCheck = {
                        calls++
                        check = FollowCheckState(busy = true, total = 1)
                    }, onLink = { _, _ -> }, onRelease = {}, onBack = {},
                    limits = ApiRequestLimits.Defaults.copy(followPullToRefreshEnabled = pullEnabled))
            }
        }
        compose.onNodeWithText("Every 6 hours").assertIsDisplayed()
        compose.onNodeWithText("Check now").assertDoesNotExist()
        compose.onNodeWithText("Android may delay", substring = true).assertDoesNotExist()
        compose.onNodeWithText("One alert per version", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Follow as many apps", substring = true).assertDoesNotExist()
        val list = compose.onNode(hasScrollToIndexAction())
        list.performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(0, calls); pullEnabled = true }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("Every 6 hours").assertIsDisplayed()
        compose.onNodeWithTag("feed-refresh-indicator").assertDoesNotExist()
        val beforeRetry = compose.runOnIdle {
            assertEquals(0, calls)
            failure = FollowingError(ScreenError("Fixture error"), fromManualCheck = false)
            localRefreshes
        }
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle {
            assertEquals(0, calls)
            assertEquals(beforeRetry + 1, localRefreshes)
            failure = null
        }
        list.performTouchInput { swipeDown() }
        if (BuildConfig.DAILY_USAGE_ENDPOINT.isNotBlank()) {
            compose.onNodeWithText("Checking 0 of 1…").assertIsDisplayed()
            compose.runOnIdle { assertEquals(1, calls) }
        } else {
            compose.runOnIdle { assertEquals(0, calls) }
        }
    }
}
