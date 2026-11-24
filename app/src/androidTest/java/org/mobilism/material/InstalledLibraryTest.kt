package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.ui.*

@RunWith(AndroidJUnit4::class)
class InstalledLibraryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun linkedAppsSkipSearchCachedMatchesOpenDirectlyAndTheFilterStaysVisible() {
        val store = AppLibraryStore(object : ContextWrapper(compose.activity) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("installed-fixture-$name", mode)
        })
        val watched = Release(9, "Fixture v1", "", null, 1, 0, 0, section = ContentSection.Apps)
        store.watch(watched, true)
        store.linkPackage(9, "org.local.1")
        store.watch(watched.copy(id = 10, title = "App 04 v1.0"), true)
        val apps = (1..30).map { i -> InstalledApp("org.local.$i", "App ${i.toString().padStart(2, '0')}", "1",
            ColorDrawable(Color.BLUE), false) }
        val searched = mutableListOf<String>()
        val opened = mutableListOf<Long>()
        var reads = 0
        compose.setContent { MobilismTheme(reduceMotion = true) {
            val library by store.state.collectAsState()
            LibraryScreen(library, InstalledState(apps), store, {}, { searched += it }, {}, { opened += it }, {}, false, {
                reads++
                InstalledReleaseCatalog(listOf(CachedInstalledRelease(7, "Different store name", "2.3", "org.local.2")))
            })
        } }
        compose.onNodeWithText("App 01").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithText("App 04").assertIsNotEnabled()
        compose.onNodeWithText("App 02").performClick()
        compose.runOnIdle { assertEquals(listOf(7L), opened); assertTrue(searched.isEmpty()) }
        compose.onNodeWithText("App 03").performClick()
        compose.runOnIdle { assertEquals(listOf("App 03"), searched) }
        compose.onNode(hasScrollAction() and !hasSetTextAction()).performScrollToIndex(22)
        compose.onNode(hasSetTextAction()).assertIsDisplayed().performTextInput("App 29")
        compose.onNodeWithText("org.local.29").assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.library_clear_filter)).performClick()
        compose.onNode(hasScrollAction() and !hasSetTextAction()).performScrollToIndex(20)
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.library_back_to_top)).performClick()
        compose.onNodeWithText("App 01").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, reads); assertEquals(1, searched.size) }
    }
}
