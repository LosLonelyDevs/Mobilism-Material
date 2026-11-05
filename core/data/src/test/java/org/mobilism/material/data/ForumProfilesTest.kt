package org.mobilism.material.data

import org.junit.Assert.*
import org.junit.Test

class ForumProfilesTest {
    private val reference = ProfileReference(username = "Example Member")
    private fun page(rank: String = "Member", groups: List<String> = listOf("Registered users"),
        about: String = "A short biography.", username: String = "Example Member") = """
        <div id='viewprofile'>
          <div class='span2'><ul class='unstyled'><li><span class='avatar-frame'><img src='./download/file.php?avatar=99_sample.png'></span></li><li>$rank</li></ul></div>
          <ul class='reset-list'>
            <li><b>Username:</b><span>$username</span></li>
            <li><b>Location:</b><i class='custom-none'></i></li>
            <li><b>Device:</b>Example phone</li>
            <li><b>About me:</b>$about</li>
            <li><b>WRZ$:</b>250.00</li>
          </ul>
          <select name='g'>${groups.joinToString("") { "<option>$it</option>" }}</select>
          <ul class='recent-activity'>
            <li><div class='text'>Joined:</div><span class='recent-detail'>Jan 1st, 2020, 3:45 pm</span></li>
            <li><span class='text'>Last visited:</span><span class='recent-detail'>-</span></li>
            <li><span class='text'>Total posts:</span><span class='recent-detail'>123 (1% of all posts)</span></li>
          </ul>
        </div>
    """.trimIndent()

    @Test fun administratorAndDeveloperBadgesUseForumRankAndGroupMetadata() {
        val admin = ForumProfiles.parse(page("Administrator", listOf("Administrators", "Android Moderators")), reference)
        assertEquals(setOf(AccountRole.Administrator), admin.roles)
        val developer = ForumProfiles.parse(page("Developer", listOf("Developers", "Registered users")), reference)
        assertEquals(setOf(AccountRole.Developer), developer.roles)
        val both = ForumProfiles.parse(page("Administrator", listOf("Administrators", "Developers")), reference)
        assertEquals(setOf(AccountRole.Administrator, AccountRole.Developer), both.roles)
    }

    @Test fun biographyAndUsernameCannotClaimAStaffBadge() {
        val spoof = ForumProfiles.parse(page(about = "Administrator Developer Moderators", username = "Administrator"),
            ProfileReference(username = "Administrator"))
        assertEquals(setOf(AccountRole.Member), spoof.roles)
        val lookalike = ForumProfiles.parse(page("Not an Administrator", listOf("Developer fans", "Not Administrators")), reference)
        assertEquals(setOf(AccountRole.Member), lookalike.roles)
    }

    @Test fun contributorRanksAndAdditionalGroupsArePreservedWithoutHardcodedAccountIds() {
        val contributor = ForumProfiles.parse(page("Major Android Releaser", listOf("Major Android Releaser", "Registered users")), reference)
        assertEquals("Major Android Releaser", contributor.rank)
        assertTrue("Major Android Releaser" in contributor.groups)
        assertEquals(setOf(AccountRole.Member), contributor.roles)
        assertTrue(contributor.majorAndroidReleaser)
    }

    @Test fun majorAndroidReleaserBadgeAcceptsRankOrGroupMetadata() {
        val byRank = ForumProfiles.parse(page(rank = "major android releaser"), reference)
        assertTrue(byRank.majorAndroidReleaser)
        val byGroup = ForumProfiles.parse(page(groups = listOf("Major Android Releasers", "Registered users")), reference)
        assertTrue(byGroup.majorAndroidReleaser)
        val administrator = ForumProfiles.parse(page("Major Android Releaser", listOf("Administrators")), reference)
        assertTrue(administrator.majorAndroidReleaser)
        assertTrue(AccountRole.Administrator in administrator.roles)
    }

    @Test fun biographyUsernameAndSimilarRanksCannotClaimAReleaserBadge() {
        val spoof = ForumProfiles.parse(page(username = "Major Android Releaser", about = "Major Android Releasers"),
            ProfileReference(username = "Major Android Releaser"))
        assertFalse(spoof.majorAndroidReleaser)
        for (rank in listOf("Android Releaser", "Major iOS Releaser", "Major Android Releaser fan", "Former Major Android Releaser")) {
            assertFalse(ForumProfiles.parse(page(rank, listOf(rank)), reference).majorAndroidReleaser)
        }
    }

    @Test fun parsesMobileProfileFieldsAndSkipsEmptyDetails() {
        val profile = ForumProfiles.parse(page(), reference)
        assertEquals("Example Member", profile.username)
        assertEquals("https://forum.mobilism.org/download/file.php?avatar=99_sample.png", profile.avatar)
        assertEquals("A short biography.", profile.about)
        assertEquals(mapOf("Device" to "Example phone"), profile.details)
        assertEquals("Jan 1st, 2020, 3:45 pm", profile.joined)
        assertEquals("123", profile.posts)
        assertEquals("250.00", profile.balance)
        assertNull(profile.lastVisited)
    }

    @Test fun imageOnlyTelegramAndWhatsappLinksKeepTheirRealDestinations() {
        val html = page(about = "Contact me <a href='https://telegram.me/example_channel'><img src='telegram.png'></a>" +
            "<a href='https://whatsapp.com/channel/example'><img src='whatsapp.png'></a>" +
            "<a href='javascript:alert(1)'>Bad link</a>")
        val contacts = ForumProfiles.parse(html, reference).contacts
        assertEquals(listOf(ProfileContactKind.Telegram, ProfileContactKind.WhatsApp), contacts.map { it.kind })
        assertEquals(listOf("https://telegram.me/example_channel", "https://whatsapp.com/channel/example"), contacts.map { it.url })
        assertEquals(ProfileContactKind.Website, ForumProfiles.parse(page(about =
            "<a href='https://telegram.me.example.org/'>Telegram</a>"), reference).contacts.single().kind)
    }

    @Test fun rejectsLoginChallengeAndWrongMemberPages() {
        for (html in listOf("<h1>Verify access</h1>", "<form>Sign in</form>", page(username = "Someone else"))) {
            assertTrue(runCatching { ForumProfiles.parse(html, reference) }.exceptionOrNull() is ApiFailure)
        }
    }

    @Test fun profileLinksAndUsernameLookupsAreEncodedAndValidated() {
        val name = "Reader & Co + books"
        val lookup = ProfileReference(username = name)
        assertEquals(name, ProfileReference.fromUrl(lookup.url)?.username)
        assertEquals(99L, ProfileReference.fromUrl("https://forum.mobilism.org/memberlist.php?mode=viewprofile&u=99")?.id)
        listOf("https://evil.example/memberlist.php?mode=viewprofile&u=99",
            "https://forum.mobilism.org/memberlist.php?mode=viewprofile&u=99&u=100",
            "https://forum.mobilism.org/memberlist.php?mode=group&u=99",
            "https://user@forum.mobilism.org/memberlist.php?mode=viewprofile&u=99",
            "javascript:alert(1)").forEach { assertNull(ProfileReference.fromUrl(it)) }
    }
}
