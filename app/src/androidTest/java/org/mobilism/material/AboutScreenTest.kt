package org.mobilism.material

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.DebridRepository
import org.mobilism.material.data.DebridState
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.ui.AboutScreen
import org.mobilism.material.ui.REPOSITORY_URL
import org.mobilism.material.ui.SettingsScreen

@RunWith(AndroidJUnit4::class)
class AboutScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun opensAboutFromLastSettingsCardAndShowsBuildReleaseNotes() {
        val repository = DebridRepository(compose.activity)
        val opened = mutableListOf<String>()
        compose.setContent {
            var about by remember { mutableStateOf(false) }
            MobilismTheme(reduceMotion = true) {
                if (about) AboutScreen(onBack = { about = false }, openRepository = { opened += it })
                else SettingsScreen(DebridState(), repository, onAppearance = {}, onDownloads = {}, onBack = {},
                    onAbout = { about = true })
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(6)
        compose.onNodeWithText("About Mobilism Material").assertIsDisplayed().performClick()
        compose.onNodeWithText("Mobilism Material").assertIsDisplayed()
        compose.onNodeWithText("v${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        compose.onNodeWithText(BuildConfig.ABOUT_DESCRIPTION).assertIsDisplayed()
        val note = BuildConfig.RELEASE_NOTES.lines().first { it.startsWith("- ") }.removePrefix("- ")
        compose.onNodeWithText(note).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("GitHub").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(REPOSITORY_URL), opened) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }
}
