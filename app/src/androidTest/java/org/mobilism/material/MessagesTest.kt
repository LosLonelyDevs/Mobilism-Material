package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
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
class MessagesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun authenticatedMailboxReadAndReplyUseLegacyContractAfterSessionRestore() = runBlocking {
        val fixture = Fixture()
        fixture.repository.signIn("Reader", "sample-password")
        val restored = DefaultMobilismRepository(fixture.context, client = fixture.client)
        restored.restoreSession()
        val message = restored.messages(MessageFolder.Inbox, 1).items.single()
        restored.messages(MessageFolder.Sent, 2)
        assertEquals(42L, restored.conversation(message.id).single().id)
        restored.markMessageRead(message.messageId)
        restored.replyToMessage(message, "Thanks & hello")
        restored.postComment(80, "Comment reply", replyTo = 77)
        val paths = fixture.requests.map { it.url.encodedPath.removePrefix("/api-new/") }
        assertTrue("message/to/7/page/1/limit/20/orderby/times/desc" in paths)
        assertTrue("message/from/7/page/2/limit/20/orderby/times/desc" in paths)
        assertTrue("message/10/orderby/times/asc" in paths)
        assertTrue("message/42/read" in paths)
        val reply = fixture.requests.single { it.url.encodedPath.endsWith("message/reply/send") }
        assertEquals("sample-session", reply.header("X-Auth-Token"))
        assertTrue(checkNotNull(reply.body).isOneShot())
        val expected = FormBody.Builder().add("to", "Author").add("from", "Reader").add("subject", "Question")
            .add("content", "Thanks & hello").add("reply", "42").build()
        assertEquals(okio.Buffer().also(expected::writeTo).readUtf8(), okio.Buffer().also(reply.body!!::writeTo).readUtf8())
        val comment = fixture.requests.single { it.url.encodedPath.endsWith("release/comment") }
        assertTrue(okio.Buffer().also(comment.body!!::writeTo).readUtf8().contains("msg_id=77"))
        assertFalse(fixture.events.toString().contains("Thanks"))
        assertFalse(fixture.events.toString().contains("sample-session"))
        restored.signOut()
    }

    @Test fun writeFailuresAreNotRetriedAndCannotReportSuccess() = runBlocking {
        val fixture = Fixture()
        fixture.repository.signIn("Reader", "sample-password")
        val message = fixture.repository.messages(MessageFolder.Inbox, 1).items.single()
        fixture.actionStatus = 503
        fixture.requests.clear()
        assertTrue(runCatching { fixture.repository.replyToMessage(message, "Private draft") }.isFailure)
        assertEquals(1, fixture.requests.size)
        fixture.requests.clear()
        assertTrue(runCatching { fixture.repository.markMessageRead(42) }.isFailure)
        assertEquals(1, fixture.requests.size)
        fixture.actionStatus = 200
        fixture.actionBody = "{}"
        assertEquals(FailureKind.InvalidResponse,
            (runCatching { fixture.repository.replyToMessage(message, "Private draft") }.exceptionOrNull() as ApiFailure).kind)
        fixture.repository.signOut()
    }

    @Test fun emptyPagesOldLoginsAndExpiredSessionsHaveDistinctOutcomes() = runBlocking {
        val fixture = Fixture()
        fixture.loginId = null
        fixture.repository.signIn("Reader", "sample-password")
        assertEquals(FailureKind.Credentials,
            (runCatching { fixture.repository.messages(MessageFolder.Inbox, 1) }.exceptionOrNull() as ApiFailure).kind)
        fixture.loginId = 7
        fixture.repository.signIn("Reader", "sample-password")
        fixture.listStatus = 400
        fixture.listBody = """{"status":"error","error":{"text":"No records found."}}"""
        assertTrue(fixture.repository.messages(MessageFolder.Inbox, 1).items.isEmpty())
        fixture.listStatus = 401
        fixture.listBody = """{"status":"error","error":{"text":"Session expired"}}"""
        assertEquals(FailureKind.Session,
            (runCatching { fixture.repository.messages(MessageFolder.Inbox, 1) }.exceptionOrNull() as ApiFailure).kind)
        assertEquals(AuthState.SignedOut, fixture.repository.auth.value)
        fixture.requests.clear()
        assertTrue(runCatching { fixture.repository.markMessageRead(42) }.isFailure)
        assertTrue(fixture.requests.isEmpty())
    }

    @Test fun inboxSentReadAndReplyControlsWorkTogether() = runBlocking {
        val fixture = Fixture().apply { preview = """Premium support added [smile]\\ud83d\\ude0a[/smile]""" }
        fixture.repository.signIn("Reader", "sample-password")
        lateinit var model: MessagesViewModel
        compose.runOnUiThread { model = MessagesViewModel(fixture.repository, "Reader", SavedStateHandle()) }
        compose.setContent { MobilismTheme(reduceMotion = true) {
            val state by model.state.collectAsState()
            MessagesScreen(state, "Reader", onFolder = model::selectFolder, onRefresh = { model.loadMailbox(true) },
                onMore = { model.loadMailbox() }, onOpen = model::openMessage, onRead = model::markRead,
                onBack = model::closeConversation, onSignIn = {}, onRefreshConversation = model::loadConversation,
                onReply = model::openReply, onEdit = model::editReply, onSend = model::sendReply,
                onCloseReply = model::closeReply, onNoticeShown = model::noticeShown,
                onCheckReply = model::checkReplyDelivery, onRetryReply = model::retryReply)
        } }
        compose.waitUntil(10_000) { model.state.value.mailbox.items.isNotEmpty() }
        compose.onNodeWithText("Inbox").assertIsSelected()
        compose.onNodeWithText("Unread").assertIsDisplayed()
        compose.onNodeWithText("Premium support added 😊").assertIsDisplayed()
        compose.onNodeWithText("[smile]", substring = true).assertDoesNotExist()
        capture("inbox")
        compose.onNodeWithText("Mark as read").performClick()
        compose.waitUntil(10_000) { model.state.value.mailbox.items.single().read }
        compose.onNodeWithText("Unread").assertDoesNotExist()
        compose.onNodeWithText("Sent").performClick()
        compose.waitUntil(10_000) { model.state.value.mailbox.page == 1 }
        compose.onNodeWithText("To Author").assertIsDisplayed()
        compose.onNodeWithText("Premium support added 😊").assertIsDisplayed()
        compose.onNodeWithText("[smile]", substring = true).assertDoesNotExist()
        capture("sent-emoji")
        compose.onNodeWithText("Inbox").performClick()
        compose.onNodeWithText("Question").performClick()
        compose.waitUntil(10_000) { model.state.value.conversation.isNotEmpty() }
        compose.onNodeWithText("Reply", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Reply to Author").assertIsDisplayed()
        compose.onNodeWithTag("send-message").assertIsNotEnabled()
        compose.onNodeWithTag("message-draft").performTextInput("A private reply")
        capture("message-reply")
        compose.onNodeWithTag("send-message").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.state.value.draft.isEmpty() && !model.state.value.replying }
        assertEquals(1, fixture.requests.count { it.url.encodedPath.endsWith("message/reply/send") })
        compose.onNodeWithText("Reply sent").assertExists()
        fixture.repository.signOut()
    }

    @Test fun lostAcknowledgementRecoversDeliveredReplyWithoutAnotherPost() = runBlocking {
        val fixture = Fixture().apply { actionBody = "{}"; deliverOnSend = true }
        val model = openComposer(fixture)
        compose.onNodeWithTag("send-message").performScrollTo().assertIsEnabled().performClick()
        try {
            compose.waitUntil(15_000) { model.state.value.draft.isEmpty() && !model.state.value.replying }
        } catch (failure: Throwable) {
            val state = model.state.value
            capture("reply-recovery-failure")
            throw AssertionError("Reply recovery: posts=${fixture.requests.count { it.url.encodedPath.endsWith("message/reply/send") }}, " +
                "sending=${state.sending}, checking=${state.checkingReply}, unconfirmed=${state.sendUnconfirmed}, " +
                "loading=${state.loading}, draftLength=${state.draft.length}, error=${state.sendError}", failure)
        }
        assertFalse(model.state.value.sendUnconfirmed)
        assertEquals(1, fixture.requests.count { it.url.encodedPath.endsWith("message/reply/send") })
        compose.onNodeWithText("Reply sent").assertExists()
        fixture.repository.signOut()
    }

    @Test fun uncertainReplyChecksDeliveryAndRequiresExplicitRetryConfirmation() = runBlocking {
        val fixture = Fixture().apply { actionBody = "{}" }
        val model = openComposer(fixture)
        compose.onNodeWithTag("send-message").performScrollTo().performClick()
        compose.waitUntil(15_000) {
            model.state.value.sendUnconfirmed && !model.state.value.checkingReply && model.state.value.sendError != null
        }
        compose.onNodeWithTag("send-message").assertDoesNotExist()
        compose.onNodeWithTag("check-message-delivery").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("message-draft").assertTextContains("A private reply")
        capture("reply-delivery-unconfirmed")
        compose.onNodeWithTag("check-message-delivery").performClick()
        compose.waitUntil(15_000) { !model.state.value.checkingReply }
        assertEquals(1, fixture.requests.count { it.url.encodedPath.endsWith("message/reply/send") })
        compose.onNodeWithText("Close", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Continue reply", substring = false).performScrollTo().performClick()
        compose.onNodeWithTag("message-draft").assertTextContains("A private reply")
        compose.onNodeWithTag("retry-message").performScrollTo().performClick()
        compose.onNodeWithText("Send this reply again?").assertIsDisplayed()
        capture("reply-retry-confirmation")
        compose.onNodeWithText("Cancel", substring = false).performClick()
        assertEquals(1, fixture.requests.count { it.url.encodedPath.endsWith("message/reply/send") })
        fixture.actionBody = """{"status":"success"}"""
        compose.onNodeWithTag("retry-message").performScrollTo().performClick()
        compose.onNodeWithTag("confirm-message-retry").performClick()
        compose.waitUntil(15_000) { model.state.value.draft.isEmpty() && !model.state.value.replying }
        assertEquals(2, fixture.requests.count { it.url.encodedPath.endsWith("message/reply/send") })
        fixture.repository.signOut()
    }

    private suspend fun openComposer(fixture: Fixture): MessagesViewModel {
        fixture.repository.signIn("Reader", "sample-password")
        lateinit var model: MessagesViewModel
        compose.runOnUiThread { model = MessagesViewModel(fixture.repository, "Reader", SavedStateHandle()) }
        compose.setContent { MobilismTheme(reduceMotion = true) {
            val state by model.state.collectAsState()
            MessagesScreen(state, "Reader", onFolder = model::selectFolder, onRefresh = { model.loadMailbox(true) },
                onMore = { model.loadMailbox() }, onOpen = model::openMessage, onRead = model::markRead,
                onBack = model::closeConversation, onSignIn = {}, onRefreshConversation = model::loadConversation,
                onReply = model::openReply, onEdit = model::editReply, onSend = model::sendReply,
                onCloseReply = model::closeReply, onNoticeShown = model::noticeShown,
                onCheckReply = model::checkReplyDelivery, onRetryReply = model::retryReply)
        } }
        compose.waitUntil(10_000) { model.state.value.mailbox.items.isNotEmpty() }
        compose.onNodeWithText("Question").performClick()
        compose.waitUntil(10_000) { model.state.value.conversation.isNotEmpty() }
        compose.onNodeWithText("Reply", substring = false).performScrollTo().performClick()
        compose.onNodeWithTag("message-draft").performTextInput("A private reply")
        // The keyboard resizes the sheet outside Compose's idle tracking.
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
        return model
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(500, 5_000)
        val image = checkNotNull(automation.takeScreenshot())
        val directory = java.io.File(compose.activity.getExternalFilesDir(null), "message-screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        java.io.File(directory, "$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    private class Fixture {
        val requests = CopyOnWriteArrayList<Request>()
        val events = CopyOnWriteArrayList<DiagnosticEvent>()
        var loginId: Long? = 7
        var actionStatus = 200
        @Volatile var actionBody = """{"status":"success"}"""
        @Volatile var deliverOnSend = false
        @Volatile var delivered = false
        var listStatus = 200
        var listBody: String? = null
        var preview = "Hello"
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("messages-test-$name", mode)
        }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val path = request.url.encodedPath.removePrefix("/api-new/")
            var status = 200
            val body = when {
                path == "auth/login" -> """{"access_token":"sample-session","id":${loginId ?: "null"}}"""
                path == "release/comment" -> """{"status":"success","response":{"comment_id":99}}"""
                path == "message/reply/send" -> { if (deliverOnSend) delivered = true; status = actionStatus; actionBody }
                path.endsWith("/read") -> { status = actionStatus; """{"status":"success"}""" }
                path == "message/10/orderby/times/asc" -> {
                    val reply = if (delivered) """,{"id":43,"subject":"Re: Question","from_user":"Reader","to_user":"Author","content":"A private reply","times":${System.currentTimeMillis() / 1000},"readed":1}""" else ""
                    """{"response":[{"id":42,"subject":"Question","from_user":"Author","to_user":"Reader","content":"Hello https://example.org","times":1700000000,"readed":0}$reply]}"""
                }
                path.startsWith("message/to/") || path.startsWith("message/from/") -> {
                    status = listStatus
                    val sent = path.startsWith("message/from/")
                    listBody ?: """{"response":[{"id":10,"msg_id":42,"subject":"Question","from_user":"${if (sent) "Reader" else "Author"}","to_user":"${if (sent) "Author" else "Reader"}","preview":"$preview","times":1700000000,"readed":0}]}"""
                }
                else -> error("Unexpected fixture path")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Fixture")
                .body(body.toResponseBody()).build()
        }.build()
        val repository = DefaultMobilismRepository(context, client = client, diagnostics = DiagnosticLogger { events += it })
    }
}
