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
                action == "connectUser" -> """{"response_code":"ok","token":"test-only-token","vip_end":${System.currentTimeMillis() / 1000 + 3600}}"""
                action == "getHostersList" -> if (failHostRefresh) """{"response_code":"SERVER_ERROR"}""" else
                    """{"response_code":"ok","hosters":[{"name":"Send.now","domains":["send.now"],"status":"up"},{"name":"Uploadrar","domains":["uploadrar.com"],"status":"down"}]}"""
                else -> error("Unexpected provider request")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build()
        var enabled = false
        val repository = DebridRepository(context, client, isEnabled = { enabled })
        try {
            assertTrue(DebridRepository(context, client).state.value.enabled)
            val disabled = DebridRepository(context, client, isEnabled = { false })
            disabled.load()
            disabled.refresh(DebridProvider.RealDebrid)
            assertTrue(runCatching { disabled.saveKey(DebridProvider.RealDebrid, "test-only-token") }.exceptionOrNull() is DebridException)
            assertTrue(requestedHosts.isEmpty())
            enabled = true
            assertTrue(repository.fileHosts().any { it.domain == "send.now" })
            assertTrue(requestedHosts.isEmpty())
            assertTrue(runCatching { repository.saveKey(DebridProvider.MegaDebrid, "application-key") }.exceptionOrNull() is DebridException)
            repository.saveKey(DebridProvider.RealDebrid, "test-only-token")
            repository.connectMegaDebrid("sample", "test-password")
            assertEquals(2, repository.state.value.supporting("https://send.now/file").size)
            assertTrue(repository.state.value.supporting("https://uploadrar.com/file").isEmpty())
            assertTrue(repository.state.value.supporting("https://unrelated.example.org/file").isEmpty())
            failHostRefresh = true
            for (provider in DebridProvider.entries) repository.refresh(provider)
            assertTrue(repository.state.value.accounts.filter { it.configured }.all { it.validated && !it.hostsVerified && it.error != null })
            assertTrue(repository.state.value.supporting("https://send.now/file").isEmpty())
            failHostRefresh = false
            for (provider in DebridProvider.entries) repository.refresh(provider)
            assertEquals(2, repository.state.value.supporting("https://send.now/file").size)
            assertTrue(repository.state.value.accounts.all { it.error == null && !it.busy })
            assertEquals(setOf("api.real-debrid.com", "www.mega-debrid.eu"), requestedHosts.toSet())
            enabled = false
            repository.deactivate()
            val before = requestedHosts.size
            repository.load()
            repository.refresh(DebridProvider.RealDebrid)
            assertTrue(runCatching { repository.resolve("https://send.now/file") }.exceptionOrNull() is DebridException)
            assertTrue(runCatching { repository.saveKey(DebridProvider.RealDebrid, "replacement-token") }.exceptionOrNull() is DebridException)
            assertEquals(before, requestedHosts.size)
            assertFalse(repository.state.value.enabled)
            assertTrue(repository.state.value.supporting("https://send.now/file").isEmpty())
            enabled = true
            repository.load()
            assertEquals(setOf(DebridProvider.RealDebrid, DebridProvider.MegaDebrid),
                repository.state.value.accounts.filter { it.configured }.map { it.provider }.toSet())
            assertEquals(2, repository.state.value.supporting("https://send.now/file").size)
            context.getSharedPreferences("material-debrid-hosts", 0).edit().remove("mega-premium-until").commit()
            val beforeLegacyRefresh = requestedHosts.size
            repository.refresh(DebridProvider.MegaDebrid)
            val legacy = repository.state.value.accounts.single { it.provider == DebridProvider.MegaDebrid }
            assertTrue(legacy.configured)
            assertFalse(legacy.validated)
            assertFalse(legacy.hostsVerified)
            assertTrue(legacy.error.orEmpty().contains("sign in"))
            assertEquals(beforeLegacyRefresh, requestedHosts.size)
        } finally {
            for (provider in DebridProvider.entries) repository.removeKey(provider)
        }
    }
}
