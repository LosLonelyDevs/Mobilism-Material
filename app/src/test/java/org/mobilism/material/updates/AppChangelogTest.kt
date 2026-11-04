package org.mobilism.material.updates

import org.junit.Assert.*
import org.junit.Test

class AppChangelogTest {
    private val notes = "- First change.\n- Second change."

    @Test fun showsInstalledReleaseOnceIncludingWhenVersionsWereSkipped() {
        assertEquals(listOf("First change.", "Second change."), AppChangelog.pending("0.1.20", "0.1.18", notes)?.bullets)
        assertNotNull(AppChangelog.pending("0.1.20", null, notes))
        assertNull(AppChangelog.pending("0.1.20", "0.1.20", notes))
        assertNull(AppChangelog.pending("0.1.19", "0.1.20", notes))
        assertNull(AppChangelog.pending("0.1.20", "0.1.18", ""))
    }
}
