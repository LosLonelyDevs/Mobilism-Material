package org.mobilism.material.ui

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class ReleaseSectionsTest {
    @Test fun handlesUnderlinedChangelogAndBulletModBlock() {
        val result = ReleaseSections.parse("""<p>App description.</p><b>◉ Premium Activated<br>◉ Debug information removed</b><br><u>What's New:</u><br>No changelog<br><u>More Info:</u><a href="https://example.com">Store</a>""")
        val sections = result.associate { it.kind to Jsoup.parse(it.html).text() }
        assertEquals("App description. More Info:Store", sections[ReleaseSection.Description])
        assertEquals("No changelog", sections[ReleaseSection.Changes])
        assertTrue(sections.getValue(ReleaseSection.Mod).contains("Premium Activated"))
    }
    @Test fun separatesNestedHeadingsWithoutLosingFormattingOrLinks() {
        val sections = ReleaseSections.parse("""<div><p>A useful <em>description</em>.</p><b>What's New:</b><ul><li>Fixed a crash.</li></ul><strong>Mod Info:</strong><p>Premium features. <a href="https://example.com/help">Help</a></p></div>""")
        assertEquals(ReleaseSection.entries, sections.map { it.kind })
        assertTrue(sections[0].html.contains("<em>description</em>"))
        assertEquals("Fixed a crash.", Jsoup.parse(sections[1].html).text())
        assertTrue(sections[2].html.contains("https://example.com/help"))
        assertFalse(sections[0].html.contains("Fixed a crash"))
    }

    @Test fun omitsMissingSectionsAndDoesNotSplitMentionsInsideProse() {
        val html = "<p>Read the changelog to learn what's new in this app.</p>"
        val sections = ReleaseSections.parse(html)
        assertEquals(listOf(ReleaseSection.Description), sections.map { it.kind })
        assertEquals(Jsoup.parse(html).text(), Jsoup.parse(sections.single().html).text())
    }

    @Test fun handlesInlineHeadingAndCurlyApostrophe() {
        val sections = ReleaseSections.parse("<p>Description</p><p>Overview text.</p><b>What’s New: Bug fixes.</b><p>Mod features: Unlocked.</p>")
        assertEquals(listOf("Overview text.", "Bug fixes.", "Unlocked."), sections.map { Jsoup.parse(it.html).text() })
    }
}
