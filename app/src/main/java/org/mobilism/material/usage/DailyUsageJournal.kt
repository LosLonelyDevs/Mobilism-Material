package org.mobilism.material.usage

import java.time.LocalDate

internal interface UsageStorage {
    fun get(key: String): String?
    fun put(key: String, value: String): Boolean
    fun keys(): Set<String>
    fun remove(keys: List<String>)
}

internal class DailyUsageJournal(private val storage: UsageStorage) {
    @Synchronized fun prepare(day: LocalDate, create: () -> String): String? {
        if (storage.get("sent-$day") == "true") return null
        storage.get("report-$day")?.let { return it }
        val report = create()
        // Persist the ID before enqueueing so a crash cannot mint a second report for the same day.
        return report.takeIf { storage.put("report-$day", it) }
    }

    @Synchronized fun wasSent(day: LocalDate): Boolean = storage.get("sent-$day") == "true"

    @Synchronized fun markSent(day: LocalDate): Boolean {
        if (!storage.put("sent-$day", "true")) return false
        // Prune against an acknowledged date, so a bad device clock cannot discard
        // a still-valid receipt and cause another count when the clock is corrected.
        val expired = storage.keys().filter { key ->
            if (!key.startsWith("report-") && !key.startsWith("sent-")) false
            else runCatching { LocalDate.parse(key.substringAfter('-')).isBefore(day.minusDays(7)) }.getOrDefault(false)
        }
        storage.remove(expired)
        return true
    }
}

internal fun reportDayIsValid(day: LocalDate, today: LocalDate): Boolean =
    !day.isAfter(today) && !day.isBefore(today.minusDays(7))
