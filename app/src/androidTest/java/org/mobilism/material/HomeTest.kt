package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.*
import org.mobilism.material.ui.*

@RunWith(AndroidJUnit4::class)
class HomeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun drawerFillsViewportAndScrollsOnShortScreens() {
        var short by mutableStateOf(false)
        var guest by mutableStateOf(false)
        compose.setContent { MobilismTheme(reduceMotion = true) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (short) 1.5f else 1f)) {
                Box(Modifier.fillMaxWidth().height(if (short) 320.dp else 800.dp).testTag("drawer-frame")) {
                    FeedScreen(FeedState(), if (guest) AuthState.Guest else AuthState.SignedIn("Reader"),
                        {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, CategoryState(), {}, {})
                }
            }
        } }
        compose.onNodeWithContentDescription("Open navigation drawer").performClick()
        fun assertFullHeight() {
            val frame = compose.onNodeWithTag("drawer-frame").fetchSemanticsNode().boundsInRoot
            val drawer = compose.onNodeWithTag("navigation-drawer").fetchSemanticsNode().boundsInRoot
            assertEquals(frame.top, drawer.top, 1f)
            assertEquals(frame.bottom, drawer.bottom, 1f)
        }
        assertFullHeight()
        val settings = compose.onNodeWithText(compose.activity.getString(R.string.settings_title))
        val logout = compose.onNodeWithText(compose.activity.getString(R.string.drawer_logout))
        val scrollable = hasAnyAncestor(hasScrollAction())
        settings.assertIsDisplayed().assert(!scrollable)
        logout.assertIsDisplayed().assert(!scrollable)
        capture("drawer-full-height")
        compose.runOnIdle { short = true }
        assertFullHeight()
        val settingsBounds = settings.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val logoutBounds = logout.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(settingsBounds.bottom <= logoutBounds.top + 1f)
        compose.onNodeWithText(compose.activity.getString(R.string.drawer_messages)).performScrollTo().assertIsDisplayed()
        assertEquals(settingsBounds, settings.fetchSemanticsNode().boundsInRoot)
        assertEquals(logoutBounds, logout.fetchSemanticsNode().boundsInRoot)
        capture("drawer-short-large-text")
        compose.runOnIdle { guest = true }
        logout.assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.drawer_login)).assert(scrollable).performScrollTo().assertIsDisplayed()
        settings.assertIsDisplayed().assert(!scrollable)
    }

    @Test fun allThreeSectionsExposeHeroCollectionsAndWorkingShortcuts() {
        var section by mutableStateOf(ContentSection.Apps)
        var opened = 0L
        var order: FeedOrder? = null
        var category = 0L
        var forum: DiscussionCategory? = null
        var topic: DiscussionTopic? = null
        var categories = false
        val states = ContentSection.entries.associateWith(::fixtureState)
        compose.setContent {
            MobilismTheme(theme = when (section) { ContentSection.Apps -> CatppuccinFrappe; ContentSection.Books -> CatppuccinLatte; ContentSection.Games -> AmoledBlack }) {
                key(section) {
                    HomeHost(states.getValue(section)) {
                        HomeScreen(states.getValue(section), {}, { opened = it }, { order = it }, { category = it.id },
                            { categories = true }, { item, _ -> topic = item }, { forum = it },
                            library = listOf(LibraryEntry(states.getValue(section).popular.items.first(), watched = true)))
                    }
                }
            }
        }
        ContentSection.entries.forEach { current ->
            compose.runOnIdle { section = current }
            val home = compose.onNodeWithTag("home-${current.name}")
            if (current == ContentSection.Apps) {
                compose.onNodeWithText("Discover apps").assertIsDisplayed()
                compose.onNodeWithText("Popular apps in Creative tools").assertIsDisplayed()
            }
            capture("${current.name.lowercase()}-hero")
            compose.onNodeWithContentDescription("Show featured 2 of 5").performClick()
            compose.onNodeWithContentDescription("Show featured 1 of 5").performClick()
            val firstCard = compose.onNodeWithTag("home-hero").onChildren().filter(hasClickAction()).onFirst()
            val fixture = states.getValue(current)
            val displayed = (fixture.discovery.items + fixture.popular.items + fixture.latest.items).distinctBy { it.id }
                .single { hasText(ReleaseTitles.parse(it.title, current).name).matches(firstCard.fetchSemanticsNode()) }
            firstCard.performClick()
            compose.runOnIdle { assertEquals(displayed.id, opened) }
            home.performScrollToNode(hasContentDescription("More Latest ${current.label.lowercase()}"))
            compose.onNodeWithContentDescription("More Latest ${current.label.lowercase()}").performClick()
            compose.runOnIdle { assertEquals(FeedOrder.Latest, order) }
            home.performScrollToNode(hasContentDescription("More Popular in ${current.label}"))
            compose.onNodeWithContentDescription("More Popular in ${current.label}").performClick()
            compose.runOnIdle { assertEquals(FeedOrder.Top, order) }
            home.performScrollToIndex(2)
            capture("${current.name.lowercase()}-collections")
            home.performScrollToNode(hasContentDescription("More Browse by category"))
            compose.onNodeWithContentDescription("More Browse by category").performClick()
            compose.runOnIdle { assertTrue(categories) }
            compose.onNodeWithText("Creative tools").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(21L, category) }
            home.performScrollToNode(hasContentDescription("More Reviews"))
            compose.onNodeWithContentDescription("More Reviews").performClick()
            compose.runOnIdle { assertEquals(current.name, forum?.area?.name) }
            home.performScrollToNode(hasText("A community review"))
            // The merged card also contains a separate username action. Tap the title itself.
            compose.onNodeWithText("A community review", useUnmergedTree = true).performClick()
            compose.runOnIdle { assertEquals(80L, topic?.id) }
            home.performScrollToNode(hasContentDescription("More Recent discussions"))
            compose.onNodeWithContentDescription("More Recent discussions").performClick()
            compose.runOnIdle { assertEquals(12L, forum?.id) }
            capture("${current.name.lowercase()}-community")
        }
    }

    @Test fun pairedDayShowsBothCategoriesInOneRowBelowDiscover() {
        val base = fixtureState(ContentSection.Apps)
        val split = base.popular.items.mapIndexed { index, release -> release.copy(categoryId = if (index % 2 == 0) 21L else 22L) } +
            base.popular.items.last().copy(id = 50, title = "Creative option v1", categoryId = 22L)
        val state = base.copy(popular = HomeShelf(split, loaded = true), day = -3)
        val followed = split.take(2).map { LibraryEntry(it, watched = true) }
        compose.setContent {
            MobilismTheme(theme = CatppuccinFrappe) {
                HomeHost(state) { HomeScreen(state, {}, {}, {}, {}, {}, { _, _ -> }, {}, library = followed) }
            }
        }
        val heading = compose.onNode(hasText("Popular apps in Everyday essentials and Creative tools") or
            hasText("Popular apps in Creative tools and Everyday essentials"))
        heading.assertIsDisplayed()
        assertTrue(heading.fetchSemanticsNode().boundsInRoot.top >
            compose.onNodeWithText("Discover apps").fetchSemanticsNode().boundsInRoot.bottom)
        capture("apps-paired-categories")
    }

    @OptIn(coil3.annotation.DelicateCoilApi::class)
    @Test fun discoverDrawsThumbnailPixelsAndExpandsAVerifiedMusicRowSavedWithOneApp() {
        val base = fixtureState(ContentSection.Apps)
        val keyPrefix = java.util.UUID.randomUUID().toString()
        fun imageUrl(id: Long) = "https://images.mobilism.org/?dm=$keyPrefix-$id"
        val hero = base.latest.items.first().copy(id = 500, title = "Daily Notes v1", thumbnail = imageUrl(500), images = emptyList())
        val music = List(7) { index ->
            val id = 600L + index
            hero.copy(id = id, title = "Player ${index + 1} v2", thumbnail = imageUrl(id), categoryId = 1332, categoryName = "Music & Audio")
        }
        val heroBytes = File(java.net.URI(base.latest.items[0].thumbnail)).readBytes()
        val musicBytes = File(java.net.URI(base.latest.items[1].thumbnail)).readBytes()
        val calls = CopyOnWriteArrayList<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url.toString()
            calls += url
            val bytes = if (url == hero.thumbnail) heroBytes else musicBytes
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", "image/png").body(bytes.toResponseBody()).build()
        }.build()
        val loader = org.mobilism.material.images.appImageLoader(compose.activity, client)
        val previous = coil3.SingletonImageLoader.get(compose.activity)
        coil3.SingletonImageLoader.setUnsafe(loader)
        val state = base.copy(latest = HomeShelf(loaded = true), popular = HomeShelf(loaded = true),
            discovery = HomeShelf(listOf(hero) + music, loaded = true))
        val account = java.util.UUID.randomUUID().toString()
        val prefs = compose.activity.getSharedPreferences("daily-home", Context.MODE_PRIVATE)
        val key = "false:$account"
        DailyHomeStore(prefs, key).save(state.day, DailySelection(listOf(hero.discoveryKey()), listOf(1332), listOf(music.first().discoveryKey())))
        try {
            compose.setContent { MobilismTheme(reduceMotion = true) {
                HomeHost(state) { HomeScreen(state, {}, {}, {}, {}, {}, { _, _ -> }, {}, account = account) }
            } }
            fun assertPixels(tag: String, color: Int) {
                compose.waitUntil(8_000) {
                    if (compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) false
                    else {
                        val bmp = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap()
                        (3 until bmp.width step 7).any { x -> (3 until bmp.height step 7).any { y -> bmp.getPixel(x, y) == color } }
                    }
                }
            }
            assertPixels("home-hero-artwork-500", 0xff285e55.toInt())
            assertPixels("home-category-artwork-600", 0xff3b467d.toInt())
            compose.onNodeWithText("Popular apps in Music & Audio").assertIsDisplayed()
            val selected = requireNotNull(DailyHomeStore(prefs, key).read(state.day))
            assertEquals(5, selected.featured.distinct().size)
            val row = compose.onNodeWithTag("home-daily-category")
            val last = music.single { it.discoveryKey() == selected.featured.last() }
            assertFalse(last.thumbnail in calls)
            row.performScrollToIndex(4)
            compose.onNodeWithText(last.appTitle.name).assertIsDisplayed()
            assertPixels("home-category-artwork-${last.id}", 0xff3b467d.toInt())
            row.performScrollToIndex(0)
            capture("apps-music-artwork")
        } finally {
            coil3.SingletonImageLoader.setUnsafe(previous)
            loader.shutdown()
            prefs.edit().remove(key).apply()
        }
    }

    @Test fun dailySelectionsSurviveReopeningButExpireAtTheNextDayAndStayAccountScoped() {
        val prefs = compose.activity.getSharedPreferences("home-selection-fixture", Context.MODE_PRIVATE)
        val key = java.util.UUID.randomUUID().toString()
        val choices = DailySelection(listOf("mypgallery", "sketch"), listOf(7, 8), listOf("camera", "editor"))
        try {
            DailyHomeStore(prefs, key).save(10, choices)
            assertEquals(choices, DailyHomeStore(prefs, key).read(10))
            assertNull(DailyHomeStore(prefs, key).read(11))
            assertNull(DailyHomeStore(prefs, key + "-other").read(10))
        } finally { prefs.edit().remove(key).apply() }
    }

    @Test fun eachHomeShowsOneCenteredInitialIndicatorUntilContentArrives() {
        fun loading(section: ContentSection) = HomeState(section, latest = HomeShelf(busy = true), popular = HomeShelf(busy = true),
            categories = HomeShelf(busy = true), reviews = HomeForum(busy = true), discussions = HomeForum(busy = true))
        var home by mutableStateOf(loading(ContentSection.Apps))
        compose.setContent { MobilismTheme {
            HomeHost(home) { HomeScreen(home, {}, {}, {}, {}, {}, { _, _ -> }, {}) }
        } }
        val label = compose.activity.getString(R.string.home_loading)
        for (section in ContentSection.entries) {
            compose.runOnIdle { home = loading(section) }
            val frame = compose.onNodeWithTag("home-${section.name}").fetchSemanticsNode().boundsInRoot
            val indicator = compose.onNodeWithTag("home-initial-loading").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals(frame.center.x, indicator.center.x, 1f)
            assertEquals(frame.center.y, indicator.center.y, 1f)
            compose.onAllNodesWithText(label).assertCountEquals(1)
            compose.onNodeWithTag("feed-refresh-indicator").assertDoesNotExist()
            compose.runOnIdle { home = home.copy(categories = HomeShelf(listOf(ReleaseCategory(1, "Utilities")), loaded = true)) }
            compose.onNodeWithTag("home-initial-loading").assertDoesNotExist()
            compose.onNodeWithText("Utilities").assertIsDisplayed()
            compose.onAllNodesWithText(label).assertCountEquals(0)
            compose.onNodeWithTag("feed-refresh-indicator").assertDoesNotExist()
        }
    }

    @Test fun discoverStaysAtTheTopFromTheFirstFrameThroughDelayedRecommendations() {
        val loaded = fixtureState(ContentSection.Apps)
        var home by mutableStateOf(HomeState(ContentSection.Apps))
        var installed by mutableStateOf<Map<String, String>?>(null)
        compose.setContent { MobilismTheme(reduceMotion = true) {
            HomeHost(home) { HomeScreen(home, {}, {}, {}, {}, {}, { _, _ -> }, {}, installed = installed) }
        } }
        val heading = compose.onNodeWithText("Discover apps")
        val initialY = heading.assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("home-hero-loading").assertIsDisplayed()
        compose.runOnIdle { home = loaded }
        compose.onNodeWithTag("home-hero-loading").assertIsDisplayed()
        compose.runOnIdle { installed = emptyMap() }
        compose.onNodeWithTag("home-hero").assertIsDisplayed()
        assertEquals(initialY, heading.fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithTag("home-hero-loading").assertDoesNotExist()
        compose.runOnIdle { home = loaded.copy(discovery = HomeShelf(loaded = true), latest = HomeShelf(loaded = true),
            popular = HomeShelf(loaded = true)) }
        compose.onNodeWithTag("home-hero-loading").assertDoesNotExist()
        compose.onNodeWithText("Browse by category").assertIsDisplayed()
    }

    @Test fun homeTabReturnsToItsScrollPositionAndFeedsRemainAccessible() {
        val state = fixtureState(ContentSection.Apps)
        var tab by mutableStateOf(BrowseTab.Home)
        var feedOrder by mutableStateOf(FeedOrder.Latest)
        var homeRefreshes = 0
        val feedRefreshes = mutableListOf<FeedOrder>()
        var refreshing by mutableStateOf(false)
        compose.setContent { MobilismTheme(reduceMotion = true) {
            FeedScreen(FeedState(items = state.latest.items, order = feedOrder, busy = refreshing, refreshing = refreshing),
                AuthState.Guest, onSection = {}, onOrder = {}, onRefresh = { feedRefreshes += feedOrder; refreshing = true },
                onMore = {}, onRelease = {}, onSettings = {}, onSearch = {}, onLibrary = {}, onFollowing = {}, onSignOut = {},
                categories = CategoryState(), onLoadCategories = {}, onFilters = {}, tab = tab,
                onTab = { tab = it; if (it != BrowseTab.Home) feedOrder = if (it == BrowseTab.Top) FeedOrder.Top else FeedOrder.Latest },
                onHomeRefresh = { homeRefreshes++; refreshing = true }, homeRefreshing = refreshing,
                homeContent = { HomeScreen(state, {}, {}, {}, {}, it, { _, _ -> }, {}) })
        } }
        compose.onNodeWithContentDescription("Refresh").assertDoesNotExist()
        compose.onNodeWithTag("home-Apps").performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, homeRefreshes) }
        compose.onNodeWithTag("feed-refresh-indicator").assertIsDisplayed()
        compose.onNodeWithTag("home-Apps").performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, homeRefreshes); refreshing = false }
        compose.onNodeWithTag("home-Apps").performScrollToNode(hasText("Browse by category"))
        compose.onNodeWithText("Latest", substring = false).performClick()
        compose.onNodeWithText("Latest", substring = false).assertIsSelected()
        compose.onNodeWithText(state.latest.items.first().title.substringBefore(" v")).assertExists()
        compose.onNodeWithTag("feed-list").performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(listOf(FeedOrder.Latest), feedRefreshes) }
        compose.onNodeWithTag("feed-refresh-indicator").assertIsDisplayed()
        compose.runOnIdle { refreshing = false }
        compose.onNodeWithText("Top", substring = false).performClick()
        compose.onNodeWithText("Top", substring = false).assertIsSelected()
        compose.onNodeWithTag("feed-list").performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(listOf(FeedOrder.Latest, FeedOrder.Top), feedRefreshes); refreshing = false }
        compose.onNodeWithText("Home", substring = false).performClick()
        compose.onNodeWithText("Browse by category").assertIsDisplayed()
        capture("apps-tabs")
    }

    @Test fun largeTextReducedMotionKeyboardAndRetryRemainUsable() {
        val state = fixtureState(ContentSection.Books).copy(popular = HomeShelf(error = ScreenError("Offline. Try again.")))
        var retry: HomeCollection? = null
        lateinit var input: InputModeManager
        compose.setContent { MobilismTheme(theme = AmoledBlack, reduceMotion = true) {
            input = LocalInputModeManager.current
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                HomeHost(state) { HomeScreen(state, { retry = it }, {}, {}, {}, {}, { _, _ -> }, {}) }
            }
        } }
        compose.onNodeWithText("Your next read").assertIsDisplayed()
        compose.runOnIdle { input.requestInputMode(InputMode.Keyboard) }
        compose.onRoot().performKeyInput { pressKey(Key.Tab) }
        compose.onAllNodes(isFocused()).onFirst().assertExists()
        capture("books-large-text-keyboard")
        compose.onNodeWithTag("home-Books").performScrollToNode(hasText("Offline. Try again."))
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(HomeCollection.Popular, retry) }
    }

    @Test fun homeUsesCorrectSectionEndpointsWithoutPinnedTopicsAndHonorsAdultFiltering() = runBlocking {
        val requests = CopyOnWriteArrayList<Request>()
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext(): Context = this
            private val opened = mutableSetOf<String>()
            @Synchronized override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("home-test-$name", mode).also { prefs ->
                    if (opened.add(name)) prefs.edit().clear().commit()
                }
        }
        val preferences = ContentPreferencesStore(context)
        preferences.hideAdult(true)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val path = request.url.encodedPath
            val payload = when {
                path.endsWith("release/category") -> {
                    val items = if (request.url.queryParameter("no_adult") == "1") """[{"id":21,"name":"Creative tools"}]"""
                        else """[{"id":21,"name":"Creative tools"},{"id":22,"name":"Excluded"}]"""
                    """{"app":$items,"games":$items,"books":$items}"""
                }
                path.endsWith("discussion/categories") -> """{"response":{"app|game":[{"id":70,"name":"Android Reviews"}],"books":[{"id":71,"name":"Book Reviews"}]}}"""
                path.contains("/release/") -> """{"response":[{"id":1,"subject":"Visible","forum_id":21},{"id":2,"subject":"Hidden","forum_id":22}]}"""
                path.contains("/discussion/category/") -> """{"response":[{"id":80,"subject":"A review","forum_id":70,"content":"Text"}]}"""
                else -> error("Unexpected Home fixture path: $path")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body(payload.toResponseBody()).build()
        }.build()
        val repository = DefaultMobilismRepository(context, preferences, client = client)
        repository.continueAsGuest()
        ContentSection.entries.forEach { section ->
            FeedOrder.entries.forEach { order ->
                assertEquals(listOf(1L), repository.homeReleases(section, order).map { it.id })
                assertTrue(requests.any { it.url.encodedPath.contains("release/${section.apiPath}/category/all/page/1/") &&
                    it.url.encodedPath.endsWith("/orderby/${order.apiValue}/desc/0") && it.url.queryParameter("no_adult") == "1" })
            }
            assertEquals(listOf(21L), repository.homeCategories(section).map { it.id })
            val review = repository.homeReviews(section)!!
            assertEquals(if (section == ContentSection.Books) 71L else 70L, review.category.id)
            assertEquals(section.name, review.category.area.name)
            assertEquals(80L, review.items.single().id)
            repository.homeDiscussions(section)
            assertTrue(requests.last().url.encodedPath.contains("discussion/category/${section.name.lowercase()}/"))
            assertTrue(requests.last().url.encodedPath.endsWith("/desc/0"))
            assertEquals(80L, repository.discussionTopics(review.category, 1).items.single().id)
            // Apps and Games share the same review forum, including its cached first page.
            assertEquals(1, requests.count { it.url.encodedPath.contains("discussion/category/${review.category.id}/page/1/") &&
                it.url.encodedPath.endsWith("/desc/1") })
        }
        assertTrue(requests.all { it.method == "GET" })
    }

    private fun fixtureState(section: ContentSection): HomeState {
        val names = when (section) {
            ContentSection.Apps -> listOf("Focus Notes", "Moonlight Reader", "Pocket Studio", "Open Weather", "Daily Planner")
            ContentSection.Books -> listOf("The Quiet Forest", "A Map of Stars", "The Last Library", "Beyond the Blue", "Small Wonders")
            ContentSection.Games -> listOf("Orbit Explorer", "Hidden Valleys", "Pixel Quest", "Skybound", "Tiny Worlds")
        }
        val releases = names.mapIndexed { index, name ->
            val image = artwork(section, index, name)
            Release(index.toLong() + 1, if (section == ContentSection.Books) "$name by Alex Rivers" else "$name v2.1", "Community member",
                image, 1793000000, 12, 2400 + index * 101L, images = listOf(image), section = section)
        }
        val category = DiscussionCategory(70, "Reviews", DiscussionArea.valueOf(section.name))
        val review = DiscussionTopic(80, "A community review", "Avid reader", "Review", DiscussionKind.Discussion, 70, replies = 12)
        val latest = if (section == ContentSection.Apps) releases.mapIndexed { i, r ->
            r.copy(id = 100L + i, title = listOf("Color Notebook", "Sun Map", "Pixel Desk", "Daybreak", "Pocket Paint")[i] + " v2.1")
        } else releases
        val discovery = if (section == ContentSection.Apps) listOf("Frame Studio", "Pocket Canvas", "Hue Notes", "Tiny Sketch", "Light Meter")
            .mapIndexed { i, name -> releases[i].copy(id = 201L + i, title = "$name v2.1", thumbnail = null, images = emptyList(),
                categoryId = 21, categoryName = "Creative tools", views = 0) } else emptyList()
        return HomeState(section, HomeShelf(latest, loaded = true), HomeShelf(releases.map { it.copy(categoryId = 21) }, loaded = true),
            HomeShelf(listOf(ReleaseCategory(21, "Creative tools"), ReleaseCategory(22, "Everyday essentials"), ReleaseCategory(23, "Explore more")), loaded = true),
            HomeForum(HomeTopics(category, listOf(review)), loaded = true),
            HomeForum(HomeTopics(category.copy(id = 12, name = "Recent discussions"), listOf(review.copy(id = 81, title = "What are you enjoying lately?"))), loaded = true),
            discovery = HomeShelf(discovery, loaded = true))
    }

    @Composable
    private fun HomeHost(state: HomeState, content: @Composable () -> Unit) {
        FeedScreen(FeedState(section = state.section, items = state.latest.items), AuthState.Guest,
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, CategoryState(), {}, {}, tab = BrowseTab.Home,
            homeRefreshing = state.refreshing, homeBusy = state.busy, homeContent = { content() })
    }

    // Local artwork makes screenshots deterministic; these fixtures never enter the app catalog.
    private fun artwork(section: ContentSection, index: Int, title: String): String {
        val bitmap = Bitmap.createBitmap(360, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val colors = intArrayOf(0xff285e55.toInt(), 0xff3b467d.toInt(), 0xffa94e38.toInt(), 0xff507a96.toInt(), 0xff795386.toInt())
        canvas.drawColor(colors[index])
        paint.color = 0x44ffffff
        canvas.drawCircle(280f, 120f, 170f, paint)
        canvas.drawCircle(50f, 490f, 200f, paint)
        paint.color = Color.WHITE
        paint.textSize = 28f
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        if (section == ContentSection.Apps) {
            paint.textSize = 25f
            canvas.drawText(title, 28f, 85f, paint)
            repeat(4) { row ->
                paint.color = 0xbbffffff.toInt()
                canvas.drawRoundRect(28f, 140f + row * 85, 332f, 205f + row * 85, 18f, 18f, paint)
                paint.color = colors[index]
                paint.textSize = 19f
                canvas.drawText(listOf("Your day, at a glance", "Make room for ideas", "Keep it simple", "A little inspiration")[row], 45f, 180f + row * 85, paint)
            }
        } else {
            paint.textSize = if (section == ContentSection.Books) 37f else 34f
            title.split(" ").forEachIndexed { line, word -> canvas.drawText(word, 32f, 270f + line * 48, paint) }
            paint.textSize = 20f
            canvas.drawText(if (section == ContentSection.Books) "ALEX RIVERS" else "EXPLORE YOUR WORLD", 32f, 540f, paint)
        }
        val file = File(compose.activity.cacheDir, "home-${section.name}-$index.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file.toURI().toString()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(compose.activity.getExternalFilesDir(null), "home-screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
