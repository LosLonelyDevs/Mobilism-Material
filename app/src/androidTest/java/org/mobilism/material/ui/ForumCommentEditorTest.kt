package org.mobilism.material.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mobilism.material.data.DefaultMobilismRepository
import org.mobilism.material.designsystem.MobilismTheme

private fun isolatedContext() = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("comment-test-$name", mode)
}

internal class CommentForumFixture {
    val writes = CopyOnWriteArrayList<String>()
    val formReads = CopyOnWriteArrayList<String>()
    @Volatile var source = "My comment"
    @Volatile var requireForumId = false
    @Volatile var missing = false
    @Volatile var loginRequired = false
    @Volatile var blocked = false
    @Volatile var confirmed = true
    @Volatile var formPostId: Long = 123
    private val context = isolatedContext()
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val path = request.url.encodedPath
        if (path == "/posting.php" && request.method == "GET") formReads += request.url.toString()
        val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
        val body = when {
            path.endsWith("/auth/login") -> """{"access_token":"sample-session","id":7}"""
            request.header("X-Auth-Token") != null -> error("API token sent to forum")
            path == "/viewtopic.php" -> """<a href="./posting.php?mode=quote&amp;f=428&amp;p=123">Quote</a>"""
            missing || (path == "/posting.php" && request.url.queryParameter("f") == null && requireForumId) -> {
                response.code(404)
                "Page not found"
            }
            blocked -> {
                response.code(403).header("cf-mitigated", "challenge")
                "<html>Verify connection</html>"
            }
            path == "/ucp.php" && request.method == "POST" -> {
                val payload = Buffer().also(request.body!!::writeTo).readUtf8()
                assertTrue(payload.contains("username=Reader") && payload.contains("password=fixture"))
                loginRequired = false
                response.code(303).header("Location", "/index.php")
                ""
            }
            loginRequired && path != "/ucp.php" -> {
                response.code(302).header("Location", "/ucp.php?mode=login")
                ""
            }
            loginRequired -> """<form action="/ucp.php?mode=login" method="post"><input name="username">
                <input type="password" name="password"><input type="submit" name="login" value="Login"></form>"""
            path == "/posting.php" && request.method == "POST" -> {
                assertTrue(request.body!!.isOneShot())
                writes += Buffer().also(request.body!!::writeTo).readUtf8()
                if (confirmed) response.code(303).header("Location", "/viewtopic.php?t=42&p=123#p123")
                "<html></html>"
            }
            path == "/posting.php" -> {
                val action = request.url.newBuilder().setQueryParameter("p", formPostId.toString()).build()
                    .toString().replace("&", "&amp;")
                val controls = if (request.url.queryParameter("mode") == "edit")
                    """<input name="subject" value="Re: Original"><textarea name="message">${org.jsoup.nodes.Entities.escape(source)}</textarea><input type="submit" name="preview" value="Preview"><input type="submit" name="post" value="Submit">"""
                else """<input type="submit" name="confirm" value="Yes">"""
                """<form action="$action" method="post"><input type="hidden" name="form_token" value="test-token">$controls</form>"""
            }
            else -> error("Unexpected fixture request")
        }
        response.body(body.toResponseBody()).build()
    }.build()
    private val repository = DefaultMobilismRepository(context, client = client)
    val gateway = repository.commentGateway
    suspend fun signIn(username: String = "Reader") { repository.signIn(username, "fixture") }
}

class ForumCommentEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val target = ForumCommentTarget(42, 123, ForumCommentAction.Edit, 428)

    private fun show(fixture: CommentForumFixture, close: (Boolean) -> Unit = {}) {
        compose.setContent { CompositionLocalProvider(LocalCommentGateway provides fixture.gateway) {
            MobilismTheme(reduceMotion = true) { ForumCommentEditor(target, "Reader", "My comment", close) }
        } }
    }

    private fun awaitText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun accountChangeOrWrongPostCannotSubmitAnEdit() = runBlocking {
        val fixture = CommentForumFixture()
        fixture.signIn()
        fixture.formPostId = 999
        assertTrue(runCatching { fixture.gateway.load(target, "Reader") }.isFailure)
        fixture.formPostId = 123
        val form = fixture.gateway.load(target, "Reader")
        fixture.signIn("Second reader")
        assertTrue(runCatching { fixture.gateway.submit(form, "Changed") }.isFailure)
        assertTrue(fixture.writes.isEmpty())
    }

    @Test fun forumSignInCookiesReachTheEditAndStayWithTheAccount() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("forum.mobilism.org")
            .addSubjectAlternativeName("app.mobilism.org").build()
        val tls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(tls.sslSocketFactory(), false)
            server.start(java.net.InetAddress.getLoopbackAddress(), 0)
            val client = OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY)
                .dns(object : okhttp3.Dns {
                    override fun lookup(hostname: String) = listOf(java.net.InetAddress.getLoopbackAddress())
                })
                .sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
                .addInterceptor { chain ->
                    val request = chain.request()
                    chain.proceed(request.newBuilder().url(request.url.newBuilder().port(server.port).build()).build())
                }.build()
            val repository = DefaultMobilismRepository(isolatedContext(), client = client)
            val login = MockResponse().setBody("""{"access_token":"sample-session","id":7}""")
            val form = MockResponse().setBody("""<form method="post" action="/posting.php?mode=edit&amp;p=123">
                <textarea name="message">Original</textarea><input type="submit" name="post" value="Submit"></form>""")
            server.enqueue(login)
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/ucp.php?mode=login")
                .setHeader("Set-Cookie", "phpbb_sid=guest; Secure; Path=/"))
            server.enqueue(MockResponse().setBody("""<form method="post" action="/ucp.php?mode=login">
                <input name="password" type="password"><input type="submit" name="login" value="Login"></form>"""))
            server.enqueue(MockResponse().setResponseCode(303).setHeader("Location", "/index.php")
                .setHeader("Set-Cookie", "phpbb_sid=member; Secure; Path=/"))
            server.enqueue(form)
            server.enqueue(MockResponse().setResponseCode(303).setHeader("Location", "/viewtopic.php?p=123#p123"))
            server.enqueue(login)
            server.enqueue(form)
            repository.signIn("Reader", "fixture")
            val gateway = repository.commentGateway
            val guest = gateway.load(target, "Reader")
            assertTrue(guest.needsLogin)
            val editor = gateway.login(guest, "fixture")
            assertEquals("Original", editor.source)
            gateway.submit(editor, "Edited")
            repository.signOut()
            repository.signIn("Second reader", "fixture")
            gateway.load(target, "Second reader")
            assertEquals(8, server.requestCount)
            val requests = List(8) { server.takeRequest() }
            assertEquals(listOf("POST", "GET", "GET", "POST", "GET", "POST", "POST", "GET"), requests.map { it.method })
            assertTrue(requests[3].getHeader("Cookie").orEmpty().contains("phpbb_sid=guest"))
            assertTrue(requests[5].getHeader("Cookie").orEmpty().contains("phpbb_sid=member"))
            assertTrue(requests[5].body.readUtf8().contains("message=Edited"))
            assertFalse(requests[7].getHeader("Cookie").orEmpty().contains("phpbb_sid"))
            assertTrue(requests.slice(1..5).all { it.getHeader("X-Auth-Token") == null })
            repository.signOut()
        }
    }

    @Test fun nativeLoginEditAndUnknownAcknowledgementNeverReplayTheChange() = runBlocking {
        val fixture = CommentForumFixture().apply { signIn(); loginRequired = true; confirmed = false }
        var refresh = false
        show(fixture) { refresh = it }
        compose.onNodeWithTag("edit-comment-text").assertTextContains("My comment")
        compose.onNodeWithText("Update").assertIsNotEnabled()
        assertTrue(fixture.formReads.isEmpty())
        compose.onNodeWithTag("edit-comment-text").performTextReplacement("[b]Edited[/b]")
        compose.onNodeWithText("Update").performClick()
        awaitText("Forum password")
        compose.onNodeWithText("Forum password").performTextInput("fixture")
        compose.onNodeWithText("Sign in and update").performClick()
        awaitText("Check comment")
        assertEquals(1, fixture.writes.size)
        val body = java.net.URLDecoder.decode(fixture.writes.single(), "UTF-8")
        assertTrue(body.contains("form_token=test-token") && body.contains("message=[b]Edited[/b]"))
        assertTrue(body.contains("subject=Re: Original") && body.contains("post=Submit"))
        assertFalse(body.contains("preview="))
        compose.onNodeWithText("Check comment").performClick()
        assertTrue(refresh)
        assertEquals(1, fixture.writes.size)
    }

    @Test fun blockedForumShowsCommentAndCanRecoverInPlace() = runBlocking {
        val fixture = CommentForumFixture().apply { signIn(); blocked = true }
        var refreshed = false
        show(fixture) { refreshed = it }
        compose.onNodeWithTag("edit-comment-text").performTextReplacement("My draft")
        assertTrue(fixture.formReads.isEmpty())
        compose.onNodeWithText("Update").performClick()
        awaitText("requires a browser check")
        compose.onNodeWithTag("edit-comment-text").assertTextContains("My draft")
        fixture.blocked = false
        compose.onNodeWithText("Update").performClick()
        compose.waitUntil(10_000) { refreshed }
        assertEquals(1, fixture.writes.size)
    }

    @Test fun openingAndCancellingOnlyUsesTheVisibleComment() = runBlocking {
        val fixture = CommentForumFixture().apply { signIn() }
        var dismissed: Boolean? = null
        show(fixture) { dismissed = it }
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(false, dismissed)
        assertTrue(fixture.formReads.isEmpty())
        assertTrue(fixture.writes.isEmpty())
    }

    @Test fun missingForumIdIsRecoveredFromThePostLink() = runBlocking {
        val fixture = CommentForumFixture().apply { signIn(); requireForumId = true }
        fixture.gateway.load(target.copy(forumId = null), "Reader")
        assertEquals(2, fixture.formReads.size)
        assertTrue(fixture.formReads.last().contains("f=428"))
        assertTrue(fixture.writes.isEmpty())
    }

    @Test fun missingFormKeepsTheDraft() = runBlocking {
        val fixture = CommentForumFixture().apply { signIn(); missing = true }
        show(fixture)
        compose.onNodeWithTag("edit-comment-text").performTextReplacement("Change this")
        compose.onNodeWithText("Update").performClick()
        awaitText("(404)")
        compose.onNodeWithTag("edit-comment-text").assertTextContains("Change this")
        assertEquals(1, fixture.formReads.size)
        assertTrue(fixture.writes.isEmpty())
    }

    @Test fun newerForumCopyRequiresReviewBeforeWriting() = runBlocking {
        val fixture = CommentForumFixture().apply { signIn(); source = "An edit from another device" }
        show(fixture)
        compose.onNodeWithTag("edit-comment-text").performTextReplacement("My local draft")
        compose.onNodeWithText("Update").performClick()
        awaitText("An edit from another device")
        compose.onNodeWithTag("edit-comment-text").assertTextContains("My local draft")
        assertTrue(fixture.writes.isEmpty())
        compose.onNodeWithText("Update").performClick()
        compose.waitUntil(10_000) { fixture.writes.size == 1 }
    }
}
