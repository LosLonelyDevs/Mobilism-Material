package org.mobilism.material.ui

import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.ReleaseTitle
import org.mobilism.material.data.ReleaseTitles

class ReleaseTitleTest {
    @Test fun separatesVersionBuildAndMultipleTags() {
        assertEquals(ReleaseTitle("Example - Multiple Accounts", "6.0.8", "130", listOf("Premium")),
            ReleaseTitles.parse("Example - Multiple Accounts v6.0.8 build 130 [Premium]"))
        assertEquals(ReleaseTitle("App & Tools", "2.3.0-beta", "425", listOf("Mod", "arm64-v8a")),
            ReleaseTitles.parse("App &amp; Tools v2.3.0-beta (425) [Mod] [arm64-v8a]"))
        assertEquals(ReleaseTitle("Music", "9.1.94.1912", tags = listOf("beta", "Mod by author")),
            ReleaseTitles.parse("Music v9.1.94.1912 beta [Mod by author]"))
    }

    @Test fun preservesAmbiguousTitlesAndNumbersInNames() {
        listOf("2048", "Example 8", "Book v2 of the series", "Example 2.0 edition", "[Project] Notes").forEach {
            assertEquals(ReleaseTitle(it), ReleaseTitles.parse(it))
        }
        assertEquals("8 Example", ReleaseTitles.parse("8 Example v56.2.1 [Mod]").name)
        assertEquals(ReleaseTitle("Tool", tags = listOf("Premium")), ReleaseTitles.parse("Tool [Premium]"))
    }

    @Test fun keepsNamesSeparateWhenMetadataIsUnfamiliarOrTruncated() {
        assertEquals(ReleaseTitle("Example: A subtitle", "1.1.13", tags = listOf("18+ Adult Content", "Premium", "[Mo")),
            ReleaseTitles.parse("Example: A subtitle v1.1.13 [18+ Adult Content] [Premium] [Mo"))
        assertEquals(ReleaseTitle("EXAMPLE", "0.67", tags = listOf("Fixed", "18+ Adult Content", "Mod Extra")),
            ReleaseTitles.parse("EXAMPLE v0.67 Fixed [18+ Adult Content] [Mod Extra]"))
        assertEquals(ReleaseTitle("Example", "26.2.2", "20260202", listOf("adfree", "18+")),
            ReleaseTitles.parse("Example v26.2.2 b20260202 [adfree] (18+)"))
        assertEquals(ReleaseTitle("Example", "2.0", tags = listOf("special edition")),
            ReleaseTitles.parse("Example v2.0 special edition"))
    }

    @Test fun acceptsAlternateVersionLabelsWithoutAnAppNameAllowlist() {
        listOf("Version 2.4", "ver. 2.4", "V 2.4", "v:2.4", "2.4").forEach { version ->
            assertEquals(ReleaseTitle("Example", "2.4", tags = listOf("Premium")),
                ReleaseTitles.parse("Example $version [Premium]"))
        }
        assertEquals(ReleaseTitle("Example", "3", tags = listOf("Final")), ReleaseTitles.parse("Example v3 Final"))
        assertEquals(ReleaseTitle("Example", "2.4", "42", listOf("Mod", "beta 2")),
            ReleaseTitles.parse("Example v2.4 [Mod] build 42 beta 2"))
    }
}
