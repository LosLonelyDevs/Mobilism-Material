package org.mobilism.material.members

import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.*

@OptIn(ExperimentalCoroutinesApi::class)
class MemberIdentityStoreTest {
    @Test fun remoteLookupPacingAndFailureCooldownFallBackImmediately() = runTest {
        var time = 1_000L
        var calls = 0
        var limits = ApiRequestLimits.Defaults.copy(memberLookupSpacingMs = 5_000,
            memberFailureCooldownMs = 120_000)
        val store = MemberIdentityStore({ reference ->
            calls++
            if (reference.username == "Fail") error("Unavailable")
            profile().copy(username = reference.username!!)
        }, { time }, { limits }) { time += it }
        store.request("One")
        store.request("Two")
        assertEquals(6_000L, time)
        store.request("Fail")
        time += 60_000
        store.request("Three")
        assertEquals(3, calls)
        limits = ApiRequestLimits.Defaults
        store.request("One")
        assertEquals(3, calls)
        store.request("Three")
        assertEquals(4, calls)
        store.request("Four")
        assertEquals(72_000L, time)
    }

    @Test fun repeatedFailuresPauseAllOptionalLookupsAndIncreaseTheCooldown() = runTest {
        var calls = 0
        val store = MemberIdentityStore({ calls++; error("Unavailable") }, { testScheduler.currentTime + 1 })
        repeat(3) {
            repeat(10) { user -> store.request("Member $user") }
            advanceTimeBy(60_000)
        }
        assertEquals(2, calls)
        store.request("Member 9")
        assertEquals(3, calls)
    }

