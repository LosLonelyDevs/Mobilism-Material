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

internal class MobilismApi(private val context: Context) {
    private val client = OkHttpClient.Builder()
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

    suspend fun releases(section: ContentSection, order: FeedOrder, page: Int, token: String?): ReleasePage {
        require(page > 0)
        val path = "release/${section.apiPath}/category/all/page/$page/limit/$PAGE_SIZE/orderby/${order.apiValue}/desc/0"
        val response = request(path, token).getJSONArray("response")
        return ReleaseJson.page(response)
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

    private suspend fun request(path: String, token: String? = null, form: Map<String, String>? = null): JSONObject =
        withContext(Dispatchers.IO) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
                throw ApiFailure(FailureKind.Permission, "Network access is disabled for Mobilism. Enable Network in app permissions.")
            }
            val builder = Request.Builder().url(BASE_URL.resolve(path)!!).header("Accept", "application/json")
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
