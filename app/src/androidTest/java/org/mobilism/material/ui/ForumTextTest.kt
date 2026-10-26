package org.mobilism.material.ui

import android.text.style.URLSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForumTextTest {
    @Test fun bareUrlsAreClickableWithoutTrailingPunctuation() {
        val text = forumText("Visit https://example.org/path?a=1&amp;b=2. Also www.example.net/help!", "https://forum.mobilism.org/")
        val urls = text.getSpans(0, text.length, URLSpan::class.java).map { it.url }
        assertEquals(listOf("https://example.org/path?a=1&b=2", "http://www.example.net/help"), urls)
    }

    @Test fun existingAnchorKeepsItsDestinationEvenWhenLabelIsAnotherUrl() {
        val text = forumText("<a href='/viewtopic.php?t=123'>https://example.org/short</a> https://example.net", "https://forum.mobilism.org/")
        val urls = text.getSpans(0, text.length, URLSpan::class.java).map { it.url }
        assertEquals(listOf("https://forum.mobilism.org/viewtopic.php?t=123", "https://example.net"), urls)
    }

    @Test fun unsafeAnchorsCannotBecomeClickableAndFormattingSurvives() {
        val text = forumText("<b>Safe</b> <a href='javascript:alert(1)'>unsafe</a><script>alert(2)</script>", "https://forum.mobilism.org/")
        assertEquals(0, text.getSpans(0, text.length, URLSpan::class.java).size)
        assertEquals("Safe unsafe", text.toString())
        assertEquals(1, text.getSpans(0, text.length, android.text.style.StyleSpan::class.java).size)
    }

    @Test fun sentenceEndTopicLinksRouteAndBalancedParenthesesStayInUrl() {
        val text = forumText("See https://forum.mobilism.org/viewtopic.php?t=12345. " +
            "(https://example.org/wiki/Title_(detail)).", "https://forum.mobilism.org/")
        val urls = text.getSpans(0, text.length, URLSpan::class.java).map { it.url }
        assertEquals("https://forum.mobilism.org/viewtopic.php?t=12345", urls.first())
        assertEquals(12345L, org.mobilism.material.ReleaseLinks.topicId(urls.first()))
        assertEquals("https://example.org/wiki/Title_(detail)", urls.last())
    }
}
