package org.mobilism.material.remote

import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.AppNotice

class NoticeNotificationDeliveryTest {
    private val notice = AppNotice("test", "Title", "Message", "notice", "once", true,
        null, null, 1, null, null, null, null, deviceNotification = true)

    @Test fun alertsOncePerContentAndAccountAcrossChecksAndRestarts() {
        var saved = emptySet<String>()
        val posted = mutableListOf<Pair<String, String>>()
        fun delivery() = NoticeNotificationDelivery(readSent = { saved }, saveSent = { saved = it },
            post = { account, notice -> posted.add(account to notice.message); true })
        val first = delivery()
        repeat(3) { first.sync("alice", listOf(notice)) }
        delivery().sync("alice", listOf(notice))
        assertEquals(listOf("alice" to "Message"), posted)
        val restarted = delivery()
        restarted.sync("bob", listOf(notice))
        restarted.sync("alice", listOf(notice.copy(message = "Updated")))
        assertEquals(listOf("alice" to "Message", "bob" to "Message", "alice" to "Updated"), posted)
    }

    @Test fun disabledOptedOutAndUnknownIdentityNeverPostAndClearOldNotifications() {
        val retained = mutableListOf<Pair<String?, Set<String>>>()
        val delivery = NoticeNotificationDelivery(post = { _, _ -> fail("Unexpected notification"); false },
            retain = { account, ids -> retained.add(account to ids) })
        delivery.sync("alice", listOf(notice.copy(enabled = false), notice.copy(deviceNotification = false)))
        delivery.sync(null, emptyList())
        assertEquals(listOf("alice" to emptySet<String>(), null to emptySet<String>()), retained)
    }

    @Test fun deniedPermissionCanRetryWithoutMarkingNoticeAsSent() {
        var allowed = false
        var attempts = 0
        var saved = emptySet<String>()
        val delivery = NoticeNotificationDelivery(saveSent = { saved = it }, post = { _, _ -> attempts++; allowed })
        delivery.sync("alice", listOf(notice))
        assertTrue(saved.isEmpty())
        allowed = true
        repeat(2) { delivery.sync("alice", listOf(notice)) }
        assertEquals(2, attempts)
        assertEquals(1, saved.size)
    }
}
