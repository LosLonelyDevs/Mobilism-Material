package org.mobilism.material

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
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

    @Test fun tenIconTapsUnlockSubmenuAndDisablingRequiresUnlockAgain() {
        compose.activity.getSharedPreferences("material-developer", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val developer = org.mobilism.material.settings.DeveloperPreferencesStore(compose.activity)
        val repository = DebridRepository(compose.activity)
        var exports = 0
        compose.setContent {
            var page by remember { mutableStateOf("settings") }
            val state by developer.state.collectAsState()
            androidx.compose.runtime.LaunchedEffect(state.enabled) {
                if (!state.enabled && page == "developer") page = "settings"
            }
            MobilismTheme(reduceMotion = true) {
                if (page == "about") AboutScreen(onBack = { page = "settings" }, developerMode = state.enabled,
                    onEnableDeveloperMode = developer::unlock)
                else if (page == "developer") org.mobilism.material.ui.DeveloperOptionsScreen(state,
                    developer::setVerboseLogging, developer::setDebridEnabled, developer::disable,
                    onBack = { page = "settings" }, onExportLogs = { exports++ })
                else SettingsScreen(DebridState(), repository, onAppearance = {}, onDownloads = {}, onBack = {},
                    onAbout = { page = "about" }, developer = state, onDeveloperOptions = { page = "developer" })
            }
        }
        compose.runOnIdle {
            assertFalse(developer.state.value.enabled)
            assertTrue(developer.state.value.debridEnabled)
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Premium"))
        compose.onNodeWithText("Premium").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("About Mobilism Material"))
        compose.onNodeWithText("Developer options").assertDoesNotExist()
        compose.onNodeWithText("Open Mobilism links").assertDoesNotExist()
        compose.onNodeWithText("About Mobilism Material").performClick()
        repeat(9) { compose.onNodeWithContentDescription("Mobilism Material icon").performClick() }
        compose.runOnIdle { assertFalse(developer.state.value.enabled) }
        compose.onNodeWithContentDescription("Mobilism Material icon").performClick()
        compose.runOnIdle {
            val restored = org.mobilism.material.settings.DeveloperPreferencesStore(compose.activity).state.value
            assertTrue(restored.enabled)
            assertFalse(restored.verboseLogging)
            assertTrue(restored.debridEnabled)
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("About Mobilism Material"))
        val modeBounds = compose.onNodeWithText("Developer options").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val aboutBounds = compose.onNodeWithText("About Mobilism Material").fetchSemanticsNode().boundsInRoot
        assertTrue(modeBounds.top < aboutBounds.top)
        compose.onNodeWithText("Developer options").performClick()
        val screenshot = java.io.File(compose.activity.getExternalFilesDir(null), "screenshots/developer-options.png")
        screenshot.parentFile?.mkdirs()
        screenshot.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("Verbose logging").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("Experimental premium services").performScrollTo().assertIsOn().performClick().assertIsOff()
        compose.onNodeWithText("Export logs").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, exports) }
        compose.onNodeWithText("Developer mode").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("About Mobilism Material"))
        compose.onNodeWithText("Developer options").assertDoesNotExist()
        compose.runOnIdle {
            val restored = org.mobilism.material.settings.DeveloperPreferencesStore(compose.activity).state.value
            assertFalse(restored.enabled)
            assertFalse(restored.verboseLogging)
            assertFalse(restored.debridEnabled)
        }
        compose.onNodeWithText("About Mobilism Material").performClick()
        repeat(9) { compose.onNodeWithContentDescription("Mobilism Material icon").performClick() }
        compose.runOnIdle { assertFalse(developer.state.value.enabled) }
        compose.onNodeWithContentDescription("Mobilism Material icon").performClick()
        compose.runOnIdle {
            assertTrue(developer.state.value.enabled)
            assertFalse(developer.state.value.debridEnabled)
            developer.setDebridEnabled(true)
            developer.disable()
            val restored = org.mobilism.material.settings.DeveloperPreferencesStore(compose.activity).state.value
            assertFalse(restored.enabled)
            assertTrue(restored.debridEnabled)
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
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("GitHub"))
        compose.onNodeWithText("GitHub").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(REPOSITORY_URL), opened) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }
}
