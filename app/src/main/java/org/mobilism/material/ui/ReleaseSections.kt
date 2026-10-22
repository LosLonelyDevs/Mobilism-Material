package org.mobilism.material.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

enum class ReleaseSection(val title: String) { Description("Description"), Changes("What's new"), Mod("Mod info") }
data class ReleaseTextSection(val kind: ReleaseSection, val html: String)

/** Split headings without dropping nested formatting or treating prose as a heading. */
object ReleaseSections {
    private val heading = Regex("""(?i)^\s*(description|overview|more\s+info|what['’]?s\s+new(?:\s+in\s+this\s+version)?|changelog|change\s+log|mod\s+(?:info(?:rmation)?|features)|modded\s+info)\s*(?::\s*(.*))?$""")
    private val modBullet = Regex("""(?i)^[◉•●✓✔★\-]+\s*(premium|pro|vip|paid)\s+(activated|unlocked)\b.*""")
    private val decoration = Regex("""^[\s\p{So}\p{Sk}\p{Cf}\uFFFD*#_\[\]<>|=~\-]+|[\s\p{So}\p{Sk}\p{Cf}\uFFFD*#_\[\]<>|=~\-]+$""")
    private val modCredit = Regex("""(?i)^mod\s+by\s+\S.{0,80}$""")
    private val titleTag = Regex("""\[([^\[\]]+)]""")
    private val modTag = Regex("""(?i)^mod(?:ded)?(?:\s.*)?$""")
    private val namedVersion = Regex("""(?i)^(.+?)\s+v?\d+(?:\.\d+)+(?:[-+][\w.-]+)?$""")
    private val featureBullet = Regex("""^(?:[-•●◉✓✔★]|\d+[.)])\s+\S.*$""")

    fun parse(html: String, releaseTitle: String = ""): List<ReleaseTextSection> {
        val tags = titleTag.findAll(Jsoup.parseBodyFragment(releaseTitle).text()).map { it.groupValues[1].trim() }.toList()
        val modNames = if (tags.any(modTag::matches)) tags.filterNot(modTag::matches).map { it.lowercase() }.toSet() else emptySet()
        fun isNamedModBlock(node: Element): Boolean {
            if (modNames.isEmpty()) return false
            val lines = node.wholeText().lineSequence().map(String::trim).filter(String::isNotBlank).take(2).toList()
            if (lines.size < 2 || !featureBullet.matches(lines[1])) return false
            val name = namedVersion.matchEntire(lines[0].replace(decoration, "").trim())?.groupValues?.get(1)
            return name?.lowercase() in modNames
        }
        var current = ReleaseSection.Description
        fun split(node: Node): List<Pair<ReleaseSection, Node>> {
            // Named mods can use their own version heading instead of "Mod Info".
            if (node is Element && node.normalName() in setOf("b", "strong") &&
                (modBullet.matches(node.text().trim()) || isNamedModBlock(node))) {
                current = ReleaseSection.Mod
            }
            if (node is TextNode) {
                if (node.text().trim().equals("This app has no advertisements", ignoreCase = true)) current = ReleaseSection.Mod
                val parent = node.parent()
                val ancestors = generateSequence(parent) { it.parent() }.toList()
                val block = ancestors.firstOrNull { it.isBlock }
                val headingContext = parent?.isBlock == true || block?.text()?.trim() == node.text().trim() ||
                    ancestors.takeWhile { !it.isBlock }.any { it.normalName() in setOf("b", "strong", "u", "h1", "h2", "h3", "h4", "h5", "h6") }
                val labelText = node.text().trim().replace(decoration, "").trim()
                if (headingContext && modCredit.matches(labelText)) current = ReleaseSection.Mod
                val match = if (headingContext) heading.matchEntire(labelText) else null
                if (match != null) {
                    val label = match.groupValues[1].lowercase()
                    current = when {
                        label.startsWith("mod") -> ReleaseSection.Mod
                        label == "description" || label == "overview" || label == "more info" -> ReleaseSection.Description
                        else -> ReleaseSection.Changes
                    }
                    val rest = match.groupValues[2]
                    if (label == "more info") return listOf(current to node.clone())
                    return if (rest.isBlank()) emptyList() else listOf(current to TextNode(rest))
                }
                return listOf(current to node.clone())
            }
            if (node !is Element || node.childNodeSize() == 0) return listOf(current to node.clone())
            val output = mutableListOf<Pair<ReleaseSection, Node>>()
            node.childNodes().flatMap { split(it) }.forEach { (kind, child) ->
                val wrapper = if (output.lastOrNull()?.first == kind) output.last().second as Element
                    else node.shallowClone().also { output += kind to it }
                wrapper.appendChild(child)
            }
            return output
        }
        val fragments = Jsoup.parseBodyFragment(html).body().childNodes().flatMap { split(it) }
        return ReleaseSection.entries.mapNotNull { kind ->
            val body = fragments.filter { it.first == kind }.joinToString("") { it.second.outerHtml() }
            if (Jsoup.parseBodyFragment(body).text().isBlank()) null else ReleaseTextSection(kind, body)
        }
    }
}
