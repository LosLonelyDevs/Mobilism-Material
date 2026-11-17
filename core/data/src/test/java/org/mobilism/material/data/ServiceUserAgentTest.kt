package org.mobilism.material.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ServiceUserAgentTest {
    @Test fun updateAndConfigRequestsUseTheSuppliedBuildIdentity() = runBlocking {
        val requests = mutableListOf<Request>()
        val identity = AppUserAgent.format(null, "3.4.5", 678)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(503).message("fixture")
                .body("{}".toResponseBody()).build()
        }.build()
        runCatching { AppReleaseUpdates(identity, client).latest() }
        runCatching { RemoteConfigClient("https://config.example/api/usage", identity, client).fetch() }
        assertEquals(2, requests.size)
        requests.forEach {
            assertEquals("MobilismMaterial/3.4.5 (Android; build 678)", it.header("User-Agent"))
            assertNull(it.header("Authorization"))
            assertNull(it.header("Cookie"))
        }
    }
}
