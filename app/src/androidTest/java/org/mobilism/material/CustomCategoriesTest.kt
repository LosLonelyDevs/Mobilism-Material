package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme
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

    private fun capture(name: String, node: SemanticsNodeInteraction? = null) {
        compose.waitForIdle()
        val screenshot = node?.captureToImage()?.asAndroidBitmap()
            ?: checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory = File(compose.activity.getExternalFilesDir(null), "custom-categories")
        check(directory.mkdirs() || directory.isDirectory)
        File(directory, "$name.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun preferenceDefaultsOnAndPickerUsesOneCategorySelection() {
        val context = context()
        val options = ContentPreferencesStore(context)
        val store = CustomCategoryStore(context)
        val sports = store.create(ContentSection.Apps, "Sports")
        store.assign(release(), setOf(sports.id))
        assertTrue(options.customCategoriesEnabled.value)
        var screen by mutableStateOf("filters")
        var filters by mutableStateOf(FeedFilters())
        compose.setContent { MobilismTheme(reduceMotion = true) {
            val enabled by options.customCategoriesEnabled.collectAsState()
            when (screen) {
                "options" -> CustomCategoriesSetting(enabled) { options.setCustomCategoriesEnabled(it); screen = "filters" }
                "filters" -> CategoryFilterSheet(CategoryState(items = listOf(ReleaseCategory(20, "Utilities"))),
                    filters, {}, { filters = it; screen = "options" }, { screen = "options" },
                    customCategories = store.takeIf { enabled })
            }
        } }
        val heading = compose.onNodeWithText("Categories · Apps").getUnclippedBoundsInRoot()
        val manage = compose.onNodeWithText("Manage").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(manage.left >= heading.right && manage.top < heading.bottom && heading.top < manage.bottom)
        compose.onNodeWithText("Sports").assertIsDisplayed().performClick()
        compose.onNodeWithText("Utilities").performClick()
        capture("category-picker")
        compose.onNodeWithText("Apply filters").performClick()
        compose.runOnIdle {
            assertEquals(setOf(20L), filters.categoryIds)
            assertEquals(setOf(sports.id), filters.customCategoryIds)
        }
        compose.onNodeWithText("Custom categories").performClick()
        assertFalse(ContentPreferencesStore(context).customCategoriesEnabled.value)
        compose.onNodeWithText("Sports").assertDoesNotExist()
        compose.onNodeWithText("Manage").assertDoesNotExist()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Custom categories").performClick()
        assertEquals(store.state.value, CustomCategoryStore(context).state.value)
        assertEquals(setOf(sports.id), store.state.value.memberships(release()))
        assertTrue(ContentPreferencesStore(context).customCategoriesEnabled.value)
    }

    @Test fun assignmentSupportsSeveralCategoriesAndCancelDiscardsChanges() {
        val store = CustomCategoryStore(context())
        val sports = store.create(ContentSection.Apps, "Sports")
        val favorites = store.create(ContentSection.Apps, "Favorites")
        var open by mutableStateOf(true)
        compose.setContent { MobilismTheme(reduceMotion = true) {
            if (open) CategoryAssignmentSheet(release(), store, { open = false }, {})
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

    @Test fun manageFromAssignmentPreservesDraftAndShowsUpdatedDescriptions() {
        val context = context()
        val store = CustomCategoryStore(context)
        val sports = store.create(ContentSection.Apps, "Sports", "Live scores")
        val release = release()
        compose.setContent { MobilismTheme(reduceMotion = true) {
            val nav = rememberNavController()
            NavHost(nav, startDestination = "release") {
                composable("release") {
                    CategoryAssignmentSheet(release, store, onDismiss = {}, onManage = {
                        nav.navigate("manage/${it.name}")
                    })
                }
                composable("manage/{section}") { entry ->
                    CustomCategoriesScreen(ContentSection.valueOf(requireNotNull(entry.arguments?.getString("section"))),
                        store, onBack = { nav.popBackStack() }, onRelease = {})
                }
            }
        } }
        compose.onNodeWithText("Reset").assertDoesNotExist()
        compose.onNodeWithText("Live scores").performClick()
        compose.onNodeWithText("Manage").performClick()
        compose.onNodeWithText("Sports").performClick()
        compose.onNodeWithContentDescription("Edit category").performClick()
        compose.onNodeWithText("Description (optional)").performTextReplacement("Scores and fixtures")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasText("Scores and fixtures") and isToggleable()).assertIsOn()
        compose.runOnIdle { assertTrue(store.state.value.memberships(release).isEmpty()) }
        compose.onNodeWithText("Create category").performClick()
        compose.onNodeWithText("Category name").performTextInput("Reading")
        compose.onNodeWithText("Description (optional)").performTextInput("Read later")
        compose.onNodeWithText("Save").performClick()
        compose.onNode(hasText("Read later") and isToggleable()).assertIsOn()
        compose.onNodeWithText("Save categories").performClick()
        compose.runOnIdle {
            val restored = CustomCategoryStore(context).state.value
            assertEquals("Scores and fixtures", restored.categories.single { it.id == sports.id }.description)
            assertEquals(restored.categories.map { it.id }.toSet(), restored.memberships(release))
        }
    }

    @Test fun releaseCardsShowForumAndAssignedCategoriesInBothDensities() {
        val store = CustomCategoryStore(context())
        val release = release().copy(categoryId = 20)
        val favorites = store.create(ContentSection.Apps, "Favorites")
        val travel = store.create(ContentSection.Apps, "Travel")
        store.assign(release, setOf(travel.id, favorites.id))
        var enabled by mutableStateOf(true)
        var compact by mutableStateOf(false)
        var opened: FeedFilters? = null
        compose.setContent {
            val custom by store.state.collectAsState()
            MobilismTheme(reduceMotion = true) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalForumCategoryNames provides mapOf(20L to "Utilities"),
                    LocalReleaseAssignments provides custom.takeIf { enabled },
                    LocalOpenReleaseCategory provides { _, filters -> opened = filters },
                ) {
                    ReleaseCard(release, androidx.compose.ui.graphics.Color.Blue, compact = compact) {}
                }
            }
        }
        for (density in listOf(false, true)) {
            compose.runOnIdle { compact = density }
            val chips = compose.onNode(hasAnyDescendant(hasText("Utilities")) and SemanticsMatcher.keyIsDefined(
                androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange), useUnmergedTree = true)
            chips.performTouchInput { swipeLeft() }
            compose.onNodeWithText("Utilities").assertIsDisplayed()
            val order = compose.onAllNodes(hasText("Favorites") or hasText("Travel") or hasText("Utilities"))
                .fetchSemanticsNodes().flatMap { it.config[androidx.compose.ui.semantics.SemanticsProperties.Text] }.map { it.text }
            assertEquals(listOf("Favorites", "Travel", "Utilities"), order)
            chips.performTouchInput { swipeRight() }
            compose.onNodeWithText("Favorites").assertIsDisplayed().performTouchInput { click() }
            assertEquals(setOf(favorites.id), opened?.customCategoryIds)
        }
        val allCategories = compose.onNodeWithTag("release-1").getUnclippedBoundsInRoot()
        compose.runOnIdle { store.assign(release, setOf(travel.id)) }
        compose.onNodeWithText("Travel").assertIsDisplayed()
        compose.onNodeWithText("Favorites").assertDoesNotExist()
        compose.runOnIdle { enabled = false }
        compose.onNodeWithText("Travel").assertDoesNotExist()
        compose.onNodeWithText("Utilities").assertIsDisplayed()
        assertEquals(allCategories, compose.onNodeWithTag("release-1").getUnclippedBoundsInRoot())
    }

    @Test fun cardsKeepTheirHeightAndPlaceCategoriesAfterDateAndViews() {
        val sample = release().copy(title = "Aloha Browser v2.15.1 [Premium]", categoryId = 20, views = 139, replies = 1,
            author = "Reader", thumbnail = "file:///android_asset/readme/aloha.png")
        val unassigned = sample.copy(id = 2, title = "Flud - Torrent Downloader for Android v2.0", author = "Uploader",
            thumbnail = "file:///android_asset/readme/flud.png", publishedAt = sample.publishedAt + 3600, categoryId = null, views = 37, replies = 0)
        var compact by mutableStateOf(true)
        compose.setContent { MobilismTheme(reduceMotion = true) {
            CompositionLocalProvider(LocalForumCategoryNames provides mapOf(20L to "Photography")) {
                Column(Modifier.requiredWidth(360.dp).testTag("card-comparison"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReleaseCard(sample, androidx.compose.material3.MaterialTheme.colorScheme.primary, compact = compact) {}
                    ReleaseCard(unassigned, androidx.compose.material3.MaterialTheme.colorScheme.primary, compact = compact) {}
                }
            }
        } }
        for (isCompact in listOf(true, false)) {
            compose.runOnIdle { compact = isCompact }
            val clock = android.text.format.DateFormat.getTimeFormat(compose.activity)
            fun timeOf(item: Release) = clock.format(java.util.Date(item.publishedAt * 1000L))
            val date = compose.onNode(hasText(" · ${timeOf(sample)}", substring = true) and hasAnyAncestor(hasTestTag("release-1")),
                useUnmergedTree = true).getUnclippedBoundsInRoot()
            compose.onNode(hasText(" · ${timeOf(unassigned)}", substring = true) and hasAnyAncestor(hasTestTag("release-2")),
                useUnmergedTree = true).assertExists()
            val views = compose.onNodeWithContentDescription("139 views", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val comments = compose.onNodeWithContentDescription("1 comment", useUnmergedTree = true).getUnclippedBoundsInRoot()
            compose.onNodeWithContentDescription("0 comments", useUnmergedTree = true).assertDoesNotExist()
            val visibleViews = compose.onNodeWithContentDescription("139 views", useUnmergedTree = true).fetchSemanticsNode()
            assertEquals(visibleViews.size.width.toFloat(), visibleViews.boundsInRoot.width, 2f)
            val chip = compose.onNodeWithText("Photography").getUnclippedBoundsInRoot()
            val card = compose.onNodeWithTag("release-1").getUnclippedBoundsInRoot()
            assertTrue(views.left - date.right >= 9.dp && comments.left >= views.right && comments.right <= chip.left)
            for (item in listOf(date, views, comments)) assertEquals(chip.bottom.value, item.bottom.value, 1f)
            assertTrue((card.bottom - chip.bottom) <= 16.dp)
            val artwork = compose.onNodeWithTag("release-artwork-1", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue(date.top - artwork.bottom in 0.dp..26.dp)
            val uploader = compose.onNode(hasText("Reader") and hasAnyAncestor(hasTestTag("release-1")),
                useUnmergedTree = true).getUnclippedBoundsInRoot()
            val title = compose.onNode(hasText("Aloha Browser", substring = true) and hasAnyAncestor(hasTestTag("release-1")),
                useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals(if (isCompact) 88f else 128f, (artwork.right - artwork.left).value, 1f)
            assertTrue(uploader.left >= artwork.right && uploader.top >= title.bottom)
            assertTrue(uploader.top < artwork.bottom)
            val inset = if (isCompact) 12.dp else 16.dp
            assertEquals((card.right - inset).value, chip.right.value, 1f)
            val other = compose.onNodeWithTag("release-2").getUnclippedBoundsInRoot()
            assertEquals((card.bottom - card.top).value, (other.bottom - other.top).value, 1f)
            capture(if (isCompact) "compact-category-footer" else "detailed-category-footer", compose.onNodeWithTag("card-comparison"))
        }
    }

    @Test fun activeFiltersScrollOnOneLineWithMarginsAndFixedClearButton() {
        val store = CustomCategoryStore(context())
        val travel = store.create(ContentSection.Apps, "Travel")
        var filters by mutableStateOf(FeedFilters(categoryIds = setOf(20, 30), customCategoryIds = setOf(travel.id),
            postedThrough = java.time.LocalDate.of(2026, 11, 19)))
        var edits = 0
        compose.setContent { MobilismTheme(reduceMotion = true) {
            CompositionLocalProvider(LocalForumCategoryNames provides mapOf(20L to "Photography"),
                LocalReleaseAssignments provides store.state.collectAsState().value) {
                androidx.compose.material3.Surface(Modifier.width(280.dp).testTag("filter-strip")) {
                    ActiveFilterSummary(filters, { edits++ }, { filters = FeedFilters() }, listOf(ReleaseCategory(30, "Art")))
                }
            }
        } }
        val clear = compose.onNodeWithContentDescription("Clear filters")
        val fixed = clear.assertIsDisplayed().getUnclippedBoundsInRoot()
        val first = compose.onNodeWithText("Art").assertIsDisplayed().getUnclippedBoundsInRoot()
        val photo = compose.onNodeWithText("Photography")
        assertTrue(photo.getUnclippedBoundsInRoot().left - first.right >= 8.dp)
        capture("active-filter-strip", compose.onNodeWithTag("filter-strip"))
        photo.performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Travel").performScrollTo().assertIsDisplayed()
        val last = compose.onNode(hasText("Through ", substring = true)).performScrollTo().assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals(first.top.value, last.top.value, 1f)
        assertTrue(last.right <= fixed.left)
        assertEquals(fixed, clear.getUnclippedBoundsInRoot())
        compose.runOnIdle { assertEquals(1, edits) }
        clear.performClick().assertDoesNotExist()
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

    @Test fun narrowHomeFooterFitsViewCountAndOpensCategoryIcon() {
        val music = release().copy(categoryId = 1332, views = 26)
        var opened: FeedFilters? = null
        compose.setContent { MobilismTheme(reduceMotion = true) {
            CompositionLocalProvider(LocalForumCategoryNames provides mapOf(1332L to "Music & Audio"),
                LocalOpenReleaseCategory provides { _, filters -> opened = filters }) {
                Column(Modifier.width(136.dp).testTag("footer")) {
                    ReleaseMetadataFooter(music, singleRow = true, inlineIcons = true) {
                        ReleaseViewCount(music.views, numberOnly = true)
                    }
                }
            }
        } }
        val bounds = compose.onNodeWithTag("footer").getUnclippedBoundsInRoot()
        val count = compose.onNodeWithContentDescription("26 views").assertIsDisplayed().getUnclippedBoundsInRoot()
        val button = compose.onNodeWithContentDescription("Music & Audio").assertIsDisplayed()
        val icon = button.getUnclippedBoundsInRoot()
        assertTrue(count.right <= icon.left && icon.right <= bounds.right && icon.right - icon.left <= 19.dp)
        val dateTime = compose.onNode(hasText("\n", substring = true)).getUnclippedBoundsInRoot()
        assertEquals((dateTime.top + dateTime.bottom).value / 2, (icon.top + icon.bottom).value / 2, 1f)
        assertEquals(bounds.right.value, icon.right.value, 1f)
        assertTrue(dateTime.right <= count.left && bounds.bottom - bounds.top < 48.dp)
        assertEquals((count.left - dateTime.right).value, (icon.left - count.right).value, 1f)
        compose.onNodeWithText("Music & Audio").assertDoesNotExist()
        button.performClick()
        compose.runOnIdle { assertEquals(setOf(1332L), opened?.categoryIds) }
    }

    @Test fun savedAppsShowArtworkAndPostDateInNameOrder() {
        val context = context()
        val store = CustomCategoryStore(context)
        val category = store.create(ContentSection.Apps, "Players")
        val late = release().copy(title = "Zebra v1")
        val early = late.copy(id = 2, title = "alpha v2.0 [Premium]")
        val image = File(context.cacheDir, "category-art-test.png")
        val color = android.graphics.Color.rgb(193, 49, 122)
        val bitmap = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        store.assign(late, setOf(category.id))
        store.assign(early, setOf(category.id))
        store.observe(early.copy(images = listOf(image.toURI().toString())))
        val reopened = CustomCategoryStore(context)
        compose.setContent { MobilismTheme(reduceMotion = true) {
            CustomCategoriesScreen(ContentSection.Apps, reopened, {}, {})
        } }
        compose.onNodeWithText("Players").performClick()
        val alpha = compose.onNodeWithText("alpha")
        assertTrue(alpha.getUnclippedBoundsInRoot().top < compose.onNodeWithText("Zebra").getUnclippedBoundsInRoot().top)
        val locale = context.resources.configuration.locales[0]
        val date = java.time.Instant.ofEpochSecond(early.publishedAt).atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale))
        compose.onNodeWithText("v2.0 · $date · Premium").assertIsDisplayed()
        compose.waitUntil(8000) {
            val pixels = alpha.captureToImage().asAndroidBitmap()
            (0 until pixels.width step 5).any { x -> (0 until pixels.height step 5).any { y -> pixels.getPixel(x, y) == color } }
        }
    }

    @Test fun managementCreatesRenamesRemovesMembersAndConfirmsDeletion() {
        val context = context()
        val store = CustomCategoryStore(context)
        val favorites = store.create(ContentSection.Apps, "Favorites")
        compose.setContent { MobilismTheme(reduceMotion = true) {
            CustomCategoriesScreen(ContentSection.Apps, store, {}, {})
        } }
        compose.onNodeWithContentDescription("Create category").performClick()
        val iconButton = compose.onNodeWithContentDescription("Choose icon")
        val iconBounds = iconButton.getUnclippedBoundsInRoot()
        val nameBounds = compose.onNodeWithText("Category name").getUnclippedBoundsInRoot()
        assertTrue(iconBounds.right + 12.dp <= nameBounds.left && iconBounds.top < nameBounds.bottom)
        assertEquals(nameBounds.bottom.value, iconBounds.bottom.value, 1f)
        assertEquals((iconBounds.right - iconBounds.left).value, (iconBounds.bottom - iconBounds.top).value, 1f)
        iconButton.performClick()
        compose.onNodeWithText("Search icons").performTextInput("sports")
        compose.onNodeWithContentDescription("Ball football").assertIsDisplayed()
        compose.onNodeWithText("Search icons").performTextReplacement("football")
        compose.onNodeWithContentDescription("Ball football").performClick()
        compose.onNodeWithText("Category name").performTextInput(" Sports ")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Sports").performClick()
        val sports = store.state.value.categories.first { it.name == "Sports" }
        assertEquals("ball-football", CustomCategoryStore(context).state.value.categories.single { it.id == sports.id }.icon)
        compose.runOnIdle { store.assign(release(), setOf(sports.id, favorites.id)) }
        capture("category-members")
        compose.onNodeWithContentDescription("Edit category").performClick()
        compose.onNodeWithText("Category name").performTextReplacement("Matchdays")
        compose.onNodeWithContentDescription("Choose icon").performClick()
        compose.onNodeWithText("Search icons").performTextInput("camera")
        compose.onNodeWithContentDescription("Camera").performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals("camera", CustomCategoryStore(context).state.value.categories.single { it.id == sports.id }.icon)
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
        val queries = CopyOnWriteArrayList<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            paths += path
            if (path.endsWith("/search")) {
                val buffer = okio.Buffer()
                checkNotNull(chain.request().body).writeTo(buffer)
                queries += checkNotNull(("https://fixture.invalid/?" + buffer.readUtf8()).toHttpUrl().queryParameter("search"))
            }
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
        assertFalse(paths.any { it.contains("/category/all/") })
        assertTrue(queries.isNotEmpty() && queries.all { it == "Example" })
        assertTrue(paths.any { it.contains("/category/20/") })
        val search = repository.search("Example", SearchScope.Apps, 1, filters)
        assertEquals(listOf(2L, 3L), search.items.map { it.id })
        assertTrue(search.items.all { it.section == ContentSection.Apps })
        paths.clear()
        enabled = false
        assertEquals(listOf(3L), repository.releases(ContentSection.Apps, FeedOrder.Latest, 1, filters).items.map { it.id })
        assertTrue("The native category page is already cached", paths.isEmpty())
        assertEquals(listOf(4L, 2L, 3L), repository.releases(ContentSection.Apps, FeedOrder.Latest, 1,
            filters.copy(categoryIds = emptySet())).items.map { it.id })
        assertEquals(listOf(4L, 2L, 3L), repository.search("Example", SearchScope.Apps, 1,
            filters.copy(categoryIds = emptySet())).items.map { it.id })
        assertEquals(setOf(sports.id), CustomCategoryStore(context).state.value.memberships(release()))
    }

    @Test fun categorySearchesEachAssignedAppAndFindsPastAndFutureVersions() = runBlocking {
        val context = context()
        val store = CustomCategoryStore(context)
        val category = store.create(ContentSection.Apps, "Favorites")
        store.assign(release(), setOf(category.id))
        store.assign(release().copy(id = 10, title = "Second App v2.0"), setOf(category.id))
        val queries = CopyOnWriteArrayList<Pair<String, Int>>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            check(request.url.encodedPath.endsWith("/search")) { "Custom categories must search app names: ${request.url.encodedPath}" }
            val buffer = okio.Buffer()
            checkNotNull(request.body).writeTo(buffer)
            val form = ("https://fixture.invalid/?" + buffer.readUtf8()).toHttpUrl()
            fun field(name: String) = checkNotNull(form.queryParameter(name))
            val name = field("search")
            val page = field("count").toInt()
            queries += name to page
            val rows = if (page > 1) "" else when (name) {
                "Example" -> """{"id":1,"subject":"Example v1.0","date":100},
                    {"id":2,"subject":"Example v3.0","date":300},
                    {"id":99,"subject":"Unrelated v4.0","date":900}"""
                "Second App" -> """{"id":10,"subject":"Second App v1.0","date":50},
                    {"id":11,"subject":"Second App v4.0","date":400}"""
                else -> error("Unexpected search: $name")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body("""{"releases":[$rows]}""".toResponseBody()).build()
        }.build()
        val repository = DefaultMobilismRepository(context, ContentPreferencesStore(context).also { it.hideAdult(false) },
            client = client, customCategories = store, customCategoriesEnabled = { true })
        val result = repository.releases(ContentSection.Apps, FeedOrder.Latest, 1,
            FeedFilters(customCategoryIds = setOf(category.id)))
        assertEquals(listOf(11L, 2L, 1L, 10L), result.items.map { it.id })
        assertFalse(result.canLoadMore)
        assertEquals(setOf("Example" to 1, "Example" to 2, "Second App" to 1, "Second App" to 2), queries.toSet())
    }
}
