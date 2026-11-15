package org.mobilism.material.following

import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.LibraryEntry
import org.mobilism.material.data.Release

class FollowCheckPolicyTest {
    private val now = 100_000_000L
    private fun entry(id: Long, checked: Long? = null, watched: Boolean = true) =
        LibraryEntry(Release(id, "App $id", "", null, 1, 0, 0), watched = watched, checkedAt = checked)

    @Test fun checksAtMostFiveOldestDueAppsAndSkipsRecentOrUnfollowedEntries() {
        val entries = listOf(entry(99, now - 1), entry(98, watched = false)) +
            (1L..20L).reversed().map { entry(it, it) }
        assertEquals((1L..5L).toList(), FollowCheckPolicy.due(entries, now).map { it.release.id })
    }

    @Test fun successfulChecksRotateToTheRemainingApps() {
        val entries = (1L..12L).map { entry(it) }
        val first = FollowCheckPolicy.due(entries, now).map { it.release.id }.toSet()
        val updated = entries.map { if (it.release.id in first) it.copy(checkedAt = now) else it }
        assertEquals((6L..10L).toList(), FollowCheckPolicy.due(updated, now + 60_000).map { it.release.id })
        assertTrue(FollowCheckPolicy.due(entries.take(5).map { it.copy(checkedAt = now) }, now + 5 * 3_600_000).isEmpty())
        assertEquals(5, FollowCheckPolicy.due(entries.take(5).map { it.copy(checkedAt = now) }, now + 6 * 3_600_000).size)
    }
}
