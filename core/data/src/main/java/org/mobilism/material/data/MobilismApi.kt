package org.mobilism.material.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONException
import org.json.JSONObject

internal class MobilismApi(private val context: Context, private val diagnostics: DiagnosticLogger = DiagnosticLogger.None,
    client: OkHttpClient = OkHttpClient()) {
    private val client = client.newBuilder()
        .apply { interceptors().add(0, DiagnosticInterceptor(diagnostics)) }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false)
        .retryOnConnectionFailure(false)
        .build()
    // Connection recovery can resend a request. Keep writes on the non-retrying client.
    private val readClient = this.client.newBuilder().retryOnConnectionFailure(true).build()

    suspend fun signIn(username: String, password: String): Session {
        val result = request("auth/login", form = mapOf("username" to username, "password" to password))
        val token = result.optString("access_token")
        if (token.isBlank() || token == "null") throw ApiFailure(
            FailureKind.InvalidResponse, "The server did not return a login session. Please try again.",
        )
        return Session(username, token, result.optString("refresh_token").takeUnless { it.isBlank() || it == "null" },
            result.optLong("id").takeIf { it > 0 })
    }

    suspend fun categories(token: String?, hideAdult: Boolean = false): Map<ContentSection, List<ReleaseCategory>> {
        val response = request("release/category", token, hideAdult = hideAdult)
        return ContentSection.entries.associateWith { CategoryJson.parse(response, it) }
    }

    suspend fun releases(section: ContentSection, order: FeedOrder, page: Int, token: String?, categoryId: Long? = null, hideAdult: Boolean = false): ReleasePage {
        require(page > 0)
        require(categoryId == null || categoryId > 0)
        val category = categoryId?.toString() ?: "all"
        val path = "release/${section.apiPath}/category/$category/page/$page/limit/$PAGE_SIZE/orderby/${order.apiValue}/desc/0"
        val response = request(path, token, emptyRecordsAsArray = true, hideAdult = hideAdult).getJSONArray("response")
        return ReleaseJson.page(response).let { result ->
            result.copy(items = result.items.map { it.copy(section = it.section ?: section) })
        }
    }

    suspend fun register(username: String, email: String, password: String) {
        request("auth/register", form = mapOf(
            "username" to username.trim(), "email" to email.trim(),
            "password" to password, "repeatPassword" to password,
        ))
    }

    suspend fun restorePassword(email: String) {
        request("auth/restore", form = mapOf("email" to email.trim()))
    }

    suspend fun release(id: Long, token: String?): Release {
        require(id > 0)
        return ReleaseJson.parse(request("release/$id", token).getJSONObject("response"))
    }

    suspend fun toggleLike(release: Release, token: String): Boolean {
        require(release.id > 0)
        // Current release responses can omit like_id. The API expects the first post ID, not the topic ID.
        val postId = release.likeId ?: withContext(Dispatchers.IO) {
            val request = Request.Builder().url("https://forum.mobilism.org/viewtopic.php?t=${release.id}").build()
            readClient.newCall(request).await().use { response ->
                if (!response.isSuccessful) throw ApiFailure(FailureKind.Server, "The forum could not load this post. Try again later.")
                val source = response.body?.source()
                    ?: throw ApiFailure(FailureKind.InvalidResponse, "The forum returned an empty post.")
                source.request(MAX_RESPONSE_BYTES + 1)
                if (source.buffer.size > MAX_RESPONSE_BYTES) throw ApiFailure(FailureKind.InvalidResponse, "The forum response was too large.")
                ReleaseLikes.postId(source.readUtf8())
                    ?: throw ApiFailure(FailureKind.InvalidResponse, "The forum did not return the original post. Try again later.")
            }
        }
        val response = request("post/like", token, mapOf("id" to postId.toString())).getJSONObject("response")
        return ReleaseLikes.liked(response)
            ?: throw ApiFailure(FailureKind.InvalidResponse, "The server did not confirm the thumbs-up state.")
    }

    suspend fun comments(id: Long, page: Int, token: String?): CommentPage {
        require(id > 0 && page > 0)
        return CommentJson.page(request("release/$id/comments/page/$page/limit/$PAGE_SIZE/orderby/date/desc", token,
            emptyRecordsAsArray = true).getJSONArray("response"))
    }

    suspend fun postComment(id: Long, text: String, token: String, replyTo: Long? = null): Long {
        require(id > 0 && token.isNotBlank())
        require(text.isNotBlank() && text.length <= ReleaseComments.MAX_LENGTH)
        require(replyTo == null || replyTo > 0)
        return ReleaseComments.confirmedId(request("release/comment", token,
            form = buildMap {
                put("id", id.toString()); put("comment", text.trim())
                replyTo?.let { put("msg_id", it.toString()) }
            }))
    }

    suspend fun discussionCategories(area: DiscussionArea, token: String?): List<DiscussionCategory> =
        DiscussionJson.categories(request(if (area == DiscussionArea.Community) "other/category" else "discussion/categories", token), area)

    suspend fun discussionTopics(category: DiscussionCategory, page: Int, query: String, token: String?, includePinned: Boolean = true): DiscussionPage {
        require(page > 0)
        if (query.isNotBlank()) return DiscussionJson.page(request("search", token, form = mapOf(
            "search" to query.trim(), "count" to page.toString(), "limits" to PAGE_SIZE.toString(),
            "isrelease" to "0", "isdiscussion" to if (category.kind == DiscussionKind.Discussion) "1" else "0",
            "isother" to if (category.kind == DiscussionKind.Other) "1" else "0",
            "id" to if (category.id == 12L) "all" else category.id.toString(),
        )), category.kind, search = true)
        return DiscussionJson.page(request("${category.kind.path}/category/${category.apiId}/page/$page/limit/$PAGE_SIZE/orderby/topic_last_post_time/desc/${if (includePinned) 1 else 0}",
            token, emptyRecordsAsArray = true), category.kind)
    }

    suspend fun discussionComments(topic: DiscussionTopic, page: Int, token: String?): CommentPage {
        require(topic.id > 0 && page > 0)
        return CommentJson.page(request("${topic.kind.path}/${topic.id}/comments/page/$page/limit/$PAGE_SIZE/orderby/date/desc",
            token, emptyRecordsAsArray = true).getJSONArray("response"))
    }

    suspend fun postDiscussionComment(topic: DiscussionTopic, text: String, replyTo: Long?, token: String): Long {
        require(topic.id > 0 && token.isNotBlank() && text.isNotBlank() && text.length <= ReleaseComments.MAX_LENGTH)
        require(replyTo == null || replyTo > 0)
        return ReleaseComments.confirmedId(request("${topic.kind.path}/comment", token, form = buildMap {
            put("id", topic.id.toString()); put("comment", text.trim())
            replyTo?.let { put("msg_id", it.toString()) }
        }))
    }

    suspend fun messages(folder: MessageFolder, userId: Long, page: Int, token: String): MessagePage {
        require(userId > 0 && page > 0)
        val items = PrivateMessages.parse(request(
            "message/${folder.apiPath}/$userId/page/$page/limit/$PAGE_SIZE/orderby/times/desc", token,
            emptyRecordsAsArray = true))
        return MessagePage(items, items.isNotEmpty())
    }

    suspend fun conversation(id: Long, token: String): List<PrivateMessage> {
        require(id > 0)
        return PrivateMessages.parse(request("message/$id/orderby/times/asc", token))
    }

    suspend fun markMessageRead(id: Long, token: String) {
        require(id > 0)
        PrivateMessages.confirm(request("message/$id/read", token, retryRead = false))
    }

    suspend fun replyToMessage(message: PrivateMessage, text: String, account: Session) {
        val recipient = message.recipient(account.username)
        require(message.messageId > 0 && recipient.isNotBlank())
        require(text.isNotBlank() && text.length <= PrivateMessages.MAX_LENGTH)
        PrivateMessages.confirm(request("message/reply/send", account.accessToken, form = mapOf(
            "to" to recipient, "from" to account.username, "subject" to message.subject,
            "content" to text.trim(), "reply" to message.messageId.toString(),
        )))
    }

    suspend fun search(query: String, scope: SearchScope, page: Int, token: String?, hideAdult: Boolean = false): ReleasePage {
        require(query.isNotBlank() && page > 0)
        return SearchJson.page(request("search", token, form = mapOf(
            "search" to query.trim(), "count" to page.toString(), "limits" to PAGE_SIZE.toString(),
            "isrelease" to "1", "isdiscussion" to "0", "isother" to "0", "id" to scope.apiId,
        ), hideAdult = hideAdult))
    }

    private suspend fun request(path: String, token: String? = null, form: Map<String, String>? = null,
        emptyRecordsAsArray: Boolean = false, hideAdult: Boolean = false, retryRead: Boolean = true): JSONObject =
        withContext(Dispatchers.IO) {
            val operation = when {
                path.startsWith("auth/") -> DiagnosticOperation.Login
                path == "search" -> DiagnosticOperation.Search
                "comment" in path -> DiagnosticOperation.Comments
                path.startsWith("message/") -> DiagnosticOperation.Messages
                path.endsWith("category") -> DiagnosticOperation.Categories
                path.startsWith("post/") -> DiagnosticOperation.Reaction
                else -> DiagnosticOperation.Releases
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
                diagnostics.record(DiagnosticEvent(DiagnosticSource.Forum, operation, DiagnosticPhase.Failed,
                    failure = DiagnosticFailure.Permission))
                throw ApiFailure(FailureKind.Permission, "Network access is disabled for Mobilism. Enable Network in app permissions.")
            }
            val url = BASE_URL.resolve(path)!!.newBuilder().apply {
                if (hideAdult) addQueryParameter("no_adult", "1")
            }.build()
            val builder = Request.Builder().url(url).header("Accept", "application/json")
            // This client is used only for the API origin, never images or download URLs.
            token?.let { builder.header("X-Auth-Token", it) }
            form?.let { fields ->
                val body = FormBody.Builder().apply { fields.forEach { (name, value) -> add(name, value) } }.build()
                // Even HTTP follow-ups must not replay a comment or another account action.
                builder.post(if (path == "search") body else object : RequestBody() {
                    override fun contentType() = body.contentType()
                    override fun contentLength() = body.contentLength()
                    override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
                    override fun isOneShot() = true
                })
            }
            try {
                val readOnly = retryRead && (form == null || path == "search")
                val request = builder.build()
                val transport = if (readOnly) readClient else client
                var result = transport.newCall(request).await()
                if (readOnly && result.code in setOf(502, 503, 504, 520, 521, 522, 523, 524) &&
                    result.header("cf-mitigated") != "challenge") {
                    diagnostics.record(DiagnosticEvent(DiagnosticSource.Forum, operation, DiagnosticPhase.Retrying,
                        httpStatus = result.code, failure = DiagnosticFailure.Server))
                    result.close()
                    delay(500)
                    result = transport.newCall(request).await()
                }
                result.use { response ->
                    val body = response.body ?: throw ApiFailure(FailureKind.InvalidResponse, "The server returned an empty response.")
                    val source = body.source()
                    source.request(MAX_RESPONSE_BYTES + 1)
                    if (source.buffer.size > MAX_RESPONSE_BYTES) throw ApiFailure(
                        FailureKind.InvalidResponse, "The server response was too large.",
                    )
                    val text = source.readUtf8()
                    NetworkFailures.response(response.code, response.header("cf-mitigated") == "challenge", text)?.let { throw it }
                    val json = try { JSONObject(text) } catch (_: JSONException) {
                        throw ApiFailure(FailureKind.InvalidResponse, "The server returned an unreadable response.")
                    }
                    // Comments use HTTP 400 for an empty page, including the end of pagination.
                    if (emptyRecordsAsArray && response.code == 400 && json.optJSONObject("error")?.optString("text") == "No records found.") {
                        return@withContext JSONObject().put("response", org.json.JSONArray())
                    }
                    if (!response.isSuccessful || json.optString("status") == "error") {
                        val kind = when {
                            form != null && path == "auth/login" && response.code in listOf(400, 401, 403) -> FailureKind.Credentials
                            response.code == 401 && token != null -> FailureKind.Session
                            else -> FailureKind.Server
                        }
                        val fallback = when (kind) {
                            FailureKind.Credentials -> "Login was rejected. Check your username and password."
                            FailureKind.Session -> "Your session has expired. Sign in again."
                            else -> if (response.code == 429) "Mobilism is limiting requests. Wait a moment and try again."
                                else "Mobilism could not complete the request (HTTP ${response.code})."
                        }
                        val message = json.optJSONObject("error")?.optString("text")
                            ?.takeIf { it.isNotBlank() && !it.contains('<') }?.take(240) ?: fallback
                        throw ApiFailure(kind, message, response.code)
                    }
                    json
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ApiFailure) {
                diagnostics.record(DiagnosticEvent(DiagnosticSource.Forum, operation, DiagnosticPhase.Failed,
                    httpStatus = failure.httpStatus, failure = when (failure.kind) {
                        FailureKind.Challenge -> DiagnosticFailure.Challenge
                        FailureKind.Credentials -> DiagnosticFailure.Credentials
                        FailureKind.Session -> DiagnosticFailure.SessionExpired
                        FailureKind.InvalidResponse -> DiagnosticFailure.InvalidResponse
                        else -> DiagnosticFailure.Server
                    }))
                throw failure
            } catch (_: SecurityException) {
                diagnostics.record(DiagnosticEvent(DiagnosticSource.Forum, operation, DiagnosticPhase.Failed,
                    failure = DiagnosticFailure.Permission))
                throw ApiFailure(FailureKind.Permission, "Network access is blocked. Check Mobilism's app permissions.")
            } catch (failure: IOException) {
                diagnostics.record(DiagnosticEvent(DiagnosticSource.Forum, operation, DiagnosticPhase.Failed,
                    failure = NetworkFailures.category(failure)))
                throw NetworkFailures.forScreen(failure)
            }
        }

    private companion object {
        val BASE_URL = "https://app.mobilism.org/api-new/".toHttpUrl()
        const val PAGE_SIZE = 20
        const val MAX_RESPONSE_BYTES = 4L * 1024 * 1024
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
        }
    })
}
