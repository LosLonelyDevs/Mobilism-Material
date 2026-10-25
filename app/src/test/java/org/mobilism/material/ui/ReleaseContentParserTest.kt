package org.mobilism.material.ui

import org.junit.Assert.*
import org.junit.Test

class ReleaseContentParserTest {
    @Test fun preservesDownloadNotesWithoutTurningForumHelpIntoAHost() {
        val parsed = ReleaseContentParser.parse(
            """<p>Overview</p><b>Download Instructions:</b>
                <p>Password: example</p><a href="https://rapidgator.net/file/one">Mirror</a>
                <a href="https://forum.mobilism.org/viewtopic.php?t=123">Download help</a>""",
            emptyList(), "https://forum.mobilism.org/",
        )
        assertEquals(1, parsed.downloads.size)
        assertTrue(parsed.downloads.single().notesHtml.contains("Password: example"))
        assertTrue(parsed.downloads.single().notesHtml.contains("Download help"))
        assertTrue(parsed.downloads.single().notesHtml.contains("forum.mobilism.org"))
        assertFalse(parsed.bodyHtml.contains("rapidgator.net"))
    }

    @Test fun extractsMirrorsAndPreservesArchitectureAndDescriptionLinks() {
        val html = """
            <p>Details <a href="https://play.google.com/store/apps/details?id=example">Store</a></p>
            <b>Download Instructions:</b><br>armeabi-v7a:<br>
            <a href="https://upfilesgo.com/first">https://upfilesgo.com/first</a><br>
            Mirrors:<br><a href="https://rapidgator.net/file/one">Mirror</a><br>
            arm64-v8a:<br>https://upfilesgo.com/second
        """.trimIndent()
        val parsed = ReleaseContentParser.parse(html, listOf("https://rapidgator.net/file/one"), "https://forum.mobilism.org/")
        assertEquals(3, parsed.downloads.size)
        assertEquals("armeabi-v7a", parsed.downloads[0].section)
        assertEquals("arm64-v8a", parsed.downloads[2].section)
        assertTrue(parsed.bodyHtml.contains("play.google.com"))
        assertFalse(parsed.bodyHtml.contains("rapidgator.net"))
        assertFalse(parsed.bodyHtml.contains("upfilesgo.com"))
    }

    @Test fun withoutMarkerOnlyKnownDownloadsAreRemoved() {
        val parsed = ReleaseContentParser.parse(
            """<a href="https://example.org/help">Help</a><br><a href="https://rapidgator.net/file/one">File</a>""",
            listOf("https://rapidgator.net/file/one"), "https://forum.mobilism.org/",
        )
        assertTrue(parsed.bodyHtml.contains("example.org/help"))
        assertFalse(parsed.bodyHtml.contains("rapidgator.net"))
        assertEquals(1, parsed.downloads.size)
    }

    @Test fun refusesExecutableLinksAndDeduplicatesUrls() {
        val parsed = ReleaseContentParser.parse(
            """Download Links:<br><a href="javascript:alert(1)">Bad</a><a href="//files.example.org/a">File</a>""",
            listOf("https://files.example.org/a", "file:///private/data"),
            "https://forum.mobilism.org/",
        )
        assertEquals(listOf("https://files.example.org/a"), parsed.downloads.map { it.url })
    }

    @Test fun keepsGmsCoreParentAcrossBothCpuChildrenAndTheirMirrors() {
        val html = "<u>Download Instructions:</u><br>" +
            cpu("armeabi-v7a", "standard32") + cpu("arm64-v8a", "standard64") +
            "<b>with <span>GmsCore</span> support:</b><br>" +
            cpu("armeabi-v7a", "gms32") + cpu("arm64-v8a", "gms64") +
            "Trouble downloading? Read <a href='viewtopic.php?t=649944'>This</a>"
        val parsed = ReleaseContentParser.parse(html, emptyList(), "https://forum.mobilism.org/")
        val groups = DownloadGroups.group(parsed.downloads, org.mobilism.material.settings.DownloadPreferences())
        assertEquals(4, groups.size)
        assertEquals(12, groups.sumOf { it.links.size })
        listOf("armeabi-v7a" to "32", "arm64-v8a" to "64").forEach { (cpu, suffix) ->
            assertTrue(groups.single { it.title == cpu }.links.all { it.url.contains("standard$suffix") })
            assertTrue(groups.single { it.title == "with GmsCore support / $cpu" }.links.all { it.url.contains("gms$suffix") })
        }
        assertTrue(parsed.downloadNotesHtml.contains("viewtopic.php?t=649944"))
        assertFalse(parsed.bodyHtml.contains("GmsCore"))
    }

    @Test fun groupsArbitraryIconAndAppVariantsWithoutCpuLabels() {
        val html = "<h2>Download Links:</h2>" +
            "<h3>Player</h3><h4>Blue icon</h4>" + mirrors("player-blue") +
            "<h4>Red icon</h4>" + mirrors("player-red") +
            "<h3>Recorder</h3><h4>Blue icon</h4>" + mirrors("recorder-blue") +
            "<h4>Red icon</h4>" + mirrors("recorder-red")
        val parsed = ReleaseContentParser.parse(html, emptyList(), "https://forum.mobilism.org/")
        val groups = DownloadGroups.group(parsed.downloads, org.mobilism.material.settings.DownloadPreferences())
        assertEquals(setOf("Player / Blue icon", "Player / Red icon", "Recorder / Blue icon", "Recorder / Red icon"), groups.map { it.title }.toSet())
        assertTrue(groups.all { it.links.size == 3 })
    }

