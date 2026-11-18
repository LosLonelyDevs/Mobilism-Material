package org.mobilism.material.following

import org.mobilism.material.data.LibraryEntry
import org.mobilism.material.data.ApiRequestLimits

internal object FollowCheckPolicy {
    const val MIN_BACKGROUND_INTERVAL_HOURS = 6L

    fun due(entries: List<LibraryEntry>, now: Long, limits: ApiRequestLimits = ApiRequestLimits.Defaults): List<LibraryEntry> = entries
        .filter { entry -> entry.watched && (entry.checkedAt?.let { now - it >= limits.followAppIntervalMs } ?: true) }
        .sortedBy { it.checkedAt ?: 0L }
        .take(limits.followBatchSize)

    fun canAttempt(previous: Long, now: Long, limits: ApiRequestLimits): Boolean =
        previous <= 0 || now - previous >= limits.followAttemptIntervalMs
}
