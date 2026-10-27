package org.mobilism.material.data

import org.junit.Assert.*
import org.junit.Test

class FollowingVersionsTest {
    private fun release(id: Long, version: String, suffix: String = "") = Release(id, "Example v$version $suffix".trim(), "", null, 0, 0, 0,
        contentHtml = "<a href=\"https://play.google.com/store/apps/details?id=org.example\">Play</a>")
    private fun installed(version: String, code: Long = 10) = InstalledVersion("org.example", "Example", version, code)

    @Test fun televizoFlavorSuffixesDoNotFallBackToAnOlderSuffixFreeRelease() {
        fun televizo(id: Long, version: String) = release(id, version).copy(
            title = "Televizo - IPTV player v$version [Final] [Pro] [Mod]")
        val followed = televizo(6362786, "1.9.9.31-g")
        val stale = televizo(4694627, "1.9.3.21")
        val newer = televizo(6422874, "1.9.9.43-d")
        val entry = LibraryEntry(followed, watched = true, latest = stale)
        val current = installed("1.9.9.31-g").copy(name = "Televizo")
        val result = FollowingVersions.evaluate(entry, current, listOf(followed, newer, stale))
        assertEquals(newer, result.latest)
        assertEquals(newer, result.update)
        assertTrue(result.comparable)
        val saved = entry.copy(latest = result.latest, update = result.update)
        assertEquals(newer, FollowingVersions.evaluate(saved, current, listOf(stale)).latest)
        assertNull(FollowingVersions.evaluate(saved, current.copy(version = "1.9.9.43-g"), emptyList()).update)
        assertNull(FollowingVersions.evaluate(saved, current.copy(version = "1.9.9.44-g"), emptyList()).update)
    }

    @Test fun flavorSuffixesPreserveStableChannelAndAppIdentityGuards() {
        val entry = LibraryEntry(release(1, "1.9.9.31-g"), watched = true)
        val current = installed("1.9.9.31-g")
        val result = FollowingVersions.evaluate(entry, current, listOf(
            release(2, "1.9.9.50-beta1-g"), release(3, "1.9.9.60-nightly"),
            release(4, "1.9.9.70-g").copy(contentHtml = "<a href='https://play.google.com/store/apps/details?id=org.other'>Play</a>"),
        ))
        assertEquals(entry.release, result.latest)
        assertNull(result.update)
        assertTrue(result.comparable)
    }

    @Test fun installedVersionTakesPriorityOverFollowedRelease() {
        val entry = LibraryEntry(release(1, "3.0"), watched = true)
        assertEquals(1L, FollowingVersions.evaluate(entry, installed("2.0"), emptyList()).update?.id)
        assertNull(FollowingVersions.evaluate(entry, installed("3.0"), emptyList()).update)
        assertNull(FollowingVersions.evaluate(entry, installed("4.0"), emptyList()).update)
    }

    @Test fun installedBuildAndUnknownVersionsStayConservative() {
        val entry = LibraryEntry(release(1, "3.0", "build 12"), watched = true)
        assertNotNull(FollowingVersions.evaluate(entry, installed("3.0", 10), emptyList()).update)
        assertNull(FollowingVersions.evaluate(entry, installed("3.0", 12), emptyList()).update)
        val unknown = FollowingVersions.evaluate(entry, installed("custom"), emptyList())
        assertFalse(unknown.comparable)
        assertNull(unknown.update)
    }

    @Test fun uninstalledAppsTrackFollowedVersionAndRetainKnownNewerReleases() {
        val entry = LibraryEntry(release(1, "1.0"), watched = true, latest = release(2, "2.0"))
        assertEquals(2L, FollowingVersions.evaluate(entry, null, emptyList()).update?.id)
        assertEquals(3L, FollowingVersions.evaluate(entry, null, listOf(release(3, "3.0"))).update?.id)
    }

    @Test fun matchingUsesPackageThenUniqueExactName() {
        val known = LibraryEntry(release(1, "1.0"))
        assertEquals(installed("1.0"), FollowingVersions.matchingInstalled(known, listOf(installed("1.0"))))
        val noPackage = known.copy(release = known.release.copy(contentHtml = ""))
        val duplicate = installed("1.0").copy(packageName = "org.other")
        assertNull(FollowingVersions.matchingInstalled(noPackage, listOf(installed("1.0"), duplicate)))
        assertEquals(duplicate, FollowingVersions.matchingInstalled(noPackage.copy(linkedPackage = "org.other"), listOf(installed("1.0"), duplicate)))
    }

    @Test fun notificationsIgnoreSameVersionRepostsAndRejectTvAndBeta() {
        val entry = LibraryEntry(release(1, "1.0"), watched = true)
        assertEquals(FollowingVersions.notificationKey(release(2, "2.0")), FollowingVersions.notificationKey(release(3, "2.0", "[Premium]")))
        val result = FollowingVersions.evaluate(entry, installed("1.0"), listOf(release(2, "3.0", "[TV]"), release(3, "4.0-beta1")))
        assertNull(result.update)
    }
}
