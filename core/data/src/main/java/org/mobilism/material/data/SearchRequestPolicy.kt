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
    private val pausedMessage: () -> String,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private data class Cached(val body: String, val expires: Long)
    private val mutex = Mutex()
    private val cache = linkedMapOf<String, Cached>()
    private val invalidAt = linkedMapOf<String, Long>()
    private var lastRequest: Long? = null
    private var failedAt: Long? = null
    private var blockedUntil = 0L
    private var previousTtl = ApiRequestLimits.Defaults.searchCacheTtlMs

    suspend fun <T> request(key: String, decode: (String) -> T, load: suspend () -> String): T = mutex.withLock {
        val policy = limits()
        if (policy.searchCacheTtlMs != previousTtl) {
            cache.clear()
            previousTtl = policy.searchCacheTtlMs
        }
        cache.entries.removeAll { it.value.expires <= now() }
        invalidAt.entries.removeAll { now() - it.value >= policy.searchFailureCooldownMs }
        if (key in invalidAt) throw ApiFailure(FailureKind.InvalidResponse, pausedMessage(), blockedBeforeRequest = true)
        cache[key]?.let { cached ->
            return@withLock decodePage(key, cached.body, decode).also { diagnostics.cacheLookup(AppCache.Search, CacheOutcome.MemoryHit) }
        }
        diagnostics.cacheLookup(AppCache.Search, CacheOutcome.Miss)
        if (now() < blockedUntil || failedAt?.let { now() - it < policy.searchFailureCooldownMs } == true) throw ApiFailure(FailureKind.Server,
            pausedMessage(), blockedBeforeRequest = true)
        val remaining = lastRequest?.let { it + policy.searchSpacingMs - now() } ?: 0
        if (remaining > 0) wait(remaining)
        lastRequest = now()
        val body = try {
            load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (error is ApiFailure && error.kind == FailureKind.InvalidResponse) markInvalid(key) else failedAt = now()
            throw error
        }
        decodePage(key, body, decode).also {
            if (policy.searchCacheTtlMs > 0 && limits().searchCacheTtlMs == policy.searchCacheTtlMs && body.length <= 256_000) {
                cache[key] = Cached(body, now() + policy.searchCacheTtlMs)
                while (cache.size > 32) cache.remove(cache.keys.first())
            }
        }
    }

    private fun <T> decodePage(key: String, body: String, decode: (String) -> T): T = try {
        decode(body)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        markInvalid(key)
        throw failure
    }

    private fun markInvalid(key: String) {
        cache.remove(key)
        invalidAt[key] = now()
        while (invalidAt.size > 32) invalidAt.remove(invalidAt.keys.first())
    }

    fun retryAfter(value: String?) {
        val duration = retryAfterMillis(value, wallTime())
        val cooldown = maxOf(limits().searchFailureCooldownMs, duration).coerceAtMost(Long.MAX_VALUE - now())
        blockedUntil = maxOf(blockedUntil, now() + cooldown)
    }
}
