package org.mobilism.material.ui

import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.*

class AppDiscoveryTest {
    private fun app(id: Long, title: String, category: Long = 7, posted: Long = id) =
        Release(id, title, "Member", null, posted, 0, 0, section = ContentSection.Apps, categoryId = category)

    @Test fun myPGalleryAppearsOnceAndOpensNewestPostAcrossAllHomeShelves() {
        val older = app(51, "MyPGallery v1.1.3 build 370 [Official UnTouched]")
        val newest = app(59, "MyPGallery v1.2.6 build 391 [18+ Adult Content]").copy(thumbnail = "local-cover", views = 42)
        val cached = newest.copy(categoryName = "Adult", thumbnail = null, views = 0)
        val followed = app(1, "Followed app v1.0")
        val top = (2L..11).map { app(it, "Popular App ${it} v1.0") } + older
        val shelves = discoverPicks(listOf(older, cached, followed), listOf(LibraryEntry(followed, watched = true)),
            listOf(newest, followed, older), top, emptyList(), 0)

        assertEquals(listOf("Adult"), shelves.categoryNames)
        assertEquals(59L, shelves.discover.single().id)
        assertEquals("local-cover", shelves.discover.single().thumbnail)
        assertEquals(42L, shelves.discover.single().views)
        val shown = shelves.discover + shelves.featured + shelves.latest + shelves.popular
        assertEquals(shown.size, shown.distinctBy { it.discoveryKey() }.size)
        assertEquals(listOf(followed.id), shelves.latest.map { it.id })
    }

    @Test fun topKeepsFirstPopularityPositionWithNewestPostEvenForTiesAndDifferentCase() {
        val old = app(90, "MyPGallery v1.0", posted = 2)
        val other = app(80, "MyPGallery TV v3.0")
        val newer = app(20, "mypgallery v2.0", posted = 9)
        val last = app(21, "MyPGallery v2.0 build 9", posted = 9)
        assertEquals(listOf(last.id, other.id), newestPerApp(listOf(old, other, last, newer, old)).map { it.id })
    }

    @Test fun dailyRowsUseWatchedCategoriesAndPairOnlyOncePerLocalWeek() {
        val releases = listOf(app(1, "Music Watch v2"), app(2, "Photo Watch v2", 8), app(3, "Player v1"),
            app(4, "Camera v1", 8), app(5, "Diary v1", 9), app(6, "Mixer v1"))
        val library = listOf(LibraryEntry(app(9, "Music Watch v1").copy(categoryId = null), watched = true),
            LibraryEntry(releases[1], watched = true), LibraryEntry(releases[4], downloaded = true))
        val labels = listOf(ReleaseCategory(7, "Music"), ReleaseCategory(8, "Photography"), ReleaseCategory(9, "Productivity"))
        val candidates = releases + (10L..29L).map { app(it, "Gem $it v1", if (it % 2L == 0L) 7 else 8) }
        fun pick(day: Int, saved: DailySelection? = null, rows: List<Release> = candidates) =
            discoverPicks(rows, library, emptyList(), emptyList(), labels, day, saved = saved)
        for (week in 0..4) {
            val days = (0..6).map { pick(week * 7 - 3 + it) }
            assertEquals(1, days.count { it.categoryNames.size == 2 })
            assertTrue(days.all { it.categoryNames.isNotEmpty() && "Productivity" !in it.categoryNames })
            assertTrue(days.all { (it.discover + it.featured).distinctBy(Release::discoveryKey).size == it.discover.size + it.featured.size })
            assertTrue(days.map { it.discover }.distinct().size > 1)
        }
        val first = pick(35)
        val selected = first.discover.first()
        val newer = selected.copy(id = 500, title = selected.appTitle.name + " v9", publishedAt = 500)
        val reopened = pick(35, first.selection, candidates.reversed() + newer)
        assertEquals(first.selection, reopened.selection)
        assertEquals(newer.id, reopened.discover.first().id)
        assertEquals(first.featured, reopened.featured)
    }

