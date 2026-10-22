package org.mobilism.material.ui

import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.ReleaseTitle
import org.mobilism.material.data.ReleaseTitles

class ReleaseTitleTest {
    @Test fun separatesVersionBuildAndMultipleTags() {
        assertEquals(ReleaseTitle("Dual Space - Multiple Accounts", "6.0.8", "130", listOf("Premium")),
            ReleaseTitles.parse("Dual Space - Multiple Accounts v6.0.8 build 130 [Premium]"))
        assertEquals(ReleaseTitle("App & Tools", "2.3.0-beta", "425", listOf("Mod", "arm64-v8a")),
            ReleaseTitles.parse("App &amp; Tools v2.3.0-beta (425) [Mod] [arm64-v8a]"))
        assertEquals(ReleaseTitle("Music", "9.1.94.1912", tags = listOf("beta", "Mod by author")),
            ReleaseTitles.parse("Music v9.1.94.1912 beta [Mod by author]"))
    }

    @Test fun preservesAmbiguousTitlesAndNumbersInNames() {
        listOf("2048", "1Password 8", "Book v2 of the series", "Tool v2.0 special edition", "[Project] Notes").forEach {
            assertEquals(ReleaseTitle(it), ReleaseTitles.parse(it))
        }
        assertEquals("8 Ball Pool", ReleaseTitles.parse("8 Ball Pool v56.2.1 [Mod]").name)
        assertEquals(ReleaseTitle("Tool", tags = listOf("Premium")), ReleaseTitles.parse("Tool [Premium]"))
    }
}
