package org.mobilism.material

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.MessageFolder
import org.mobilism.material.data.PrivateMessage
import org.mobilism.material.designsystem.AmoledBlack
import org.mobilism.material.designsystem.CatppuccinLatte
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.designsystem.ThemeCatalog
import org.mobilism.material.designsystem.ThemeDefinition
import org.mobilism.material.ui.MailboxState
import org.mobilism.material.ui.MessagesScreen
import org.mobilism.material.ui.MessagesState

@RunWith(AndroidJUnit4::class)
class MessageMailboxTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val first = PrivateMessage(10, 42, "App feedback", "Morgan", "Reader", "", "Original message",
        Instant.parse("2026-11-01T12:00:00Z").epochSecond, false)
    private val latest = first.copy(messageId = 44, preview = "Thanks, the update fixed it!",
        publishedAt = first.publishedAt + 86400, read = true)
    private val replies = listOf(latest, first.copy(messageId = 43, preview = "Earlier reply"), first)
    private val state = mutableStateOf(MessagesState(mailboxes = mapOf(
        MessageFolder.Inbox to MailboxState(replies, page = 1, canLoadMore = false),
        MessageFolder.Sent to MailboxState(replies.map { it.copy(from = "Reader", to = "Morgan") }, page = 1, canLoadMore = false),
    )))
    private var opened: PrivateMessage? = null
    private val reads = mutableListOf<Long>()

    @Test fun inboxShowsOneRowPerChainAndMarksAllUnreadReplies() {
        setScreen()
        compose.onAllNodesWithTag("message-thread-10").assertCountEquals(1)
        compose.onAllNodesWithText("App feedback").assertCountEquals(1)
        compose.onNodeWithText(latest.preview).assertIsDisplayed()
        compose.onNodeWithText(first.preview).assertDoesNotExist()
        compose.onNodeWithText("Earlier reply").assertDoesNotExist()
        compose.onNodeWithContentDescription("Unread messages").assertIsDisplayed()
        compose.onNodeWithTag("message-thread-time-10", useUnmergedTree = true).assertTextContains(" · ", substring = true)
        capture("inbox-threads-dark")
        compose.onNodeWithText("Mark as read").performClick()
        compose.runOnIdle { assertEquals(setOf(42L, 43L), reads.toSet()) }
        compose.onNodeWithContentDescription("Unread messages").assertDoesNotExist()
        compose.onNodeWithTag("message-thread-10").performClick()
        compose.runOnIdle { assertEquals(latest, opened) }
        compose.onNodeWithText("Sent").performClick()
        compose.onAllNodesWithTag("message-thread-10").assertCountEquals(1)
        compose.onNodeWithText("To Morgan").assertIsDisplayed()
        compose.onNodeWithText("Mark as read").assertDoesNotExist()
        capture("sent-threads-dark")
    }

    @Test fun separateConversationsWithTheSameSubjectStaySeparateInLightTheme() {
        val other = first.copy(id = 11, messageId = 50, preview = "A separate conversation", read = true)
        state.value = state.value.copy(mailboxes = state.value.mailboxes +
            (MessageFolder.Inbox to MailboxState(replies + other, page = 1, canLoadMore = false)))
        setScreen(CatppuccinLatte)
        compose.onAllNodesWithText("App feedback").assertCountEquals(2)
        compose.onNodeWithTag("message-thread-10").assertIsDisplayed()
        compose.onNodeWithTag("message-thread-11").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(other, opened) }
        capture("inbox-threads-light")
    }

    @Test fun olderRepliesLoadedLaterDoNotAddDuplicateRowsOrReplaceThePreview() {
        state.value = state.value.copy(mailboxes = state.value.mailboxes +
            (MessageFolder.Inbox to MailboxState(listOf(latest), page = 1)))
        setScreen(AmoledBlack)
        compose.onNodeWithContentDescription("Unread messages").assertDoesNotExist()
        compose.runOnIdle {
            state.value = state.value.copy(mailboxes = state.value.mailboxes +
                (MessageFolder.Inbox to MailboxState(replies, page = 2)))
        }
        compose.onAllNodesWithTag("message-thread-10").assertCountEquals(1)
        compose.onNodeWithText(latest.preview).assertIsDisplayed()
        compose.onNodeWithContentDescription("Unread messages").assertIsDisplayed()
        compose.onNodeWithText("Load more messages").assertIsDisplayed()
        capture("inbox-threads-amoled")
    }

    private fun setScreen(theme: ThemeDefinition = ThemeCatalog.default) {
        compose.setContent { MobilismTheme(theme = theme, reduceMotion = true) {
            MessagesScreen(state.value, "Reader", onFolder = { state.value = state.value.copy(folder = it) },
                onRefresh = {}, onMore = {}, onOpen = { opened = it }, onRead = { id ->
                    reads += id
                    state.value = state.value.copy(mailboxes = state.value.mailboxes.mapValues { (_, box) ->
                        box.copy(items = box.items.map { if (it.messageId == id) it.copy(read = true) else it })
                    })
                }, onBack = {}, onSignIn = {}, onRefreshConversation = {}, onReply = {}, onEdit = {},
                onSend = {}, onCloseReply = {}, onNoticeShown = {})
        } }
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
