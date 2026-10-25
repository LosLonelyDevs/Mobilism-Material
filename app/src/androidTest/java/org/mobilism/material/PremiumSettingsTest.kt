package org.mobilism.material

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
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
