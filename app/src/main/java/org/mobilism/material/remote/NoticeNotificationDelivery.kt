package org.mobilism.material.remote

import org.mobilism.material.data.AppNotice

internal class NoticeNotificationDelivery(
    readSent: () -> Set<String> = { emptySet() },
    private val saveSent: (Set<String>) -> Unit = {},
    private val post: (String, AppNotice) -> Boolean,
    private val retain: (String?, Set<String>) -> Unit = { _, _ -> },
) {
    private val sent = readSent().toMutableSet()

    @Synchronized fun sync(account: String?, notices: List<AppNotice>) {
        val eligible = notices.filter { it.enabled && it.deviceNotification }
        retain(account, eligible.map { it.id }.toSet())
        if (account == null) return
        for (notice in eligible) {
            val key = "$account:${notice.dismissalKey}"
            if (key in sent || !post(account, notice)) continue
            sent.add(key)
            while (sent.size > 200) sent.remove(sent.first())
            saveSent(sent.toSet())
        }
    }
}
