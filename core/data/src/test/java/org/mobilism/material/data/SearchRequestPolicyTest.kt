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

class SearchRequestPolicyTest {
    private suspend fun SearchRequestPolicy.request(key: String, load: suspend () -> String) =
        request(key, { it }, load)

    @Test fun remoteThresholdsApplyAndFallbackRestoresShippedSpacingAndCooldown() = runBlocking {
        var time = 1_000L
        var limits = ApiRequestLimits.Defaults.copy(searchSpacingMs = 10_000, searchFailureCooldownMs = 600_000)
        val waits = mutableListOf<Long>()
        val policy = SearchRequestPolicy({ time }, { time }, { limits }, pausedMessage = { "Paused" }) { waits += it; time += it }
        policy.request("one") { "one" }
        policy.request("two") { "two" }
        assertEquals(listOf(10_000L), waits)
        limits = ApiRequestLimits.Defaults
        policy.request("three") { "three" }
        assertEquals(listOf(10_000L, 3_000L), waits)
        limits = limits.copy(searchFailureCooldownMs = 600_000)
        runCatching { policy.request("failure") { error("unavailable") } }
        time += 60_000
        assertTrue(runCatching { policy.request("four") { "four" } }.isFailure)
        limits = ApiRequestLimits.Defaults
        assertEquals("four", policy.request("four") { "four" })
    }

    @Test fun changedCacheThresholdDropsPreviouslyCachedResponses() = runBlocking {
        var time = 1000L
        var limits = ApiRequestLimits.Defaults
        var calls = 0
        val policy = SearchRequestPolicy({ time }, { time }, { limits }, pausedMessage = { "Paused" }) { time += it }
        policy.request("one") { calls++; "first" }
        assertEquals("first", policy.request("one") { calls++; "second" })
        limits = limits.copy(searchCacheTtlMs = 0)
        assertEquals("second", policy.request("one") { calls++; "second" })
        policy.request("one") { calls++; "third" }
        assertEquals(3, calls)
    }

    @Test fun unrelatedLimitChangeDuringSearchPreservesItsValidatedResult() = runBlocking {
        var limits = ApiRequestLimits.Defaults
        val policy = SearchRequestPolicy(limits = { limits }, pausedMessage = { "Paused" })
        val page = policy.request("query", { SearchJson.page(org.json.JSONObject(it)) }) {
            limits = limits.copy(debridRefreshIntervalMs = 60_000)
            """{"releases":null}"""
        }
        assertTrue(page.items.isEmpty())
        limits = limits.copy(followBatchSize = 2)
        assertEquals(page, policy.request("query", { SearchJson.page(org.json.JSONObject(it)) }) { error("Unexpected load") })
    }

    @Test fun malformedSearchWaitsForCooldownThenCachesTheFirstValidResponse() = runBlocking {
        val clock = Clock()
        var calls = 0
        suspend fun page() = clock.policy.request("query", { SearchJson.page(org.json.JSONObject(it)) }) {
            if (++calls == 1) "{}" else """{"releases":[]}"""
        }
        assertTrue(runCatching { page() }.exceptionOrNull() is org.json.JSONException)
        assertEquals("valid", clock.policy.request("another query") { "valid" })
        clock.time = 60_999
        assertTrue((runCatching { page() }.exceptionOrNull() as ApiFailure).blockedBeforeRequest)
        assertEquals(1, calls)
        clock.time++
        assertTrue(page().items.isEmpty())
        assertTrue(page().items.isEmpty())
        assertEquals(2, calls)
    }

    private class Clock {
        var time = 1_000L
        val waits = mutableListOf<Long>()
        val policy = SearchRequestPolicy({ time }, { time }, pausedMessage = { "Paused" }) { waits += it; time += it }
    }

    @Test fun concurrentDuplicateSearchesShareOneResponse() = runBlocking {
        val clock = Clock()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var calls = 0
        val first = async { clock.policy.request("same") { calls++; started.complete(Unit); finish.await(); "result" } }
        started.await()
        val others = (1..20).map { async { clock.policy.request("same") { calls++; "duplicate" } } }
        yield()
        assertEquals(1, calls)
        finish.complete(Unit)
        assertEquals(List(21) { "result" }, (listOf(first) + others).awaitAll())
        assertEquals(1, calls)
    }

    @Test fun differentQueriesAreSerializedAndStartAtLeastThreeSecondsApart() = runBlocking {
        val clock = Clock()
        val starts = mutableListOf<Long>()
        (1..5).map { key -> async { clock.policy.request("$key") { starts += clock.time; "$key" } } }.awaitAll()
        assertEquals(listOf(1_000L, 4_000L, 7_000L, 10_000L, 13_000L), starts)
    }

    @Test fun cacheExpiresAndEvictsOldResponses() = runBlocking {
        val clock = Clock()
        var calls = 0
        suspend fun request(key: String) = clock.policy.request(key) { "${++calls}" }
        assertEquals("1", request("one"))
        clock.time += 299_999
        assertEquals("1", request("one"))
        clock.time++
        assertEquals("2", request("one"))
        (1..32).forEach { request("other-$it") }
        assertEquals("35", request("one"))
    }

    @Test fun failedSearchStopsQueuedQueriesAndDoesNotCacheTheFailure() = runBlocking {
        val clock = Clock()
        var calls = 0
        val failure = ApiFailure(FailureKind.Challenge, "Blocked", 403)
        assertSame(failure, runCatching { clock.policy.request("one") { calls++; throw failure } }.exceptionOrNull())
        repeat(20) {
            assertTrue(runCatching { clock.policy.request("query-$it") { calls++; "unexpected" } }.isFailure)
        }
        assertEquals(1, calls)
        clock.time += 60_000
        assertEquals("ok", clock.policy.request("one") { calls++; "ok" })
        assertEquals(2, calls)
    }

    @Test fun retryAfterSecondsAndHttpDatesPauseAllSearches() = runBlocking {
        for (header in listOf("120", DateTimeFormatter.RFC_1123_DATE_TIME.format(
            Instant.ofEpochMilli(121_000).atZone(ZoneOffset.UTC)))) {
            val clock = Clock()
            runCatching { clock.policy.request("one") {
                clock.policy.retryAfter(header)
                throw ApiFailure(FailureKind.Server, "Busy", 429)
            } }
            clock.time += 119_999
            assertTrue(runCatching { clock.policy.request("two") { "unexpected" } }.isFailure)
            clock.time++
            assertEquals("ok", clock.policy.request("two") { "ok" })
        }
    }

    @Test fun cancellationReleasesTheQueueWithoutReplayingTheRequest() = runBlocking {
        val clock = Clock()
        val started = CompletableDeferred<Unit>()
        val first = async { clock.policy.request("one") { started.complete(Unit); CompletableDeferred<String>().await() } }
        started.await()
        first.cancel()
        first.join()
        assertEquals("ok", clock.policy.request("two") { "ok" })
        assertEquals(listOf(3_000L), clock.waits)
    }
}
