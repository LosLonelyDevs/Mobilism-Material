package org.mobilism.material.data

import okhttp3.Interceptor
import okhttp3.Response

class AppUserAgentInterceptor(
    private val defaultUserAgent: String,
    private val mobilismUserAgent: () -> String,
    private val onMobilismResponse: (Int) -> Unit,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        val mobilism = request.url.isHttps && (host == "mobilism.org" || host.endsWith(".mobilism.org"))
        // Recheck each redirect so a remote override cannot follow a request to another host.
        val userAgent = if (mobilism) mobilismUserAgent() else defaultUserAgent
        val response = chain.proceed(request.newBuilder().header("User-Agent", userAgent).build())
        if (mobilism) onMobilismResponse(response.code)
        return response
    }
}
