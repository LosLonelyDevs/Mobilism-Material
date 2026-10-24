package org.mobilism.material.data

internal object MobilismHosts {
    // From https://forum.mobilism.org/filehosts.xml, checked 2026-10-24.
    // Keep this local so a forum outage cannot block either debrid provider.
    val domains: List<String> = listOf(
        "rapidgator.net", "rg.to", "uploadrar.com", "uploadrar.net",
        "upfiles.com", "upfilesgo.com", "sendit.cloud", "filedot.to", "filedot.pw",
        "devuploads.com", "10drives.com", "uploda.sh", "vikingfile.com", "send.now",
        "cloudfam.io", "zincdrive.com", "zdrive.to", "zipyshare.com", "up-4ever.net",
    )

    fun shortlist(domains: List<String>, supported: List<SupportedHost>): List<SupportedHost> =
        domains.mapNotNull { domain ->
            val match = supported.firstOrNull { HostMatcher.matches(domain, it.domain) }
                ?: return@mapNotNull null
            val knownName = HostMatcher.displayName(domain)
            SupportedHost(domain, if (knownName == domain) match.name else knownName)
        }.distinctBy { it.domain }
}
