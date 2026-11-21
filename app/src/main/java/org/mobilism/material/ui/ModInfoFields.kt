package org.mobilism.material.ui

import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

internal sealed interface ModInfoPart {
    data class Body(val html: String) : ModInfoPart
    data class Languages(val values: List<String>) : ModInfoPart {
        val expandable get() = values.size > 6 || values.sumOf { it.length + 2 } > 72
    }
    data class Densities(val values: List<String>) : ModInfoPart
}

internal object ModInfoFields {
    private val label = Regex("""(?ix)^[\s\p{So}\p{Cf}\uFE0F•·*➜→»▸–—-]*
        ((?:supported\s+)?(?:languages?|locales?)|(?:supported\s+)?(?:screen\s+)?(?:dpis?|densit(?:y|ies)))
        \s*(?:[:：=]\s*|\s*[-–—]\s+|$)(.*)$""")
    private val separators = Regex("""\s*[,;/|·]\s*""")
    private val localeCode = Regex("""(?i)[a-z]{2,3}(?:[-_][a-z0-9]{2,8})*""")
    private val density = Regex("""(?i)(\d{2,4})\s*(?:dpi)?""")
    private val languageCodes by lazy {
        Locale.getISOLanguages().flatMap { code -> listOfNotNull(code, runCatching { Locale.forLanguageTag(code).isO3Language }.getOrNull()) }
            .toSet() + setOf("iw", "in", "ji", "fil", "und")
    }

    private fun isLocale(value: String) = localeCode.matches(value) &&
        value.takeWhile { it != '-' && it != '_' }.lowercase(Locale.ROOT) in languageCodes
    private val languageNames by lazy {
        Locale.getAvailableLocales().flatMap { listOf(it.getDisplayLanguage(Locale.ENGLISH), it.getDisplayLanguage(it)) }
            .mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }
    }
    private val protectedTags = setOf("a", "pre", "tt", "code", "blockquote", "ol", "table")

    fun parse(html: String, baseUrl: String, markdown: Boolean): List<ModInfoPart> {
        if (html.length > 200_000 || !Regex("(?i)lang|locale|dpi|densit").containsMatchIn(html)) return emptyList()
        val source = if (markdown) ForumFormatting.format(html, baseUrl, ForumFormattingOptions(markdown = true)) else html
        val body = Jsoup.parseBodyFragment(source, baseUrl).body()
        val lines = ReleaseSections.lines(body)
        val groupByNode = mutableMapOf<TextNode, Int>()
        val fields = mutableListOf<ModInfoPart>()
        fun plain(line: ReleaseLine) = line.nodes.none { node ->
            generateSequence(node.parent()) { it.parent() }.any { it.normalName() in protectedTags }
        }
        var index = 0
        while (index < lines.size) {
            val line = lines[index++]
            val match = label.matchEntire(line.text.trim()) ?: continue
            if (!plain(line)) continue
            val languages = match.groupValues[1].contains("lang", true) || match.groupValues[1].contains("locale", true)
            val raw = match.groupValues[2].trim()
            val entries = if (raw.isEmpty()) mutableListOf() else values(raw, languages)?.toMutableList() ?: continue
            val nodes = line.nodes.toMutableList()
            var continuation = raw.isEmpty() || raw.last() in ",;/|·"
            while (index < lines.size) {
                val next = lines[index]
                val text = next.text.trim()
                if (!continuation && !separators.containsMatchIn(text)) break
                if (!plain(next)) break
                val more = values(text, languages) ?: break
                entries += more
                nodes += next.nodes
                index++
                continuation = text.lastOrNull() in listOf(',', ';', '/', '|', '·')
            }
            if (entries.isEmpty()) continue
            val distinct = entries.distinctBy { it.lowercase(Locale.ROOT) }
            fields += if (languages) ModInfoPart.Languages(distinct) else ModInfoPart.Densities(distinct)
            nodes.forEach { groupByNode[it] = fields.size }
        }
        if (fields.isEmpty()) return emptyList()

        val fragments = splitReleaseNodes(body, 0) { node ->
            when {
                node is TextNode && !node.isBlank -> groupByNode[node] ?: 0
                node is Element && node.normalName() in setOf("img", "hr") -> 0
                else -> null
            }
        }
        val result = mutableListOf<ModInfoPart>()
        val prose = StringBuilder()
        fun flush() {
            if (prose.isEmpty()) return
            val original = prose.toString()
            val fragment = Jsoup.parseBodyFragment(original).body()
            if (fragment.text().isNotBlank() || fragment.selectFirst("img, hr") != null) result += ModInfoPart.Body(original)
            prose.clear()
        }
        var lastField = 0
        for ((group, node) in fragments) {
            if (group == 0) prose.append(node.outerHtml())
            else if (group != lastField) {
                flush()
                result += fields[group - 1]
                lastField = group
            }
        }
        flush()
        return result
    }

    private fun values(raw: String, languages: Boolean): List<String>? {
        val text = raw.trim().trimEnd(',', ';', '/', '|', '·').trimEnd()
        if (text.isEmpty()) return null
        val parts = separators.split(text)
        if (!languages) {
            return parts.flatMap { it.split(Regex("""\s+(?=\d)""")) }.map {
                density.matchEntire(it)?.groupValues?.get(1) ?: return null
            }
        }
        return parts.flatMap { entry ->
            when {
                isLocale(entry) -> listOf(entry)
                entry.lowercase(Locale.ROOT) in languageNames -> listOf(entry)
                else -> entry.split(Regex("""\s+""")).takeIf { it.size > 1 && it.all(::isLocale) } ?: return null
            }
        }
    }
}
