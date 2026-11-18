package org.mobilism.material.usage

import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.RequestTraffic

class RequestTrafficJournalTest {
    private val day = LocalDate.of(2026, 11, 17)
    private val event = RequestTraffic("api/search", "POST", 0, 24)

    @Test fun finishedDaysKeepStableImmutableTotalsAcrossRestartsAndReports() {
        val storage = Storage()
        val journal = RequestTrafficJournal(storage, "0.1.24", 25)
        repeat(3) { journal.record(day, event) }
        journal.record(day, event.copy(outcome = 3, durationMs = 400_000))
        assertEquals(0, journal.closedDays(day).length())
        val first = journal.closedDays(day.plusDays(1)).getJSONObject(0)
        assertFalse(first.has("sealed"))
        val metric = first.getJSONArray("metrics").getJSONObject(0)
        assertEquals("[3,0,0,1,0,0]", metric.getJSONArray("counts").toString())
        assertEquals(300_072, metric.getLong("durationMs"))
        val restarted = RequestTrafficJournal(storage, "0.1.24", 25)
        restarted.record(day, event)
        val retry = restarted.closedDays(day.plusDays(2)).getJSONObject(0)
        assertEquals(first.toString(), retry.toString())
    }

    @Test fun upgradesKeepSeparateVersionTotalsAndExpiredDaysArePruned() {
        val storage = Storage()
        RequestTrafficJournal(storage, "0.1.24", 25).record(day, event)
        val upgraded = RequestTrafficJournal(storage, "0.1.25", 26)
        upgraded.record(day, event)
        upgraded.record(day.minusDays(8), event)
        val rows = upgraded.closedDays(day.plusDays(1))
        assertEquals(2, rows.length())
        assertEquals(setOf(25, 26), (0 until rows.length()).map { rows.getJSONObject(it).getInt("versionCode") }.toSet())
        assertNotEquals(rows.getJSONObject(0).getString("id"), rows.getJSONObject(1).getString("id"))
        assertEquals(0, upgraded.closedDays(day.plusDays(8)).length())
        assertTrue(storage.values.isEmpty())
    }

    @Test fun storageFailureDoesNotExposeMutableTotalsAndOptOutClearsOnlyTraffic() {
        val storage = Storage()
        val journal = RequestTrafficJournal(storage, "0.1.24", 25)
        journal.record(day, event)
        storage.writable = false
        assertEquals(0, journal.closedDays(day.plusDays(1)).length())
        assertFalse(JSONObject(storage.values.values.single()).optBoolean("sealed"))
        storage.writable = true
        assertEquals(1, journal.closedDays(day.plusDays(1)).length())
        storage.put("report-$day", "keep daily deduplication")
        journal.clear()
        assertEquals(mapOf("report-$day" to "keep daily deduplication"), storage.values)
    }

    @Test fun malformedGroupsAreIgnoredAndPayloadBucketCountIsBounded() {
        val storage = Storage()
        val journal = RequestTrafficJournal(storage, "0.1.24", 25)
        journal.record(day, event.copy(endpoint = "api/search?private=true"))
        journal.record(day, event.copy(outcome = 6))
        assertTrue(storage.values.isEmpty())
        repeat(12) { RequestTrafficJournal(storage, "0.1.${24 + it}", 25 + it).record(day, event) }
        assertEquals(8, journal.closedDays(day.plusDays(1)).length())
    }

    private class Storage : UsageStorage {
        val values = mutableMapOf<String, String>()
        var writable = true
        override fun get(key: String) = values[key]
        override fun keys() = values.keys.toSet()
        override fun remove(keys: List<String>) { keys.forEach(values::remove) }
        override fun put(key: String, value: String): Boolean {
            if (writable) values[key] = value
            return writable
        }
    }
}
