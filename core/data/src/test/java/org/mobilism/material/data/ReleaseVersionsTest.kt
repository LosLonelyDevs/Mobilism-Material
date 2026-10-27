package org.mobilism.material.data

import org.junit.Assert.*
import org.junit.Test

class ReleaseVersionsTest {
    private fun release(id: Long, title: String, packageName: String? = null) = Release(
        id, title, "", null, id, 0, 0,
        contentHtml = packageName?.let { "<a href='https://play.google.com/store/apps/details?id=$it'>Store</a>" }.orEmpty(),
    )
    @Test fun comparesNumericVersionsAndBuildsRatherThanPostDates() {
        assertTrue(ReleaseVersions.isNewer(release(2, "Tool v1.10"), release(1, "Tool v1.9")))
        assertTrue(ReleaseVersions.isNewer(release(2, "Tool v1.0 build 12"), release(1, "Tool v1.0 build 9")))
        assertFalse(ReleaseVersions.isNewer(release(3, "Tool v1.0 [Premium]"), release(1, "Tool v1.0 [Pro]")))
        assertFalse(ReleaseVersions.isNewer(release(3, "Tool v1.0"), release(1, "Tool v2.0")))
    }
    @Test fun rejectsUnrelatedAppsAndStableToBetaUpdates() {
        assertFalse(ReleaseVersions.isNewer(release(2, "Tool Lite v5.0"), release(1, "Tool v1.0")))
        assertFalse(ReleaseVersions.isNewer(release(2, "Tool v2.0 [Beta]"), release(1, "Tool v1.0")))
        assertTrue(ReleaseVersions.isNewer(release(2, "Tool v2.0"), release(1, "Tool v2.0 beta")))
        assertFalse(ReleaseVersions.isNewer(release(2, "Tool v2.0", "com.other.app"), release(1, "Tool v1.0", "com.example.app")))
        assertTrue(ReleaseVersions.isNewer(release(2, "Renamed Tool v2.0", "com.example.app"), release(1, "Tool v1.0", "com.example.app")))
    }
    @Test fun unknownVersionDoesNotInventAnUpdate() {
        assertFalse(ReleaseVersions.isNewer(release(2, "Tool"), release(1, "Tool")))
    }

    @Test fun singleLetterDistributionSuffixesKeepNumericOrderingWithoutLexicalUpdates() {
        fun version(value: String) = ReleaseTitles.parse("Tool v$value")
        assertTrue(ReleaseVersions.compare(version("1.9.9.43-g"), version("1.9.9.31-g"))!! > 0)
        assertTrue(ReleaseVersions.compare(version("1.9.9.43-d"), version("1.9.9.31-g"))!! > 0)
        assertTrue(ReleaseVersions.compare(version("1.10-f"), version("1.9-g"))!! > 0)
        assertEquals(0, ReleaseVersions.compare(version("1.9.9.43-g"), version("1.9.9.43-d")))
        assertEquals(0, ReleaseVersions.compare(version("1.9.9.43-g"), version("1.9.9.43")))
        assertFalse(ReleaseVersions.newerThan(version("1.9.3.21"), version("1.9.9.31-g")))
        assertNull(ReleaseVersions.compare(ReleaseTitle("Tool", "custom"), version("1.0-g")))
        assertNull(ReleaseVersions.compare(version("1.0--broken"), version("1.0-g")))
        assertNull(ReleaseVersions.compare(version("2.0-unknown"), version("1.0-g")))
        assertFalse(ReleaseVersions.newerThan(version("2.0-rc1-g"), version("1.0-g")))
        assertFalse(ReleaseVersions.newerThan(version("2.0-nightly"), version("1.0-g")))
    }
}
