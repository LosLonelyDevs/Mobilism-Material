package org.mobilism.material.data

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Captures the source and visibility of one already-authorized Following search. */
class FollowSearchObservation : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<FollowSearchObservation>

    private var readAt = 0L
    private var adultFiltered = false
    private var fullPage = false

    internal fun observed(at: Long, filtered: Boolean, full: Boolean) {
        readAt = at
        adultFiltered = filtered
        fullPage = full
    }

    fun contribution(followed: Release, page: ReleasePage): LatestAppsPage? {
        if (readAt == 0L) return null
        val key = latestAppKey(ReleaseTitles.parse(followed.title).name)
        val newest = page.items.filter { item ->
            val title = ReleaseTitles.parse(item.title)
            item.section == ContentSection.Apps && item.id > 0 && item.publishedAt > 0 &&
                latestAppKey(title.name) == key && ReleaseVersions.sameApp(item, followed)
        }.maxWithOrNull(compareBy<Release> { it.publishedAt }.thenBy { it.id }) ?: return null
        if (newest.author.isBlank() || ReleaseTitles.parse(newest.title).version == null) return null
        // Search is sorted by activity. A full page may conceal a newer release on the next page.
        if (fullPage && (page.nextActivityAtMost ?: Long.MAX_VALUE) > newest.publishedAt) return null
        return LatestAppsPage(listOf(newest), firstPage = false, adultFiltered = adultFiltered, readAt = readAt)
    }
}
