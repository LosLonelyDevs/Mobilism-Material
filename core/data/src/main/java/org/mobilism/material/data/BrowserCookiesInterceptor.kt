package org.mobilism.material.data

import android.webkit.CookieManager
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/** Keep native responses and verification browsers on the same first-party cookie store. */
internal class BrowserCookiesInterceptor : Interceptor {
    private fun accepts(url: HttpUrl): Boolean = url.isHttps && url.host in setOf("app.mobilism.org", "forum.mobilism.org")

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!accepts(request.url)) return chain.proceed(request)
        val manager = runCatching { CookieManager.getInstance() }.getOrNull()
        val cookies = runCatching { manager?.getCookie(request.url.toString()) }.getOrNull()
        val outgoing = request.newBuilder().apply {
            removeHeader("Cookie")
            if (!cookies.isNullOrBlank()) header("Cookie", cookies)
        }.build()
        val response = chain.proceed(outgoing)
        if (manager != null && accepts(response.request.url)) {
            val updates = response.headers("Set-Cookie")
            if (updates.isNotEmpty()) runCatching {
                // The two-argument setter is synchronous. The next read must see rotations and deletions.
                updates.forEach { manager.setCookie(response.request.url.toString(), it) }
                manager.flush()
            }
        }
        return response
    }
}
