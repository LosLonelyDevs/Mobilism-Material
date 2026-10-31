package org.mobilism.material.settings

import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.ContentSection
import org.mobilism.material.data.Release

class NewReleaseSessionTest {
    private val apps = ContentSection.Apps
    private fun releases(vararg ids: Long) = ids.map { Release(it, "Release $it", "Member", null, 0, 0, 0) }

    @Test fun firstVisitEstablishesBaselineAndRefreshKeepsNewBadgesForTheSession() {
        val saved = mutableMapOf<ContentSection, Long>()
        val session = NewReleaseSession(saved) { section, id -> saved[section] = id }
        assertTrue(session.observe(apps, releases(10, 9)).isEmpty())
        assertEquals(10L, saved[apps])
        assertEquals(setOf(11L), session.observe(apps, releases(11, 10)))
        assertEquals(setOf(11L, 12L), session.observe(apps, releases(12, 11, 10, 9)))
        assertEquals(12L, saved[apps])
        assertEquals(setOf(11L), session.observe(apps, releases(11, 10)))
        assertEquals(12L, saved[apps])
    }

    @Test fun nextSessionClearsPriorBadgesAndSectionsKeepSeparateMarkers() {
        val saved = mutableMapOf(apps to 10L, ContentSection.Books to 30L)
        val session = NewReleaseSession(saved) { section, id -> saved[section] = id }
        assertEquals(setOf(12L), session.observe(apps, releases(12, 9)))
        assertEquals(setOf(31L), session.observe(ContentSection.Books, releases(31, 29)))
        val next = NewReleaseSession(saved) { section, id -> saved[section] = id }
        assertTrue(next.observe(apps, releases(12, 9)).isEmpty())
        assertEquals(setOf(13L), next.observe(apps, releases(13, 12)))
        assertTrue(next.observe(ContentSection.Books, releases(31)).isEmpty())
    }

    @Test fun emptyOrFilteredFirstPagesDoNotCreateAnIncorrectBaseline() {
        val saved = mutableMapOf<ContentSection, Long>()
        val session = NewReleaseSession(saved) { section, id -> saved[section] = id }
        assertTrue(session.observe(apps, emptyList()).isEmpty())
        assertTrue(session.observe(apps, releases(1, 2), initialize = false).isEmpty())
        assertTrue(saved.isEmpty())
        assertTrue(session.observe(apps, releases(100, 99)).isEmpty())
        assertEquals(setOf(101L), session.observe(apps, releases(101, 1), initialize = false))
    }
}
