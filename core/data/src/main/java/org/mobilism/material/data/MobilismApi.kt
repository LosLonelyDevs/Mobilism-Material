package org.mobilism.material.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject

internal class MobilismApi(private val context: Context, diagnostics: DiagnosticLogger = DiagnosticLogger.None) {
    private val client = OkHttpClient.Builder()
        .addInterceptor(DiagnosticInterceptor(diagnostics))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun signIn(username: String, password: String): Session {
        val result = request("auth/login", form = mapOf("username" to username, "password" to password))
        val token = result.optString("access_token")
        if (token.isBlank() || token == "null") throw ApiFailure(
            FailureKind.InvalidResponse, "The server did not return a login session. Please try again.",
        )
        return Session(username, token, result.optString("refresh_token").takeUnless { it.isBlank() || it == "null" })
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
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) throw ApiFailure(FailureKind.Server, "The forum could not load this post. Try again later.")
                val source = response.body?.source()
                    ?: throw ApiFailure(FailureKind.InvalidResponse, "The forum returned an empty post.")
                source.request(MAX_RESPONSE_BYTES + 1)
                if (source.buffer.size > MAX_RESPONSE_BYTES) throw ApiFailure(FailureKind.InvalidResponse, "The forum response was too large.")
                ReleaseLikes.postId(source.readUtf8())
                    ?: throw ApiFailure(FailureKind.InvalidResponse, "The forum did not return this post's thumbs-up action.")
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

    suspend fun search(query: String, scope: SearchScope, page: Int, token: String?, hideAdult: Boolean = false): ReleasePage {
        require(query.isNotBlank() && page > 0)
        return SearchJson.page(request("search", token, form = mapOf(
            "search" to query.trim(), "count" to page.toString(), "limits" to PAGE_SIZE.toString(),
            "isrelease" to "1", "isdiscussion" to "0", "isother" to "0", "id" to scope.apiId,
        ), hideAdult = hideAdult))
    }

    private suspend fun request(path: String, token: String? = null, form: Map<String, String>? = null,
        emptyRecordsAsArray: Boolean = false, hideAdult: Boolean = false): JSONObject =
        withContext(Dispatchers.IO) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
                throw ApiFailure(FailureKind.Permission, "Network access is disabled for Mobilism. Enable Network in app permissions.")
            }
            val url = BASE_URL.resolve(path)!!.newBuilder().apply {
                if (hideAdult) addQueryParameter("no_adult", "1")
            }.build()
            val builder = Request.Builder().url(url).header("Accept", "application/json")
            // This client is used only for the API origin, never images or download URLs.
            token?.let { builder.header("X-Auth-Token", it) }
            form?.let { fields ->
                builder.post(FormBody.Builder().apply { fields.forEach { (name, value) -> add(name, value) } }.build())
            }
            try {
                client.newCall(builder.build()).await().use { response ->
                    val body = response.body ?: throw ApiFailure(FailureKind.InvalidResponse, "The server returned an empty response.")
                    val source = body.source()
                    source.request(MAX_RESPONSE_BYTES + 1)
                    if (source.buffer.size > MAX_RESPONSE_BYTES) throw ApiFailure(
                        FailureKind.InvalidResponse, "The server response was too large.",
                    )
                    val text = source.readUtf8()
                    if (response.header("cf-mitigated") == "challenge" || text.trimStart().startsWith('<')) {
                        throw ApiFailure(FailureKind.Challenge, "The server returned a web security page instead of app data. Try again later or use the forum in your browser.")
                    }
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
                            else -> "Mobilism could not complete the request (HTTP ${response.code})."
                        }
                        val message = json.optJSONObject("error")?.optString("text")
                            ?.takeIf { it.isNotBlank() && !it.contains('<') }?.take(240) ?: fallback
                        throw ApiFailure(kind, message)
                    }
                    json
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: UnknownHostException) {
                throw ApiFailure(FailureKind.Dns, "Mobilism's server address could not be resolved. Check Network permission, DNS, and your connection.")
            } catch (_: SocketTimeoutException) {
                throw ApiFailure(FailureKind.Connection, "The server took too long to respond. Try again.")
            } catch (_: SecurityException) {
                throw ApiFailure(FailureKind.Permission, "Network access is blocked. Check Mobilism's app permissions.")
            } catch (_: IOException) {
                throw ApiFailure(FailureKind.Connection, "Could not connect securely to Mobilism. Check your connection and try again.")
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
