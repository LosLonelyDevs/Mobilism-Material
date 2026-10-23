package org.mobilism.material.ui

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class ReleaseSectionsTest {
    @Test fun findsUnlabelledModDetailsOnEitherSideOfAdvertisementStatements() {
        val statements = listOf("This app has no advertisements", "This app has advertisements", "This app has no credit advertisements")
        statements.forEach { statement ->
            listOf(true, false).forEach { before ->
                val details = "<p>Premium features unlocked.</p><p>License verification disabled.</p>"
                val ad = "<b>${statement.substringBeforeLast(' ')} <span>${statement.substringAfterLast(' ')}</span></b><br>"
                val html = "<p>Read books and organize your library.</p>" +
                    (if (before) details + ad else ad + details) +
                    "<u>More Info:</u><a href=\"https://example.com/store\">Store</a>"
                val sections = ReleaseSections.parse(html).associate { it.kind to Jsoup.parse(it.html).text() }
                assertEquals("$statement, before=$before", "Read books and organize your library. More Info:Store", sections[ReleaseSection.Description])
                assertTrue(sections.getValue(ReleaseSection.Mod).contains("Premium features unlocked."))
                assertTrue(sections.getValue(ReleaseSection.Mod).contains("License verification disabled."))
                assertTrue(sections.getValue(ReleaseSection.Mod).contains(statement))
            }
        }
    }

    @Test fun usesNearbyAdvertisingContextForASingleModFeature() {
        val html = "<p>App description.</p><strong>Build notes</strong><ul><li>Ads removed.</li></ul><p><a href=\"https://example.com/notes\">Release notes</a></p><p>This app contains no advertisements.</p>"
        val sections = ReleaseSections.parse(html)
        assertEquals("App description.", Jsoup.parse(sections.single { it.kind == ReleaseSection.Description }.html).text())
        val mod = sections.single { it.kind == ReleaseSection.Mod }.html
        assertTrue(Jsoup.parse(mod).text().contains("Build notes"))
        assertTrue(mod.contains("<li>Ads removed.</li>"))
        assertTrue(mod.contains("https://example.com/notes"))
    }

    @Test fun findsAnUnformattedModLineWithoutAbsorbingTheFollowingProse() {
        val html = "<p>App description.</p><p>This app has no advertisements</p><p>Premium unlocked.</p><p>Available for phones and tablets.</p>"
        val sections = ReleaseSections.parse(html).associate { it.kind to Jsoup.parse(it.html).text() }
        assertEquals("App description. Available for phones and tablets.", sections[ReleaseSection.Description])
        assertEquals("This app has no advertisements Premium unlocked.", sections[ReleaseSection.Mod])
    }

    @Test fun recognizesModHeadingVariantsAndSplitFormatting() {
        listOf("MOD", "Mod notes", "What's modded", "What's modified", "Modifications", "Patch details", "About this mod", "MOD INFO / FEATURES").forEach { heading ->
            val html = "<p>App description.</p><b>$heading:</b><p>Extra controls.</p><u>What's New:</u><p>Bug fixes.</p>"
            val sections = ReleaseSections.parse(html).associate { it.kind to Jsoup.parse(it.html).text() }
            assertEquals(heading, "App description.", sections[ReleaseSection.Description])
            assertEquals(heading, "Extra controls.", sections[ReleaseSection.Mod])
            assertEquals(heading, "Bug fixes.", sections[ReleaseSection.Changes])
        }
        val split = ReleaseSections.parse("<p>App description.</p><strong>Mod <span>Info</span>:</strong><p><em>Extra</em> controls.</p>")
        assertEquals(listOf(ReleaseSection.Description, ReleaseSection.Mod), split.map { it.kind })
        assertTrue(split.last().html.contains("<em>Extra</em>"))
        assertEquals("Extra controls.", Jsoup.parse(split.last().html).text())
    }

    @Test fun guessesCustomBuildNotesWithoutMatchingATitleTag() {
        val html = "<p>App description.</p><strong>Custom build</strong><ul><li>Trackers removed</li><li>Signature checks disabled</li><li>Extra theme options</li></ul><p>Note: uninstall the original app first.</p><u>What's New:</u><p>Bug fixes.</p>"
        val sections = ReleaseSections.parse(html).associate { it.kind to Jsoup.parse(it.html).text() }
        assertEquals("App description.", sections[ReleaseSection.Description])
        assertTrue(sections.getValue(ReleaseSection.Mod).startsWith("Custom build"))
        assertTrue(sections.getValue(ReleaseSection.Mod).contains("Extra theme options"))
        assertTrue(sections.getValue(ReleaseSection.Mod).contains("uninstall the original"))
        assertEquals("Bug fixes.", sections[ReleaseSection.Changes])
    }

    @Test fun advertisingStatementDoesNotPullInMarketingFeaturesOrChangelog() {
        val html = "<p>Listen to music.</p><b>Premium features</b><ul><li>Download music offline</li><li>Enjoy ad-free listening</li><li>Choose a dark theme</li></ul><u>What's New:</u><p>Removed ads from the welcome screen.</p><p>This app has no advertisements</p><p>Available for phones and tablets.</p><u>More Info:</u><a href=\"https://example.com/store\">Store</a>"
        val sections = ReleaseSections.parse(html, "Music v1.2.3 [Mod]").associate { it.kind to Jsoup.parse(it.html).text() }
        assertTrue(sections.getValue(ReleaseSection.Description).contains("Premium features"))
        assertTrue(sections.getValue(ReleaseSection.Description).contains("Download music offline"))
        assertTrue(sections.getValue(ReleaseSection.Description).contains("Available for phones"))
        assertEquals("Removed ads from the welcome screen.", sections[ReleaseSection.Changes])
        assertEquals("This app has no advertisements", sections[ReleaseSection.Mod])
    }

    @Test fun advertisingMentionsInsideProseAreNotSectionAnchors() {
        val html = "<p>This app has no advertisements while you read, and supports offline books.</p><p>Premium features unlock more themes when you subscribe.</p>"
        val sections = ReleaseSections.parse(html)
        assertEquals(listOf(ReleaseSection.Description), sections.map { it.kind })
        assertEquals(Jsoup.parse(html).text(), Jsoup.parse(sections.single().html).text())
    }

    @Test fun combinesWeakCustomBuildCluesWithAdvertisingContext() {
        val html = "<p>App description.</p><b>Community edition 2.4.0</b><ul><li>AMOLED theme</li><li>Accent color</li></ul><p>This app has no advertisements</p>"
        val sections = ReleaseSections.parse(html, "Video v4.2.0 [Mod]").associate { it.kind to Jsoup.parse(it.html).text() }
        assertEquals("App description.", sections[ReleaseSection.Description])
        assertTrue(sections.getValue(ReleaseSection.Mod).contains("Community edition 2.4.0"))
        assertTrue(sections.getValue(ReleaseSection.Mod).contains("Accent color"))
    }

    @Test fun keepsOriginalAppFeaturesAndPurchaseConditionsInDescription() {
        val html = "<p>App description.</p><b>Video 4.2.0</b><ul><li>AMOLED theme</li><li>Accent color</li><li>Premium features unlocked when you subscribe.</li></ul><p>This app has no advertisements</p>"
        val sections = ReleaseSections.parse(html, "Video v4.2.0 [Mod]").associate { it.kind to Jsoup.parse(it.html).text() }
        assertTrue(sections.getValue(ReleaseSection.Description).contains("Video 4.2.0"))
        assertTrue(sections.getValue(ReleaseSection.Description).contains("when you subscribe"))
        assertEquals("This app has no advertisements", sections[ReleaseSection.Mod])
    }

    @Test fun recognizesDifferentNamedModsFromReleaseTitles() {
        val cases = listOf(
            Triple("ReVanced Extended", "v2.4.1-beta", "Mod"),
            Triple("HushFacebook", "0.14.0", "MOD v0.14.0"),
            Triple("Example.Custom+", "1.5.2", "Modded"),
        )
        cases.forEach { (name, version, tag) ->
            val title = "Original App v4.2.0 [$name] [$tag]"
            val html = """<p>Original app description.</p><strong>Original App 4.2.0<br/>- Original features</strong><strong><span>${name.lowercase()} $version</span><br/>- Custom theme<br/>- Extra controls</strong><p><a href="https://example.com/changes">Mod changelog</a></p><u>What's New:</u><p>App bug fixes.</p>"""
            val result = ReleaseSections.parse(html, title)
            val sections = result.associate { it.kind to Jsoup.parse(it.html).text() }
            assertEquals(name, "Original app description. Original App 4.2.0 - Original features", sections[ReleaseSection.Description])
            assertEquals(name, "${name.lowercase()} $version - Custom theme - Extra controls Mod changelog", sections[ReleaseSection.Mod])
            assertEquals(name, "App bug fixes.", sections[ReleaseSection.Changes])
            assertTrue(name, result.single { it.kind == ReleaseSection.Mod }.html.contains("https://example.com/changes"))
        }
    }

    @Test fun separatesHushfeedVersionedModBlockFromTikTokDescription() {
        val html = """<div>TikTok description.<br/><br/><b>Hushfeed 0.75.0<br/>- AMOLED dark theme<br/>- Accent color<br/>- Advanced downloads<br/><br/>Changes and bug fixes read here:</b><br/><a href="https://github.com/SysAdminDoc/HushFeed/releases/tag/v0.75.0">Changelog</a><br/><u>What's New:</u><br/>Bug fixes and performance improvements.<br/><b>This app has no advertisements</b><br/><u>More Info:</u><a href="https://example.com/store">Store</a></div>"""
        val result = ReleaseSections.parse(html, "TikTok v47.1.4 [HushFeed] v0.75.0 [Mod]")
        val sections = result.associate { it.kind to Jsoup.parse(it.html).text() }
        assertEquals("TikTok description. More Info:Store", sections[ReleaseSection.Description])
        assertTrue(sections.getValue(ReleaseSection.Mod).contains("Hushfeed 0.75.0"))
        assertTrue(sections.getValue(ReleaseSection.Mod).contains("AMOLED dark theme"))
        assertTrue(sections.getValue(ReleaseSection.Mod).contains("This app has no advertisements"))
        assertEquals("Bug fixes and performance improvements.", sections[ReleaseSection.Changes])
        assertTrue(result.single { it.kind == ReleaseSection.Mod }.html.contains("https://github.com/SysAdminDoc/HushFeed/releases/tag/v0.75.0"))
    }

    @Test fun keepsUnrelatedVersionedFeatureListsInDescription() {
        val html = "<b>TikTok 47.1.4<br/>- Watch videos<br/>- Share with friends</b>"
        val result = ReleaseSections.parse(html, "TikTok v47.1.4 [HushFeed] v0.75.0 [Mod]")
        assertEquals(listOf(ReleaseSection.Description), result.map { it.kind })
        assertEquals(Jsoup.parse(html).text(), Jsoup.parse(result.single().html).text())
    }

    @Test fun keepsNamedModMentionsAndUnmodifiedReleaseFeaturesInDescription() {
        val title = "TikTok v47.1.4 [HushFeed] v0.75.0 [Mod]"
        val prose = "<p>Hushfeed 0.75.0 adds options to the app.</p><b>Hushfeed 0.75.0</b><p>A description of the release.</p>"
        assertEquals(listOf(ReleaseSection.Description), ReleaseSections.parse(prose, title).map { it.kind })
        val features = "<b>Hushfeed 0.75.0<br/>- Theme options</b>"
        assertEquals(listOf(ReleaseSection.Description), ReleaseSections.parse(features, "Hushfeed v0.75.0").map { it.kind })
    }

    @Test fun separatesDecoratedSpotifyModHeadingFromPremiumDescription() {
        val html = """<div>Spotify Premium features<br>• Play any song.<br>• Download music.<br><br><b>★★★ Mod by kefir52 ★★★</b><br><br><b>▣ Mod Info:</b><br>• Unlocked Next Track<br>• Disabled Audio Ads<br><br>Use a different account if needed.</div>"""
        val sections = ReleaseSections.parse(html).associate { it.kind to Jsoup.parse(it.html).text() }
        assertTrue(sections.getValue(ReleaseSection.Description).contains("Spotify Premium features"))
        assertFalse(sections.getValue(ReleaseSection.Description).contains("Mod by"))
        assertFalse(sections.getValue(ReleaseSection.Description).contains("Unlocked Next Track"))
        assertTrue(sections.getValue(ReleaseSection.Mod).contains("Mod by kefir52"))
        assertTrue(sections.getValue(ReleaseSection.Mod).contains("Unlocked Next Track"))
        assertTrue(sections.getValue(ReleaseSection.Mod).contains("different account"))
    }

    @Test fun handlesSymbolHeadingsWithoutMovingOrdinaryMentions() {
        val sections = ReleaseSections.parse("<p>Includes a mod by a community member.</p><b>☢ MOD INFO ☢</b><p>Unlocked.</p><u>★ What's New: ★</u><p>Fixed.</p>")
            .associate { it.kind to Jsoup.parse(it.html).text() }
        assertEquals("Includes a mod by a community member.", sections[ReleaseSection.Description])
        assertEquals("Unlocked.", sections[ReleaseSection.Mod])
        assertEquals("Fixed.", sections[ReleaseSection.Changes])
    }

    @Test fun handlesUnderlinedChangelogAndBulletModBlock() {
        val result = ReleaseSections.parse("""<p>App description.</p><b>◉ Premium Activated<br>◉ Debug information removed</b><br><u>What's New:</u><br>No changelog<br><u>More Info:</u><a href="https://example.com">Store</a>""")
        val sections = result.associate { it.kind to Jsoup.parse(it.html).text() }
        assertEquals("App description. More Info:Store", sections[ReleaseSection.Description])
        assertEquals("No changelog", sections[ReleaseSection.Changes])
        assertTrue(sections.getValue(ReleaseSection.Mod).contains("Premium Activated"))
    }
    @Test fun separatesNestedHeadingsWithoutLosingFormattingOrLinks() {
        val sections = ReleaseSections.parse("""<div><p>A useful <em>description</em>.</p><b>What's New:</b><ul><li>Fixed a crash.</li></ul><strong>Mod Info:</strong><p>Premium features. <a href="https://example.com/help">Help</a></p></div>""")
        assertEquals(ReleaseSection.entries, sections.map { it.kind })
        assertTrue(sections[0].html.contains("<em>description</em>"))
        assertEquals("Fixed a crash.", Jsoup.parse(sections[1].html).text())
        assertTrue(sections[2].html.contains("https://example.com/help"))
        assertFalse(sections[0].html.contains("Fixed a crash"))
    }

    @Test fun omitsMissingSectionsAndDoesNotSplitMentionsInsideProse() {
        val html = "<p>Read the changelog to learn what's new in this app.</p>"
        val sections = ReleaseSections.parse(html)
        assertEquals(listOf(ReleaseSection.Description), sections.map { it.kind })
        assertEquals(Jsoup.parse(html).text(), Jsoup.parse(sections.single().html).text())
    }

    @Test fun handlesInlineHeadingAndCurlyApostrophe() {
        val sections = ReleaseSections.parse("<p>Description</p><p>Overview text.</p><b>What’s New: Bug fixes.</b><p>Mod features: Unlocked.</p>")
        assertEquals(listOf("Overview text.", "Bug fixes.", "Unlocked."), sections.map { Jsoup.parse(it.html).text() })
    }
}
