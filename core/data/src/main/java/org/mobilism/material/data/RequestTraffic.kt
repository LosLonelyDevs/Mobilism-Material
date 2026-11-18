package org.mobilism.material.data

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

data class RequestTraffic(val endpoint: String, val method: String, val outcome: Int, val durationMs: Long)

fun interface RequestTrafficSink {
    fun record(event: RequestTraffic)
    companion object { val None = RequestTrafficSink {} }
}

interface RequestTrafficProvider { val requestTraffic: RequestTrafficSink }

object RequestTrafficProtocol {
    val endpoints = setOf("api/login", "api/register", "api/restore", "api/categories", "api/releases",
        "api/release", "api/search", "api/comments", "api/messages", "api/reactions", "api/discussions",
        "api/other", "forum/topic", "forum/list", "forum/profile", "forum/search", "forum/other", "images")
    val methods = setOf("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "OTHER")
    const val MAX_REQUESTS = 1_000_000L
    const val MAX_DURATION_MS = 300_000L
    const val MAX_ROWS = 128
    const val MAX_DAYS = 8

    // Counts are ordered: success, redirect, client error, rate limit, server error, network error.
    fun outcome(status: Int?): Int = when {
        status == null -> 5
        status == 429 -> 3
        status >= 500 -> 4
        status >= 400 -> 2
        status >= 300 -> 1
        else -> 0
    }

    fun endpoint(request: Request, image: Boolean = false): String? {
        val url = request.url
        if (url.host != "mobilism.org" && !url.host.endsWith(".mobilism.org")) return null
        if (image) return "images"
        val path = url.encodedPath
        if (url.host == "app.mobilism.org" && path.startsWith("/api-new/")) {
            val parts = path.removePrefix("/api-new/").trim('/').split('/')
            return when {
                parts.take(2) == listOf("auth", "login") -> "api/login"
                parts.take(2) == listOf("auth", "register") -> "api/register"
                parts.take(2) == listOf("auth", "restore") -> "api/restore"
                parts.first() == "search" -> "api/search"
                parts.first() == "message" -> "api/messages"
                parts.first() == "post" -> "api/reactions"
                parts.last() in setOf("category", "categories") -> "api/categories"
                "comments" in parts || parts.last() == "comment" -> "api/comments"
                parts.first() == "release" && parts.getOrNull(1)?.toLongOrNull() != null -> "api/release"
                parts.first() == "release" -> "api/releases"
                parts.first() in setOf("discussion", "review", "other") -> "api/discussions"
                else -> "api/other"
            }
        }
        return when (path.substringAfterLast('/')) {
            "viewtopic.php" -> "forum/topic"
            "viewforum.php" -> "forum/list"
            "memberlist.php" -> "forum/profile"
            "search.php" -> "forum/search"
            else -> "forum/other"
        }
    }
}

/** Network interceptors run for each wire attempt, including redirects, but not cache hits. */
class RequestTrafficInterceptor(private val sink: RequestTrafficSink, private val image: Boolean = false) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val endpoint = RequestTrafficProtocol.endpoint(request, image) ?: return chain.proceed(request)
        val start = System.nanoTime()
        var status: Int? = null
        try {
            return chain.proceed(request).also { status = it.code }
        } finally {
            // Telemetry failure must not break an API request or image load.
            runCatching { sink.record(RequestTraffic(endpoint, request.method.takeIf { it in RequestTrafficProtocol.methods } ?: "OTHER",
                RequestTrafficProtocol.outcome(status), ((System.nanoTime() - start) / 1_000_000).coerceIn(0, RequestTrafficProtocol.MAX_DURATION_MS))) }
        }
    }
}
