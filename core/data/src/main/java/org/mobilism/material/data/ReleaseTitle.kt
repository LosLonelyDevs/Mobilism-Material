package org.mobilism.material.data

import org.jsoup.Jsoup

data class ReleaseTitle(val name: String, val version: String? = null, val build: String? = null,
    val tags: List<String> = emptyList(), val bookAuthor: String? = null, val series: String? = null) {
    val subtitle: String get() = (listOfNotNull(bookAuthor?.let { "by $it" }, series,
        version?.let { "v$it" }, build?.let { "build $it" }) + tags).joinToString(" · ")
}

/** A confident version boundary survives unfamiliar or truncated metadata. */
object ReleaseTitles {
    private val version = Regex("""(?i)\s+(?:v(?:ersion|er\.?)?\s*[:=]?\s*)(\d[\w.+-]*)(?=\s|[\[(]|$)""")
    private val bareVersion = Regex("""\s+(\d+(?:\.\d+){1,}(?:[-+][\w.]+)?)(?=\s|[\[(]|$)""")
    private val build = Regex("""(?i)^(?:build\s*[:#]?\s*|b\s*)(\d[\w.-]*)\s*""")
    private val numericBuild = Regex("""^\((\d+)\)\s*""")
    private val tag = Regex("""^(?:\[([^\[\]]+)\]|\(([^()]+)\))\s*""")
    private val channel = Regex("""(?i)^(alpha|beta|rc|stable|final|fixed|hotfix|release)(?:\s+(\d+))?(?=\s|[\[(]|$)\s*""")
    private val standaloneTags = setOf("premium", "pro", "paid", "mod", "unlocked", "patched", "adfree", "ad-free", "free")

    fun parse(html: String, section: ContentSection? = null): ReleaseTitle {
        val original = Jsoup.parseBodyFragment(html).text().replace('\u00a0', ' ').trim()
        if (section == ContentSection.Books || (section == null && BookTitles.hasBookMetadata(original))) {
            return BookTitles.parse(original)
        }
        val marker = version.findAll(original).firstOrNull { match ->
            match.groupValues[1].contains('.') || understoodSuffix(original.substring(match.range.last + 1))
        } ?: bareVersion.findAll(original).firstOrNull { match ->
            val suffix = original.substring(match.range.last + 1).trim()
            suffix.isNotEmpty() && understoodSuffix(suffix)
        }
        if (marker == null) {
            val suffix = Regex("""\s+\[([^\[\]]+)\]$""").find(original)
            return if (suffix != null && suffix.groupValues[1].lowercase() in standaloneTags) {
                ReleaseTitle(original.substring(0, suffix.range.first).trim(), tags = listOf(suffix.groupValues[1]))
            } else ReleaseTitle(original)
        }
        val name = original.substring(0, marker.range.first).trim().trimEnd('-', ':', '|').trim()
        if (name.isBlank()) return ReleaseTitle(original)
        var rest = original.substring(marker.range.last + 1).trim()
        var buildValue: String? = null
        val tags = mutableListOf<String>()
        while (rest.isNotEmpty()) {
            val buildMatch = if (buildValue == null) build.find(rest) ?: numericBuild.find(rest) else null
            val tagMatch = tag.find(rest)
            val channelMatch = channel.find(rest)
            val consumed = when {
                buildMatch != null -> { buildValue = buildMatch.groupValues[1]; buildMatch.value.length }
                tagMatch != null -> {
                    tags += tagMatch.groupValues.drop(1).first { it.isNotEmpty() }.trim()
                    tagMatch.value.length
                }
                channelMatch != null -> { tags += channelMatch.value.trim(); channelMatch.value.length }
                else -> {
                    // Keep unknown text, including an unfinished tag, without guessing its completion.
                    val end = rest.indexOfAny(charArrayOf('[', '('), startIndex = 1).takeIf { it > 0 } ?: rest.length
                    tags += rest.substring(0, end).trim()
                    end
                }
            }
            rest = rest.substring(consumed).trim()
        }
        return ReleaseTitle(name, marker.groupValues[1], buildValue, tags)
    }

    private fun understoodSuffix(value: String): Boolean {
        val suffix = value.trim()
        return suffix.isEmpty() || suffix.startsWith('[') || build.containsMatchIn(suffix) ||
            numericBuild.containsMatchIn(suffix) || channel.containsMatchIn(suffix) ||
            suffix.lowercase() in standaloneTags
    }
}
