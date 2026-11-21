package org.mobilism.material.data

import okhttp3.Interceptor
import okhttp3.Response

class MobilismResponseInterceptor(
    private val onMobilismResponse: (Int) -> Unit,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        val mobilism = request.url.isHttps && (host == "mobilism.org" || host.endsWith(".mobilism.org"))
        val response = chain.proceed(request)
        if (mobilism) onMobilismResponse(response.code)
        return response
    }
}
