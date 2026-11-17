package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.*

@RunWith(AndroidJUnit4::class)
class ConnectionRecoveryTest {
    private class RecoveringFixture(val remainsBlocked: Boolean = false, val allowRecovery: Boolean = true,
        val blockLogin: Boolean = false) {
        val requests = mutableListOf<Request>()
        val sites = mutableListOf<VerificationSite>()
        private var verified = false
        private val base = InstrumentationRegistry.getInstrumentation().targetContext
        private val prefix = "automatic-recovery-${java.util.UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("$prefix-$name", mode)
        }
        val repository = DefaultMobilismRepository(context, challengeRecovery = ChallengeRecovery { site ->
            sites += site
            verified = true
            allowRecovery
        }, client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val login = request.url.encodedPath.endsWith("auth/login")
            val blocked = if (login) blockLogin else !verified || remainsBlocked
            val body = when {
                blocked -> "<html>challenge-platform</html>"
                login -> """{"access_token":"fixture-session"}"""
                request.url.encodedPath.endsWith("search") -> """{"releases":[]}"""
                else -> """{"response":[]}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).message("Fixture")
                .code(if (blocked) 403 else 200).apply { if (blocked) header("cf-mitigated", "challenge") }
                .body(body.toResponseBody()).build()
        }.build())
    }

    @Test fun successfulVerificationRetriesTheReadExactlyOnce() = runBlocking {
        val fixture = RecoveringFixture()
        assertTrue(fixture.repository.comments(42, 1).items.isEmpty())
        assertEquals(listOf(VerificationSite.App), fixture.sites)
        assertEquals(2, fixture.requests.size)
        assertEquals(fixture.requests.first().url, fixture.requests.last().url)
        assertTrue(fixture.requests.all { it.header("User-Agent")?.contains("MobilismMaterial/${BuildConfig.VERSION_NAME} (Android; build ${BuildConfig.VERSION_CODE})") == true })
    }

    @Test fun blockedSearchStopsWithoutAutomaticVerificationOrReplay() = runBlocking {
        val fixture = RecoveringFixture()
        assertTrue(runCatching { fixture.repository.search("fixture", SearchScope.All, 1) }.exceptionOrNull() is ApiFailure)
        assertTrue(runCatching { fixture.repository.search("another", SearchScope.All, 1) }.exceptionOrNull() is ApiFailure)
        assertEquals(1, fixture.requests.size)
        assertTrue(fixture.sites.isEmpty())
        assertTrue(fixture.requests.all { it.method == "POST" })
        assertTrue(checkNotNull(fixture.requests.single().body).isOneShot())
    }

    @Test fun aRepeatedChallengeOrCancelledCheckCannotLoop() = runBlocking {
        for (allowed in listOf(true, false)) {
            val fixture = RecoveringFixture(remainsBlocked = true, allowRecovery = allowed)
            val failure = runCatching { fixture.repository.comments(42, 1) }.exceptionOrNull() as ApiFailure
            assertEquals(FailureKind.Challenge, failure.kind)
            assertEquals(if (allowed) 2 else 1, fixture.requests.size)
            assertEquals(1, fixture.sites.size)
        }
    }

    @Test fun loginCommentAndReadReceiptActionsNeverAutomaticallyReplay() = runBlocking {
        val blockedLogin = RecoveringFixture(blockLogin = true)
        assertTrue(runCatching { blockedLogin.repository.signIn("fixture", "password") }.exceptionOrNull() is ApiFailure)
        assertEquals(1, blockedLogin.requests.size)
        assertTrue(blockedLogin.sites.isEmpty())
        val fixture = RecoveringFixture()
        fixture.repository.signIn("fixture", "password")
        try {
            assertTrue(runCatching { fixture.repository.postComment(42, "Draft") }.exceptionOrNull() is ApiFailure)
            assertTrue(runCatching { fixture.repository.markMessageRead(42) }.exceptionOrNull() is ApiFailure)
            assertEquals(3, fixture.requests.size)
            assertTrue(fixture.sites.isEmpty())
        } finally { fixture.repository.signOut() }
    }

    @Test fun forumReadsRecoverOnlyTheirOwnOriginOnce() = runBlocking {
        val fixture = RecoveringFixture(remainsBlocked = true)
        val failure = runCatching { fixture.repository.profile(ProfileReference(username = "Reader")) }.exceptionOrNull() as ApiFailure
        assertEquals(VerificationSite.Forum, failure.verificationSite)
        assertEquals(listOf(VerificationSite.Forum), fixture.sites)
        assertEquals(2, fixture.requests.size)
        assertTrue(fixture.requests.all { it.header("X-Auth-Token") == null })
    }

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

    @Test fun failedSearchDoesNotRetryOrContinueToAnotherQuery() = runBlocking {
        val fixture = Fixture(503, "<html>Unavailable</html>")
        repeat(5) { index ->
            assertTrue(runCatching { fixture.repository.search("fixture-$index", SearchScope.Apps, 1) }.exceptionOrNull() is ApiFailure)
        }
        assertEquals(1, fixture.calls)
        assertFalse(fixture.events.any { it.phase == DiagnosticPhase.Retrying })
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
