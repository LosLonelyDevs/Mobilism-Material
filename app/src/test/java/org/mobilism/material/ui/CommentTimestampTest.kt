package org.mobilism.material.ui

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentTimestampTest {
    private val zone = ZoneId.of("America/New_York")

    @Test fun countsHoursTodayAndCalendarDaysAfterMidnight() {
        val posted = Instant.parse("2026-11-01T04:30:00Z")
        fun age(at: String) = commentAge(posted, Instant.parse(at), zone)
        assertEquals("<1h", age("2026-11-01T04:45:00Z").label)
        assertEquals("2h", age("2026-11-01T06:30:00Z").label)
        assertEquals(Instant.parse("2026-11-02T05:00:00Z"), age("2026-11-02T04:45:00Z").nextChange)
        assertEquals("1d", age("2026-11-02T05:00:00Z").label)
        assertEquals("16d", age("2026-11-17T05:00:00Z").label)
    }
}
