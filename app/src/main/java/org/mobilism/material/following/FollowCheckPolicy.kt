package org.mobilism.material.following

import org.mobilism.material.data.LibraryEntry

internal object FollowCheckPolicy {
    const val MIN_INTERVAL_HOURS = 6L
    const val ATTEMPT_INTERVAL_MS = 15 * 60_000L

    fun due(entries: List<LibraryEntry>, now: Long): List<LibraryEntry> = entries
        .filter { entry -> entry.watched && (entry.checkedAt?.let { now - it >= MIN_INTERVAL_HOURS * 3_600_000 } ?: true) }
        .sortedBy { it.checkedAt ?: 0L }
        .take(5)
}
