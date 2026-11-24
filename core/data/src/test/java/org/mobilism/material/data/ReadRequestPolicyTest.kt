package org.mobilism.material.data

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ReadRequestPolicyTest {
    private suspend fun ReadRequestPolicy.request(key: String, ttl: Long, fresh: Boolean = false, load: suspend () -> String) =
        request(key, ttl, fresh, { it }, load = load)

    @Test fun restoringPacingAndConcurrencyKeepsValidCachedPages() = runBlocking {
        var time = 1_000L
        var limits = ApiRequestLimits.Defaults.copy(readSpacingMs = 2_000, readConcurrency = 1)
        val policy = ReadRequestPolicy({ time }, { time }, { limits }, busyMessage = { "Paused" }) { time += it }
        val finish = CompletableDeferred<Unit>()
        val starts = mutableListOf<Long>()
        val jobs = (1..3).map { key -> async {
            policy.request("$key", 60_000) { starts += time; finish.await(); "$key" }
        } }
        yield()
        assertEquals(listOf(1_000L), starts)
        finish.complete(Unit)
        jobs.awaitAll()
        assertEquals(listOf(1_000L, 3_000L, 5_000L), starts)
        assertEquals("1", policy.request("1", 60_000) { error("Cached") })
        limits = ApiRequestLimits.Defaults
        assertEquals("1", policy.request("1", 60_000) { error("Unrelated limits must preserve cached data") })
        assertEquals("new", policy.request("4", 60_000) { "new" })
        assertEquals(5_250L, time)
    }

    @Test fun expiredOverridesRestoreBackoffButNeverShortenRetryAfter() {
        var time = 1_000L
        var limits = ApiRequestLimits.Defaults.copy(readFailureCooldownMs = 120_000)
        val policy = ReadRequestPolicy({ time }, { time }, { limits }, busyMessage = { "Paused" })
        policy.response(503, null)
        time += 60_000
        assertTrue(runCatching { policy.checkAllowed() }.isFailure)
        limits = ApiRequestLimits.Defaults
        policy.checkAllowed()
        policy.response(429, "600")
        time += 120_000
        assertTrue(runCatching { policy.checkAllowed() }.isFailure)
        time += 480_000
        policy.checkAllowed()
    }

    private class Clock {
        var time = 1_000L
        val policy = ReadRequestPolicy({ time }, { time }, busyMessage = { "Paused" }) { time += it }
    }

    @Test fun concurrentReadsShareOneResponseWithoutCachingLaterReadsByDefault() = runBlocking {
        val clock = Clock()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var calls = 0
        val first = async { clock.policy.request("comments") { calls++; started.complete(Unit); finish.await(); "one" } }
        started.await()
        val others = (1..20).map { async { clock.policy.request("comments") { calls++; "duplicate" } } }
        yield()
        finish.complete(Unit)
        assertEquals(List(21) { "one" }, (listOf(first) + others).awaitAll())
        assertEquals(1, calls)
        assertEquals("fresh", clock.policy.request("comments") { calls++; "fresh" })
        assertEquals(2, calls)
    }

    @Test fun cachedPagesExpireStaySeparateAndExplicitRefreshBypassesThem() = runBlocking {
        val clock = Clock()
        var calls = 0
        suspend fun read(key: String = "account-a/page-1", fresh: Boolean = false) =
            clock.policy.request(key, 60_000, fresh) { "${++calls}" }
        assertEquals("1", read())
        assertEquals("1", read())
        assertEquals("2", read("account-b/page-1"))
        assertEquals("3", read("account-a/page-2"))
        assertEquals("4", read(fresh = true))
        assertEquals("4", read())
        clock.time += 60_000
        assertEquals("5", read())
    }

    @Test fun changingOnePageLifetimePreservesOtherPagesAndZeroBypassesTheOldEntry() = runBlocking {
        val clock = Clock()
        val policy = clock.policy
        policy.request("categories", 300_000) { "categories" }
        policy.request("topics", 60_000) { "topics" }
        assertEquals("new", policy.request("topics", 0) { "new" })
        assertEquals("next", policy.request("topics", 0) { "next" })
        assertEquals("categories", policy.request("categories", 300_000) { error("Unexpected load") })
    }

    @Test fun concurrentMalformedDiscussionReadsShareTheFailureAndRetryOnlyAfterCooldown() = runBlocking {
        val clock = Clock()
        var calls = 0
        val finish = CompletableDeferred<Unit>()
        suspend fun page(fresh: Boolean = false) = clock.policy.request("topics", 300_000, fresh,
            { DiscussionJson.page(org.json.JSONObject(it), DiscussionKind.Discussion) }) {
            calls++
            finish.await()
            if (calls == 1) "{}" else """{"response":null}"""
        }
        val attempts = (1..6).map { async { runCatching { page() }.exceptionOrNull() as ApiFailure } }
        yield()
        finish.complete(Unit)
        assertTrue(attempts.awaitAll().all { it.kind == FailureKind.InvalidResponse })
        assertEquals(1, calls)
        assertEquals("ok", clock.policy.request("unrelated") { "ok" })
        clock.time = 60_999
        assertTrue((runCatching { page(fresh = true) }.exceptionOrNull() as ApiFailure).blockedBeforeRequest)
        clock.policy.response(429, "120")
        clock.time++
        assertEquals(429, (runCatching { page() }.exceptionOrNull() as ApiFailure).httpStatus)
        assertEquals(1, calls)
        clock.time += 120_000
        assertTrue(page().items.isEmpty())
        assertTrue(page().items.isEmpty())
        assertEquals(2, calls)
    }

    @Test fun missingCategoryGroupDoesNotBlockOrRefetchAValidGroupInTheSameResponse() = runBlocking {
        val clock = Clock()
        var calls = 0
        suspend fun categories(group: String) = clock.policy.request("categories", 300_000, false, { body ->
            org.json.JSONObject(body).optJSONArray(group)?.length()
                ?: throw ApiFailure(FailureKind.InvalidResponse, "Missing group")
        }, pageKey = "categories:$group") {
            if (++calls == 1) """{"apps":[1]}""" else """{"apps":[1],"books":[2,3]}"""
        }
        assertEquals(1, categories("apps"))
        assertTrue(runCatching { categories("books") }.isFailure)
        assertTrue((runCatching { categories("books") }.exceptionOrNull() as ApiFailure).blockedBeforeRequest)
        assertEquals(1, categories("apps"))
        assertEquals(1, calls)
        clock.time += 60_000
        assertEquals(2, categories("books"))
        assertEquals(2, categories("books"))
        assertEquals(1, categories("apps"))
        assertEquals(2, calls)
    }

    @Test fun differentReadsArePacedAndAtMostThreeAreInFlight() = runBlocking {
        val clock = Clock()
        val finish = CompletableDeferred<Unit>()
        val starts = mutableListOf<Long>()
        val jobs = (1..8).map { key -> async {
            clock.policy.request("$key") { starts += clock.time; finish.await(); "$key" }
        } }
        yield()
        assertEquals(listOf(1_000L, 1_250L, 1_500L), starts)
        finish.complete(Unit)
        jobs.awaitAll()
        assertEquals(8, starts.size)
        assertTrue(starts.zipWithNext().all { (a, b) -> b - a >= 250 })
    }

    @Test fun retryAfterPausesOtherEndpointsAndRecoversAfterTheDeadline() = runBlocking {
        for (header in listOf("120", DateTimeFormatter.RFC_1123_DATE_TIME.format(
            Instant.ofEpochMilli(121_000).atZone(ZoneOffset.UTC)))) {
            val clock = Clock()
            clock.policy.response(429, header)
            clock.time += 119_999
            var calls = 0
            assertTrue(runCatching { clock.policy.request("another-endpoint") { calls++; "bad" } }.isFailure)
            assertEquals(0, calls)
            clock.time++
            assertEquals("ok", clock.policy.request("another-endpoint") { "ok" })
        }
    }

    @Test fun repeatedOutagesBackOffAndLateSuccessfulRequestsDoNotClearTheCooldown() = runBlocking {
        val clock = Clock()
        clock.policy.response(503, null)
        clock.policy.response(200, null)
        assertTrue(runCatching { clock.policy.checkAllowed() }.isFailure)
        clock.time += 60_000
        clock.policy.checkAllowed()
        clock.policy.response(503, null)
        clock.time += 60_000
        assertTrue(runCatching { clock.policy.checkAllowed() }.isFailure)
        clock.time += 60_000
        clock.policy.checkAllowed()
        clock.policy.response(200, null)
        clock.policy.response(503, null)
        clock.time += 60_000
        clock.policy.checkAllowed()
    }

    @Test fun challengesDoNotBlockTheVerificationRetry() {
        val clock = Clock()
        clock.policy.response(503, "120", challenged = true)
        clock.policy.checkAllowed()
    }

    @Test fun cancellingAWaiterDoesNotCancelTheOwnerAndCancellingTheOwnerAllowsRetry() = runBlocking {
        val clock = Clock()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val owner = async { clock.policy.request("same") { started.complete(Unit); finish.await(); "ok" } }
        started.await()
        val waiter = async { clock.policy.request("same") { error("Duplicate") } }
        yield()
        waiter.cancel(); waiter.join()
        assertTrue(owner.isActive)
        finish.complete(Unit)
        assertEquals("ok", owner.await())
        val nextStarted = CompletableDeferred<Unit>()
        val cancelled = async { clock.policy.request("same") { nextStarted.complete(Unit); CompletableDeferred<String>().await() } }
        nextStarted.await()
        cancelled.cancel(); cancelled.join()
        assertEquals("retry", clock.policy.request("same") { "retry" })
    }

    @Test fun anActiveReaderCanFinishWhenTheOriginalScreenLeaves() = runBlocking {
        val clock = Clock()
        val started = CompletableDeferred<Unit>()
        var calls = 0
        val owner = async { clock.policy.request("same") { calls++; started.complete(Unit); CompletableDeferred<String>().await() } }
        started.await()
        val waiter = async { clock.policy.request("same") { calls++; "still visible" } }
        yield()
        owner.cancel(); owner.join()
        assertEquals("still visible", waiter.await())
        assertEquals(2, calls)
    }
}
