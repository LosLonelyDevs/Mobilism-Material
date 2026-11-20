package org.mobilism.material.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CommentEditBodyTest {
    @Test fun extractsOnlyTheReplyAndRestoresTruncatedLinks() {
        val html = """<blockquote><cite>Another user wrote:</cite><p>Quoted comment</p></blockquote>First &amp; second<br><b>See</b> <a href="https://example.org/issue">https://example...issue</a>"""
        assertEquals("First & second\n[b]See[/b] https://example.org/issue", CommentEditBody.fromHtml(html))
        assertEquals("First\nsecond\nthird", CommentEditBody.fromHtml("First<div>second</div><p>third</p>"))
    }

    @Test fun preservesMultipleNestedQuotePrefixesExactlyOnUpdate() {
        val prefix = "[quote=\"Reader\"]A [quote]nested[/quote] reply[/quote]\r\n[quote]B[/quote]\r\n"
        assertEquals(CommentEditBody(prefix, "My message"), CommentEditBody.fromForum(prefix + "My message"))
        for (text in listOf("Unquoted text", "[quote]unclosed", "My message [quote]later[/quote]"))
            assertEquals(CommentEditBody("", text), CommentEditBody.fromForum(text))
    }
}
