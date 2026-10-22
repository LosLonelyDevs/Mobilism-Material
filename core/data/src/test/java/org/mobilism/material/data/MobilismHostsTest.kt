package org.mobilism.material.data

import org.junit.Assert.*
import org.junit.Test

class MobilismHostsTest {
    @Test fun parsesOnlyPublisherHostEntries() {
        val domains = MobilismHosts.parse("""
            <supported-filehosts>
                <host url="rapidgator.net"/><host url="rg.to"/>
                <host url="rapidgator.net"/><host url="javascript:bad"/>
            </supported-filehosts>
        """.trimIndent())
        assertEquals(listOf("rapidgator.net", "rg.to"), domains)
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
