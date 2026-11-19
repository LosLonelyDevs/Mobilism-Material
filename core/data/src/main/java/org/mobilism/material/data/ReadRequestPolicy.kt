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
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private data class Cached(val body: String, val expires: Long)
    private val lock = Any()
    private val pending = mutableMapOf<String, CompletableDeferred<String>>()
    private val cache = linkedMapOf<String, Cached>()
    private val starts = Mutex()
    private val active = MutableStateFlow(0)
    private var lastStart: Long? = null
    private var failedAt: Long? = null
    private var retryAfterUntil = 0L
    private var failures = 0
    private var blockedStatus = 429
    private var previousLimits = ApiRequestLimits.Defaults

    suspend fun request(key: String, cacheMillis: Long = 0, fresh: Boolean = false, load: suspend () -> String): String {
        val requestPolicy = limits()
        val result: CompletableDeferred<String>
        val owner: Boolean
        synchronized(lock) {
            if (requestPolicy != previousLimits) {
                cache.clear()
                previousLimits = requestPolicy
            }
            cache.entries.removeAll { it.value.expires <= now() }
            if (fresh) cache.remove(key)
            else cache[key]?.let { return it.body }
            val existing = pending[key]
            owner = existing == null
            result = existing ?: CompletableDeferred<String>().also { pending[key] = it }
        }
        if (!owner) return try { result.await() }
        catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
            request(key, cacheMillis, fresh, load)
        }
        try {
            val body = pacedRead(load)
            synchronized(lock) {
                if (cacheMillis > 0 && body.length <= 256_000 && limits() == requestPolicy) {
                    cache[key] = Cached(body, now() + cacheMillis)
                    while (cache.size > 32) cache.remove(cache.keys.first())
                }
            }
            result.complete(body)
            return body
        } catch (failure: Throwable) {
            synchronized(lock) { if (pending[key] === result) pending.remove(key) }
            result.completeExceptionally(failure)
            throw failure
        } finally { synchronized(lock) { if (pending[key] === result) pending.remove(key) } }
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
            "Mobilism is temporarily busy. Wait a moment before trying again.", blockedStatus, blockedBeforeRequest = true)
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
