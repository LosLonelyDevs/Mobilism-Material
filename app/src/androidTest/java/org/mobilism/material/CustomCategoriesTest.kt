package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.settings.DeveloperPreferencesStore
import org.mobilism.material.ui.*

@RunWith(AndroidJUnit4::class)
class CustomCategoriesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun context(): Context {
        val prefix = "custom-categories-test-${UUID.randomUUID()}"
        return object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("$prefix-$name", mode)
        }
    }

    private fun release() = Release(1, "Example v1.0", "Author", null, 1_793_188_800, 0, 10, section = ContentSection.Apps)

    private fun capture(name: String) {
        compose.waitForIdle()
        val screenshot = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory = File(compose.activity.getExternalFilesDir(null), "custom-categories")
        check(directory.mkdirs() || directory.isDirectory)
        File(directory, "$name.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun developerToggleDefaultsOffAndPickerUsesOneCategorySelection() {
        val context = context()
        val options = DeveloperPreferencesStore(context)
        val store = CustomCategoryStore(context)
        val sports = store.create(ContentSection.Apps, "Sports")
        store.assign(release(), setOf(sports.id))
        assertFalse(options.state.value.customCategoriesActive)
        options.setCustomCategoriesEnabled(true)
        assertFalse(options.state.value.customCategoriesEnabled)
        options.unlock()
        var screen by mutableStateOf("filters")
        var filters by mutableStateOf(FeedFilters())
        compose.setContent { MobilismTheme(reduceMotion = true) {
            val state by options.state.collectAsState()
            when (screen) {
                "options" -> DeveloperOptionsScreen(state, options::setVerboseLogging, options::disable,
                    onBack = { screen = "filters" }, onCustomCategories = options::setCustomCategoriesEnabled)
                "filters" -> CategoryFilterSheet(CategoryState(items = listOf(ReleaseCategory(20, "Utilities"))),
                    filters, {}, { filters = it; screen = "options" }, { screen = "options" },
                    customCategories = store.takeIf { state.customCategoriesActive })
            }
        } }
        compose.onNodeWithText("Sports").assertDoesNotExist()
        compose.onNodeWithText("Manage categories").assertDoesNotExist()
        compose.onNodeWithText("Cancel").performClick()
        assertFalse(options.state.value.customCategoriesEnabled)
        capture("developer-options")
        compose.onNodeWithText("Custom categories").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Sports").assertIsDisplayed().performClick()
        compose.onNodeWithText("Utilities").performClick()
        capture("category-picker")
        compose.onNodeWithText("Apply filters").performClick()
        compose.runOnIdle {
            assertEquals(setOf(20L), filters.categoryIds)
            assertEquals(setOf(sports.id), filters.customCategoryIds)
        }
        compose.onNodeWithText("Custom categories").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Sports").assertDoesNotExist()
        compose.onNodeWithText("Manage categories").assertDoesNotExist()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Custom categories").performClick()
        assertEquals(store.state.value, CustomCategoryStore(context).state.value)
        assertEquals(setOf(sports.id), store.state.value.memberships(release()))
        options.disable()
        assertFalse(DeveloperPreferencesStore(context).state.value.customCategoriesActive)
    }

    @Test fun assignmentSupportsSeveralCategoriesAndCancelDiscardsChanges() {
        val store = CustomCategoryStore(context())
        val sports = store.create(ContentSection.Apps, "Sports")
        val favorites = store.create(ContentSection.Apps, "Favorites")
        var open by mutableStateOf(true)
        compose.setContent { MobilismTheme(reduceMotion = true) {
            if (open) CategoryAssignmentSheet(release(), store, { open = false })
        } }
        compose.onNodeWithText("Sports").performClick()
        compose.onNodeWithText("Favorites").performClick()
        capture("category-assignment")
        compose.onNodeWithText("Save categories").performClick()
        compose.runOnIdle { assertEquals(setOf(sports.id, favorites.id), store.state.value.memberships(release())); open = true }
        compose.onNodeWithText("Sports").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(setOf(sports.id, favorites.id), store.state.value.memberships(release())); open = true }
        compose.onNodeWithText("Sports").performClick()
        compose.onNodeWithText("Save categories").performClick()
        compose.runOnIdle { assertEquals(setOf(favorites.id), store.state.value.memberships(release())) }
    }

    @Test fun homeUsesNativeCategoryTilesWithSeparateCategoriesForEverySection() {
        val store = CustomCategoryStore(context())
        val categories = ContentSection.entries.associateWith { store.create(it, "Sports") }
        var section by mutableStateOf(ContentSection.Apps)
        var visible by mutableStateOf(true)
        var opened: CustomCategory? = null
        compose.setContent { MobilismTheme(reduceMotion = true) {
            val state by store.state.collectAsState()
            HomeScreen(HomeState(section), {}, {}, {}, {}, {}, { _, _ -> }, {},
                customCategories = if (visible) state.categories(section) else emptyList(),
                onCustomCategory = { opened = it })
        } }
        ContentSection.entries.forEach { current ->
            compose.runOnIdle { section = current }
            compose.onNodeWithTag("home-${current.name}").performScrollToNode(hasText("Sports"))
            compose.onNodeWithText("Sports").performClick()
            compose.onNodeWithText("No categories available.").assertDoesNotExist()
            compose.runOnIdle { assertEquals(categories.getValue(current), opened) }
        }
        compose.runOnIdle { visible = false }
        compose.onNodeWithText("Sports").assertDoesNotExist()
    }

    @Test fun managementCreatesRenamesRemovesMembersAndConfirmsDeletion() {
        val store = CustomCategoryStore(context())
        val favorites = store.create(ContentSection.Apps, "Favorites")
        compose.setContent { MobilismTheme(reduceMotion = true) {
            CustomCategoriesScreen(ContentSection.Apps, store, {}, {})
        } }
        compose.onNodeWithContentDescription("Create category").performClick()
        compose.onNodeWithText("Category name").performTextInput(" Sports ")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Sports").performClick()
        val sports = store.state.value.categories.first { it.name == "Sports" }
        compose.runOnIdle { store.assign(release(), setOf(sports.id, favorites.id)) }
        capture("category-members")
        compose.onNodeWithContentDescription("Rename category").performClick()
        compose.onNodeWithText("Category name").performTextReplacement("Matchdays")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithContentDescription("Remove Example from Matchdays").performClick()
        compose.runOnIdle {
            assertEquals(setOf(favorites.id), store.state.value.memberships(release()))
            store.assign(release(), setOf(sports.id, favorites.id))
        }
        compose.onNodeWithContentDescription("Delete category").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Matchdays").assertIsDisplayed()
        compose.onNodeWithContentDescription("Delete category").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle {
            assertFalse(store.state.value.categories.any { it.id == sports.id })
            assertEquals(setOf(favorites.id), store.state.value.memberships(release()))
        }
    }

    @Test fun repositoryAppliesUnionLocallyAndIgnoresCustomFiltersWhenDisabled() = runBlocking {
        val context = context()
        val store = CustomCategoryStore(context)
        val sports = store.create(ContentSection.Apps, "Sports")
        store.assign(release(), setOf(sports.id))
        val preferences = ContentPreferencesStore(context).also { it.hideAdult(false) }
        var enabled = true
        val paths = CopyOnWriteArrayList<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            paths += path
            val rows = if ("/category/20/" in path)
                """{"id":3,"subject":"Native v1.0","date":100,"section":"app"}"""
            else """{"id":4,"subject":"Other v1.0","date":300,"category_id":30,"section":"app"},
                {"id":2,"subject":"Example v2.0","date":200,"category_id":10,"section":"app"},
                {"id":3,"subject":"Native v1.0","date":100,"section":"app"}"""
            val body = if (path.endsWith("/search")) {
                val searchRows = rows.replace(",\"section\":\"app\"", "")
                    .replace("\"subject\":\"Native v1.0\"", "\"subject\":\"Native v1.0\",\"browser_url\":\"/viewtopic.php?f=20&t=3\"")
                """{"releases":[$searchRows]}"""
            } else """{"response":[$rows]}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body(body.toResponseBody()).build()
        }.build()
        val repository = DefaultMobilismRepository(context, preferences, client = client,
            customCategories = store, customCategoriesEnabled = { enabled })
        val filters = FeedFilters(categoryIds = setOf(20), customCategoryIds = setOf(sports.id))
        assertEquals(listOf(2L, 3L), repository.releases(ContentSection.Apps, FeedOrder.Latest, 1, filters).items.map { it.id })
        assertTrue(paths.any { it.contains("/category/all/") })
        assertTrue(paths.any { it.contains("/category/20/") })
        val search = repository.search("Example", SearchScope.Apps, 1, filters)
        assertEquals(listOf(2L, 3L), search.items.map { it.id })
        assertTrue(search.items.all { it.section == ContentSection.Apps })
        paths.clear()
        enabled = false
        assertEquals(listOf(3L), repository.releases(ContentSection.Apps, FeedOrder.Latest, 1, filters).items.map { it.id })
        assertTrue(paths.last().contains("/category/20/"))
        assertEquals(listOf(4L, 2L, 3L), repository.releases(ContentSection.Apps, FeedOrder.Latest, 1,
            filters.copy(categoryIds = emptySet())).items.map { it.id })
        assertEquals(listOf(4L, 2L, 3L), repository.search("Example", SearchScope.Apps, 1,
            filters.copy(categoryIds = emptySet())).items.map { it.id })
        assertEquals(setOf(sports.id), CustomCategoryStore(context).state.value.memberships(release()))
    }
}
