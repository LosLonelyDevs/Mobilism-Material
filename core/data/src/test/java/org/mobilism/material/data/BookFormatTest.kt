package org.mobilism.material.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BookFormatTest {
    private fun book(id: Long, title: String) = Release(id, title, "", null, 1, 0, 0, section = ContentSection.Books)

    @Test fun recognizesMultipleTitleFormatsWithoutMatchingOrdinaryWords() {
        assertEquals(setOf(BookFormat.Epub, BookFormat.Pdf), BookFormat.forRelease(book(1, "Example by A. Writer (.ePUB / PDF)")))
        assertEquals(setOf(BookFormat.M4b, BookFormat.Mp3), BookFormat.forRelease(book(1, "Example [m4b, MP3]")))
        assertTrue(BookFormat.forRelease(book(1, "The PDF Handbook by A. Writer")).isEmpty())
        assertTrue(BookFormat.forRelease(book(1, "Reader [PDF]").copy(section = ContentSection.Apps)).isEmpty())
        val filters = FeedFilters(bookFormats = setOf(BookFormat.Epub, BookFormat.M4b))
        assertTrue(filters.matches(book(1, "Example (.epub)")))
        assertTrue(filters.matches(book(2, "Example [M4B]")))
        assertFalse(filters.matches(book(3, "Example [PDF]")))
        assertFalse(filters.matches(book(4, "Example")))
        assertFalse(filters.copy(categoryIds = setOf(10)).matches(book(5, "Example [EPUB]").copy(categoryId = 20)))
    }

    @Test fun sparseFormatMatchesContinueFromTheCorrectPage() = runBlocking {
        val filters = FeedFilters(bookFormats = setOf(BookFormat.Mobi))
        assertTrue(filters.isActive)
        val first = filteredReleasePages(1, filters) { page -> ReleasePage(listOf(book(page.toLong(), "Example [PDF]")), true) }
        assertEquals(5, first.lastPage)
        assertTrue(first.canLoadMore)
        assertTrue(first.items.isEmpty())
        val next = filteredReleasePages(first.lastPage!! + 1, filters) { page ->
            ReleasePage(listOf(book(page.toLong(), "Example [MOBI]")), false)
        }
        assertEquals(6L, next.items.single().id)
        assertFalse(next.canLoadMore)
    }
}
