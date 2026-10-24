package org.mobilism.material.data

import org.junit.Assert.*
import org.junit.Test

class MobilismHostsTest {
    @Test fun bundledDirectoryContainsValidUniqueDomainsAndAliases() {
        val domains = MobilismHosts.domains
        assertTrue(domains.isNotEmpty())
        assertEquals(domains.distinct(), domains)
        assertTrue(domains.all { it == HostMatcher.domain(it) })
        assertTrue(domains.containsAll(listOf("rapidgator.net", "rg.to", "uploadrar.com", "uploadrar.net")))
    }

    @Test fun bundledDirectoryDoesNotGrantSupportWithoutProviderVerification() {
        val supported = listOf(SupportedHost("send.now", "Send.now"), SupportedHost("example.org", "Other"))
        val hosts = MobilismHosts.shortlist(MobilismHosts.domains, supported)
        assertEquals(listOf(SupportedHost("send.now", "Send.now")), hosts)
        val account = DebridAccount(DebridProvider.RealDebrid, configured = true, hosts = hosts, validated = true)
        assertTrue(DebridState(listOf(account)).supporting("https://send.now/file").isEmpty())
        assertEquals(1, DebridState(listOf(account.copy(hostsVerified = true))).supporting("https://send.now/file").size)
        assertTrue(MobilismHosts.shortlist(MobilismHosts.domains, emptyList()).isEmpty())
    }

    @Test fun excludesUnrelatedProviderDomainsAndGroupsAliases() {
        val hosts = MobilismHosts.shortlist(
            listOf("rapidgator.net", "rg.to", "uploadrar.com"),
            listOf(
                SupportedHost("rapidgator.net", "RapidGator"), SupportedHost("rg.to", "rg.to"),
                SupportedHost("youtube.com", "YouTube"), SupportedHost("example.org", "Unrelated"),
            ),
        )
        val account = DebridAccount(DebridProvider.RealDebrid, true, hosts)
        assertEquals(listOf("Rapidgator"), account.hostNames)
        assertEquals(2, hosts.size)
        assertFalse(DebridState(listOf(account)).supporting("https://youtube.com/example").isNotEmpty())
    }
}
