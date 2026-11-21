package org.mobilism.material

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mobilism.material.data.ApiRequestLimits
import org.mobilism.material.data.AppLibraryStore
import org.mobilism.material.data.LibraryEntry
import org.mobilism.material.data.Release
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.following.*
import org.mobilism.material.ui.FollowingScreen

class FollowingScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun updateCardHasNoManualButtonAndPullRespectsRemoteSwitch() {
        val activity = compose.activity
        val settings = FollowingPreferences(activity)
        val notifications = FollowingNotifications(activity)
        val library = AppLibraryStore(activity)
        val entry = LibraryEntry(Release(1, "Reader v1.0", "Sample", null, 0, 0, 0), watched = true)
        var pullEnabled by mutableStateOf(false)
        var check by mutableStateOf(FollowCheckState())
        var calls = 0
        compose.setContent {
            MobilismTheme(reduceMotion = true) {
                FollowingScreen(listOf(entry), emptyList(), check, null, FollowPreferences(intervalHours = 6),
                    settings, notifications, library, onRefresh = {}, onCheck = {
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
        list.performTouchInput { swipeDown() }
        if (BuildConfig.DAILY_USAGE_ENDPOINT.isNotBlank()) {
            compose.onNodeWithText("Checking 0 of 1…").assertIsDisplayed()
            compose.runOnIdle { assertEquals(1, calls) }
        } else {
            compose.runOnIdle { assertEquals(0, calls) }
        }
    }
}
