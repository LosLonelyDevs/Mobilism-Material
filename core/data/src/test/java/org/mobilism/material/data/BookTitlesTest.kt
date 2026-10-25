package org.mobilism.material.data

import org.junit.Assert.*
import org.junit.Test

class BookTitlesTest {
    @Test fun separatesAuthorSeriesAndFormats() {
        val title = ReleaseTitles.parse("The Last Light (Night Watch #2) by A. Writer [ePUB, PDF]", ContentSection.Books)
        assertEquals("The Last Light", title.name)
        assertEquals("A. Writer", title.bookAuthor)
        assertEquals("Night Watch #2", title.series)
        assertEquals(listOf("ePUB, PDF"), title.tags)
        assertNull(title.version)
    }

    @Test fun handlesMobilismDottedFormatsAndMultipleAuthors() {
        val title = ReleaseTitles.parse("All-Night Terror by Adam Cesare, Matt Serafini (.ePUB)", ContentSection.Books)
        assertEquals("All-Night Terror", title.name)
        assertEquals("Adam Cesare, Matt Serafini", title.bookAuthor)
        assertEquals(listOf("ePUB"), title.tags)
        assertEquals(title, ReleaseTitles.parse("All-Night Terror by Adam Cesare, Matt Serafini (.ePUB)"))
    }

    @Test fun preservesNumbersDatesSubtitlesAndUnknownParentheses() {
        for (name in listOf("1984", "Python 3.12", "The Hindu - Issue 43, 25 October 2026", "Life (A Memoir)", "Fred Series (1-4)")) {
            val title = ReleaseTitles.parse("$name by A. Writer (.PDF)", ContentSection.Books)
            assertEquals(name, title.name)
            assertNull(title.version)
        }
        val magazine = ReleaseTitles.parse("Science - October 2026 (.PDF)", ContentSection.Books)
        assertEquals("Science - October 2026", magazine.name)
        assertNull(magazine.bookAuthor)
    }

    @Test fun handlesHtmlSeriesAfterAuthorAndIncompleteMetadata() {
        val title = ReleaseTitles.parse("Stars &amp; Sea by A. Writer (Sky, Book 3) [EPUB]", ContentSection.Books)
        assertEquals("Stars & Sea", title.name)
        assertEquals("Sky, Book 3", title.series)
        assertEquals("A. Writer", title.bookAuthor)
        assertEquals("Book by", ReleaseTitles.parse("Book by", ContentSection.Books).name)
        assertEquals("Book [unfinished", ReleaseTitles.parse("Book [unfinished", ContentSection.Books).name)
    }

    @Test fun doesNotInterpretAppTitlesAsBooks() {
        val title = ReleaseTitles.parse("Reader by Example v2.0 [Premium]", ContentSection.Apps)
        assertEquals("Reader by Example", title.name)
        assertEquals("2.0", title.version)
        assertNull(title.bookAuthor)
        assertEquals("Stand by Me", ReleaseTitles.parse("Stand by Me").name)
        assertEquals("Death by Water", ReleaseTitles.parse("Death by Water (.ePUB)", ContentSection.Books).name)
        assertNull(ReleaseTitles.parse("Death by Water", ContentSection.Books).bookAuthor)
    }
}
