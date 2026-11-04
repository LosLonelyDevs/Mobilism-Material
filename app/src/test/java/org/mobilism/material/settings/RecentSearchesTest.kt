package org.mobilism.material.settings

import org.junit.Assert.*
import org.junit.Test

class RecentSearchesTest {
    @Test fun recordsNewestFirstDeduplicatesAndPersistsOnlyTen() {
        var saved = emptyList<String>()
        val history = RecentSearches(save = { saved = it })
        (1..12).forEach { history.record("query $it") }
        history.record(" QUERY 8 ")
        assertEquals("QUERY 8", saved.first())
        assertEquals(10, saved.size)
        assertEquals(1, saved.count { it.equals("query 8", true) })
        assertFalse("query 1" in saved)
        val restored = RecentSearches(load = { saved })
        assertEquals(history.queries.value, restored.queries.value)
        history.remove("query 8")
        assertFalse(saved.any { it.equals("query 8", true) })
        history.clear()
        assertTrue(saved.isEmpty())
    }

    @Test fun blankQueriesAreNotRecordedAndMalformedOldListsAreCleaned() {
        val history = RecentSearches(load = { listOf("", " ", " Book ", "book") })
        history.record(" ")
        assertEquals(listOf("Book"), history.queries.value)
    }
}
