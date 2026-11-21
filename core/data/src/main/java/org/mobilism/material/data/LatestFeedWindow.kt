package org.mobilism.material.data

import android.content.SharedPreferences

/** Only compare complete, date-ordered first pages read with the same account and visibility. */
internal class LatestFeedWindow(private val storage: SharedPreferences) {
    @Synchronized fun baseline(scope: String, rows: List<Release>, at: Long): List<Long>? {
        if (rows.size !in 2..20 || rows.any { it.id <= 0 || it.publishedAt <= 0 || it.section != ContentSection.Apps } ||
            rows.zipWithNext().any { (a, b) -> a.publishedAt <= b.publishedAt }) return null
        val ids = rows.map { it.id }
        if (ids.distinct().size != ids.size) return null
        val sameScope = storage.getString("scope", null) == scope
        if (sameScope && at <= storage.getLong("read", 0)) return null
        val previous = if (sameScope && at - storage.getLong("baseline-at", 0) in 0 until 86_400)
            storage.getString("ids", null).orEmpty().split(',').mapNotNull(String::toLongOrNull) else emptyList()
        val gap = previous.windowed(3).any { (newer, missing, older) ->
            val first = ids.indexOf(newer)
            missing !in ids && first >= 0 && ids.indexOf(older) > first
        }
        // Keep the original neighbors while a gap is visible so a later independent read can confirm it.
        storage.edit().apply {
            putLong("read", at)
            if (!gap) putString("scope", scope).putString("ids", ids.joinToString(",")).putLong("baseline-at", at)
        }.apply()
        return previous
    }
}
