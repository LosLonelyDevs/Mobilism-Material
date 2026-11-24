package org.mobilism.material.data

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ReadRequestPolicy(
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wallTime: () -> Long = System::currentTimeMillis,
    private val limits: () -> ApiRequestLimits = { ApiRequestLimits.Defaults },
    private val diagnostics: DiagnosticLogger = DiagnosticLogger.None,
    private val busyMessage: () -> String,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private data class Cached(val body: String, val ttl: Long, val expires: Long)
    private val lock = Any()
    private val pending = mutableMapOf<String, CompletableDeferred<String>>()
    private val cache = linkedMapOf<String, Cached>()
    private val invalidAt = linkedMapOf<String, Long>()
    private val starts = Mutex()
    private val active = MutableStateFlow(0)
    private var lastStart: Long? = null
    private var failedAt: Long? = null
    private var retryAfterUntil = 0L
    private var failures = 0
    private var blockedStatus = 429

    suspend fun request(key: String, load: suspend () -> String): String =
        request(key, 0, false, { it }, pageKey = null, load = load)

    suspend fun <T> request(key: String, cacheMillis: Long, fresh: Boolean, decode: (String) -> T,
        pageKey: String? = key, load: suspend () -> String): T {
        lateinit var result: CompletableDeferred<String>
        var owner = false
        val cached = synchronized(lock) {
            val time = now()
            cache.entries.removeAll { it.value.expires <= time }
            val failed = invalidAt[pageKey]
            if (failed != null && time - failed < limits().readFailureCooldownMs)
                throw ApiFailure(FailureKind.InvalidResponse, busyMessage(), blockedBeforeRequest = true)
            if (fresh || cache[key]?.ttl != cacheMillis) cache.remove(key)
            if (failed == null) cache[key]?.let { return@synchronized it }
            val existing = pending[key]
            owner = existing == null
            result = existing ?: CompletableDeferred<String>().also { pending[key] = it }
            null
        }
        if (cached != null) {
            return decodeSharedPage(key, pageKey, cached.body, decode).also {
                diagnostics.cacheLookup(AppCache.Read, CacheOutcome.MemoryHit)
            }
        }
        if (!owner) return try { decodeSharedPage(key, pageKey, result.await(), decode) }
        catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
            request(key, cacheMillis, fresh, decode, pageKey, load)
        }
        try {
            if (cacheMillis > 0 && !fresh) diagnostics.cacheLookup(AppCache.Read, CacheOutcome.Miss)
            val body = pacedRead(load)
            val page = try { decode(body) } catch (failure: ApiFailure) {
                // Another reader may need a different category group from this same response.
                if (failure.kind == FailureKind.InvalidResponse) result.complete(body)
                throw failure
            }
            synchronized(lock) {
                invalidAt.remove(pageKey)
                if (cacheMillis > 0 && body.length <= 256_000) {
                    cache[key] = Cached(body, cacheMillis, now() + cacheMillis)
                    while (cache.size > 32) cache.remove(cache.keys.first())
                }
            }
            result.complete(body)
            return page
        } catch (failure: Throwable) {
            synchronized(lock) {
                if (pageKey != null && failure is ApiFailure && failure.kind == FailureKind.InvalidResponse && !failure.blockedBeforeRequest)
                    rememberInvalid(pageKey)
                if (pending[key] === result) pending.remove(key)
            }
            result.completeExceptionally(failure)
            throw failure
        } finally { synchronized(lock) { if (pending[key] === result) pending.remove(key) } }
    }

    private fun <T> decodeSharedPage(key: String, pageKey: String?, body: String, decode: (String) -> T): T = try {
        decode(body).also { synchronized(lock) {
            if (invalidAt[pageKey]?.let { now() - it >= limits().readFailureCooldownMs } == true) invalidAt.remove(pageKey)
        } }
    } catch (failure: ApiFailure) {
        if (pageKey != null && failure.kind == FailureKind.InvalidResponse) synchronized(lock) {
            val current = cache[key]
            if (current == null || current.body === body) rememberInvalid(pageKey)
        }
        throw failure
    }

    private fun rememberInvalid(key: String) {
        invalidAt.remove(key)
        invalidAt[key] = now()
        while (invalidAt.size > 32) invalidAt.remove(invalidAt.keys.first())
    }

    suspend fun <T> pacedRead(load: suspend () -> T): T = withReadPermit {
        starts.withLock {
            checkAllowed()
            val remaining = lastStart?.let { it + limits().readSpacingMs - now() } ?: 0
            if (remaining > 0) wait(remaining)
            checkAllowed()
            lastStart = now()
        }
        load()
    }

    private suspend fun <T> withReadPermit(load: suspend () -> T): T {
        while (true) {
            val acquired = synchronized(lock) {
                if (active.value < limits().readConcurrency) {
                    active.value += 1
                    true
                } else false
            }
            if (acquired) break
            active.first { it < limits().readConcurrency }
        }
        try { return load() }
        finally { synchronized(lock) { active.value -= 1 } }
    }

    private fun blockedUntil(): Long {
        val policy = limits()
        val backoff = (policy.readFailureCooldownMs shl (failures - 1).coerceAtLeast(0))
            .coerceAtMost(maxOf(policy.readFailureCooldownMs, policy.readFailureMaxCooldownMs))
        return maxOf(retryAfterUntil, failedAt?.let { it + backoff } ?: 0)
    }

    fun checkAllowed() = synchronized(lock) {
        if (now() < blockedUntil()) throw ApiFailure(FailureKind.Server,
            busyMessage(), blockedStatus, blockedBeforeRequest = true)
    }

    fun response(status: Int, retryAfter: String?, challenged: Boolean = false) = synchronized(lock) {
        if (challenged) return@synchronized
        if (status == 429 || status in 500..599) {
            failures = (failures + 1).coerceAtMost(5)
            blockedStatus = status
            failedAt = now()
            val duration = retryAfterMillis(retryAfter, wallTime()).coerceAtMost(Long.MAX_VALUE - now())
            retryAfterUntil = maxOf(retryAfterUntil, now() + duration)
        } else if (status in 200..299 && now() >= blockedUntil()) {
            failures = 0
            failedAt = null
        }
    }
}

fun retryAfterMillis(value: String?, wallTime: Long): Long {
    val seconds = value?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
    return if (seconds != null) seconds.coerceAtMost(Long.MAX_VALUE / 1_000) * 1_000 else
        runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - wallTime }
            .getOrDefault(0).coerceAtLeast(0)
}
