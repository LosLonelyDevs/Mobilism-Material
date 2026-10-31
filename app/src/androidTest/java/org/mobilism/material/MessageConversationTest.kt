package org.mobilism.material

import android.graphics.Bitmap
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.text.DateFormat
import java.time.Instant
import java.util.Date
import org.hamcrest.Matchers.containsString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.PrivateMessage
import org.mobilism.material.designsystem.AmoledBlack
import org.mobilism.material.designsystem.CatppuccinLatte
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.designsystem.ThemeCatalog
import org.mobilism.material.designsystem.ThemeDefinition
import org.mobilism.material.ui.MessagesScreen
import org.mobilism.material.ui.MessagesState

@RunWith(AndroidJUnit4::class)
class MessageConversationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val timestamp = Instant.parse("2026-10-31T18:20:00Z").epochSecond
    private val incoming = PrivateMessage(1, 10, "App feedback", "Morgan", "Reader",
        "Thanks for the update! The new layout is much easier to read.", "", timestamp, true)
    private val outgoing = incoming.copy(id = 2, from = "rEaDeR", to = "Morgan",
        contentHtml = "Glad it helps 😊", publishedAt = timestamp + 600)
    private val state = mutableStateOf(MessagesState(conversationId = 1, messageId = 10,
        conversation = listOf(incoming, outgoing)))
    private var openedRelease: Long? = null

    @Test fun bubblesKeepDirectionTimestampsAndIndependentCollapseStateAfterRestoration() {
        val restoration = StateRestorationTester(compose)
        setScreen(restoration = restoration)
        compose.onNodeWithText("Morgan to Reader").assertDoesNotExist()
        compose.onNodeWithText("Morgan").assertIsDisplayed()
        compose.onNodeWithText("You").assertIsDisplayed()
        val received = compose.onNodeWithTag("message-1").fetchSemanticsNode().boundsInRoot
        val sent = compose.onNodeWithTag("message-2").fetchSemanticsNode().boundsInRoot
        assertTrue(received.left < sent.left)
        assertTrue(received.right < sent.right)
        val body = compose.onNodeWithTag("message-content-1").fetchSemanticsNode().boundsInRoot
        val date = compose.onNodeWithTag("message-time-1").fetchSemanticsNode().boundsInRoot
        assertTrue(date.top >= body.bottom)
        assertDateAndTime()
        capture("conversation-dark")

        compose.onNode(hasContentDescription("Collapse message") and hasAnyAncestor(hasTestTag("message-1"))).performClick()
        compose.onNodeWithTag("message-body-1").assertDoesNotExist()
        compose.onNodeWithTag("message-body-2").assertIsDisplayed()
        compose.onNodeWithTag("message-toggle-1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        assertDateAndTime()
        capture("conversation-collapsed")
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { state.value = state.value.copy(conversation = state.value.conversation.map { it.copy(read = true) }) }
        compose.onNodeWithTag("message-body-1").assertDoesNotExist()
        compose.onNodeWithTag("message-body-2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Expand message").performClick()
        compose.onNodeWithTag("message-body-1").assertIsDisplayed()
        compose.onNodeWithTag("message-toggle-1").performClick()
        compose.onNodeWithTag("message-body-1").assertDoesNotExist()
    }

    @Test fun lightBubblesKeepRichLinksAndReadControlsAfterExpanding() {
        state.value = state.value.copy(conversation = listOf(incoming.copy(read = false,
            contentHtml = "<p>Thanks for the update! Downloads are working well.</p>" +
                "<p>I appreciate the time you put into the app. Having the messages in a conversation makes it easier to follow.</p>" +
                "<p><a href=\"https://forum.mobilism.org/viewtopic.php?t=42\">Open release</a></p>"), outgoing))
        setScreen(theme = CatppuccinLatte)
        compose.onNodeWithTag("message-toggle-1").performClick()
        compose.onNodeWithContentDescription("Expand message").performClick()
        onView(withText(containsString("Open release"))).perform(object : ViewAction {
            override fun getConstraints() = isAssignableFrom(TextView::class.java)
            override fun getDescription() = "Open the message's release link"
            override fun perform(uiController: UiController, view: View) {
                val text = view as TextView
                assertTrue(text.isTextSelectable)
                val spans = text.text as Spanned
                spans.getSpans(0, spans.length, ClickableSpan::class.java).single().onClick(text)
            }
        })
        compose.runOnIdle { assertEquals(42L, openedRelease) }
        compose.onNodeWithText("Mark as read").performScrollTo().performClick()
        compose.onNodeWithText("Mark as read").assertDoesNotExist()
        compose.onNodeWithTag("message-conversation").performScrollToIndex(0)
        capture("conversation-light")
    }

    @Test fun largeFontRtlBubblesKeepTimestampsAndCollapseControlsVisible() {
        setScreen(theme = AmoledBlack, largeFontRtl = true)
        val received = compose.onNodeWithTag("message-1").fetchSemanticsNode().boundsInRoot
        val sent = compose.onNodeWithTag("message-2").fetchSemanticsNode().boundsInRoot
        assertTrue(received.left > sent.left)
        compose.onNodeWithTag("message-toggle-1").performClick()
        compose.onNodeWithContentDescription("Expand message").assertIsDisplayed()
        assertDateAndTime()
        capture("conversation-amoled-large-font-rtl")
    }

    private fun setScreen(theme: ThemeDefinition = ThemeCatalog.default,
        restoration: StateRestorationTester? = null, largeFontRtl: Boolean = false) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, if (largeFontRtl) 1.6f else density.fontScale),
                LocalLayoutDirection provides if (largeFontRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                MobilismTheme(theme = theme, reduceMotion = true) {
                    Box(if (largeFontRtl) Modifier.widthIn(max = 320.dp) else Modifier) {
                        MessagesScreen(state.value, "Reader", onFolder = {}, onRefresh = {}, onMore = {}, onOpen = {},
                            onRead = { id -> state.value = state.value.copy(conversation = state.value.conversation.map {
                                if (it.id == id) it.copy(read = true) else it
                            }) },
                            onBack = {}, onSignIn = {}, onRefreshConversation = {}, onReply = {}, onEdit = {},
                            onSend = {}, onCloseReply = {}, onNoticeShown = {}, onRelease = { openedRelease = it })
                    }
                }
            }
        }
        if (restoration != null) restoration.setContent(content) else compose.setContent(content)
    }

    private fun assertDateAndTime() {
        val context = compose.activity
        val date = Date(timestamp * 1000)
        compose.onNodeWithTag("message-time-1", useUnmergedTree = true)
            .assertTextContains(DateFormat.getDateInstance(DateFormat.MEDIUM, context.resources.configuration.locales[0]).format(date), substring = true)
            .assertTextContains(android.text.format.DateFormat.getTimeFormat(context).format(date), substring = true)
            .assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(500, 5_000)
        val image = checkNotNull(automation.takeScreenshot())
        val directory = File(compose.activity.getExternalFilesDir(null), "message-screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
