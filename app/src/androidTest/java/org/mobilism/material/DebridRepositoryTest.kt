package org.mobilism.material

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.*

@RunWith(AndroidJUnit4::class)
class DebridRepositoryTest {
    @Test fun bothProvidersRefreshWithoutContactingMobilismAndKeepStaleHostsInactive() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val requestedHosts = mutableListOf<String>()
        var failHostRefresh = false
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requestedHosts += request.url.host
            val action = request.url.queryParameter("action")
            val body = when {
                request.url.host == "forum.mobilism.org" -> error("Bundled host list must not contact the forum")
                request.url.encodedPath.endsWith("/user") -> """{"id":1,"type":"premium","premium":3600}"""
                request.url.encodedPath.endsWith("/hosts/domains") -> """["send.now","uploadrar.com","unrelated.example.org"]"""
                request.url.encodedPath.endsWith("/hosts/status") -> if (failHostRefresh) """{"error_code":5}""" else
                    """{"uploadrar.com":{"supported":1,"status":"down","name":"Uploadrar"}}"""
                action == "getUserHistory" -> """{"response_code":"ok","history":[]}"""
                action == "getHostersList" -> if (failHostRefresh) """{"response_code":"SERVER_ERROR"}""" else
                    """{"response_code":"ok","hosters":[{"name":"Send.now","domains":["send.now"],"status":"up"},{"name":"Uploadrar","domains":["uploadrar.com"],"status":"down"}]}"""
                else -> error("Unexpected provider request")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build()
        val repository = DebridRepository(context, client)
        try {
            assertTrue(repository.fileHosts().any { it.domain == "send.now" })
            assertTrue(requestedHosts.isEmpty())
            for (provider in DebridProvider.entries) repository.saveKey(provider, "test-only-token")
            assertEquals(2, repository.state.value.supporting("https://send.now/file").size)
            assertTrue(repository.state.value.supporting("https://uploadrar.com/file").isEmpty())
            assertTrue(repository.state.value.supporting("https://unrelated.example.org/file").isEmpty())
            failHostRefresh = true
            for (provider in DebridProvider.entries) repository.refresh(provider)
            assertTrue(repository.state.value.accounts.all { it.validated && !it.hostsVerified && it.error != null })
            assertTrue(repository.state.value.supporting("https://send.now/file").isEmpty())
            failHostRefresh = false
            for (provider in DebridProvider.entries) repository.refresh(provider)
            assertEquals(2, repository.state.value.supporting("https://send.now/file").size)
            assertTrue(repository.state.value.accounts.all { it.error == null && !it.busy })
            assertEquals(setOf("api.real-debrid.com", "www.mega-debrid.eu"), requestedHosts.toSet())
        } finally {
            for (provider in DebridProvider.entries) repository.removeKey(provider)
        }
    }
}
