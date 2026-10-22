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
}
