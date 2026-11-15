package org.mobilism.material.ui

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class ForumCodeBlocksTest {
    @Test fun moreInfoKeepsProjectUrlAndDropsSelectAllControlBeforeSectionParsing() {
        val html = """<p>Description.</p><u>More Info:</u><div class="codebox"><span>Code:
            <a href="#" onclick="selectCode(this);return false;">Select all</a></span>
            <code>https://github.com/LosLonelyDevs/Mobilism-Material</code></div>
            <u>Download Instructions:</u><br><a href="https://send.now/example">Download</a>"""
        val parsed = ReleaseContentParser.parse(html, emptyList(), "https://forum.mobilism.org/viewtopic.php?t=6451540")
        val description = ReleaseSections.parse(parsed.bodyHtml).single { it.kind == ReleaseSection.Description }.html
        assertTrue(description.contains("More Info:"))
        assertTrue(description.contains("https://github.com/LosLonelyDevs/Mobilism-Material"))
        assertEquals(1, Jsoup.parse(description).select("pre code").size)
        assertFalse(description.contains("Select all"))
        assertFalse(description.contains("Code:"))
        assertFalse(description.contains("selectCode"))
        assertEquals("https://send.now/example", parsed.downloads.single().url)
    }

    @Test fun preservesLiteralCodeIndentationAndEntities() {
        val html = "<div class='codebox'><span>Code: <a href='#'>Select all</a></span>" +
            "<code>if (a &lt; b) {\n  keep();\n}</code></div>"
        val parsed = Jsoup.parse(ForumCodeBlocks.normalize(html, "https://forum.mobilism.org/"))
        assertEquals("if (a < b) {\n  keep();\n}", parsed.selectFirst("pre code")!!.wholeText())
    }
}