    @Test fun leavingTheScreenCancelsActiveAndQueuedLookupsAndAllowsLaterRetry() = runTest {
        val response = CompletableDeferred<Unit>()
        var calls = 0
        val store = MemberIdentityStore({ reference ->
            calls++
            response.await()
            profile().copy(reference = reference, username = reference.username!!)
        }, { testScheduler.currentTime + 1 })
        val lookups = (1..30).map { user -> launch { store.request("Member $user") } }
        runCurrent()
        assertEquals(1, calls)
        lookups.forEach { it.cancelAndJoin() }
        response.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, calls)
        store.request("Member 1")
        assertEquals(2, calls)
        assertTrue(store.identities.value.containsKey("member 1"))
    }

    @Test fun missingMemberDoesNotPauseValidMembers() = runTest {
        var calls = 0
        val store = MemberIdentityStore({ reference ->
            calls++
            if (reference.username == "Missing") throw ApiFailure(FailureKind.Server, "Missing", 404)
            profile()
        }, { testScheduler.currentTime + 1 })
        store.request("Missing")
        store.request("Example")
        store.request("Missing")
        assertEquals(2, calls)
        assertTrue(store.identities.value.containsKey("example"))
    }

    private fun profile(roles: Set<AccountRole> = setOf(AccountRole.Member)) = UserProfile(
        ProfileReference(username = "Example"), "Example", "https://example.org/avatar.gif", roles,
        "", emptyList(), "Administrator", emptyMap(), null, null, null, null)

    @Test fun repeatedUsernameRequestsShareOneLookupAndOnlyTrustedRolesGrantBadges() = runTest {
        var calls = 0
        val store = MemberIdentityStore({ calls++; profile() })
        store.request(" Example "); store.request("example")
        advanceUntilIdle()
        store.request("EXAMPLE"); advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals("https://example.org/avatar.gif", store.identities.value["example"]?.avatar)
        assertFalse(store.identities.value.getValue("example").administrator)
        store.remember(profile(setOf(AccountRole.Administrator)))
        assertTrue(store.identities.value.getValue("example").administrator)
    }

    @Test fun failuresHaveCooldownAndExpiredAdminRolesAreRemoved() = runTest {
        var time = 1L
        var calls = 0
        val store = MemberIdentityStore({ calls++; error("Unavailable") }, { time })
        store.remember(profile(setOf(AccountRole.Administrator)))
        time += 30L * 24 * 60 * 60_000
        store.request("Example"); advanceUntilIdle()
        assertNull(store.identities.value["example"])
        store.request("Example"); advanceUntilIdle()
        assertEquals(1, calls)
        time += 60_000
        store.request("Example"); advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test fun failedBackgroundLookupDoesNotEraseManuallyVerifiedProfile() = runTest {
        val wait = CompletableDeferred<Unit>()
        val store = MemberIdentityStore({ wait.await(); error("Unavailable") })
        launch { store.request("Example") }; runCurrent()
        store.remember(profile(setOf(AccountRole.Administrator)))
        wait.complete(Unit); advanceUntilIdle()
        assertTrue(store.identities.value.getValue("example").administrator)
    }

    @Test fun coldLookupsArePacedAndDuplicateUsernamesShareTheResult() = runTest {
        val wait = CompletableDeferred<Unit>()
        var calls = 0
        var active = 0
        var maximum = 0
        val store = MemberIdentityStore({ reference ->
            calls++; active++; maximum = maxOf(maximum, active)
            try {
                wait.await()
                profile().copy(reference = reference, username = reference.username!!)
            } finally { active-- }
        }, { testScheduler.currentTime + 1 })
        listOf("First", "Second", "Third", " FIRST ").forEach { launch { store.request(it) } }
        assertEquals(0, calls)
        runCurrent()
        assertEquals(1, calls)
        assertTrue(store.identities.value.isEmpty())
        wait.complete(Unit); advanceUntilIdle()
        assertEquals(3, calls)
        assertEquals(1, maximum)
        assertEquals(setOf("first", "second", "third"), store.identities.value.keys)
        assertTrue(store.identities.value.values.all { it.avatar == "https://example.org/avatar.gif" })
    }

    @Test fun failedLookupCanPopulateAvatarAfterCooldownWithoutOpeningProfile() = runTest {
        var time = 1L
        var calls = 0
        val store = MemberIdentityStore({
            if (++calls == 1) error("Unavailable") else profile()
        }, { time })
        store.request("Example"); advanceUntilIdle()
        assertNull(store.identities.value["example"])
        store.request("Example"); advanceUntilIdle()
        assertEquals(1, calls)
        time += 60_000
        store.request("Example"); advanceUntilIdle()
        assertEquals(2, calls)
        assertEquals("https://example.org/avatar.gif", store.identities.value["example"]?.avatar)
    }

    @Test fun membersWithoutAvatarsAreCachedAndBlankNamesAreIgnored() = runTest {
        var calls = 0
        val store = MemberIdentityStore({ calls++; profile().copy(avatar = null) })
        store.request(" "); store.request("Example"); advanceUntilIdle()
        store.request("EXAMPLE"); advanceUntilIdle()
        assertEquals(1, calls)
        assertTrue(store.identities.value.containsKey("example"))
        assertNull(store.identities.value.getValue("example").avatar)
    }

    @Test fun aCachedStockAvatarYieldsToARealPictureWithoutAnotherProfileLookup() = runTest {
        val placeholder = "https://example.org/images/no_image.png?size=80"
        val picture = "https://example.org/download/file.php?avatar=123.gif"
        val store = MemberIdentityStore({ error("Unexpected profile request") })
        store.remember(profile(setOf(AccountRole.Administrator)).copy(avatar = placeholder))
        assertNull(store.identities.value.getValue("example").avatar.displayAvatar())
        assertEquals("https://example.org/my-no-image-art.png", "https://example.org/my-no-image-art.png".displayAvatar())
        store.rememberAvatar("Example", picture)
        store.request("EXAMPLE")
        val member = store.identities.value.getValue("example")
        assertEquals(picture, member.avatar.displayAvatar())
        assertTrue(member.administrator)
    }

    @Test fun anAvatarFromAMessageSurvivesAChallengeAndDoesNotImplyABadge() = runTest {
        var time = 1L
        var calls = 0
        val store = MemberIdentityStore({ calls++; error("Challenge") }, { time })
        store.rememberAvatar(" Example ", "https://example.org/message.png")
        repeat(3) { store.request("EXAMPLE") }
        val observed = store.identities.value.getValue("example")
        assertEquals("https://example.org/message.png", observed.avatar)
        assertTrue(observed.badges.isEmpty())
        assertFalse(observed.badgesKnown)
        assertEquals(1, calls)
        store.remember(profile(setOf(AccountRole.Moderator)).copy(avatar = null))
        time += 24 * 60 * 60_000L
        store.request("Example")
        assertEquals(1, calls)
        val verified = store.identities.value.getValue("example")
        assertEquals(observed.avatar, verified.avatar)
        assertEquals(setOf(MemberBadge.Moderator), verified.badges)
        store.remember(profile().copy(avatar = null))
        assertTrue(store.identities.value.getValue("example").badges.isEmpty())
        assertNull(store.identities.value.getValue("example").avatar)
    }

    @Test fun avatarsAndBadgesShareOneBackgroundLookupAndRefreshTogether() = runTest {
        var time = 1L
        var calls = 0
        var current = profile(setOf(AccountRole.Administrator)).copy(rank = "Major Android Releaser")
        val store = MemberIdentityStore({ calls++; current }, { time })
        store.request("Example"); store.request("EXAMPLE"); advanceUntilIdle()
        val identity = store.identities.value.getValue("example")
        assertEquals(1, calls)
        assertEquals(current.avatar, identity.avatar)
        assertTrue(identity.administrator)
        assertTrue(identity.majorAndroidReleaser)

        current = profile().copy(avatar = null, rank = "Member")
        time += 24L * 60 * 60_000
        store.request("Example")
        assertEquals(1, calls)
        assertTrue(store.identities.value.getValue("example").administrator)
        time += 29L * 24 * 60 * 60_000
        store.request("Example"); advanceUntilIdle()
        val refreshed = store.identities.value.getValue("example")
        assertEquals(2, calls)
        assertNull(refreshed.avatar)
        assertFalse(refreshed.administrator)
        assertFalse(refreshed.majorAndroidReleaser)
    }
}
