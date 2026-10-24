package org.mobilism.material

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.designsystem.AppFont
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.designsystem.ThemeCatalog
import org.mobilism.material.settings.AppearanceStore
import org.mobilism.material.ui.AppearanceScreen

@RunWith(AndroidJUnit4::class)
class AppearanceOptionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before @After fun clearAppearance() {
        compose.activity.getSharedPreferences("material-appearance", 0).edit().clear().commit()
        File(compose.activity.filesDir, "fonts").deleteRecursively()
    }

    @Test fun pairedThemesAndFontPreviewsPersistSelections() = runBlocking {
        val store = AppearanceStore(compose.activity)
        store.load()
        render(store)
        for (theme in ThemeCatalog.builtIn) {
            compose.onNodeWithContentDescription("Choose theme").performClick()
            compose.onNodeWithTag("theme-options").performScrollToNode(hasContentDescription(theme.name))
            compose.onNodeWithContentDescription(theme.name).performClick()
            compose.runOnIdle { assertEquals(theme.id, store.state.value.theme.id) }
            compose.onNodeWithTag("theme-options").assertDoesNotExist()
        }
        compose.runOnIdle { store.selectTheme(ThemeCatalog.pairs.first().light) }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        capture("appearance-light")
        compose.onNodeWithContentDescription("Choose theme").performClick()
        compose.onNodeWithText("Light").assertIsDisplayed()
        capture("theme-picker-light")
        compose.onNodeWithContentDescription("Close themes").performClick()
        compose.runOnIdle { store.selectTheme(ThemeCatalog.default) }
        capture("appearance-dark")
        compose.onNodeWithContentDescription("Choose theme").performClick()
        compose.onNodeWithTag("theme-options").performScrollToNode(hasText("Dark"))
        compose.onNodeWithText("Dark").assertIsDisplayed()
        capture("theme-picker-dark")
        compose.onNodeWithContentDescription("Close themes").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Choose font"))
        compose.onNodeWithContentDescription("Choose font").performClick()
        capture("font-menu")
        compose.onNodeWithText("Atkinson Hyperlegible").performScrollTo().performClick()
        assertEquals(AppFont.AtkinsonHyperlegible, store.state.value.font)
        val restored = AppearanceStore(compose.activity)
        restored.load()
        assertEquals(AppFont.AtkinsonHyperlegible, restored.state.value.font)
        assertEquals(ThemeCatalog.default.id, restored.state.value.theme.id)
        compose.onNodeWithContentDescription("Choose font").performClick()
        compose.onNodeWithText("Use custom font").performScrollTo().assertIsDisplayed()
        val custom = compose.onNodeWithText("Use custom font").fetchSemanticsNode().boundsInRoot
        val system = compose.onNodeWithText("System sans serif").fetchSemanticsNode().boundsInRoot
        assertTrue(custom.top > system.bottom)
        capture("font-menu-custom")
    }

    @Test fun unavailableSavedFontFallsBackToGeist() = runBlocking {
        compose.activity.getSharedPreferences("material-appearance", 0).edit()
            .putString("font", "RemovedFont").commit()
        val store = AppearanceStore(compose.activity)
        store.load()
        assertEquals(AppFont.Geist, store.state.value.font)
    }

    @Test fun customFontPersistsAndInvalidFilesKeepTheWorkingFont() = runBlocking {
        val store = AppearanceStore(compose.activity)
        store.load()
        val source = File(compose.activity.cacheDir, "font-test.ttf")
        val invalid = File(compose.activity.cacheDir, "not-a-font.ttf")
        try {
            compose.activity.resources.openRawResource(org.mobilism.material.designsystem.R.font.alata_regular).use { input ->
                source.outputStream().use { input.copyTo(it) }
            }
            store.importFont(Uri.fromFile(source))
            val imported = store.state.value.customFontFile
            assertNotNull(imported)
            assertEquals(AppFont.Custom, store.state.value.font)
            source.delete()
            val restored = AppearanceStore(compose.activity)
            restored.load()
            assertEquals(imported, restored.state.value.customFontFile)
            assertEquals(AppFont.Custom, restored.state.value.font)
            invalid.writeText("not a font")
            assertTrue(runCatching { restored.importFont(Uri.fromFile(invalid)) }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(imported, restored.state.value.customFontFile)
            assertTrue(checkNotNull(imported).isFile)
            assertEquals(1, File(compose.activity.filesDir, "fonts").listFiles().orEmpty().size)
            render(restored)
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Choose font"))
            compose.onNodeWithContentDescription("Choose font").performClick()
            compose.onNodeWithText("Use custom font").performScrollTo().assertIsDisplayed()
            capture("custom-font")
        } finally {
            source.delete()
            invalid.delete()
        }
    }

    private fun render(store: AppearanceStore) {
        compose.setContent {
            val state by store.state.collectAsState()
            MobilismTheme(state.theme, state.font, true, state.customFontFile) {
                AppearanceScreen(state, store, {})
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = File(compose.activity.getExternalFilesDir(null), "appearance-checks").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
