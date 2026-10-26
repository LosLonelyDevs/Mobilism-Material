package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
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
class ConnectionRecoveryTest {
    private class Fixture(val code: Int, val body: String, val challenge: Boolean = false) {
        var calls = 0
        val events = mutableListOf<DiagnosticEvent>()
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("network-test-$name", mode)
        }
        val repository = DefaultMobilismRepository(context, diagnostics = DiagnosticLogger { events += it },
            client = OkHttpClient.Builder().addInterceptor { chain ->
                calls++
                val request = chain.request()
                val login = request.url.encodedPath.endsWith("auth/login")
                if (request.method == "POST" && !request.url.encodedPath.endsWith("/search")) {
                    assertTrue(checkNotNull(request.body).isOneShot())
                }
                val success = calls > 1 && !challenge && code == 503
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(if (login || success) 200 else code).message("Fixture")
                    .apply { if (challenge) header("cf-mitigated", "challenge") }
                    .body((if (login) """{"access_token":"sample-session"}"""
                        else if (success) """{"response":[]}""" else body).toResponseBody()).build()
            }.build())
    }

    @Test fun retriesReadOnceOnServerOutageAndRecordsRecovery() = runBlocking {
        val fixture = Fixture(503, "<html>Unavailable</html>")
        assertTrue(fixture.repository.comments(42, 1).items.isEmpty())
        assertEquals(2, fixture.calls)
        assertTrue(fixture.events.any { it.phase == DiagnosticPhase.Retrying && it.httpStatus == 503 })
        assertTrue(fixture.events.any { it.phase == DiagnosticPhase.Completed && it.httpStatus == 200 })
    }

    @Test fun cloudflareChallengeDoesNotRetryAndLogsTheActualCause() = runBlocking {
        val fixture = Fixture(403, "<html>private-challenge-body</html>", challenge = true)
        val failure = runCatching { fixture.repository.comments(42, 1) }.exceptionOrNull() as ApiFailure
        assertEquals(FailureKind.Challenge, failure.kind)
        assertEquals(1, fixture.calls)
        assertTrue(fixture.events.any { it.failure == DiagnosticFailure.Challenge && it.httpStatus == 403 })
        assertFalse(fixture.events.toString().contains("private"))
    }

    @Test fun failedCommentWriteIsNotRetried() = runBlocking {
        val fixture = Fixture(503, "<html>Unavailable</html>")
        fixture.repository.signIn("sample-user", "sample-password")
        fixture.calls = 0
        try {
            assertTrue(runCatching { fixture.repository.postComment(42, "private-comment") }.exceptionOrNull() is ApiFailure)
            assertEquals(1, fixture.calls)
            assertFalse(fixture.events.any { it.phase == DiagnosticPhase.Retrying })
            assertFalse(fixture.events.toString().contains("private-comment"))
        } finally { fixture.repository.signOut() }
    }
}
