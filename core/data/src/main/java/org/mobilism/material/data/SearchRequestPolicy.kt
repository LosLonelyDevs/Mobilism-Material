package org.mobilism.material.data

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SearchRequestPolicy(
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wallTime: () -> Long = System::currentTimeMillis,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private data class Cached(val body: String, val expires: Long)
    private val mutex = Mutex()
    private val cache = linkedMapOf<String, Cached>()
    private var nextRequest = 0L
    private var blockedUntil = 0L

    suspend fun request(key: String, load: suspend () -> String): String = mutex.withLock {
        cache.entries.removeAll { it.value.expires <= now() }
        cache[key]?.let { return@withLock it.body }
        if (now() < blockedUntil) throw ApiFailure(FailureKind.Server,
            "Search is paused after a failed request. Try again later.")
        val remaining = nextRequest - now()
        if (remaining > 0) wait(remaining)
        nextRequest = now() + 3_000
        try {
            load().also { body ->
                // Keep account-specific responses in memory only, with a small bounded cache.
                if (body.length <= 256_000) {
                    cache[key] = Cached(body, now() + 5 * 60_000)
                    while (cache.size > 32) cache.remove(cache.keys.first())
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            blockedUntil = maxOf(blockedUntil, now() + 60_000)
            throw error
        }
    }

    fun retryAfter(value: String?) {
        val seconds = value?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
        val duration = if (seconds != null) seconds.coerceAtMost(Long.MAX_VALUE / 1_000) * 1_000 else
            runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - wallTime() }
                .getOrDefault(0)
        val cooldown = maxOf(60_000, duration).coerceAtMost(Long.MAX_VALUE - now())
        blockedUntil = maxOf(blockedUntil, now() + cooldown)
    }
}
