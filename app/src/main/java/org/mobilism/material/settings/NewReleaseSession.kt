package org.mobilism.material.settings

import android.content.Context
import androidx.core.content.edit
import org.mobilism.material.data.ContentSection
import org.mobilism.material.data.Release

internal class NewReleaseSession(
    previous: Map<ContentSection, Long>,
    private val save: (ContentSection, Long) -> Unit,
) {
    private val baseline = previous.filterValues { it > 0 }.toMutableMap()
    private val viewed = baseline.toMutableMap()

    constructor(context: Context) : this(
        ContentSection.entries.associateWith {
            context.getSharedPreferences("material-new-releases", Context.MODE_PRIVATE).getLong(it.name, 0)
        },
        { section, id ->
            context.getSharedPreferences("material-new-releases", Context.MODE_PRIVATE).edit { putLong(section.name, id) }
        },
    )

    fun observe(section: ContentSection, releases: List<Release>, initialize: Boolean = true): Set<Long> {
        val latest = releases.maxOfOrNull { it.id }?.takeIf { it > 0 } ?: return emptySet()
        // A filtered first visit cannot establish the newest post in this section.
        if (section !in baseline) {
            if (!initialize) return emptySet()
            baseline[section] = latest
        }
        // Keep the comparison fixed until the next activity session, including refreshes.
        return releases.filter { it.id > baseline.getValue(section) }.mapTo(mutableSetOf()) { it.id }
    }

    fun markViewed(section: ContentSection, releases: List<Release>) {
        if (section !in baseline) return
        val latest = releases.maxOfOrNull { it.id } ?: return
        // Fetching offscreen must not clear badges before the user sees the list.
        if (latest > (viewed[section] ?: 0)) {
            viewed[section] = latest
            save(section, latest)
        }
    }
}
