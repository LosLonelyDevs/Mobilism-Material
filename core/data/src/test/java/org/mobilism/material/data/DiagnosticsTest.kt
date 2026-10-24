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

class DiagnosticsTest {
    @Test fun recordsOnlyTypedMetadataEvenWithSecretsInEveryRequestField() {
        val events = mutableListOf<DiagnosticEvent>()
        val client = OkHttpClient.Builder().addInterceptor(DiagnosticInterceptor { events += it })
            .addInterceptor { chain -> Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("private-response").body("private-account-data".toResponseBody()).build() }.build()
        val request = Request.Builder().url("https://www.mega-debrid.eu/api.php?action=getLink&token=private-token")
            .header("Authorization", "Bearer private-header")
            .post(FormBody.Builder().add("link", "https://host.example/private-download")
                .add("password", "private-password").build()).build()
        client.newCall(request).execute().close()
        assertEquals(2, events.size)
        assertEquals(DiagnosticOperation.Resolve, events.last().operation)
        assertEquals(DiagnosticSource.MegaDebrid, events.last().source)
        assertEquals(200, events.last().httpStatus)
        assertEquals(events.first().requestId, events.last().requestId)
        assertFalse(events.toString().contains("private"))
        assertFalse(events.toString().contains("https"))
    }

    @Test fun neverIncludesExceptionMessagesInLogs() {
        val events = mutableListOf<DiagnosticEvent>()
        val client = OkHttpClient.Builder().addInterceptor(DiagnosticInterceptor { events += it })
            .addInterceptor { throw IOException("private-token and private-download-url") }.build()
        assertThrows(IOException::class.java) {
            client.newCall(Request.Builder().url("https://example.org/auth/login").build()).execute()
        }
        assertEquals(DiagnosticPhase.Failed, events.last().phase)
        assertEquals(DiagnosticFailure.Network, events.last().failure)
        assertFalse(events.toString().contains("private"))
    }
}
