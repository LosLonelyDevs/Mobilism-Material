package org.mobilism.material.data

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class CommentJsonTest {
    @Test fun decodesReportedSmileyAndQuotedEmojiAfterJsonParsing() {
        val page = CommentJson.page(JSONArray("""[{"id":12726994,"name":"Disk4mat",
            "content":"Taking a look now, very interesting. [smile]\\ud83d\\ude0a[/smile]",
            "replies":[{"user":"Reader","message":"[smile]\\u2764\\ufe0f[/smile]"}]}]"""))
        assertEquals("Taking a look now, very interesting. 😊", page.items.single().contentHtml)
        assertEquals("❤️", page.items.single().quotes.single().html)
    }

    @Test fun preservesOtherMarkupLiteralEscapesAndMalformedUnicode() {
        val html = """<b>Thanks</b> [SMILE]😊[/SMILE] [smile]\ud83d\udc4d\ud83c\udffd[/smile] code \u1234"""
        assertEquals("""<b>Thanks</b> 😊 👍🏽 code \u1234""", CommentMarkup.html(html))
        assertEquals("""\ud83d""", CommentMarkup.html("""[smile]\ud83d[/smile]"""))
        assertEquals("""\uZZZZ""", CommentMarkup.html("""[smile]\uZZZZ[/smile]"""))
        assertEquals("&lt;b&gt;", CommentMarkup.html("""[smile]\u003cb\u003e[/smile]"""))
        assertEquals("😊", CommentMarkup.html("[smile]&#128522;[/smile]"))
        assertEquals("👩‍💻", CommentMarkup.html("""[smile]\ud83d\udc69\u200d\ud83d\udcbb[/smile]"""))
        assertEquals("<a href=\"https://example.org\">😊</a>",
            CommentMarkup.html("<a href=\"https://example.org\">[smile]😊[/smile]</a>"))
    }

    @Test fun preservesFormattedCommentsQuotesAndSafeImages() {
        val page = CommentJson.page(JSONArray("""[{"id":5,"name":"Reader","date":1700000000,"avatar":"//example.com/avatar.png","content":"<b>Thanks</b>","images":["javascript:alert(1)","https://example.com/image.png"],"replies":[{"user":"Author","message":"<i>Quoted text</i>"}]}]"""))
        val comment = page.items.single()
        assertTrue(page.canLoadMore)
        assertEquals("<b>Thanks</b>", comment.contentHtml)
        assertEquals("https://example.com/avatar.png", comment.avatar)
        assertEquals(listOf("https://example.com/image.png"), comment.images)
        assertEquals(CommentQuote("Author", "<i>Quoted text</i>"), comment.quotes.single())
        assertFalse(CommentJson.page(JSONArray()).canLoadMore)
    }
}
