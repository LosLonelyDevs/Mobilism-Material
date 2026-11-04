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
}
