package org.mobilism.material.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumDownloadAccessTest {
    @Test fun eachVerifiedProviderOnlyUnlocksItsOwnHostsForItsForumOwner() {
        val owner = AuthState.SignedIn("ForumUser")
        for (provider in DebridProvider.entries) {
            val account = DebridAccount(provider, configured = true, validated = true, hostsVerified = true,
                hosts = listOf(SupportedHost("files.example", "Files")), forumOwner = "forumuser")
            val state = DebridState(listOf(account))
            assertTrue(state.hasPremiumAccount(owner))
            assertEquals(listOf(account), state.forForumUser(owner).supporting("https://files.example/release"))
            assertTrue(state.forForumUser(owner).supporting("https://another.example/release.apk").isEmpty())
            for (other in listOf(AuthState.Restoring, AuthState.Guest, AuthState.SignedOut, AuthState.SignedIn("Other"))) {
                assertFalse(state.hasPremiumAccount(other))
                assertTrue(state.forForumUser(other).supporting("https://files.example/release").isEmpty())
            }
            assertFalse(state.copy(enabled = false).hasPremiumAccount(owner))
            assertFalse(DebridState(listOf(account.copy(validated = false))).hasPremiumAccount(owner))
        }
    }
}
