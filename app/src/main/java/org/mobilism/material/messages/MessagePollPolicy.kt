package org.mobilism.material.messages

import java.util.Locale

class MessagePollPolicy(
    private val load: (String) -> Attempt? = { null },
    private val save: (String, Attempt) -> Unit = { _, _ -> },
) {
    data class Attempt(val startedAt: Long, val nextAt: Long, val failures: Int = 0)
    private val attempts = linkedMapOf<String, Attempt>()

    @Synchronized fun begin(account: String, now: Long): Boolean {
        val key = account.trim().lowercase(Locale.ROOT)
        val previous = attempts[key] ?: load(key)?.also { attempts[key] = it }
        if (previous != null && previous.startedAt > now) {
            remember(key, Attempt(now, now + 60_000))
            return false
        }
        if (previous != null && now < previous.nextAt) return false
        remember(key, Attempt(now, now + 60_000, previous?.failures ?: 0))
        return true
    }

    @Synchronized fun completed(account: String, now: Long, failed: Boolean = false) {
        val key = account.trim().lowercase(Locale.ROOT)
        val failures = if (failed) ((attempts[key]?.failures ?: 0) + 1).coerceAtMost(5) else 0
        val delay = if (failed) (60_000L shl (failures - 1)).coerceAtMost(15 * 60_000L) else 60_000
        remember(key, Attempt(now, now + delay, failures))
    }

    private fun remember(key: String, attempt: Attempt) {
        attempts[key] = attempt
        while (attempts.size > 4) attempts.remove(attempts.keys.first())
        save(key, attempt)
    }
}
