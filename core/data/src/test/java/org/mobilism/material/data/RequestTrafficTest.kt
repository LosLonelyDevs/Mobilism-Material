package org.mobilism.material.data

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.file.Files
import okhttp3.Cache
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class RequestTrafficTest {
    private val loopback = object : Dns {
        override fun lookup(hostname: String) = listOf(InetAddress.getLoopbackAddress())
    }
    @Test fun endpointGroupsDiscardPrivatePathsAndQueries() {
        fun endpoint(path: String) = RequestTrafficProtocol.endpoint(Request.Builder().url("https://app.mobilism.org/api-new/$path").build())
        assertEquals("api/search", endpoint("search?token=secret&query=private"))
        assertEquals("api/releases", endpoint("release/apps/category/0/page/1/limit/20/orderby/date/desc/0"))
        assertEquals("api/release", endpoint("release/123456"))
        assertEquals("api/comments", endpoint("release/123456/comments/page/1/limit/20"))
        assertEquals("api/comments", endpoint("release/comment"))
        assertEquals("api/categories", endpoint("discussion/categories"))
        assertEquals("api/messages", endpoint("message/inbox/9876/page/1"))
        assertEquals("api/login", endpoint("auth/login"))
        assertEquals("forum/topic", RequestTrafficProtocol.endpoint(Request.Builder().url("https://forum.mobilism.org/viewtopic.php?t=123").build()))
        assertEquals("images", RequestTrafficProtocol.endpoint(Request.Builder().url("https://images.mobilism.org/private.jpg").build(), true))
        for (url in listOf("https://mobilism.org.evil.test/api-new/search", "https://mobilism-material-usage.vercel.app/api/ping", "https://external.test/image.jpg")) {
            assertNull(RequestTrafficProtocol.endpoint(Request.Builder().url(url).build(), true))
        }
        assertEquals(listOf(0, 1, 2, 3, 4, 5), listOf(200, 302, 403, 429, 503, null).map(RequestTrafficProtocol::outcome))
    }

    @Test fun redirectsCountEachWireAttemptAndCacheHitsDoNotCount() {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        val directory = Files.createTempDirectory("request-traffic-cache").toFile()
        val events = mutableListOf<RequestTraffic>()
        server.createContext("/api-new/search") { exchange ->
            exchange.responseHeaders.add("Location", "/api-new/release/123")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/api-new/release/123") { exchange ->
            exchange.responseHeaders.add("Cache-Control", "public, max-age=3600")
            exchange.sendResponseHeaders(200, 2)
            exchange.responseBody.use { it.write("ok".toByteArray()) }
        }
        server.start()
        val cache = Cache(directory, 1024 * 1024)
        val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).dns(loopback)
            .cache(cache).addNetworkInterceptor(RequestTrafficInterceptor({ events.add(it) })).build()
        val base = "http://app.mobilism.org:${server.address.port}/api-new/"
        try {
            client.newCall(Request.Builder().url(base + "search").build()).execute().use { assertEquals("ok", it.body!!.string()) }
            assertEquals(listOf("api/search", "api/release"), events.map { it.endpoint })
            assertEquals(listOf(1, 0), events.map { it.outcome })
            client.newCall(Request.Builder().url(base + "release/123").build()).execute().use {
                assertNotNull(it.cacheResponse)
                assertEquals("ok", it.body!!.string())
            }
            assertEquals(2, events.size)
            val brokenSink = client.newBuilder().cache(null).addNetworkInterceptor(RequestTrafficInterceptor({ error("disk full") })).build()
            brokenSink.newCall(Request.Builder().url(base + "release/123").build()).execute().use { assertEquals(200, it.code) }
            assertTrue(events.all { it.durationMs >= 0 })
        } finally {
            server.stop(0)
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
            cache.close()
            directory.deleteRecursively()
        }
    }

    @Test fun failedHttpAttemptCountsOnceWithoutClientRetry() {
        val events = mutableListOf<RequestTraffic>()
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { it.close() }
        server.start()
        val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).dns(loopback)
            .retryOnConnectionFailure(false).addNetworkInterceptor(RequestTrafficInterceptor({ events.add(it) })).build()
        try {
            assertTrue(runCatching { client.newCall(Request.Builder().url("http://app.mobilism.org:${server.address.port}/api-new/search").build()).execute().close() }.isFailure)
            assertEquals(listOf(5), events.map { it.outcome })
        } finally {
            server.stop(0)
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }
}
