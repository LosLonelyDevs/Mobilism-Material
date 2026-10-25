package org.mobilism.material.data

import java.io.IOException
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class MobilismPremiumApiTest {
    private val original = "https://files.example.org/file?id=123&name=sample"
    private val direct = "https://downloads.example.org/sample.epub"
    private val catalog = """<supported-filehosts><host url="files.example.org"/><host url="alias.example.org"/></supported-filehosts>"""
    private val login = """<form name="transload"><input name="link"/><input name="premium_acc"/></form>"""

    private fun api(respond: (Request) -> Pair<Int, String>): MobilismPremiumApi =
        MobilismPremiumApi(OkHttpClient.Builder().addInterceptor { chain ->
            val (status, body) = respond(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Test")
                .header("Location", "https://other.example.org/").body(body.toResponseBody()).build()
        }.build())

    private fun FormBody.fields() = (0 until size).associate { name(it) to value(it) }

    @Test fun authenticatesWithEncodedPostCredentialsOnlyAtFixedOrigin() {
        val service = api { request ->
            assertEquals("mblservices.org", request.url.host)
            assertEquals("https", request.url.scheme)
            assertNull(request.url.query)
            assertEquals("sample+user", (request.body as FormBody).fields()["username"])
            assertEquals("test & password+?", request.body!!.let { it as FormBody }.fields()["password"])
            200 to login
        }
        service.validate(service.credentials(" sample+user ", "test & password+?"))
    }

    @Test fun resolvesMultiStepFormsWithoutFollowingTheirActions() {
        var step = 0
        val service = api { request ->
            if (request.url.host == "forum.mobilism.org") {
                assertNull(request.body)
                assertNull(request.header("Authorization"))
                200 to catalog
            } else {
                assertEquals("mblservices.org", request.url.host)
                val fields = (request.body as FormBody).fields()
                assertEquals("test-password", fields["password"])
                if (step++ == 0) {
                    assertEquals(original, request.url.queryParameter("dl"))
                    assertEquals(original, fields["link"])
                    assertEquals("on", fields["premium_acc"])
                    200 to """<form action="https://other.example.org/"><input name="link" value="$direct"/>
                        <input name="host" value="downloads.example.org"/><input name="path" value="/sample.epub"/>
                        <input name="filename" value="sample.epub"/><input name="cookie" value="file-session"/></form>"""
                } else {
                    assertNull(request.url.query)
                    assertEquals(direct, fields["link"])
                    assertEquals("file-session", fields["cookie"])
                    200 to """<a href="$direct">Download</a>"""
                }
            }
        }
        assertEquals(direct, service.resolve(service.credentials("sample", "test-password"), original))
        assertEquals(2, step)
    }

    @Test fun checksCurrentHostListBeforeSendingCredentialsOrResolving() {
        var calls = 0
        val service = api { request ->
            calls++
            assertEquals("forum.mobilism.org", request.url.host)
            200 to catalog
        }
        val error = assertThrows(DebridException::class.java) {
            service.resolve(service.credentials("sample", "test-password"), "https://files.example.org.attacker.test/file")
        }
        assertTrue(error.message.orEmpty().contains("not supported"))
        assertEquals(1, calls)
    }

    @Test fun acceptsDownloadAnchorInsideLegacyErrorStyledContainer() {
        val service = api { request -> 200 to if (request.url.host == "forum.mobilism.org") catalog else
            """<span class="htmlerror"><b>Download: <a href="$direct" download="sample.epub">sample.epub</a></b></span>
                <a href=""></a><div id="app_output"><a href="$direct" download="sample.epub">sample.epub</a></div>"""
        }
        assertEquals(direct, service.resolve(service.credentials("sample", "test-password"), original))
    }

    @Test fun rejectsHostChallengesMalformedListsAndUnsafeCatalogEntries() {
        for (body in listOf("<html>Challenge</html>", "<supported-filehosts/>",
            """<supported-filehosts><host url="user:password@files.example.org"/><host url="files.example.org/path"/></supported-filehosts>""")) {
            assertThrows(DebridException::class.java) { api { 200 to body }.hosts() }
        }
        assertEquals(listOf("alias.example.org", "files.example.org"), api { 200 to catalog }.hosts().map { it.domain })
    }

    @Test fun rejectsLoginFormsAndExpiredSubscriptionsWithoutReflectingText() {
        for (body in listOf("<input type=password>test-password", "<b>Subscription expired test-password</b>")) {
            val service = api { 200 to body }
            val error = assertThrows(DebridException::class.java) { service.validate(service.credentials("sample", "test-password")) }
            assertTrue(error.invalidatesAccount)
            assertFalse(error.message.orEmpty().contains("test-password"))
        }
    }

    @Test fun rejectsRedirectsOversizedBodiesUnrecognizedPagesAndNetworkErrors() {
        for ((status, body) in listOf(302 to login, 403 to login, 200 to "x".repeat(512 * 1024 + 1), 200 to "<html>Challenge</html>")) {
            val service = api { status to body }
            assertThrows(DebridException::class.java) { service.validate(service.credentials("sample", "test-password")) }
        }
        val service = api { throw IOException("private test-password") }
        val error = assertThrows(DebridException::class.java) { service.validate(service.credentials("sample", "test-password")) }
        assertFalse(error.message.orEmpty().contains("test-password"))
    }

    @Test fun rejectsUnsafeAmbiguousAndCredentialBearingFinalLinks() {
        for (target in listOf("javascript:alert(1)", original, "https://user:pass@files.example.org/a",
            "https://download.example.org/test-password", "https://download.example.org/?password=hidden",
            "https://download.example.org/test%2Dpassword", "https://forum.mobilism.org/login")) {
            val service = api { request -> 200 to if (request.url.host == "forum.mobilism.org") catalog else """<a href="$target">Download</a>""" }
            assertThrows("Reject $target", DebridException::class.java) { service.resolve(service.credentials("sample", "test-password"), original) }
        }
    }

    @Test fun boundsRepeatingFormsAndKeepsAccountOnFileFailures() {
        var calls = 0
        val service = api { request ->
            calls++
            200 to if (request.url.host == "forum.mobilism.org") catalog else
                """<input name="link" value="$direct"/><input name="host" value="downloads.example.org"/><input name="path" value="/file"/>"""
        }
        assertThrows(DebridException::class.java) { service.resolve(service.credentials("sample", "test-password"), original) }
        assertEquals(3, calls)
        val deleted = api { request -> 200 to if (request.url.host == "forum.mobilism.org") catalog else "<span><b>File not found</b></span>" }
        val error = assertThrows(DebridException::class.java) { deleted.resolve(deleted.credentials("sample", "test-password"), original) }
        assertFalse(error.invalidatesAccount)
    }
}
