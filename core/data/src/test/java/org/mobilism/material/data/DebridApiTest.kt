package org.mobilism.material.data

import java.io.IOException
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class DebridApiTest {
    private val key = "unit-test-token"
    private val original = "https://files.example.org/file?id=123&name=test.apk"
    private val direct = "https://downloads.example.org/test.apk"

    private fun api(body: String, status: Int = 200, inspect: (Request) -> Unit = {}): DebridApi =
        DebridApi(OkHttpClient.Builder().addInterceptor { chain ->
            inspect(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(status).message("Test response")
                .header("Location", "https://other.example.org/")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build())

    @Test fun realDebridValidatesPremiumUsingBearerHeader() {
        api("""{"id":1,"type":"premium","premium":3600}""") { request ->
            assertEquals("https://api.real-debrid.com/rest/1.0/user", request.url.toString())
            assertEquals("Bearer $key", request.header("Authorization"))
            assertEquals("GET", request.method)
            assertNull(request.url.query)
        }.validate(DebridProvider.RealDebrid, key)
    }

    @Test fun realDebridRejectsFreeAndExpiredAccounts() {
        for (body in listOf("""{"id":1,"type":"free","premium":0}""", """{"id":1,"type":"premium","premium":0}""")) {
            val error = assertThrows(DebridException::class.java) { api(body).validate(DebridProvider.RealDebrid, key) }
            assertTrue(error.invalidatesAccount)
            assertTrue(error.message.orEmpty().contains("Premium"))
        }
    }

    @Test fun megaDebridChecksTokenWithBoundedHistory() {
        val token = "test+token=?"
        api("""{"response_code":"ok","history":[]}""") { request ->
            assertEquals("www.mega-debrid.eu", request.url.host)
            assertEquals("getUserHistory", request.url.queryParameter("action"))
            assertEquals(token, request.url.queryParameter("token"))
            assertEquals("1", request.url.queryParameter("limit"))
        }.validate(DebridProvider.MegaDebrid, token)
    }

    @Test fun realDebridResolvesFormLinkWithBearerAuthentication() {
        val service = api("""{"download":"$direct"}""") { request ->
            assertEquals("/rest/1.0/unrestrict/link", request.url.encodedPath)
            assertEquals("Bearer $key", request.header("Authorization"))
            assertEquals("POST", request.method)
            assertEquals(original, (request.body as FormBody).value(0))
        }
        assertEquals(direct, service.resolve(DebridProvider.RealDebrid, key, original))
    }

    @Test fun megaDebridResolvesWithEncodedTokenAndFormLink() {
        val service = api("""{"response_code":"ok","debridLink":"$direct"}""") { request ->
            assertEquals("getLink", request.url.queryParameter("action"))
            assertEquals(key, request.url.queryParameter("token"))
            assertEquals("POST", request.method)
            assertEquals(original, (request.body as FormBody).value(0))
        }
        assertEquals(direct, service.resolve(DebridProvider.MegaDebrid, key, original))
    }

    @Test fun rejectedKeysAndTokensInvalidateAccountsWithoutReflectingProviderText() {
        for ((provider, body) in listOf(
            DebridProvider.RealDebrid to """{"error":"$key private details","error_code":8}""",
            DebridProvider.MegaDebrid to """{"response_code":"token_error","response_text":"Token error $key private details"}""",
        )) {
            val error = assertThrows(DebridException::class.java) { api(body, 401).validate(provider, key) }
            assertTrue(error.invalidatesAccount)
            assertFalse(error.message.orEmpty().contains(key))
            assertFalse(error.message.orEmpty().contains("private details"))
        }
    }

    @Test fun hostAndQuotaFailuresKeepAccountValid() {
        for (code in listOf(16, 17, 18, 19, 21, 23)) {
            val error = assertThrows(DebridException::class.java) {
                api("""{"error":"test","error_code":$code}""", 503).resolve(DebridProvider.RealDebrid, key, original)
            }
            assertFalse(error.invalidatesAccount)
        }
    }

    @Test fun rejectsUnsafeMissingAndUnresolvedDownloadUrls() {
        for (value in listOf("", "javascript:alert(1)", "intent://example", "https://user:pass@example.org/a", original, "https://example.org/$key")) {
            assertThrows(DebridException::class.java) {
                api("""{"download":"$value"}""").resolve(DebridProvider.RealDebrid, key, original)
            }
        }
    }

    @Test fun rejectsRedirectHtmlAndOversizedResponses() {
        for ((status, body) in listOf(302 to "{}", 200 to "<html>Blocked</html>", 200 to "x".repeat(512 * 1024 + 1))) {
            assertThrows(DebridException::class.java) { api(body, status).validate(DebridProvider.RealDebrid, key) }
        }
    }

    @Test fun networkErrorsNeverExposeRequestSecrets() {
        val service = DebridApi(OkHttpClient.Builder().addInterceptor { throw IOException("token=$key $original") }.build())
        val error = assertThrows(DebridException::class.java) { service.validate(DebridProvider.MegaDebrid, key) }
        assertFalse(error.message.orEmpty().contains(key))
        assertFalse(error.message.orEmpty().contains(original))
    }
}
