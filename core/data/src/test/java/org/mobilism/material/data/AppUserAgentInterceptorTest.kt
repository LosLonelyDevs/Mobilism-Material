package org.mobilism.material.data

import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class AppUserAgentInterceptorTest {
    private val shipped = AppUserAgent.format("Browser", "0.1.23", 24)

    @Test fun statusRulesChangeOnlySubsequentMobilismRequestsAndFallBackWhenRemoved() {
        var rules = listOf(UserAgentRule("image", "Image rule", true, false, setOf("0.1.23"), "Allowed", setOf(403)))
        val policy = UserAgentPolicy("0.1.23") { rules }
        val interceptor = AppUserAgentInterceptor(shipped,
            { policy.prefix()?.let { AppUserAgent.format(it, "0.1.23", 24) } ?: shipped }, { policy.response(it) })
        val first = Chain("https://images.mobilism.org/?dm=UP6E", 403)
        interceptor.intercept(first).close()
        assertEquals(shipped, first.sent.single().header("User-Agent"))
        assertEquals(1, first.sent.size)
        val next = Chain("https://forum.mobilism.org/filehosts.xml")
        interceptor.intercept(next).close()
        assertEquals("Allowed MobilismMaterial/0.1.23 (Android; build 24)", next.sent.single().header("User-Agent"))
        rules = emptyList()
        val restored = Chain("https://images.mobilism.org/?dm=UP6E")
        interceptor.intercept(restored).close()
        assertEquals(shipped, restored.sent.single().header("User-Agent"))
    }

    @Test fun remoteOverridesDoNotReachOtherHostsOrActivateOnTheirFailures() {
        var selected = 0
        val observed = mutableListOf<Int>()
        val interceptor = AppUserAgentInterceptor(shipped, { selected++; "Configured" }, observed::add)
        listOf("https://example.org/image.png", "https://mobilism.org.evil.example/image.png",
            "http://images.mobilism.org/image.png", "https://mblservices.org/image.png").forEach { url ->
            val chain = Chain(url, 403, previousUserAgent = "Configured")
            interceptor.intercept(chain).close()
            assertEquals(shipped, chain.sent.single().header("User-Agent"))
            assertNull(chain.sent.single().header("Authorization"))
            assertNull(chain.sent.single().header("Cookie"))
        }
        assertEquals(0, selected)
        assertTrue(observed.isEmpty())
    }

    private class Chain(url: String, private val status: Int = 200, previousUserAgent: String = "okhttp/4.12.0") : Interceptor.Chain {
        private val original = Request.Builder().url(url).header("User-Agent", previousUserAgent).build()
        val sent = mutableListOf<Request>()
        override fun request() = original
        override fun proceed(request: Request): Response {
            sent += request
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status)
                .message("fixture").body(ByteArray(0).toResponseBody()).build()
        }
        override fun call(): Call = OkHttpClient().newCall(original)
        override fun connection(): Connection? = null
        override fun connectTimeoutMillis() = 0
        override fun readTimeoutMillis() = 0
        override fun writeTimeoutMillis() = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }
}
