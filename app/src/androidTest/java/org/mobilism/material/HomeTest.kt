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
        compose.setContent { MobilismTheme(reduceMotion = true) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (short) 1.5f else 1f)) {
                Box(Modifier.fillMaxWidth().height(if (short) 320.dp else 800.dp).testTag("drawer-frame")) {
                    FeedScreen(FeedState(), AuthState.SignedIn("Reader"), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, CategoryState(), {}, {})
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
        capture("drawer-full-height")
        compose.runOnIdle { short = true }
        assertFullHeight()
        compose.onNodeWithText("Logout").performScrollTo().assertIsDisplayed()
        capture("drawer-short-large-text")
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
                            { categories = true }, { item, _ -> topic = item }, { forum = it })
                    }
                }
            }
        }
        ContentSection.entries.forEach { current ->
            compose.runOnIdle { section = current }
            val home = compose.onNodeWithTag("home-${current.name}")
            capture("${current.name.lowercase()}-hero")
            compose.onNodeWithContentDescription("Show featured 2 of 5").performClick()
            compose.onNodeWithContentDescription("Show featured 1 of 5").performClick()
            compose.onNodeWithTag("home-hero").onChildren().filter(hasClickAction()).onFirst().performClick()
            compose.runOnIdle { assertEquals(states.getValue(current).popular.items.first().id, opened) }
            compose.onNodeWithContentDescription("More Latest ${current.label.lowercase()}").performScrollTo().performClick()
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
            compose.onNodeWithText("A community review").performClick()
            compose.runOnIdle { assertEquals(80L, topic?.id) }
            home.performScrollToNode(hasContentDescription("More Recent discussions"))
            compose.onNodeWithContentDescription("More Recent discussions").performClick()
            compose.runOnIdle { assertEquals(12L, forum?.id) }
            capture("${current.name.lowercase()}-community")
        }
    }

    @Test fun homeTabReturnsToItsScrollPositionAndFeedsRemainAccessible() {
        val state = fixtureState(ContentSection.Apps)
        var tab by mutableStateOf(BrowseTab.Home)
        var feedOrder by mutableStateOf(FeedOrder.Latest)
        var refreshed = false
        compose.setContent { MobilismTheme(reduceMotion = true) {
            FeedScreen(FeedState(items = state.latest.items, order = feedOrder), AuthState.Guest, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                CategoryState(), {}, {}, tab = tab, onTab = { tab = it; if (it != BrowseTab.Home) feedOrder = if (it == BrowseTab.Top) FeedOrder.Top else FeedOrder.Latest },
                onHomeRefresh = { refreshed = true }, homeContent = { HomeScreen(state, {}, {}, {}, {}, it, { _, _ -> }, {}) })
        } }
        compose.onNodeWithContentDescription("Refresh").performClick()
        compose.runOnIdle { assertTrue(refreshed) }
        compose.onNodeWithTag("home-Apps").performScrollToNode(hasText("Browse by category"))
        compose.onNodeWithText("Latest", substring = false).performClick()
        compose.onNodeWithText("Latest", substring = false).assertIsSelected()
        compose.onNodeWithText(state.latest.items.first().title.substringBefore(" v")).assertExists()
        compose.onNodeWithText("Top", substring = false).performClick()
        compose.onNodeWithText("Top", substring = false).assertIsSelected()
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
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("home-test-$name", mode)
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
            repository.discussionTopics(review.category, 1)
            assertTrue(requests.last().url.encodedPath.endsWith("/desc/1"))
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
        return HomeState(section, HomeShelf(releases, loaded = true), HomeShelf(releases, loaded = true),
            HomeShelf(listOf(ReleaseCategory(21, "Creative tools"), ReleaseCategory(22, "Everyday essentials"), ReleaseCategory(23, "Explore more")), loaded = true),
            HomeForum(HomeTopics(category, listOf(review)), loaded = true),
            HomeForum(HomeTopics(category.copy(id = 12, name = "Recent discussions"), listOf(review.copy(id = 81, title = "What are you enjoying lately?"))), loaded = true))
    }

    @Composable
    private fun HomeHost(state: HomeState, content: @Composable () -> Unit) {
        FeedScreen(FeedState(section = state.section, items = state.latest.items), AuthState.Guest,
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, CategoryState(), {}, {}, tab = BrowseTab.Home,
            homeContent = { content() })
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
