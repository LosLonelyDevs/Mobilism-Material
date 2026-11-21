package org.mobilism.material.ui

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.settings.DeveloperPreferences

class ForumFormattingTest {
    @Test fun descriptionPreservesBothVersionsInSmallerSecondaryText() {
        val title = "MovieboxTV v1.2.3.1113.03 v1.18.0"
        val block = format("<b>$title</b><br>Requirements: Android 9", cleanup = true).child(0)
        assertTrue(styleDescriptionTitle(block, title, 0x112233, 0x445566))
        assertEquals("MovieboxTV", block.selectFirst("font[color=#112233] big b")?.text())
        assertEquals("v1.2.3.1113.03 · v1.18.0", block.selectFirst("font[color=#445566] small")?.text())
        val bare = format("MovieboxTV 1.18.0", cleanup = true).child(0)
        assertTrue(styleDescriptionTitle(bare, title, 0, 0))
        assertEquals("1.18.0", bare.selectFirst("small")?.text())
        val intro = format("Updates shipped in v2.0 yesterday.", cleanup = true).child(0)
        assertFalse(styleDescriptionTitle(intro, title, 0, 0))
    }

    private val base = "https://forum.mobilism.org/"
    private fun format(html: String, cleanup: Boolean = false, markdown: Boolean = false) =
        Jsoup.parseBodyFragment(ForumFormatting.format(html, base, ForumFormattingOptions(cleanup, markdown)), base).body()

    @Test fun plainFormattingPreservesHtmlAndDeveloperModeGatesMarkdown() {
        val html = "<b>Title</b><br>-One<br>**Literal**<br>https://example.org/file_name"
        assertEquals(html, ForumFormatting.format(html, base, ForumFormattingOptions()))
        assertFalse(DeveloperPreferences().markdownSyntaxActive)
        val disabled = DeveloperPreferences(markdownSyntax = true)
        assertFalse(disabled.markdownSyntaxActive)
        assertTrue(disabled.copy(enabled = true).markdownSyntaxActive)
    }

    @Test fun changelogDashesBecomeListsAndExistingProseGetsParagraphSpacing() {
        val body = format("<b>MyPGallery v1.1.3</b><br>First paragraph.<br>Second paragraph.<br>" +
            "VERSION 1.1.3 b370<br>-Set app version to 1.1.3<br>-Fixing tests<br>-Added 7 new video sites", cleanup = true)
        assertEquals(body.html(), listOf("Set app version to 1.1.3", "Fixing tests", "Added 7 new video sites"), body.select("li").map { it.text() })
        assertTrue(body.select("p").any { it.text() == "First paragraph." })
        assertTrue(body.select("p").any { it.text() == "Second paragraph." })
        assertEquals("MyPGallery v1.1.3", body.selectFirst("h4")?.text())
    }

    @Test fun arrowFeaturesKeepDescriptionsWithTheirBullet() {
        val body = format("FEATURES YOU'LL LOVE<br>➤ Listen at Any Speed<br>Hear any text up to 4.5x faster.<br>" +
            "➤ Snap a photo to audio<br>Take a photo of any text.<br>Privacy &amp; Security First<br>Your data stays local.", cleanup = true)
        assertEquals(2, body.select("li").size)
        assertEquals(body.html(), "Listen at Any Speed Hear any text up to 4.5x faster.", body.select("li")[0].text())
        assertTrue(body.select("h4").any { it.text() == "Privacy & Security First" })
    }

    @Test fun cleanupPreservesDuplicateTextCreditsHashesCodeAndInlineLinkTargets() {
        val hash = "2d74ad80fda65ce143e2846bb1209e5ed8824edadaf6c1b5ac4fc191d126648a8"
        val source = "Repeated paragraph.<br>Repeated paragraph.<br>- Sir Richard Branson<br>" +
            "SHA-256: $hash<br>Size: 24.1 MB<br>Read <a href='/viewtopic.php?t=42'>this guide</a> first.<br>" +
            "<pre><code>-not a list\n**not bold**\n  indented</code></pre>"
        val body = format(source, cleanup = true)
        assertEquals(2, body.select("p").count { it.text() == "Repeated paragraph." })
        assertTrue(body.text().contains("- Sir Richard Branson"))
        assertTrue(body.text().contains(hash))
        assertFalse(body.select("h4").any { it.text().startsWith("SHA-256") })
        assertEquals("Read this guide first.", body.select("p").first { it.text().startsWith("Read ") }.text())
        assertEquals(base + "viewtopic.php?t=42", body.selectFirst("a")?.absUrl("href"))
        assertEquals("-not a list\n**not bold**\n  indented", body.selectFirst("code")?.wholeText())
    }

