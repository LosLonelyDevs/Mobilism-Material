package org.mobilism.material.messages

import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.ApiRequestLimits

class MessagePollPolicyTest {
    @Test fun expiredOverridesDoNotLeavePersistedRemotePollingDelays() {
        val saved = mutableMapOf<String, MessagePollPolicy.Attempt>()
        var limits = ApiRequestLimits.Defaults.copy(messagePollIntervalMs = 180_000,
            messageFailureMaxCooldownMs = 240_000)
        fun policy() = MessagePollPolicy(saved::get, { limits }) { key, attempt -> saved[key] = attempt }
        val remote = policy()
        assertTrue(remote.begin("Reader", 1_000))
        remote.completed("Reader", 1_000)
        assertFalse(remote.begin("Reader", 61_000))
        limits = ApiRequestLimits.Defaults
        assertTrue(policy().begin("Reader", 61_000))
        limits = limits.copy(messagePollIntervalMs = 180_000, messageFailureMaxCooldownMs = 240_000)
        val failures = policy()
        failures.completed("Reader", 61_000, failed = true)
        assertTrue(failures.begin("Reader", 241_000))
        failures.completed("Reader", 241_000, failed = true)
        assertFalse(failures.begin("Reader", 480_999))
        assertTrue(failures.begin("Reader", 481_000))
    }

    @Test fun attemptsSurviveRestartAndAccountsRemainIndependent() {
        val saved = mutableMapOf<String, MessagePollPolicy.Attempt>()
        fun policy() = MessagePollPolicy(saved::get) { key, attempt -> saved[key] = attempt }
        assertTrue(policy().begin("Reader", 1_000))
        assertFalse(policy().begin(" reader ", 1_001))
        assertTrue(policy().begin("Other", 1_001))
        assertTrue(policy().begin("Reader", 61_000))
    }

    @Test fun repeatedFailuresBackOffAndSuccessRestoresNormalChecks() {
        val policy = MessagePollPolicy()
        var now = 1_000L
        for (delay in listOf(60_000, 120_000, 240_000, 480_000, 900_000, 900_000)) {
            assertTrue(policy.begin("Reader", now))
            policy.completed("Reader", now, failed = true)
            assertFalse(policy.begin("Reader", now + delay - 1))
            now += delay
        }
        assertTrue(policy.begin("Reader", now))
        policy.completed("Reader", now)
        assertFalse(policy.begin("Reader", now + 59_999))
        assertTrue(policy.begin("Reader", now + 60_000))
    }

    @Test fun clockRollbackCannotLockPollingOutOrCreateAnImmediateBurst() {
        val policy = MessagePollPolicy()
        assertTrue(policy.begin("Reader", 100_000))
        assertFalse(policy.begin("Reader", 1_000))
        assertFalse(policy.begin("Reader", 1_001))
        assertTrue(policy.begin("Reader", 61_000))
    }
}
