package org.mobilism.material.messages

import org.junit.Assert.*
import org.junit.Test

class MessagePollPolicyTest {
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
