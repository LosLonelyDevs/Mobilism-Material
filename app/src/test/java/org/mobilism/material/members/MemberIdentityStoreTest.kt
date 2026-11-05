package org.mobilism.material.members

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
    private fun profile(roles: Set<AccountRole> = setOf(AccountRole.Member)) = UserProfile(
        ProfileReference(username = "Example"), "Example", "https://example.org/avatar.gif", roles,
        "", emptyList(), "Administrator", emptyMap(), null, null, null, null)

    @Test fun repeatedUsernameRequestsShareOneLookupAndOnlyTrustedRolesGrantBadges() = runTest {
        var calls = 0
        val store = MemberIdentityStore(this, { calls++; profile() })
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
        val store = MemberIdentityStore(this, { calls++; error("Unavailable") }, { time })
        store.remember(profile(setOf(AccountRole.Administrator)))
        time += 15 * 60_000
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
        val store = MemberIdentityStore(this, { wait.await(); error("Unavailable") })
        store.request("Example"); runCurrent()
        store.remember(profile(setOf(AccountRole.Administrator)))
        wait.complete(Unit); advanceUntilIdle()
        assertTrue(store.identities.value.getValue("example").administrator)
    }

    @Test fun coldLookupsRunInBackgroundWithAtMostTwoRequestsAtOnce() = runTest {
        val wait = CompletableDeferred<Unit>()
        var calls = 0
        var active = 0
        var maximum = 0
        val store = MemberIdentityStore(this, { reference ->
            calls++; active++; maximum = maxOf(maximum, active)
            try {
                wait.await()
                profile().copy(reference = reference, username = reference.username!!)
            } finally { active-- }
        })
        listOf("First", "Second", "Third", " FIRST ").forEach(store::request)
        assertEquals(0, calls)
        runCurrent()
        assertEquals(2, calls)
        assertTrue(store.identities.value.isEmpty())
        wait.complete(Unit); advanceUntilIdle()
        assertEquals(3, calls)
        assertEquals(2, maximum)
        assertEquals(setOf("first", "second", "third"), store.identities.value.keys)
        assertTrue(store.identities.value.values.all { it.avatar == "https://example.org/avatar.gif" })
    }

    @Test fun failedLookupCanPopulateAvatarAfterCooldownWithoutOpeningProfile() = runTest {
        var time = 1L
        var calls = 0
        val store = MemberIdentityStore(this, {
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
        val store = MemberIdentityStore(this, { calls++; profile().copy(avatar = null) })
        store.request(" "); store.request("Example"); advanceUntilIdle()
        store.request("EXAMPLE"); advanceUntilIdle()
        assertEquals(1, calls)
        assertTrue(store.identities.value.containsKey("example"))
        assertNull(store.identities.value.getValue("example").avatar)
    }

    @Test fun avatarsAndBadgesShareOneBackgroundLookupAndRefreshTogether() = runTest {
        var time = 1L
        var calls = 0
        var current = profile(setOf(AccountRole.Administrator)).copy(rank = "Major Android Releaser")
        val store = MemberIdentityStore(this, { calls++; current }, { time })
        store.request("Example"); store.request("EXAMPLE"); advanceUntilIdle()
        val identity = store.identities.value.getValue("example")
        assertEquals(1, calls)
        assertEquals(current.avatar, identity.avatar)
        assertTrue(identity.administrator)
        assertTrue(identity.majorAndroidReleaser)

        current = profile().copy(avatar = null, rank = "Member")
        time += 15 * 60_000
        store.request("Example"); advanceUntilIdle()
        val refreshed = store.identities.value.getValue("example")
        assertEquals(2, calls)
        assertNull(refreshed.avatar)
        assertFalse(refreshed.administrator)
        assertFalse(refreshed.majorAndroidReleaser)
    }
}
