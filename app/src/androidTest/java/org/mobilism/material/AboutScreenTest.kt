package org.mobilism.material

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.After
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

    @After fun clearDeveloperPreferences() {
        compose.activity.getSharedPreferences("material-developer", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun fiveIconTapsUnlockPersistentSettingBeforeAbout() {
        compose.activity.getSharedPreferences("material-developer", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val developer = org.mobilism.material.settings.DeveloperPreferencesStore(compose.activity)
        val repository = DebridRepository(compose.activity)
        compose.setContent {
            var about by remember { mutableStateOf(false) }
            val state by developer.state.collectAsState()
            MobilismTheme(reduceMotion = true) {
                if (about) AboutScreen(onBack = { about = false }, developerMode = state.enabled,
                    onEnableDeveloperMode = developer::unlock)
                else SettingsScreen(DebridState(), repository, onAppearance = {}, onDownloads = {}, onBack = {},
                    onAbout = { about = true }, developer = state, onDeveloperMode = developer::setEnabled)
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("About Mobilism Material"))
        compose.onNodeWithText("Developer mode").assertDoesNotExist()
        compose.onNodeWithText("About Mobilism Material").performClick()
        repeat(4) { compose.onNodeWithContentDescription("Mobilism Material icon").performClick() }
        compose.runOnIdle { assertFalse(developer.state.value.unlocked) }
        compose.onNodeWithContentDescription("Mobilism Material icon").performClick()
        compose.runOnIdle {
            val restored = org.mobilism.material.settings.DeveloperPreferencesStore(compose.activity).state.value
            assertTrue(restored.unlocked)
            assertTrue(restored.enabled)
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("About Mobilism Material"))
        compose.onNodeWithText("Developer mode").assertIsDisplayed().assertIsOn().performClick().assertIsOff()
        val modeBounds = compose.onNodeWithText("Developer mode").fetchSemanticsNode().boundsInRoot
        val aboutBounds = compose.onNodeWithText("About Mobilism Material").fetchSemanticsNode().boundsInRoot
        assertTrue(modeBounds.top < aboutBounds.top)
        compose.runOnIdle {
            val restored = org.mobilism.material.settings.DeveloperPreferencesStore(compose.activity).state.value
            assertTrue(restored.unlocked)
            assertFalse(restored.enabled)
        }
    }

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
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("About Mobilism Material"))
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
