package org.mobilism.material.ui

import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.DebridAccount
import org.mobilism.material.data.DebridProvider
import org.mobilism.material.data.DebridState
import org.mobilism.material.data.SupportedHost
import org.mobilism.material.settings.CpuArchitecture
import org.mobilism.material.settings.DownloadPreferences

class DownloadGroupsTest {
    @Test fun keepsNonCpuVariantsAndSupportLabelsDistinct() {
        val links = listOf(DownloadLink("https://files.example.org/a", "arm64 / Original icon"),
            DownloadLink("https://files.example.org/b", "arm64 / Alternate icon"),
            DownloadLink("https://files.example.org/c", "GmsCore support / arm64"),
            DownloadLink("https://files.example.org/d", "Companion app"))
        val groups = DownloadGroups.group(links, DownloadPreferences(architecture = CpuArchitecture.Arm64))
        assertEquals(4, groups.size)
        assertTrue(groups.none { it.collapsed })
        assertTrue(groups.any { it.title == "GmsCore support / arm64" })
    }

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

    @Test fun premiumPriorityStaysWithinEachDownloadVariant() {
        val links = listOf("arm64-v8a", "x86").flatMap { architecture ->
            listOf("a.example", "b.example", "c.example").map { DownloadLink("https://$it/file", architecture) }
        }
        val debrid = DebridState(listOf(DebridAccount(DebridProvider.RealDebrid, configured = true,
            validated = true, hostsVerified = true, hosts = listOf(SupportedHost("c.example", "C")))))
        val groups = DownloadGroups.group(links, DownloadPreferences(preferredHosts = setOf("b.example")), debrid)
        assertEquals(2, groups.size)
        groups.forEach { group ->
            assertEquals(listOf("https://c.example/file", "https://b.example/file", "https://a.example/file"), group.links.map { it.url })
            assertEquals(1, group.links.map { it.section }.distinct().size)
        }
    }
}
