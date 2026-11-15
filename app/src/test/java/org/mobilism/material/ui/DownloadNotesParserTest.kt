package org.mobilism.material.ui

import org.junit.Assert.*
import org.junit.Test

class DownloadNotesParserTest {
    private val baseUrl = "https://forum.mobilism.org/viewtopic.php?t=42"
    private fun prompt(url: String) = "Trouble downloading? Read <a href='$url'><b>This</b></a>."

    @Test fun recognizesTheExactHelpTopicWithEncodedOrRelativeLinks() {
        listOf("https://forum.mobilism.org/viewtopic.php?f=399&amp;t=1489943",
            "./viewtopic.php?t=1489943&f=399").forEach { url ->
            val notes = DownloadNotesParser.parse("<p>${prompt(url)}</p>", baseUrl)
            assertEquals(1, notes.size)
            assertTrue(notes.single().help)
        }
    }

    @Test fun preservesSurroundingInstructionsAndLinksInOrder() {
        val notes = DownloadNotesParser.parse("<p><b>Password:</b> sample</p>" +
            "<p>${prompt("/viewtopic.php?t=1489943")}</p>" +
            "<p>Keep <a href='https://example.org/setup'>these instructions</a>.</p>", baseUrl)
        assertEquals(listOf(false, true, false), notes.map { it.help })
        assertTrue(notes.first().html.contains("<b>Password:</b> sample"))
        assertTrue(notes.last().html.contains("https://example.org/setup"))
    }

    @Test fun doesNotReplaceOtherTopicsLookalikeHostsOrMixedLinkInstructions() {
        listOf(prompt("https://example.org/viewtopic.php?t=1489943"),
            prompt("/viewtopic.php?t=649944"),
            prompt("/viewtopic.php?t=1489943") + " <a href='/viewtopic.php?t=42'>More help</a>",
            "Custom instructions: <a href='/viewtopic.php?t=1489943'>read the guide</a>.").forEach { html ->
            val notes = DownloadNotesParser.parse(html, baseUrl)
            assertFalse(notes.any { it.help })
            assertTrue(notes.single().html.contains("href="))
        }
    }
}
