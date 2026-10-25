package org.mobilism.material.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReleaseCommentsTest {
    @Test fun requiresAPositiveCommentIdForSuccess() {
        assertEquals(123L, ReleaseComments.confirmedId(JSONObject("""{"response":{"comment_id":123}}""")))
        for (json in listOf("{}", """{"status":"success"}""", """{"response":{"comment_id":0}}""",
            """{"response":{"comment_id":-1}}""", """{"response":{"comment_id":"bad"}}""")) {
            val failure = runCatching { ReleaseComments.confirmedId(JSONObject(json)) }.exceptionOrNull()
            assertTrue(failure is ApiFailure && failure.kind == FailureKind.InvalidResponse)
        }
    }

    @Test fun confirmedLocalCommentEscapesHtmlAndKeepsLineBreaks() {
        val result = ReleaseComments.posted(7, "Reader", " <img src=evil> & hello\r\nworld ", 123)
        assertEquals("&lt;img src=evil&gt; &amp; hello<br>world", result.contentHtml)
        assertEquals(123L, result.publishedAt)
        assertTrue(result.images.isEmpty())
    }
}
