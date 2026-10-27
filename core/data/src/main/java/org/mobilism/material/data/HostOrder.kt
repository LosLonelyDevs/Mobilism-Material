package org.mobilism.material.data

object HostOrder {
    fun <T> sorted(items: Iterable<T>, preferredHosts: Set<String>, debrid: DebridState,
        host: (T) -> String, label: (T) -> String): List<T> =
        Alphabetical.sorted(items, label).sortedBy { item ->
            val url = host(item)
            val key = HostMatcher.key(url)
            val preferred = preferredHosts.any {
                (key != null && key == HostMatcher.key(it)) || HostMatcher.matches(url, it)
            }
            val premium = debrid.supporting(url).isNotEmpty()
            when {
                preferred && premium -> 0
                premium -> 1
                preferred -> 2
                else -> 3
            }
        }
}
