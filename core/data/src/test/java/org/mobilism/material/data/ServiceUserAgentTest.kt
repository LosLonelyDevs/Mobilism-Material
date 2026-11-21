package org.mobilism.material.data

import kotlinx.coroutines.runBlocking
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ServiceUserAgentTest {
    @Test fun sharedTransportSendsOneCurrentIdentityOnEveryRedirectHop() {
        val first = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val second = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val seen = java.util.concurrent.CopyOnWriteArrayList<List<String>>()
        try {
            first.createContext("/") { exchange ->
                seen.add(exchange.requestHeaders["User-Agent"].orEmpty())
                exchange.responseHeaders.add("Location", "http://127.0.0.1:${second.address.port}/")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            second.createContext("/") { exchange ->
                seen.add(exchange.requestHeaders["User-Agent"].orEmpty())
                exchange.sendResponseHeaders(204, -1)
                exchange.close()
            }
            first.start()
            second.start()
            var prefix = "First"
            val client = AppHttp.builder(OkHttpClient()) { AppUserAgent.format(prefix, "1.2.3", 9) }.build()
            val request = Request.Builder().url("http://127.0.0.1:${first.address.port}/").build()
            for (value in listOf("First", "Second")) {
                prefix = value
                client.newCall(request).execute().use { assertEquals(204, it.code) }
                val identity = listOf("$value MobilismMaterial/1.2.3 (Android; build 9)")
                assertEquals(listOf(identity, identity), seen.toList())
                seen.clear()
            }
        } finally {
            first.stop(0)
            second.stop(0)
        }
    }

    @Test fun updateAndConfigRequestsUseTheSuppliedBuildIdentity() = runBlocking {
        val requests = mutableListOf<Request>()
        val identity = AppUserAgent.format(null, "3.4.5", 678)
        val client = AppHttp.builder(OkHttpClient()) { identity }.addInterceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(503).message("fixture")
                .body("{}".toResponseBody()).build()
        }.build()
        runCatching { AppReleaseUpdates(client).latest() }
        runCatching { RemoteConfigClient("https://config.example/api/usage", client).fetch() }
        assertEquals(2, requests.size)
        requests.forEach {
            assertEquals("MobilismMaterial/3.4.5 (Android; build 678)", it.header("User-Agent"))
            assertNull(it.header("Authorization"))
            assertNull(it.header("Cookie"))
        }
    }
}
