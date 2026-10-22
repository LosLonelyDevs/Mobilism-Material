package org.mobilism.material.data

import org.junit.Assert.*
import org.junit.Test

class AppLibraryTest {
    private fun release(id: Long, version: String) = Release(id, "Example v$version [Premium]", "author", null, 0, 0, 0,
        contentHtml = "<p>Long description</p><a href=\"https://play.google.com/store/apps/details?id=org.example.app\">Play</a>", section = ContentSection.Apps)

    @Test fun movingWatchPreservesDownloadedHistory() {
        val first = release(1, "1.0")
        val second = release(2, "2.0")
        var entries = AppLibrary.watch(emptyList(), first, true)
        entries = AppLibrary.downloaded(entries, first, true)
        entries = AppLibrary.watch(entries, second, true)
        assertEquals(listOf(2L), entries.filter { it.watched }.map { it.release.id })
        assertEquals(listOf(1L), entries.filter { it.downloaded }.map { it.release.id })
        entries = AppLibrary.watch(entries, second, false)
        assertEquals(1, entries.size)
        assertTrue(entries.single().downloaded)
    }

    @Test fun roundTripKeepsFlagsIdentityAndUpdateWithoutPostBody() {
        val entry = LibraryEntry(AppLibrary.compact(release(1, "1.0")), true, true, 123L, 456L, AppLibrary.compact(release(2, "2.0")))
        val json = LibraryJson.encode(listOf(entry))
        assertFalse(json.contains("Long description"))
        assertEquals(listOf(entry), LibraryJson.decode(json))
        assertEquals("org.example.app", ReleaseVersions.packageName(LibraryJson.decode(json).single().release))
    }

    @Test fun downloadedAndWatchFlagsAreIndependentAndDuplicateSafe() {
        val release = release(1, "1.0")
        var entries = AppLibrary.downloaded(emptyList(), release, true)
        entries = AppLibrary.downloaded(entries, release, true)
        entries = AppLibrary.watch(entries, release, true)
        entries = AppLibrary.downloaded(entries, release, false)
        assertEquals(1, entries.size)
        assertTrue(entries.single().watched)
        assertFalse(entries.single().downloaded)
        assertTrue(AppLibrary.watch(entries, release, false).isEmpty())
    }

    @Test fun trackerKeepsTvVariantsAndNumberedPreviewsSeparate() {
        val stable = release(1, "1.0")
        assertFalse(ReleaseVersions.isNewer(release(2, "2.0").copy(title = "Example v2.0 [TV]"), stable))
        assertFalse(ReleaseVersions.isNewer(release(2, "2.0-beta1"), stable))
        assertFalse(ReleaseVersions.isNewer(release(2, "2.0rc2"), stable))
        assertFalse(ReleaseVersions.isNewer(release(2, "2.0-unknown"), stable))
        assertTrue(ReleaseVersions.isNewer(release(2, "2.0"), stable))
    }

    @Test fun undoUnfollowKeepsDownloadedMarkAndNotificationState() {
        val original = LibraryEntry(release(1, "1.0"), watched = true, downloaded = true,
            linkedPackage = "org.example.app", lastNotifiedVersion = "2.0||false", latest = AppLibrary.compact(release(2, "2.0")))
        val unfollowed = AppLibrary.watch(listOf(original), original.release, false)
        val restored = AppLibrary.restore(unfollowed, original).single()
        assertTrue(restored.watched)
        assertTrue(restored.downloaded)
        assertEquals(original.lastNotifiedVersion, restored.lastNotifiedVersion)
        val persisted = LibraryJson.decode(LibraryJson.encode(listOf(restored))).single()
        assertEquals(original.linkedPackage, persisted.linkedPackage)
        assertEquals(original.latest, persisted.latest)
        assertEquals(original.lastNotifiedVersion, persisted.lastNotifiedVersion)
    }
}
