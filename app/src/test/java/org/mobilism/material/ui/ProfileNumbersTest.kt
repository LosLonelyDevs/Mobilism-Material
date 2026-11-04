package org.mobilism.material.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileNumbersTest {
    @Test fun groupsThousandsAndDropsOnlyZeroFraction() {
        assertEquals("1,234,567", formatWrzBalance("1234567.00"))
        assertEquals("1,234.50", formatWrzBalance("1,234.50"))
        assertEquals("0", formatWrzBalance("0.00"))
        assertEquals("-10,001.25", formatWrzBalance("-10001.25"))
        assertEquals("12.125", formatWrzBalance("12.125"))
        assertEquals("Unavailable", formatWrzBalance("Unavailable"))
    }
}
