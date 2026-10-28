package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.ContentSection
import org.mobilism.material.data.DebridRepository
import org.mobilism.material.data.DebridState
import org.mobilism.material.data.FeedOrder
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.settings.LandingPage
import org.mobilism.material.settings.LandingPageStore
import org.mobilism.material.ui.SettingsScreen

@RunWith(AndroidJUnit4::class)
class LandingPageSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var context: Context

    @Before fun isolatedPreferences() {
        context = object : ContextWrapper(compose.activity) {
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("landing-test-$name", mode)
        }
        clearPreferences()
    }

    @After fun clearPreferences() {
        context.getSharedPreferences("material-navigation", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun pickerDefaultsToAppsHomeAndPersistsBooksTop() {
        val store = LandingPageStore(context)
        val repository = DebridRepository(context)
        assertEquals(LandingPage(), store.state.value)
        compose.setContent {
            val page by store.state.collectAsState()
            MobilismTheme(reduceMotion = true) {
                SettingsScreen(DebridState(), repository, {}, {}, {},
                    landingPage = page, onLandingPage = store::select)
            }
        }
        compose.onNodeWithText("Apps · Home").assertIsDisplayed()
        compose.onNodeWithText("Landing page").performClick()
        compose.onNode(hasText("Apps · Home") and isSelected()).assertIsDisplayed()
        compose.onNodeWithText("Books · Top").performScrollTo().assertIsDisplayed()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(500, 5_000)
        val screenshot = File(compose.activity.getExternalFilesDir(null), "screenshots/landing-page.png")
        screenshot.parentFile?.mkdirs()
        screenshot.outputStream().use {
            automation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("Books · Top").performClick()
        compose.onNodeWithText("Default landing page").assertDoesNotExist()
        compose.onNodeWithText("Books · Top").assertIsDisplayed()
        assertEquals(LandingPage(ContentSection.Books, FeedOrder.Top), LandingPageStore(context).state.value)
    }

    @Test fun explicitLandingPageOverridesLegacySectionAndInvalidValuesFallBack() {
        val preferences = context.getSharedPreferences("material-navigation", Context.MODE_PRIVATE)
        preferences.edit().putString("section", "Books").commit()
        val store = LandingPageStore(context)
        assertEquals(LandingPage(ContentSection.Books), store.state.value)
        store.select(LandingPage(ContentSection.Apps, FeedOrder.Latest))
        preferences.edit().putString("section", "Games").commit()
        assertEquals(LandingPage(ContentSection.Apps, FeedOrder.Latest), LandingPageStore(context).state.value)
        preferences.edit().putString("landing-section", "missing").putString("landing-order", "missing").commit()
        assertEquals(LandingPage(), LandingPageStore(context).state.value)
    }
}
