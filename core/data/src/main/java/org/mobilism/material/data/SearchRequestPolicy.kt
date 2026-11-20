package org.mobilism.material.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SearchRequestPolicy(
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wallTime: () -> Long = System::currentTimeMillis,
    private val limits: () -> ApiRequestLimits = { ApiRequestLimits.Defaults },
    private val diagnostics: DiagnosticLogger = DiagnosticLogger.None,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private data class Cached(val body: String, val expires: Long)
    private val mutex = Mutex()
    private val cache = linkedMapOf<String, Cached>()
    private var lastRequest: Long? = null
    private var failedAt: Long? = null
    private var blockedUntil = 0L
    private var previousLimits = ApiRequestLimits.Defaults

    suspend fun request(key: String, load: suspend () -> String): String = mutex.withLock {
        val policy = limits()
        if (policy != previousLimits) {
            cache.clear()
            previousLimits = policy
        }
        cache.entries.removeAll { it.value.expires <= now() }
        cache[key]?.let {
            diagnostics.cacheLookup(AppCache.Search, CacheOutcome.MemoryHit)
            return@withLock it.body
        }
        diagnostics.cacheLookup(AppCache.Search, CacheOutcome.Miss)
        if (now() < blockedUntil || failedAt?.let { now() - it < policy.searchFailureCooldownMs } == true) throw ApiFailure(FailureKind.Server,
            "Search is paused after a failed request. Try again later.")
        val remaining = lastRequest?.let { it + policy.searchSpacingMs - now() } ?: 0
        if (remaining > 0) wait(remaining)
        lastRequest = now()
        try {
            load().also { body ->
                // Keep account-specific responses in memory only, with a small bounded cache.
                if (body.length <= 256_000) {
                    cache[key] = Cached(body, now() + policy.searchCacheTtlMs)
                    while (cache.size > 32) cache.remove(cache.keys.first())
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failedAt = now()
            throw error
        }
    }

    fun retryAfter(value: String?) {
        val duration = retryAfterMillis(value, wallTime())
        val cooldown = maxOf(limits().searchFailureCooldownMs, duration).coerceAtMost(Long.MAX_VALUE - now())
        blockedUntil = maxOf(blockedUntil, now() + cooldown)
    }
}
