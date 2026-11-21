package org.mobilism.material.data

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.FormElement

interface ForumCommentGateway {
    suspend fun load(target: ForumCommentTarget, username: String): CommentForm
    suspend fun login(form: CommentForm, password: String): CommentForm
    suspend fun submit(form: CommentForm, message: String)
}

class CommentForm internal constructor(
    internal val target: ForumCommentTarget, internal val username: String, internal val url: HttpUrl,
    internal val fields: List<Pair<String, String>>, val source: String?, val needsLogin: Boolean,
)

class UnconfirmedCommentChange : Exception("The forum did not confirm the change. Check the comment before trying again.")

internal class NativeForumComments(private val context: Context, private val client: OkHttpClient,
    private val policy: ReadRequestPolicy, private val account: () -> Session?, private val changed: (Long) -> Unit) : ForumCommentGateway {
    private class Page(val document: Document, val redirect: HttpUrl?)
    private var forumSession: Session? = null
    private var forumClient: OkHttpClient? = null

    @Synchronized private fun transport(username: String): OkHttpClient {
        val active = account()?.takeIf { username.isNotBlank() && username.equals(it.username, true) }
            ?: throw ApiFailure(FailureKind.Credentials, "Sign in to manage this comment.")
        if (active !== forumSession) {
            forumClient = client.newBuilder().apply { interceptors().removeAll { it is BrowserCookiesInterceptor } }
                .cookieJar(ForumSessionCookies()).build()
            forumSession = active
        }
        return checkNotNull(forumClient)
    }

    override suspend fun load(target: ForumCommentTarget, username: String): CommentForm = withContext(Dispatchers.IO) {
        var url = target.url.toHttpUrl()
        repeat(4) {
            val page = policy.pacedRead { request(target, username, url) }
            val next = page.redirect ?: guestLoginLink(target, url, page.document)
                ?: return@withContext parse(target, username, page.document)
            if (next.encodedPath !in setOf("/ucp.php", "/posting.php")) unavailable()
            url = next
        }
        unavailable()
    }

    private fun guestLoginLink(target: ForumCommentTarget, url: HttpUrl, doc: Document): HttpUrl? {
        if (url.encodedPath != "/posting.php" || doc.selectFirst(
                "textarea[name=message], form [name=confirm], input[type=password], a[href*=mode=logout]") != null) return null
        return doc.select("a[href*=ucp.php]").asSequence().mapNotNull { url.resolve(it.attr("href")) }
            .firstOrNull { it.encodedPath == "/ucp.php" && target.accepts(it.toString()) }
    }

    override suspend fun login(form: CommentForm, password: String): CommentForm {
        require(form.needsLogin && password.isNotEmpty())
        val fields = form.fields.filterNot { it.first in setOf("username", "password", "redirect") } +
            listOf("username" to form.username, "password" to password, "redirect" to form.target.url)
        val page = request(form.target, form.username, form.url, fields)
        if (page.document.selectFirst("input[type=password]") != null)
            throw ApiFailure(FailureKind.Credentials, "Forum sign-in failed. Check your password and try again.")
        return load(form.target, form.username).also {
            if (it.needsLogin) throw ApiFailure(FailureKind.Credentials, "The forum did not accept this sign-in.")
        }
    }

    override suspend fun submit(form: CommentForm, message: String) {
        require(!form.needsLogin)
        val edit = form.target.action == ForumCommentAction.Edit
        if (edit) require(message.isNotBlank())
        val fields = if (edit) form.fields.filterNot { it.first == "message" } + ("message" to message) else form.fields
        val page = try { request(form.target, form.username, form.url, fields) }
        catch (failure: IOException) { throw UnconfirmedCommentChange() }
        catch (failure: ApiFailure) {
            if (!failure.blockedBeforeRequest && (failure.kind == FailureKind.InvalidResponse ||
                    failure.httpStatus == 408 || failure.httpStatus in 500..599)) throw UnconfirmedCommentChange()
            throw failure
        }
        val destination = page.redirect
        if (destination?.encodedPath == "/viewtopic.php") {
            val selected = form.target.postId.toString()
            if (!edit || destination.queryParameter("p") == selected || destination.fragment == "p$selected") return changed(form.target.topicId)
            throw UnconfirmedCommentChange()
        }
        val doc = page.document
        val rejection = doc.selectFirst(".error, .errorbox, #error, .rules .error")?.text()?.take(240)
        if (!rejection.isNullOrBlank()) throw ApiFailure(FailureKind.Server, rejection)
        if (page.redirect?.encodedPath == "/ucp.php" || doc.selectFirst("input[type=password]") != null)
            throw ApiFailure(FailureKind.Credentials, "Your forum sign-in expired. Reopen the editor to sign in.")
        val notice = doc.select("#message, #messagebox, .messagebox, .successbox").eachText()
        val acknowledgements = if (edit) listOf("post has been edited", "message has been edited", "message has been submitted successfully")
            else listOf("post has been deleted", "message has been deleted")
        if (notice.none { text -> acknowledgements.any { text.contains(it, ignoreCase = true) } }) throw UnconfirmedCommentChange()
        changed(form.target.topicId)
    }

    private suspend fun request(target: ForumCommentTarget, username: String, url: HttpUrl,
        fields: List<Pair<String, String>>? = null): Page = withContext(Dispatchers.IO) {
        val transport = transport(username)
        require(target.accepts(url.toString()))
        policy.checkAllowed()
        val builder = Request.Builder().url(url)
            .header("Cache-Control", "no-cache, no-store")
        if (fields != null) {
            val body = FormBody.Builder().apply { fields.forEach { (name, value) -> add(name, value) } }.build()
            builder.header("Origin", "https://forum.mobilism.org").header("Referer", url.toString())
                .post(object : RequestBody() {
                    override fun contentType() = body.contentType()
                    override fun contentLength() = body.contentLength()
                    override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
                    override fun isOneShot() = true
                })
        }
        transport.newCall(builder.build()).await().use { response ->
            val source = response.body?.source() ?: unavailable()
            if (source.request(4L * 1024 * 1024 + 1)) unavailable()
            val html = source.readUtf8()
            val failure = NetworkFailures.response(response.code, response.header("cf-mitigated") == "challenge", html, VerificationSite.Forum)
            val challenged = failure?.kind == FailureKind.Challenge
            policy.response(response.code, response.header("Retry-After"), challenged)
            if (challenged) throw ApiFailure(FailureKind.Challenge, "The forum requires connection verification. Try again after verifying your connection.", response.code)
            val location = response.header("Location")
            val next = if (response.code in 300..399) location?.let(url::resolve)?.takeIf { target.accepts(it.toString()) }
                ?: unavailable() else null
            if (next == null && !response.isSuccessful) throw if (response.code == 403)
                ApiFailure(FailureKind.Permission, "The forum denied permission to change this comment.", 403)
                else failure ?: ApiFailure(FailureKind.Server, "The forum returned HTTP ${response.code}.", response.code)
            Page(Jsoup.parse(html, url.toString()), next)
        }
    }

    private fun parse(target: ForumCommentTarget, username: String, doc: Document): CommentForm {
        val login = doc.selectFirst("form:has(input[type=password])") as? FormElement
        val control = when {
            login != null -> "login"
            target.action == ForumCommentAction.Edit -> "post"
            else -> "confirm"
        }
        val form = login ?: doc.forms().firstOrNull { it.selectFirst("[name=$control]") != null &&
            (control != "post" || it.selectFirst("textarea[name=message]") != null) } ?: unavailable()
        val url = doc.baseUri().toHttpUrl().resolve(form.attr("action")) ?: unavailable()
        if (!target.accepts(url.toString()) || !form.attr("method").equals("post", true) ||
            url.encodedPath != (if (login != null) "/ucp.php" else "/posting.php")) unavailable()
        val identity = doc.selectFirst("#username_logged_in .username, #username_logged_in .username-coloured")?.text()
        if (login == null && !identity.isNullOrBlank() && !identity.equals(username, true))
            throw ApiFailure(FailureKind.Credentials, "The forum is signed in as another account.")
        val button = form.selectFirst("input[type=submit][name=$control], button[name=$control]") ?: unavailable()
        // Keep hidden tokens, subject, formatting options and attachment metadata from this exact form.
        val submitNames = form.select("button, input[type=submit], input[type=image], input[type=file], [disabled]")
            .map { it.attr("name") }.toSet()
        val fields = form.formData().filterNot { it.key() in submitNames }.map { it.key() to it.value() } +
            (control to button.attr("value").ifBlank { button.text() })
        if (fields.any { (k,v) -> k in setOf("p","post_id") && v != target.postId.toString() ||
                k in setOf("t","topic_id") && v != target.topicId.toString() }) unavailable()
        return CommentForm(target, username, url, fields, form.selectFirst("textarea[name=message]")?.wholeText(), login != null)
    }

    private fun unavailable(): Nothing = throw ApiFailure(FailureKind.InvalidResponse,
        "The forum did not return the requested comment form. The comment may no longer be editable.")
}

// Forum login cookies belong to this app sign-in. Only connection clearance is shared with the browser.
private class ForumSessionCookies : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (url.host != "forum.mobilism.org") return
        for (cookie in cookies) {
            this.cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            if (cookie.expiresAt > System.currentTimeMillis()) this.cookies += cookie
        }
    }

    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!url.isHttps || url.host != "forum.mobilism.org") return emptyList()
        cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
        val current = cookies.filter { it.matches(url) }.sortedByDescending { it.path.length }
        if (current.any { it.name == "cf_clearance" }) return current
        val sharedCookies = runCatching { android.webkit.CookieManager.getInstance().getCookie(url.toString()) }.getOrNull()
        val clearance = sharedCookies.orEmpty()
            .split(';').map(String::trim).firstOrNull { it.startsWith("cf_clearance=") }
            ?.let { Cookie.parse(url, "$it; Secure; Path=/") }
        return if (clearance == null) current else current + clearance
    }
}
