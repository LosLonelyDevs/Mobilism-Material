package org.mobilism.material.updates

import org.junit.Assert.*
import org.junit.Test

class AppChangelogTest {
    @Test fun showsInstalledReleaseOnceIncludingWhenVersionsWereSkipped() {
        assertEquals("0.1.20", AppChangelog.pending("0.1.20", "0.1.18")?.version)
        assertNotNull(AppChangelog.pending("0.1.20", null))
        assertNull(AppChangelog.pending("0.1.20", "0.1.20"))
        assertNull(AppChangelog.pending("0.1.19", "0.1.20"))
    }
}
