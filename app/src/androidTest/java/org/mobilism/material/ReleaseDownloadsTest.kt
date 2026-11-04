package org.mobilism.material

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.DebridState
import org.mobilism.material.data.Release
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.settings.DownloadPreferences
import org.mobilism.material.settings.ReleaseDisplayStore
import org.mobilism.material.ui.ReleaseScreen
import org.mobilism.material.ui.ReleaseState

@RunWith(AndroidJUnit4::class)
class ReleaseDownloadsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val link = "https://files.example.org/package"
    private val release = Release(42, "Reader v3.4.2", "Uploader", null, 0, 0, 0,
        contentHtml = "<p>A description that stays on the release page.</p>" +
            "<b>Download Instructions:</b><br><b>arm64-v8a:</b><br>" +
            "<p>Password: reader</p><a href='$link'>Download</a>")

    @Test fun shortcutOpensGroupedLinksAndClosingKeepsThePagePosition() {
        compose.setContent { MobilismTheme(reduceMotion = true) { Page(release) } }
        val shortcut = compose.onNodeWithTag("open-downloads").assertIsDisplayed()
        val before = shortcut.fetchSemanticsNode().boundsInRoot
        shortcut.performClick()
        compose.onNodeWithTag("release-downloads-sheet").assertIsDisplayed()
        val inSheet = hasAnyAncestor(hasTestTag("release-downloads-sheet"))
        compose.onNode(hasText("arm64-v8a") and inSheet).assertIsDisplayed()
        compose.onNode(hasTestTag("download:$link") and inSheet).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Close downloads").performClick()
        compose.onNodeWithTag("release-downloads-sheet").assertDoesNotExist()
        org.junit.Assert.assertEquals(before, shortcut.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun releasesWithoutLinksHaveNoDownloadsShortcut() {
        compose.setContent { MobilismTheme(reduceMotion = true) {
            Page(release.copy(contentHtml = "<p>Description only.</p>"))
        } }
        compose.onNodeWithTag("open-downloads").assertDoesNotExist()
    }

    @Test fun descriptionChoiceCarriesToTheNextReleaseAndCanBeExpandedAgain() {
        val display = ReleaseDisplayStore(true) {}
        var current by mutableStateOf(release)
        compose.setContent { MobilismTheme(reduceMotion = true) {
            val expanded by display.descriptionExpanded.collectAsState()
            Page(current, expanded, display::setDescriptionExpanded)
        } }
        compose.onNodeWithText("Description").performScrollTo().performClick()
        compose.onNodeWithText("Description").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        compose.runOnIdle { current = release.copy(id = 43, title = "Reader v3.4.3") }
        compose.onNodeWithText("Description").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        compose.onNodeWithText("Description").performClick()
        compose.onNodeWithText("Description").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
    }

    @Composable private fun Page(item: Release, expanded: Boolean? = null, onExpanded: ((Boolean) -> Unit)? = null) {
        ReleaseScreen(ReleaseState(item, busy = false), DebridState(), DownloadPreferences(),
            onBack = {}, onRetry = {}, onMoreComments = {}, watched = false, downloaded = false,
            onWatch = {}, onDownloaded = {}, onLike = {}, onActionMessageShown = {},
            descriptionExpanded = expanded, onDescriptionExpanded = onExpanded)
    }
}
