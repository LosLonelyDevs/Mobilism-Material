package org.mobilism.material.messages

import java.util.Locale
import org.mobilism.material.data.ApiRequestLimits

class MessagePollPolicy(
    private val load: (String) -> Attempt? = { null },
    private val limits: () -> ApiRequestLimits = { ApiRequestLimits.Defaults },
    private val save: (String, Attempt) -> Unit = { _, _ -> },
) {
    data class Attempt(val startedAt: Long, val nextAt: Long, val failures: Int = 0)
    private val attempts = linkedMapOf<String, Attempt>()

    @Synchronized fun begin(account: String, now: Long): Boolean {
        val key = account.trim().lowercase(Locale.ROOT)
        val previous = attempts[key] ?: load(key)?.also { attempts[key] = it }
        if (previous != null && previous.startedAt > now) {
            remember(key, Attempt(now, now + limits().messagePollIntervalMs))
            return false
        }
        // Recompute persisted deadlines so expired remote values cannot survive a restart.
        if (previous != null && now < previous.startedAt + interval(previous.failures)) return false
        remember(key, Attempt(now, now + limits().messagePollIntervalMs, previous?.failures ?: 0))
        return true
    }

    @Synchronized fun completed(account: String, now: Long, failed: Boolean = false) {
        val key = account.trim().lowercase(Locale.ROOT)
        val failures = if (failed) ((attempts[key]?.failures ?: 0) + 1).coerceAtMost(5) else 0
        val delay = interval(failures)
        remember(key, Attempt(now, now + delay, failures))
    }

    private fun interval(failures: Int): Long {
        val policy = limits()
        return if (failures == 0) policy.messagePollIntervalMs else
            (policy.messagePollIntervalMs shl (failures - 1)).coerceAtMost(
                maxOf(policy.messagePollIntervalMs, policy.messageFailureMaxCooldownMs))
    }

    private fun remember(key: String, attempt: Attempt) {
        attempts[key] = attempt
        while (attempts.size > 4) attempts.remove(attempts.keys.first())
        save(key, attempt)
    }
}