    @Test fun keepsArbitraryNotesAndLinkedInstructionsBesideTheirVariant() {
        val html = "<b>Download Instructions:</b><br><b>Minimal icon:</b><br>" +
            "<p>Save the existing configuration before switching packages.</p>" +
            "<p>Read <a href='https://docs.example.org/setup'>the setup guide</a> before installing.</p>" + mirrors("minimal") +
            "<b>Classic icon:</b><br><p>Password: example</p>" + mirrors("classic")
        val parsed = ReleaseContentParser.parse(html, emptyList(), "https://forum.mobilism.org/")
        val groups = DownloadGroups.group(parsed.downloads, org.mobilism.material.settings.DownloadPreferences())
        val minimal = groups.single { it.title == "Minimal icon" }
        assertTrue(minimal.notesHtml.contains("existing configuration"))
        assertTrue(minimal.notesHtml.contains("https://docs.example.org/setup"))
        assertTrue(groups.single { it.title == "Classic icon" }.notesHtml.contains("Password: example"))
        assertEquals(6, parsed.downloads.size)
        assertFalse(parsed.downloads.any { it.url.contains("docs.example.org") })
    }

    @Test fun repeatedLabelAfterAnotherVariantDoesNotMergeUnrelatedRuns() {
        val parsed = ReleaseContentParser.parse("Download Links:<br><b>Blue icon:</b><br>" + mirrors("first") +
            "<b>Red icon:</b><br>" + mirrors("second") + "<b>Blue icon:</b><br>" + mirrors("third"),
            emptyList(), "https://forum.mobilism.org/")
        val groups = DownloadGroups.group(parsed.downloads, org.mobilism.material.settings.DownloadPreferences())
        assertEquals(3, groups.size)
        assertEquals(3, groups.map { it.id }.distinct().size)
        assertEquals(2, groups.count { it.title == "Blue icon" })
    }

    @Test fun retainsSameUrlWhenItIsExplicitlyListedForDifferentVariants() {
        val parsed = ReleaseContentParser.parse("Download Links:<br><b>Blue icon:</b><br>" + mirrors("shared") +
            "<b>Red icon:</b><br>" + mirrors("shared"), listOf("https://files.example.org/shared"), "https://forum.mobilism.org/")
        assertEquals(2, parsed.downloads.count { it.url == "https://files.example.org/shared" })
        assertEquals(2, parsed.downloads.map { it.groupId }.distinct().size)
    }

    @Test fun nestedPlainUrlsAndAdjacentRepeatedHeadingsRemainMirrors() {
        val parsed = ReleaseContentParser.parse("<b>Download Links:</b><br><b>Compact variant:</b><p><strong>https://files.example.org/a</strong></p>" +
            "<b>Compact variant:</b><br><b>Mirrors:</b><p>https://mirror.example.org/b</p>", emptyList(), "https://forum.mobilism.org/")
        assertEquals(2, parsed.downloads.size)
        assertEquals(1, parsed.downloads.map { it.groupId }.distinct().size)
    }

    @Test fun mirrorNotesDoNotDetachUrlsFromTheirVariant() {
        val parsed = ReleaseContentParser.parse("Download Links:<br><b>Compact icon:</b><br>" +
            "<a href='https://files.example.org/a'>File</a><br><p>Password: sample</p>Mirrors:<br>" +
            "<a href='https://mirror.example.org/b'>Mirror</a>", emptyList(), "https://forum.mobilism.org/")
        assertEquals(1, parsed.downloads.map { it.groupId }.distinct().size)
        assertEquals("Compact icon", parsed.downloads.first().section)
        assertTrue(parsed.downloads.all { it.notesHtml.contains("Password: sample") })
    }

    @Test fun inlineVariantLabelsAndMirrorPrefixesKeepTheirMeaning() {
        val html = "<b>Download Links:</b><br><b>Blue icon:</b> <a href='https://files.example.org/a'>File</a><br>" +
            "Mirrors: <a href='https://mirror.example.org/a'>Mirror</a><br>" +
            "<b>Red icon:</b> <a href='https://files.example.org/b'>File</a>"
        val parsed = ReleaseContentParser.parse(html, emptyList(), "https://forum.mobilism.org/")
        assertEquals(listOf("Blue icon", "Blue icon", "Red icon"), parsed.downloads.map { it.section })
    }

    @Test fun preservesLongNotesAndDoesNotTreatAMarkerMentionAsASection() {
        val before = "<p>Read the Download Instructions: on the vendor website.</p><a href='https://example.org/docs'>Docs</a>"
        assertTrue(ReleaseContentParser.parse(before, emptyList(), "https://forum.mobilism.org/").downloads.isEmpty())
        val note = "<p>" + "Keep this detail. ".repeat(20) + "</p>"
        val parsed = ReleaseContentParser.parse("Download Links:<br>" + note + mirrors("long"), emptyList(), "https://forum.mobilism.org/")
        assertEquals(20, Regex("Keep this detail").findAll(parsed.downloads.first().notesHtml).count())
    }

    private fun cpu(label: String, id: String) = "<b>$label:</b><br>" + mirrors(id)
    private fun mirrors(id: String) = "<a href='https://files.example.org/$id'>File</a><br><br>Mirrors:<br>" +
        "<a href='https://mirror.example.org/$id'>Mirror</a><br><a href='https://backup.example.org/$id'>Mirror</a><br><br>"
}
