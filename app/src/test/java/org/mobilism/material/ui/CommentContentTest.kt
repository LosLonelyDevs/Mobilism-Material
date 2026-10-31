package org.mobilism.material.ui

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.CommentQuote
import org.mobilism.material.data.ReleaseComment

class CommentContentTest {
    @Test fun nestedForumQuotesKeepAuthorsAndSeparateEachReply() {
        val content = CommentContent.parse("""
            <blockquote><div><cite>SniperPro87 wrote:</cite>
              <blockquote><b>nutpants wrote:</b><br>Will reviews come back?</blockquote>
              <p>A</p>
            </div></blockquote><p>Cool</p>
        """.trimIndent())
        assertEquals("Cool", Jsoup.parse(content.html).text())
        val parent = content.quotes.single()
        assertEquals("SniperPro87", parent.author)
        assertEquals("A", Jsoup.parse(parent.content.html).text())
        val ancestor = parent.content.quotes.single()
        assertEquals("nutpants", ancestor.author)
        assertEquals("Will reviews come back?", Jsoup.parse(ancestor.content.html).text())
        assertFalse(content.html.contains("blockquote"))
        assertFalse(parent.content.html.contains("blockquote"))
    }

    @Test fun siblingQuotesAndOrdinaryBoldTextStaySeparate() {
        val content = CommentContent.parse("""
            <p><b>My own heading</b> and <a href="https://example.org">a link</a></p>
            <blockquote><b>Important</b> anonymous quotation</blockquote>
            <blockquote><cite>Reader &amp; friend wrote:</cite>Second quote</blockquote>
            <p>My answer</p>
        """.trimIndent())
        assertEquals(2, content.quotes.size)
        assertEquals("", content.quotes.first().author)
        assertEquals("Important anonymous quotation", Jsoup.parse(content.quotes.first().content.html).text())
        assertEquals("Reader & friend", content.quotes.last().author)
        assertTrue(content.html.contains("<b>My own heading</b>"))
        assertTrue(content.html.contains("href=\"https://example.org\""))
        assertEquals("My own heading and a link My answer", Jsoup.parse(content.html).text())
    }

    @Test fun apiQuoteMetadataAndEmbeddedQuotesBothRemainAvailable() {
        val comment = ReleaseComment(7, "Reader", null, 0, "My answer",
            quotes = listOf(CommentQuote("Parent", "<blockquote><cite>Ancestor wrote:</cite>Earlier</blockquote>Parent answer")))
        val content = CommentContent.parse(comment)
        assertEquals("My answer", content.html)
        val parent = content.quotes.single()
        assertEquals("Parent", parent.author)
        assertEquals("Parent answer", Jsoup.parse(parent.content.html).text())
        assertEquals("Ancestor", parent.content.quotes.single().author)
    }

    @Test fun excessiveNestingIsBoundedWithoutLosingText() {
        val content = CommentContent.parse("<blockquote>".repeat(100) + "Still readable" + "</blockquote>".repeat(100))
        var current = content
        var depth = 0
        while (current.quotes.isNotEmpty()) { current = current.quotes.single().content; depth++ }
        assertEquals(32, depth)
        assertEquals("Still readable", Jsoup.parse(current.html).text())
    }

    @Test fun metadataAndInlineQuoteRenderOnceDespiteFormattingDifferences() {
        val comment = ReleaseComment(7, "Reader", null, 0, """
            <blockquote><cite><a href="#p1">Parent wrote: ↑</a></cite><div>Hello &amp; goodbye</div></blockquote>
            <p>My answer</p>
        """, quotes = listOf(CommentQuote("parent", "<p>Hello &amp; goodbye</p>")))
        val content = CommentContent.parse(comment)
        assertEquals("My answer", Jsoup.parse(content.html).text())
        assertEquals(1, content.quotes.size)
        assertEquals(1L, content.quotes.single().postId)
        assertFalse(content.html.contains("Hello"))
    }

    @Test fun identicalTextFromDistinctPostsAndImageOnlyQuotesStaySeparate() {
        val comment = ReleaseComment(7, "Reader", null, 0, "Answer", quotes = listOf(
            CommentQuote("Parent", "Thanks", 1), CommentQuote("Parent", "Thanks", 2),
            CommentQuote("Parent", "Thanks"),
            CommentQuote("Parent", "<img src='https://example.org/one.png'>"),
            CommentQuote("Parent", "<img src='https://example.org/two.png'>"),
        ))
        val quotes = CommentContent.parse(comment).quotes
        assertEquals(4, quotes.size)
        assertEquals(listOf(1L, 2L), quotes.mapNotNull { it.postId })
    }

    @Test fun repeatedNestedQuotesAreDeduplicatedAtEachLevel() {
        val ancestor = "<blockquote><cite>Ancestor wrote:</cite>Earlier</blockquote>"
        val content = CommentContent.parse("<blockquote><cite>Parent wrote:</cite>$ancestor$ancestor Answer</blockquote>Reply")
        assertEquals(1, content.quotes.single().content.quotes.size)
        assertEquals("Answer", Jsoup.parse(content.quotes.single().content.html).text())
        assertEquals("Reply", content.html)
    }
}
