package org.mobilism.material.data

import org.junit.Assert.*
import org.junit.Test

class HostMatcherTest {
    @Test fun acceptsDomainAndRealSubdomains() {
        assertTrue(HostMatcher.matches("https://www.rapidgator.net/file/a", "rapidgator.net"))
        assertTrue(HostMatcher.matches("https://download.rapidgator.net/file/a", "rapidgator.net"))
    }

    @Test fun refusesLookalikeDomainsAndEmbeddedUrls() {
        assertFalse(HostMatcher.matches("https://rapidgator.net.example.org/a", "rapidgator.net"))
        assertFalse(HostMatcher.matches("https://notrapidgator.net/a", "rapidgator.net"))
        assertFalse(HostMatcher.matches("https://example.org/?next=https://rapidgator.net/a", "rapidgator.net"))
    }

    @Test fun catalogAloneDoesNotEnableHighlighting() {
        val host = SupportedHost("rapidgator.net", "Rapidgator")
        val noKey = DebridState(listOf(DebridAccount(DebridProvider.RealDebrid, hosts = listOf(host))))
        assertTrue(noKey.supporting("https://rapidgator.net/file/a").isEmpty())
        val configured = DebridState(listOf(DebridAccount(DebridProvider.RealDebrid, configured = true, hosts = listOf(host))))
        assertEquals(1, configured.supporting("https://rapidgator.net/file/a").size)
    }
}
