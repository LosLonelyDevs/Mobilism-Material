package org.mobilism.material

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
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
import org.mobilism.material.designsystem.SolarizedDark
import org.mobilism.material.settings.DeveloperPreferences
import org.mobilism.material.ui.*

@RunWith(AndroidJUnit4::class)
class PremiumSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun mobilismSignInRendersVerifiesPersistsEncryptedAndRemoves() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = if (request.url.host == "forum.mobilism.org") {
                """<supported-filehosts><host url="send.now"/></supported-filehosts>"""
            } else {
                assertEquals("mblservices.org", request.url.host)
                val form = request.body as FormBody
                assertEquals("sample-user", (0 until form.size).first { form.name(it) == "username" }.let(form::value))
                assertEquals("sample-password", (0 until form.size).first { form.name(it) == "password" }.let(form::value))
                """<form name="transload"><input name="link"/><input name="premium_acc"/></form>"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build()
        val repository = DebridRepository(context, client)
        repository.removeKey(DebridProvider.Mobilism)
        try {
            compose.setContent { MobilismTheme(reduceMotion = true) {
                val state by repository.state.collectAsState()
                SettingsScreen(state, repository, onAppearance = {}, onDownloads = {}, onBack = {})
            } }
            scrollTo("Mobilism username")
            compose.onNodeWithText("Mobilism username").performTextInput("sample-user")
            compose.onNodeWithText("Mobilism Premium password").performScrollTo().performTextInput("sample-password")
            compose.onNodeWithText("Sign in to Mobilism Premium").performScrollTo().performClick()
            compose.waitUntil(15_000) { repository.state.value.supporting("https://send.now/file").isNotEmpty() }
            scrollTo("1 Mobilism host")
            compose.onNodeWithContentDescription("Account verified").assertExists()
            val saved = context.getSharedPreferences("material-debrid-keys", 0).getString("Mobilism", "").orEmpty()
            assertTrue(saved.isNotBlank())
            assertFalse(saved.contains("sample-user"))
            assertFalse(saved.contains("sample-password"))
            val restored = DebridRepository(context, client)
            restored.load()
            assertEquals(listOf(DebridProvider.Mobilism), restored.state.value.supporting("https://send.now/file").map { it.provider })
            compose.onNodeWithContentDescription("Remove account").performClick()
            compose.waitUntil(10_000) { repository.state.value.accounts.none { it.provider == DebridProvider.Mobilism && it.configured } }
            assertFalse(context.getSharedPreferences("material-debrid-keys", 0).contains("Mobilism"))
        } finally { repository.removeKey(DebridProvider.Mobilism) }
    }

    @Test fun bookHeaderShowsTitleAuthorSeriesAndFormatSeparately() {
        compose.setContent { MobilismTheme(reduceMotion = true) {
            ReleaseTitleHeader("Nightfall (Night Watch #2) by A. Writer (.ePUB)", ContentSection.Books)
        } }
        compose.onNodeWithText("Nightfall").assertIsDisplayed()
        compose.onNodeWithText("by A. Writer").assertIsDisplayed()
        compose.onNodeWithText("Night Watch #2").assertIsDisplayed()
        compose.onNodeWithText("ePUB").assertIsDisplayed()
    }

    @Test fun verifiedMegaAccountOnlyOffersSignInAgainWhenValidationFails() {
        var state by mutableStateOf(DebridState(listOf(DebridAccount(DebridProvider.MegaDebrid,
            configured = true, validated = true, hostsVerified = true,
            hosts = listOf(SupportedHost("send.now", "Send.now"))))))
        compose.setContent { MobilismTheme(theme = SolarizedDark, reduceMotion = true) {
            SettingsScreen(state, DebridRepository(compose.activity), {}, {}, {},
                developer = DeveloperPreferences(debridEnabled = true))
        } }
        scrollTo("1 Mobilism host")
        compose.onNodeWithContentDescription("Account verified").assertIsDisplayed()
        compose.onNodeWithText("Sign in again").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(accounts = state.accounts.map { it.copy(validated = false) }) }
        scrollTo("Sign in again")
        compose.onNodeWithText("Sign in again").assertIsDisplayed().performClick()
        scrollTo("MegaDebrid username")
        compose.onNodeWithText("MegaDebrid username").assertIsDisplayed()
    }

    @Test fun hostMenuUsesRowCenterAndFitsAllProviderNamesOnOneLine() {
        val rapidgator = SupportedHost("rapidgator.net", "Rapidgator")
        val state = DebridState(DebridProvider.entries.map { provider ->
            DebridAccount(provider, configured = true, validated = true, hostsVerified = true,
                hosts = if (provider == DebridProvider.RealDebrid) listOf(rapidgator, SupportedHost("send.now", "Send.now"))
                    else listOf(rapidgator))
        })
        compose.setContent { MobilismTheme(theme = SolarizedDark, reduceMotion = true) {
            SettingsScreen(state, DebridRepository(compose.activity), {}, {}, {},
                developer = DeveloperPreferences(debridEnabled = true))
        } }
        scrollTo("2 Mobilism hosts")
        val label = compose.onNodeWithText("2 Mobilism hosts")
        val bounds = label.fetchSemanticsNode().boundsInRoot
        val left = compose.onAllNodesWithContentDescription("Verify account and refresh hosts").onLast().fetchSemanticsNode().boundsInRoot
        val right = compose.onAllNodesWithContentDescription("Remove account").onLast().fetchSemanticsNode().boundsInRoot
        assertEquals((left.right + right.left) / 2, bounds.center.x, 1f)
        label.performClick()
        val providers = compose.onNodeWithText("Real-Debrid · MegaDebrid · Mobilism Premium", useUnmergedTree = true)
        providers.assertIsDisplayed()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        providers.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        val bitmap = compose.onNode(isPopup()).captureToImage().asAndroidBitmap()
        val directory = java.io.File(compose.activity.getExternalFilesDir(null), "download-polish")
        check(directory.mkdirs() || directory.isDirectory)
        java.io.File(directory, "host-menu.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        val layout = layouts.single()
        assertFalse("Provider names should fit without truncation", layout.isLineEllipsized(0))
        assertTrue("Provider names should fit within the menu", layout.multiParagraph.maxIntrinsicWidth <= layout.size.width)
    }

    @Test fun categoriesRenderAlphabeticallyAndKeepTheirIds() {
        var selected: Set<Long>? = null
        compose.setContent { MobilismTheme(reduceMotion = true) {
            CategoryFilterSheet(CategoryState(ContentSection.Books,
                listOf(ReleaseCategory(30, "Zulu"), ReleaseCategory(10, "alpha"), ReleaseCategory(20, "Beta"))),
                FeedFilters(), onRetry = {}, onApply = { selected = it.categoryIds }, onDismiss = {})
        } }
        compose.onNodeWithText("Zulu").performScrollTo()
        val alpha = compose.onNodeWithText("alpha").fetchSemanticsNode().boundsInRoot.top
        val beta = compose.onNodeWithText("Beta").fetchSemanticsNode().boundsInRoot.top
        val zulu = compose.onNodeWithText("Zulu").fetchSemanticsNode().boundsInRoot.top
        assertTrue(alpha < beta && beta < zulu)
        compose.onNodeWithText("Beta").performClick()
        compose.onNodeWithText("Apply filters").performClick()
        compose.waitUntil(5_000) { selected != null }
        assertEquals(setOf(20L), selected)
    }

    private fun scrollTo(text: String) = compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
}
