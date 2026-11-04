package org.mobilism.material.usage

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.Callable

class DailyUsageJournalTest {
    private val day = LocalDate.of(2026, 11, 4)

    @Test fun restartAndRetriesReuseThePersistedPayload() {
        val storage = Storage()
        assertEquals("first ID and version", DailyUsageJournal(storage).prepare(day) { "first ID and version" })
        assertEquals("first ID and version", DailyUsageJournal(storage).prepare(day) { "second ID and version" })
    }

    @Test fun acknowledgementPreventsAnotherReportEvenAfterRestart() {
        val storage = Storage()
        DailyUsageJournal(storage).apply {
            prepare(day) { "first" }
            assertTrue(markSent(day))
        }
        assertNull(DailyUsageJournal(storage).prepare(day) { error("Already sent") })
        assertEquals("next day", DailyUsageJournal(storage).prepare(day.plusDays(1)) { "next day" })
    }

    @Test fun returningToPreviousDateReusesItsId() {
        val journal = DailyUsageJournal(Storage())
        journal.prepare(day) { "first" }
        journal.prepare(day.plusDays(1)) { "second" }
        assertEquals("first", journal.prepare(day) { "duplicate" })
    }

    @Test fun failedPersistenceNeverReleasesAnUnstableId() {
        val storage = Storage().apply { writable = false }
        assertNull(DailyUsageJournal(storage).prepare(day) { "not saved" })
        assertFalse(DailyUsageJournal(storage).markSent(day))
        storage.writable = true
        assertEquals("saved", DailyUsageJournal(storage).prepare(day) { "saved" })
    }

    @Test fun concurrentForegroundEventsCreateOnlyOnePayload() {
        val journal = DailyUsageJournal(Storage())
        val pool = Executors.newFixedThreadPool(4)
        try {
            val reports = pool.invokeAll((1..20).map { number -> Callable { journal.prepare(day) { "id-$number" } } })
            assertEquals(1, reports.map { it.get() }.toSet().size)
        } finally { pool.shutdownNow() }
    }

    @Test fun queuedReportsExpireAndFutureDaysAreRejected() {
        assertTrue(reportDayIsValid(day, day))
        assertTrue(reportDayIsValid(day.minusDays(7), day))
        assertFalse(reportDayIsValid(day.minusDays(8), day))
        assertFalse(reportDayIsValid(day.plusDays(1), day))
    }

    @Test fun incorrectFutureClockCannotEraseTodaysDeduplicationReceipt() {
        val storage = Storage()
        val journal = DailyUsageJournal(storage)
        journal.prepare(day) { "today" }
        journal.markSent(day)
        journal.prepare(day.plusDays(60)) { "wrong date" }
        assertNull(DailyUsageJournal(storage).prepare(day) { error("Would count twice") })
    }

    @Test fun acknowledgedDatePrunesOnlyExpiredReceipts() {
        val storage = Storage()
        val journal = DailyUsageJournal(storage)
        journal.prepare(day.minusDays(8)) { "expired" }
        journal.prepare(day.minusDays(7)) { "still retryable" }
        journal.markSent(day)
        assertFalse(storage.values.containsValue("expired"))
        assertEquals("still retryable", journal.prepare(day.minusDays(7)) { error("Lost daily ID") })
    }

    private class Storage : UsageStorage {
        val values = mutableMapOf<String, String>()
        var writable = true
        override fun get(key: String) = values[key]
        override fun keys(): Set<String> = values.keys.toSet()
        override fun remove(keys: List<String>) { keys.forEach(values::remove) }
        override fun put(key: String, value: String): Boolean {
            if (writable) values[key] = value
            return writable
        }
    }
}
