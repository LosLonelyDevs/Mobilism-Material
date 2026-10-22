package org.mobilism.material.data

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class CommentJsonTest {
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
