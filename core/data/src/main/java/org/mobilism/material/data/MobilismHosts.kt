package org.mobilism.material.data

import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/** Same publisher-maintained file-host directory used by the legacy Android client. */
internal object MobilismHosts {
    const val URL = "https://forum.mobilism.org/filehosts.xml"

    fun parse(xml: String): List<String> {
        val root = Jsoup.parse(xml, "", Parser.xmlParser()).selectFirst("supported-filehosts")
            ?: error("Missing file-host directory")
        return root.select("host[url]").mapNotNull { HostMatcher.domain(it.attr("url")) }
            .distinct().also { check(it.isNotEmpty()) }
    }

    fun shortlist(domains: List<String>, supported: List<SupportedHost>): List<SupportedHost> =
        domains.mapNotNull { domain ->
            val match = supported.firstOrNull { HostMatcher.matches(domain, it.domain) }
                ?: return@mapNotNull null
            val knownName = HostMatcher.displayName(domain)
            SupportedHost(domain, if (knownName == domain) match.name else knownName)
        }.distinctBy { it.domain }
}
