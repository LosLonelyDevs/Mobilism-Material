package org.mobilism.material.ui

import org.junit.Assert.*
import org.junit.Test

class DownloadHelpContentTest {
    private val baseUrl = "https://forum.mobilism.org/viewtopic.php?f=399&t=1489943"
    private fun spoiler(content: String) = """<div style="background-color:white">
        <div><span onclick="toggle()"><b>Expand: </b><a href="#">show</a></span></div>
        <div class="quotecontent"><div style="display:none">$content</div></div></div>"""

    @Test fun keepsHostStepsAndImagesTogetherWithoutBrowserControls() {
        val blocks = DownloadHelpContent.parse("<p>General instructions.</p><b>4file.net:</b><br>" +
            spoiler("<b>First step:</b><br><img src='/first.png'><br><b>Second step:</b><br><img src='/second.png'>") +
            "<br><b>send.now:</b><br>" + spoiler("<p>Other instructions.</p>") + "<p>Final note.</p>", baseUrl)
        assertEquals(4, blocks.size)
        val first = blocks[1] as HelpBlock.Section
        assertEquals("4file.net", first.title)
        assertEquals(listOf(HelpBlock.Text::class, HelpBlock.Image::class, HelpBlock.Text::class, HelpBlock.Image::class),
            first.blocks.map { it::class })
        assertEquals("https://forum.mobilism.org/first.png", (first.blocks[1] as HelpBlock.Image).url)
        assertTrue((first.blocks[2] as HelpBlock.Text).html.contains("Second step:"))
        assertEquals("send.now", (blocks[2] as HelpBlock.Section).title)
        assertTrue((blocks.last() as HelpBlock.Text).html.contains("Final note."))
        assertFalse(blocks.toString().contains("Expand:"))
        assertFalse(blocks.toString().contains("toggle()"))
    }

    @Test fun retainsListsLinksAndSafeImagesWithoutInventingHeaders() {
        val blocks = DownloadHelpContent.parse("<p>Intro <a href='https://example.org'>link</a>.</p>" +
            "<ul><li>First item</li><li>Second item</li></ul>" + spoiler("<img src='javascript:bad'><p>More help</p>"), baseUrl)
        assertTrue((blocks.first() as HelpBlock.Text).html.contains("<li>Second item</li>"))
        assertTrue((blocks.first() as HelpBlock.Text).html.contains("https://example.org"))
        assertEquals("More instructions", (blocks.last() as HelpBlock.Section).title)
        assertFalse(blocks.toString().contains("javascript:"))
    }
}
