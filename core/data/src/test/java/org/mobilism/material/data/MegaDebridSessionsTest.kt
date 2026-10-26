package org.mobilism.material.data

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MegaDebridSessionsTest {
    private class Fixture {
        var saved: String? = null
        var signIns = 0
        var renewFails = false
        var rejectPassword = false
        var rejectNewToken = false
        var hostFails = false
        val events = mutableListOf<DiagnosticEvent>()
        val tokens = mutableListOf<String?>()
        val api = DebridApi(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = when (request.url.queryParameter("action")) {
                "connectUser" -> {
                    signIns++
                    if (renewFails) throw IOException("private-password")
                    assertEquals("sample", request.url.queryParameter("login"))
                    assertEquals("private-password", request.url.queryParameter("password"))
                    if (rejectPassword) """{"response_code":"UNKNOWN_USER"}"""
                    else """{"response_code":"ok","token":"token-$signIns","vip_end":${System.currentTimeMillis() / 1000 + 3600}}"""
                }
                "getUserHistory", "getLink" -> {
                    val token = request.url.queryParameter("token")
                    tokens += token
                    when {
                        hostFails -> """{"response_code":"HOST_UNAVAILABLE"}"""
                        token == "token-1" || rejectNewToken -> """{"response_code":"TOKEN_ERROR"}"""
                        request.url.queryParameter("action") == "getLink" -> """{"response_code":"ok","debridLink":"https://example.org/direct"}"""
                        else -> """{"response_code":"ok","history":[]}"""
                    }
                }
                else -> error("Unexpected request")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build())
        fun sessions() = MegaDebridSessions(api, { saved }, { saved = it }, { Long.MAX_VALUE }, DiagnosticLogger { events += it })
        fun connect() = sessions().connect("sample", "private-password")
    }

    @Test fun restoresCredentialsRenewsExpiredTokenAndRetriesValidationOnce() {
        val fixture = Fixture()
        fixture.connect()
        fixture.sessions().use { fixture.api.validate(DebridProvider.MegaDebrid, it) }
        assertEquals(2, fixture.signIns)
        assertEquals(listOf("token-1", "token-2"), fixture.tokens)
        assertEquals("token-2", JSONObject(fixture.saved!!).getString("token"))
        assertTrue(fixture.events.any { it.phase == DiagnosticPhase.Completed })
        assertFalse(fixture.events.toString().contains("private"))
    }

    @Test fun retriesDownloadAfterRenewalAndRefreshesExpiredPremiumProof() {
        val fixture = Fixture()
        fixture.connect()
        fixture.saved = JSONObject(fixture.saved!!).put("premiumUntil", 1).toString()
        val url = fixture.sessions().use { fixture.api.resolve(DebridProvider.MegaDebrid, it, "https://send.now/file") }
        assertEquals("https://example.org/direct", url)
        assertEquals(2, fixture.signIns)
        assertEquals(listOf("token-2"), fixture.tokens)
    }

    @Test fun expiredDownloadTokenRenewsOnceAndReturnsDirectLink() {
        val fixture = Fixture()
        fixture.connect()
        assertEquals("https://example.org/direct", fixture.sessions().use {
            fixture.api.resolve(DebridProvider.MegaDebrid, it, "https://send.now/file")
        })
        assertEquals(listOf("token-1", "token-2"), fixture.tokens)
    }

    @Test fun repeatedExpiryStopsAfterOneRenewal() {
        val fixture = Fixture()
        fixture.connect()
        fixture.rejectNewToken = true
        assertThrows(DebridException::class.java) {
            fixture.sessions().use { fixture.api.validate(DebridProvider.MegaDebrid, it) }
        }
        assertEquals(2, fixture.signIns)
        assertEquals(2, fixture.tokens.size)
    }

    @Test fun transientRenewalFailureKeepsSavedCredentialsForNextAttempt() {
        val fixture = Fixture()
        fixture.connect()
        val saved = fixture.saved
        fixture.renewFails = true
        val error = assertThrows(DebridException::class.java) {
            fixture.sessions().use { fixture.api.validate(DebridProvider.MegaDebrid, it) }
        }
        assertFalse(error.invalidatesAccount)
        assertEquals(saved, fixture.saved)
        fixture.renewFails = false
        fixture.sessions().use { fixture.api.validate(DebridProvider.MegaDebrid, it) }
        assertEquals("token-3", JSONObject(fixture.saved!!).getString("token"))
    }

    @Test fun hostErrorsDoNotRenewAndLegacyTokensRequestOneSignIn() {
        val fixture = Fixture()
        fixture.connect()
        fixture.hostFails = true
        assertThrows(DebridException::class.java) {
            fixture.sessions().use { fixture.api.resolve(DebridProvider.MegaDebrid, it, "https://send.now/file") }
        }
        assertEquals(1, fixture.signIns)
        fixture.hostFails = false
        fixture.saved = "token-1"
        val error = assertThrows(DebridException::class.java) {
            fixture.sessions().use { fixture.api.validate(DebridProvider.MegaDebrid, it) }
        }
        assertTrue(error.message.orEmpty().contains("once"))
        assertEquals("token-1", fixture.saved)
        assertEquals(1, fixture.signIns)
    }

    @Test fun rejectedCredentialsStopAutomaticLoginsAcrossRestartsUntilUserSignsIn() {
        val fixture = Fixture()
        fixture.connect()
        fixture.rejectPassword = true
        repeat(3) {
            assertThrows(DebridException::class.java) {
                fixture.sessions().use { fixture.api.validate(DebridProvider.MegaDebrid, it) }
            }
        }
        assertEquals(2, fixture.signIns)
        fixture.rejectPassword = false
        fixture.connect()
        fixture.sessions().use { fixture.api.validate(DebridProvider.MegaDebrid, it) }
        assertEquals(3, fixture.signIns)
    }
}
