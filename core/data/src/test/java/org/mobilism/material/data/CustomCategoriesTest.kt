package org.mobilism.material.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CustomCategoriesTest {
    private val sports = CustomCategory("sports", ContentSection.Apps, "Sports")
    private val favorites = CustomCategory("favorites", ContentSection.Apps, "Favorites")
    private fun state() = CustomCategoryState(listOf(sports, favorites))
    private fun release(id: Long = 1, title: String = "Example v1.0", pkg: String? = "org.example.app",
        section: ContentSection = ContentSection.Apps) = Release(id, title, "Uploader", null,
        Instant.parse("2026-10-28T12:00:00Z").epochSecond, 0, 12,
        contentHtml = "<p>Description</p>" + pkg?.let { "<a href=\"https://play.google.com/store/apps/details?id=$it\">Store</a>" }.orEmpty(),
        section = section, categoryId = 10)

    @Test fun assignmentsFollowVersionsAndRemainIndependentAcrossCategories() {
        val first = release()
        val newer = release(2, "Example v2.0")
        var current = CustomCategories.assign(state(), first, setOf(sports.id, favorites.id))
        assertEquals(setOf(sports.id, favorites.id), current.memberships(newer))
        current = CustomCategories.assign(current, newer, setOf(favorites.id))
        assertEquals(1, current.items.size)
        assertEquals(setOf(favorites.id), current.memberships(first))
        current = CustomCategories.delete(current, sports.id)
        assertEquals(setOf(favorites.id), current.memberships(newer))
        assertTrue(CustomCategories.assign(current, first, emptySet()).items.isEmpty())
    }

    @Test fun identityRejectsOtherPackagesVariantsAndSections() {
        val current = CustomCategories.assign(state(), release(), setOf(sports.id))
        assertTrue(current.matches(release(2, "Example v2.0", pkg = null), setOf(sports.id)))
        assertFalse(current.matches(release(3, pkg = "org.other.app"), setOf(sports.id)))
        assertFalse(current.matches(release(4, "Example v2.0 [Android TV]"), setOf(sports.id)))
        assertFalse(current.matches(release(5, section = ContentSection.Games), setOf(sports.id)))
        assertTrue(CustomCategories.assign(state(), release(section = ContentSection.Games), setOf(sports.id)).items.isEmpty())
    }

    @Test fun knownPackageEnrichesTitleIdentityWithoutWeakAliasesMatchingConflictingPackages() {
        var current = CustomCategories.assign(state(), release(pkg = null), setOf(sports.id))
        current = CustomCategories.observe(current, release())
        assertEquals(1, current.items.size)
        assertTrue(current.matches(release(2, "Renamed Example v2.0"), setOf(sports.id)))
        assertFalse(current.matches(release(3, pkg = "org.other.app"), setOf(sports.id)))
        assertEquals(current, CustomCategories.observe(current, release()))
    }

    @Test fun booksFollowFormatsButKeepAuthorsAndSeriesDistinct() {
        fun book(id: Long, title: String) = release(id, title, null, ContentSection.Books)
        val first = book(1, "Example (Series, Book 1) by Jane Writer [epub]")
        assertTrue(CustomCategories.sameItem(first, book(2, "Example (Series, Book 1) by Jane Writer [pdf]")))
        assertFalse(CustomCategories.sameItem(first, book(3, "Example (Series, Book 1) by Other Writer [epub]")))
        assertFalse(CustomCategories.sameItem(first, book(4, "Example (Series, Book 2) by Jane Writer [epub]")))
        assertFalse(CustomCategories.sameItem(book(5, "Example [epub]"), book(6, "Example [pdf]")))
    }

    @Test fun observingOrSavingSparseDetailsKeepsStrongerIdentityAndDoesNotReloadForMetadata() {
        val current = CustomCategories.assign(state(), release(), setOf(sports.id))
        assertEquals(current, CustomCategories.observe(current, release().copy(views = 999)))
        assertEquals(current, CustomCategories.observe(current, release(2, "Example v2.0")))
        assertEquals(current, CustomCategories.observe(current, release(pkg = null)))
        val saved = CustomCategories.assign(current, release(pkg = null), setOf(sports.id))
        assertTrue(saved.matches(release(3, "Renamed Example v3.0"), setOf(sports.id)))
        assertFalse(saved.matches(release(4, pkg = "org.other.app"), setOf(sports.id)))
    }

    @Test fun namesAreTrimmedAndUniqueWithinTheirSection() {
        assertEquals("Training", CustomCategories.validateName(state(), ContentSection.Apps, " Training "))
        assertEquals("Sports", CustomCategories.validateName(state(), ContentSection.Books, "Sports"))
        assertEquals("sports", CustomCategories.validateName(state(), ContentSection.Apps, "sports", sports.id))
        assertThrows(IllegalArgumentException::class.java) { CustomCategories.validateName(state(), ContentSection.Apps, " SPORTS ") }
        assertThrows(IllegalArgumentException::class.java) { CustomCategories.validateName(state(), ContentSection.Apps, " ") }
    }

    @Test fun serializationRetainsMembershipAndIdentityWithoutReleaseBodies() {
        val current = CustomCategories.assign(state(), release(), setOf(sports.id, favorites.id))
        val encoded = CustomCategoryJson.encode(current)
        assertFalse(encoded.contains("Description"))
        assertEquals(current, CustomCategoryJson.decode(encoded))
        val deleted = CustomCategories.delete(current, sports.id)
        assertEquals(deleted, CustomCategoryJson.decode(CustomCategoryJson.encode(deleted)))
        assertEquals(setOf(favorites.id), deleted.items.single().categoryIds)
    }

    @Test fun mixedCategorySelectionsUseNativeUnionThenDates() {
        val local = CustomCategories.assign(state(), release(), setOf(sports.id))
        val filters = FeedFilters(categoryIds = setOf(20), customCategoryIds = setOf(sports.id))
        assertTrue(filters.matches(release(2, "Example v2.0"), customCategories = local))
        assertTrue(filters.matches(release(3, "Other v1.0", "org.other.app").copy(categoryId = 20), customCategories = local))
        assertFalse(filters.matches(release(4, "Other v1.0", "org.other.app"), customCategories = local))
        assertFalse(filters.copy(postedFrom = LocalDate.parse("2026-10-29"))
            .matches(release(), ZoneId.of("UTC"), local))
        assertFalse(FeedFilters(customCategoryIds = setOf("empty")).matches(release(), customCategories = local))
        assertTrue(FeedFilters().matches(release(), customCategories = local))
    }

    @Test fun sparseCustomFiltersContinueAndRespectHiddenCategories() = runBlocking {
        val local = CustomCategories.assign(state(), release(), setOf(sports.id))
        val filters = FeedFilters(customCategoryIds = setOf(sports.id))
        val first = filteredReleasePages(1, filters, customCategories = local) { page ->
            ReleasePage(listOf(release(100L + page, "Other v$page", "org.other.app")), true)
        }
        assertTrue(first.items.isEmpty())
        assertTrue(first.canLoadMore)
        assertEquals(5, first.lastPage)
        val next = filteredReleasePages(6, filters, customCategories = local) {
            ReleasePage(listOf(release(6, "Example v6.0")), false)
        }
        assertEquals(6L, next.items.single().id)
        val hidden = filteredReleasePages(1, filters, excludedCategories = setOf(10), customCategories = local) {
            ReleasePage(listOf(release()), false)
        }
        assertTrue(hidden.items.isEmpty())
    }
}
