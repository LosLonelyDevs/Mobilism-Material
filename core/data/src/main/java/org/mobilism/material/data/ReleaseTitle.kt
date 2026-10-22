package org.mobilism.material.data

import org.jsoup.Jsoup

data class ReleaseTitle(val name: String, val version: String? = null, val build: String? = null, val tags: List<String> = emptyList())

/** Only split explicit version markers when the entire metadata suffix is understood. */
object ReleaseTitles {
    private val version = Regex("""\s+[vV](\d[\w.+-]*)(?=\s|\[|$)""")
    private val build = Regex("""(?i)^(?:build\s*[:#]?\s*|b\s*)(\d[\w.-]*)\s*""")
    private val numericBuild = Regex("""^\((\d+)\)\s*""")
    private val tag = Regex("""^\[([^\[\]]+)\]\s*""")
    private val channel = Regex("""(?i)^(alpha|beta|rc|stable|final)(?:\s+(\d+))?(?=\s|\[|$)\s*""")
    private val standaloneTags = setOf("premium", "pro", "paid", "mod", "unlocked", "patched", "adfree", "ad-free", "free")

    fun parse(html: String): ReleaseTitle {
        val original = Jsoup.parseBodyFragment(html).text().trim()
        val marker = version.find(original)
        if (marker == null) {
            val suffix = Regex("""\s+\[([^\[\]]+)\]$""").find(original)
            return if (suffix != null && suffix.groupValues[1].lowercase() in standaloneTags) {
                ReleaseTitle(original.substring(0, suffix.range.first).trim(), tags = listOf(suffix.groupValues[1]))
            } else ReleaseTitle(original)
        }
        val name = original.substring(0, marker.range.first).trim()
        if (name.isBlank()) return ReleaseTitle(original)
        var rest = original.substring(marker.range.last + 1).trim()
        val channelMatch = channel.find(rest)
        if (channelMatch != null) rest = rest.substring(channelMatch.range.last + 1).trim()
        val buildMatch = build.find(rest) ?: numericBuild.find(rest)
        val buildValue = buildMatch?.groupValues?.get(1)
        if (buildMatch != null) rest = rest.substring(buildMatch.range.last + 1).trim()
        val tags = mutableListOf<String>()
        channelMatch?.value?.trim()?.let(tags::add)
        while (rest.isNotEmpty()) {
            val match = tag.find(rest) ?: return ReleaseTitle(original)
            tags += match.groupValues[1].trim()
            rest = rest.substring(match.range.last + 1).trim()
        }
        return ReleaseTitle(name, marker.groupValues[1], buildValue, tags)
    }
}