    @Test fun twentyThreeUtilityFollowsAndOneMusicFollowGetTwentyThreePrimaryDaysAndOneMusicDay() {
        val labels = listOf(ReleaseCategory(7, "Music"), ReleaseCategory(8, "Utilities"))
        val library = (1L..24).map { LibraryEntry(app(it, "Followed $it v1", if (it == 1L) 7 else 8), watched = true) }
        val music = (101L..106).map { app(it, "Player $it v1") }
        val utilities = (201L..206).map { app(it, "Tool $it v1", 8) }
        val previouslyMusic = DailySelection(emptyList(), listOf(7), music.take(5).map(Release::discoveryKey))
        fun days(rows: List<Release>, entries: List<LibraryEntry> = library) = (20_000 until 20_024).map { day ->
            discoverPicks(rows, entries, emptyList(), emptyList(), labels, day, saved = previouslyMusic, seed = 91)
        }
        val full = days(music + utilities)
        assertEquals(23, full.count { it.categoryNames.first() == "Utilities" })
        assertEquals(1, full.count { it.categoryNames.first() == "Music" })
        assertEquals(full.map { it.categoryNames }, days((music + utilities).reversed(), library.reversed()).map { it.categoryNames })
        val sparse = days(utilities + music.first() + music.first().copy(id = 107, title = "Player 101 v2"))
        assertTrue(sparse.all { it.categoryNames == listOf("Utilities") })
        assertTrue(full.all { shelf -> shelf.categoryNames.all { label ->
            shelf.featured.count { app -> labels.single { it.name == label }.id == app.categoryId } >= 2
        } })
        val unknown = library.map { it.copy(release = it.release.copy(categoryId = null)) }.toMutableList()
        unknown[0] = library[0]
        assertEquals(1, days(music, unknown).count { "Music" == it.categoryNames.firstOrNull() })
    }

    @Test fun installedAppsGuideDiscoverAndAreNeverSuggestionsEvenWhenTheirStoreTitleDiffers() {
        val installed = mapOf("org.reader" to "My Library", "org.notes" to "Notes")
        val reader = app(1, "Reader Premium v2", 8).copy(storePackage = "org.reader")
        val notes = app(2, "Notes v2", 8)
        val gem = app(3, "Book Basket v1", 8)
        val top = (4L..13).map { app(it, "Bestseller $it v1", 8) }
        val result = discoverPicks(top + listOf(reader, notes, gem), emptyList(), emptyList(), top,
            listOf(ReleaseCategory(8, "Reading")), 0, installed)
        assertEquals(listOf("Reading"), result.categoryNames)
        assertEquals(listOf(gem), result.discover)
        assertTrue(result.featured.none { it == reader || it == notes })
    }

    @Test fun aSparseSavedCategoryUsesViewedLatestAndSwitchesWhenItCannotFill() {
        val song = app(1, "Deezer v1")
        val labels = listOf(ReleaseCategory(7, "Music"), ReleaseCategory(8, "Photography"))
        val photos = (10L..16L).map { app(it, "Camera $it v1", 8).copy(views = it) }
        val following = listOf(LibraryEntry(app(2, "Player v1"), watched = true))
        val saved = DailySelection(emptyList(), listOf(7), listOf(song.discoveryKey()))
        fun picks(latest: List<Release>) = discoverPicks(listOf(song), following, latest, emptyList(), labels, 1, saved = saved)
        assertTrue(picks(emptyList()).featured.isEmpty())
        val alternative = picks(photos)
        assertEquals(listOf("Photography"), alternative.categoryNames)
        assertTrue(alternative.featured.size in 3..5)
        val music = (20L..24L).map { app(it, "Music app $it v1").copy(views = it) }
        val expanded = picks(photos + music)
        assertEquals(listOf("Music"), expanded.categoryNames)
        assertEquals(5, expanded.featured.size)
        assertEquals(song.id, expanded.featured.first().id)
        assertTrue(expanded.discover.none { it in expanded.featured })
    }

    @Test fun discoverLeadsWithArtworkEvenWhenEverySavedHeroHasOnlyAnInitial() {
        val savedApps = (1L..5).map { app(it, "Saved app $it v1") }
        val pictured = app(6, "Illustrated app v1").copy(thumbnail = " ", images = listOf("", "cover.png"))
        val followed = app(7, "Followed v1").copy(thumbnail = "followed.png")
        val library = listOf(LibraryEntry(followed, watched = true))
        val saved = DailySelection(savedApps.map(Release::discoveryKey), emptyList(), emptyList())
        fun picks(selection: DailySelection) = discoverPicks(savedApps + followed + pictured, library,
            emptyList(), emptyList(), emptyList(), 1, saved = selection)
        val first = picks(saved)
        assertEquals(pictured, first.discover.first())
        assertEquals("cover.png", pictured.homeArtwork)
        assertEquals(savedApps.take(4), first.discover.drop(1))
        assertEquals(first.selection, picks(first.selection).selection)
    }

    @Test fun emptySharedCacheFallsBackToDistinctUnfollowedLocallyLoadedApps() {
        val followed = app(1, "Followed v1")
        val old = app(2, "Sketch v1")
        val current = app(3, "Sketch v2")
        val top = app(4, "Popular v1")
        val result = discoverPicks(emptyList(), listOf(LibraryEntry(followed, watched = true)),
            listOf(old, top, followed, current), listOf(top), emptyList(), 0)
        assertEquals(listOf(current), result.discover)
        assertTrue(result.categoryNames.isEmpty())
        assertEquals(listOf(followed), result.latest)
        assertEquals(listOf(top), result.popular)
    }
}
