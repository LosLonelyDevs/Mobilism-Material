package org.mobilism.material.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HostOrderTest {
    @Test fun premiumAccessRanksAheadOfPreferenceWithAlphabeticalNamesWithinEachTier() {
        val hosts = listOf(
            SupportedHost("other-z.example", "z other"), SupportedHost("preferred-z.example", "z preferred"),
            SupportedHost("premium-z.example", "z premium"), SupportedHost("both-z.example", "z both"),
            SupportedHost("other-a.example", "A other"), SupportedHost("preferred-a.example", "A preferred"),
            SupportedHost("premium-a.example", "A premium"), SupportedHost("both-a.example", "A both"),
        )
        val preferred = hosts.filter { "preferred" in it.domain || "both" in it.domain }.map { it.domain }.toSet()
        val premium = hosts.filter { "premium" in it.domain || "both" in it.domain }
        val debrid = DebridState(listOf(DebridAccount(DebridProvider.RealDebrid, configured = true,
            validated = true, hostsVerified = true, hosts = premium)))
        assertEquals(listOf("A both", "z both", "A premium", "z premium", "A preferred", "z preferred", "A other", "z other"),
            HostOrder.sorted(hosts, preferred, debrid, { it.domain }) { it.name }.map { it.name })
    }

    @Test fun savedUnverifiedOrDisabledAccountsDoNotGivePremiumPriority() {
        val hosts = listOf(SupportedHost("z.example", "Z host"), SupportedHost("a.example", "A host"))
        val active = DebridAccount(DebridProvider.MegaDebrid, configured = true, validated = true,
            hostsVerified = true, hosts = listOf(hosts.first()))
        val states = listOf(DebridState(listOf(active.copy(configured = false))),
            DebridState(listOf(active.copy(validated = false))), DebridState(listOf(active.copy(hostsVerified = false))),
            DebridState(listOf(active), enabled = false), DebridState())
        states.forEach { state ->
            assertEquals(listOf("A host", "Z host"),
                HostOrder.sorted(hosts, emptySet(), state, { it.domain }) { it.name }.map { it.name })
        }
    }

    @Test fun preferredAliasesAndPremiumSubdomainsUseTheSameHostRulesAsDownloads() {
        val urls = listOf("https://alpha.example/a", "https://cdn.devuploads.com/b", "https://rg.to/c")
        val debrid = DebridState(listOf(DebridAccount(DebridProvider.RealDebrid, configured = true,
            validated = true, hostsVerified = true, hosts = listOf(SupportedHost("devuploads.com", "DevUploads")))))
        assertEquals(listOf(urls[1], urls[2], urls[0]),
            HostOrder.sorted(urls, setOf("rapidgator.net"), debrid, { it }, HostMatcher::displayName))
    }
}