    @Test fun markdownHandlesHeadingsEmphasisLinksQuotesListsAndFencesInForumHtml() {
        val body = format("<div># Heading<br><br>**Bold** and *italic*, `x_y`.<br><br>" +
            "[Guide](https://example.org/a?x=1&amp;y=2)<br><br>&gt; Quoted text<br><br>" +
            "3. Three<br>4. Four<br><br>```kotlin<br>val literal = \"**text**\"<br>```</div>", markdown = true)
        assertEquals("Heading", body.selectFirst("h1")?.text())
        assertEquals("Bold", body.selectFirst("strong")?.text())
        assertEquals("italic", body.selectFirst("em")?.text())
        assertEquals("x_y", body.selectFirst("code")?.text())
        assertEquals("https://example.org/a?x=1&y=2", body.selectFirst("a")?.attr("href"))
        assertEquals("Quoted text", body.selectFirst("blockquote")?.text())
        assertEquals("3", body.selectFirst("ol")?.attr("start"))
        assertEquals(body.html(), "val literal = \"**text**\"\n", body.selectFirst("pre code")?.wholeText())
    }

    @Test fun markdownCoexistsWithExistingLinksStylesListsAndCode() {
        val source = "<p><b>Existing bold</b> plus **new bold** and " +
            "<a href='https://example.org/a_b'>literal_label</a>.</p>" +
            "<ul><li>Existing **list**<ul><li>Nested item</li></ul></li></ul>" +
            "<pre>**literal**\n- keep</pre>"
        val body = format(source, markdown = true)
        assertEquals("Existing bold", body.selectFirst("b")?.text())
        assertEquals("literal_label", body.selectFirst("a")?.text())
        assertEquals("https://example.org/a_b", body.selectFirst("a")?.attr("href"))
        assertEquals(listOf("new bold", "list"), body.select("strong").map { it.text() })
        assertEquals(2, body.select("li").size)
        assertEquals("**literal**\n- keep", body.selectFirst("pre")?.wholeText())
    }

    @Test fun markdownImagesBecomeLinksWithoutAddingAnyImageSource() {
        val body = format("![Screenshot](https://example.org/new.png) and ![](https://example.org/empty.png)", markdown = true)
        assertTrue(body.select("img").isEmpty())
        assertEquals(listOf("Screenshot", "Image"), body.select("a").map { it.text() })
    }

    @Test fun forumAutolinksInsideMarkdownDestinationsKeepTheFullUrl() {
        val body = format("[Guide](<a href='https://example.org/a_b?x=1&amp;y=2'>https://example.org/a_b?x=1&amp;y=2</a>)", markdown = true)
        assertEquals(1, body.select("a").size)
        assertEquals("Guide", body.selectFirst("a")?.text())
        assertEquals("https://example.org/a_b?x=1&y=2", body.selectFirst("a")?.attr("href"))
    }

    @Test fun fencedCodeKeepsLeadingSpacesAndMultipleBlankLinesAfterLists() {
        val html = "3. Three<br><br>4. Four<br>   1. Child<br><br>```<br>  indented<br><br><br>  kept<br>```"
        val normalized = ForumCodeBlocks.normalize(html, base)
        assertEquals("  indented\n\n\n  kept\n", format(normalized, markdown = true).selectFirst("pre code")?.wholeText())
    }

    @Test fun switchesWorkIndependentlyAndTogetherWithoutParsingCodeAsLists() {
        val source = "# Features<br><br>**Bold**<br><br>-One<br>-Two<br><br>```<br>-raw<br>```"
        assertTrue(format(source, cleanup = true).text().contains("**Bold**"))
        assertTrue(format(source, markdown = true).text().contains("-One"))
        val both = format(source, cleanup = true, markdown = true)
        assertEquals("Features", both.selectFirst("h1")?.text())
        assertEquals("Bold", both.selectFirst("strong")?.text())
        assertEquals(listOf("One", "Two"), both.select("li").map { it.text() })
        assertEquals("-raw\n", both.selectFirst("pre code")?.wholeText())
    }

    @Test fun malformedLargeAndDeepContentStaysBoundedAndReadable() {
        val large = "x".repeat(200_001)
        assertEquals(large, ForumFormatting.format(large, base, ForumFormattingOptions(true, true)))
        val deep = "<div>".repeat(80) + "Keep **this**" + "</div>".repeat(80)
        assertTrue(format(deep, cleanup = true, markdown = true).text().contains("Keep"))
    }
}
