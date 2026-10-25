package org.mobilism.material.ui

import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.settings.CpuArchitecture
import org.mobilism.material.settings.DownloadPreferences

class DownloadGroupsTest {
    @Test fun groupsAliasesAndAlphabetizesWithinFavoriteAndOtherHosts() {
        val links = listOf(
            DownloadLink("https://upfiles.com/a", "ARM64"),
            DownloadLink("https://rg.to/b", "arm64-v8a"),
            DownloadLink("https://cdn.devuploads.com/c", "ARM64-v8A"),
        )
        val groups = DownloadGroups.group(links, DownloadPreferences(preferredHosts = setOf("rapidgator.net", "devuploads.com")))
        assertEquals(1, groups.size)
        assertEquals("arm64-v8a", groups.single().title)
        assertEquals(listOf(links[2], links[1], links[0]), groups.single().links)
    }

    @Test fun collapsesOnlyExplicitNonMatchingArchitecturesAndKeepsUniversalAndUnknownVisible() {
        val links = listOf(DownloadLink("https://example.com/x", "x86_64"),
            DownloadLink("https://example.com/u", "Universal"), DownloadLink("https://example.com/unknown"),
            DownloadLink("https://example.com/a", "armeabi-v7a / arm64-v8a"))
        val groups = DownloadGroups.group(links, DownloadPreferences(architecture = CpuArchitecture.Arm64))
        assertEquals(4, groups.size)
        assertEquals("x86_64", groups.last().title)
        assertTrue(groups.last().collapsed)
        assertTrue(groups.dropLast(1).none { it.collapsed })
        assertEquals(4, groups.sumOf { it.links.size })
    }
}
