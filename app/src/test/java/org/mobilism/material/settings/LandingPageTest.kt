package org.mobilism.material.settings

import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.ContentSection
import org.mobilism.material.data.FeedOrder

class LandingPageTest {
    @Test fun defaultAndInvalidSettingsOpenAppsHome() {
        assertEquals(LandingPage(), LandingPage.parse(null, null))
        assertEquals(LandingPage(), LandingPage.parse("missing", "missing"))
    }

    @Test fun savedFeedChoicesKeepTheirSectionAndOrder() {
        assertEquals(LandingPage(ContentSection.Apps, FeedOrder.Latest), LandingPage.parse("Apps", "Latest"))
        assertEquals(LandingPage(ContentSection.Books, FeedOrder.Top), LandingPage.parse("Books", "Top"))
        assertEquals("Books · Top", LandingPage.parse("Books", "Top").label)
    }

    @Test fun everySectionOffersHomeLatestAndTopWithoutDuplicates() {
        assertEquals(9, LandingPage.options.size)
        assertEquals(9, LandingPage.options.distinct().size)
        ContentSection.entries.forEach { section ->
            assertEquals(listOf(null, FeedOrder.Latest, FeedOrder.Top),
                LandingPage.options.filter { it.section == section }.map { it.order })
        }
    }
}
