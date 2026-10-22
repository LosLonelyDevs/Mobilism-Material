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
        assertTrue(parsed.bodyHtml.contains("Password: example"))
        assertTrue(parsed.bodyHtml.contains("Download help"))
        assertTrue(parsed.bodyHtml.contains("forum.mobilism.org"))
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
        assertEquals(listOf(DownloadLink("https://files.example.org/a")), parsed.downloads)
    }
}
