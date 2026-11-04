package org.mobilism.material.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme

class SearchDiscoveryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun recentQueriesSupportSearchRemoveAndClearOnNarrowPhone() {
        var recent by mutableStateOf(listOf("A very long recent book query that should occupy just one line", "Another book"))
        var submitted: String? = null
        compose.setContent { MobilismTheme(reduceMotion = true) {
            Box(Modifier.width(320.dp)) {
                SearchScreen(SearchState(), {}, {}, {}, {}, {}, {}, CategoryState(), {}, {},
                    recentQueries = recent, onRecentQuery = { submitted = it },
                    onRemoveRecent = { recent = recent - it }, onClearRecent = { recent = emptyList() })
            }
        } }
        compose.onNodeWithText("Newest posts").assertDoesNotExist()
        compose.onNodeWithText("Another book").performClick()
        compose.runOnIdle { assertEquals("Another book", submitted) }
        compose.onNodeWithContentDescription("Remove recent search: Another book").performClick()
        compose.onNodeWithText("Another book").assertDoesNotExist()
        compose.onNodeWithText("Clear all").performClick()
        compose.onNodeWithText("Recent searches").assertDoesNotExist()
    }

    @Test fun bookTypesAllowMultipleSelectionsAndResetTogether() {
        var applied: FeedFilters? = null
        compose.setContent { MobilismTheme(reduceMotion = true) {
            CategoryFilterSheet(CategoryState(section = ContentSection.Books), FeedFilters(), {}, { applied = it }, {})
        } }
        compose.onNodeWithText("EPUB").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("PDF").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Reset").performClick()
        compose.onNodeWithText("EPUB").performScrollTo().assertIsNotSelected().performClick()
        compose.onNodeWithText("PDF").performScrollTo().assertIsNotSelected().performClick()
        compose.onNodeWithText("Apply filters").performClick()
        compose.runOnIdle { assertEquals(setOf(BookFormat.Epub, BookFormat.Pdf), applied?.bookFormats) }
    }
}
