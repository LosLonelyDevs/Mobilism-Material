package org.mobilism.material.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

enum class ReleaseSection(val title: String) { Description("Description"), Changes("What's new"), Mod("Mod info") }
data class ReleaseTextSection(val kind: ReleaseSection, val html: String, val startsWithAdvertisingNotice: Boolean = false,
    val changelogPlaceholder: String? = null)

internal data class ReleaseLine(
    val nodes: List<TextNode>,
    val text: String,
    val styled: Boolean,
    val listItem: Boolean,
    val linkOnly: Boolean,
    var kind: ReleaseSection = ReleaseSection.Description,
    var boundary: Boolean = false,
    var advertising: Boolean = false,
)

/** Classify visible lines, then split the original DOM so links and formatting survive. */
object ReleaseSections {
    private val heading = Regex("""(?ix)^\s*(
        description | overview | more\s+info |
        what['’]?s\s+new(?:\s+in\s+this\s+version)? | changelog | change\s+log |
        (?:custom\s+)?mod(?:ded)?(?:\s+(?:info(?:rmation)?|features|details|notes|version))?(?:\s*[/&+]\s*(?:info|features|details))? |
        modifications? | what['’]?s\s+(?:modded|modified) | about\s+(?:this\s+)?mod |
        patch(?:ed)?\s+(?:info|details|features)
        )\s*(?::\s*(.*))?$""")
    private val decoration = Regex("""^[\s\p{So}\p{Sk}\p{Cf}\uFFFD*#_\[\]<>|=~\-]+|[\s\p{So}\p{Sk}\p{Cf}\uFFFD*#_\[\]<>|=~\-]+$""")
    private val modCredit = Regex("""(?i)^(?:mod(?:ded|ified)?|patched|cracked|repacked)\s+by\s+\S.{0,80}$""")
    private val attributedModHeading = Regex("""(?ix)^(?:custom\s+)?
        (?:mod(?:ded)?\s+(?:info(?:rmation)?|features|details|notes|version)|patch(?:ed)?\s+(?:info|details|features))
        \s+(?:by|from)\s+[^:!?\n]{1,80}:?$""")
    private val advertising = Regex("""(?i)^this\s+(?:app|application|game)\s+(?:has|contains|includes|does\s+not\s+contain)\s+(?:[\w-]+\s+){0,3}(?:advertisements?|ads)[.!]?$""")
    private val styledTags = setOf("b", "strong", "u", "h1", "h2", "h3", "h4", "h5", "h6")
    private val noChangelog = Regex("""(?i)no\s+change\s*log(?:\s+(?:available|provided))?[.!]?""")

    internal fun isAdvertisingNotice(text: String) = advertising.matches(text.trim())

    fun parse(html: String, releaseTitle: String = "", cleanUp: Boolean = false): List<ReleaseTextSection> {
        val body = Jsoup.parseBodyFragment(html).body()
        val lines = lines(body)
        val replacements = mutableMapOf<TextNode, String>()
        var current = ReleaseSection.Description
        lines.forEach { line ->
            val labelText = line.text.trim().replace(decoration, "").trim()
            val match = if (line.linkOnly) null else heading.matchEntire(labelText)
            when {
                match != null -> {
                    val label = match.groupValues[1].lowercase().replace(Regex("""\s+"""), " ")
                    current = when {
                        label in setOf("description", "overview", "more info") -> ReleaseSection.Description
                        (label.startsWith("what") && label.contains("new")) || label in setOf("changelog", "change log") -> ReleaseSection.Changes
                        else -> ReleaseSection.Mod
                    }
                    line.kind = current
                    line.boundary = true
                    if (label != "more info") {
                        val rest = match.groupValues[2]
                        var toRemove = if (rest.isBlank()) line.text.length else line.text.lastIndexOf(rest)
                        line.nodes.forEach { node ->
                            val raw = node.wholeText
                            val count = toRemove.coerceIn(0, raw.length)
                            replacements[node] = raw.drop(count)
                            toRemove -= count
                        }
                    }
                }
                !line.linkOnly && (modCredit.matches(labelText) || (attributedModHeading.matches(labelText) &&
                    !labelText.endsWith('.') && (line.styled || labelText.endsWith(':')))) -> {
                    current = ReleaseSection.Mod
                    line.kind = current
                    line.boundary = true
                }
                isAdvertisingNotice(labelText) -> {
                    line.kind = ReleaseSection.Mod
                    line.advertising = true
                    line.boundary = true
                    // This footer ends the previous section; it does not make every following line mod info.
                    current = ReleaseSection.Description
                }
                else -> line.kind = current
            }
        }
        ReleaseModHeuristics.classify(lines, releaseTitle, cleanUp)
        val kinds = lines.flatMap { line -> line.nodes.map { it to line.kind } }.toMap()
        val fragments = splitReleaseNodes(body, ReleaseSection.Description, replacements) { kinds[it] }
        val firstModLine = lines.firstOrNull { line -> line.kind == ReleaseSection.Mod &&
            line.nodes.any { (replacements[it] ?: it.wholeText).isNotBlank() } }
        return ReleaseSection.entries.mapNotNull { kind ->
            val content = fragments.filter { it.first == kind }.joinToString("") { it.second.outerHtml() }
            val fragment = Jsoup.parseBodyFragment(content)
            val text = fragment.text()
            if (text.isBlank()) null else ReleaseTextSection(kind, content,
                startsWithAdvertisingNotice = kind == ReleaseSection.Mod && firstModLine?.advertising == true,
                changelogPlaceholder = text.takeIf { cleanUp && kind == ReleaseSection.Changes && noChangelog.matches(it) &&
                    fragment.selectFirst("a, img, code, pre") == null })
        }
    }

    internal fun lines(body: Element): List<ReleaseLine> {
        val result = mutableListOf<ReleaseLine>()
        val pending = mutableListOf<TextNode>()
        fun flush() {
            val text = pending.joinToString("") { it.wholeText }
            if (text.isNotBlank()) {
                val visible = pending.filter { !it.isBlank }
                fun ancestors(node: TextNode) = generateSequence(node.parent()) { it.parent() }.takeWhile { it != body }
                result += ReleaseLine(
                    pending.toList(), text,
                    styled = visible.any { node -> ancestors(node).any { it.normalName() in styledTags } },
                    listItem = visible.any { node -> ancestors(node).any { it.normalName() == "li" } },
                    linkOnly = visible.all { node -> ancestors(node).any { it.normalName() == "a" } },
                )
            }
            pending.clear()
        }
        fun visit(node: Node) {
            when (node) {
                is TextNode -> pending += node
                is Element -> {
                    if (node.normalName() == "br") flush()
                    else {
                        val multilineStyle = node.normalName() in styledTags && node.selectFirst("br") != null
                        if (node.isBlock || multilineStyle) flush()
                        node.childNodes().forEach(::visit)
                        if (node.isBlock || multilineStyle) flush()
                    }
                }
            }
        }
        body.childNodes().forEach(::visit)
        flush()
        return result
    }
}

/** Keep wrappers and links intact on both sides of a section boundary. */
internal fun <T : Any> splitReleaseNodes(body: Element, initial: T, replacements: Map<TextNode, String> = emptyMap(),
    group: (Node) -> T?): List<Pair<T, Node>> {
    var current = initial
    fun split(node: Node): List<Pair<T, Node>> {
        current = group(node) ?: current
        if (node is TextNode) {
            val text = replacements[node] ?: node.wholeText
            return if (text.isEmpty()) emptyList() else listOf(current to TextNode(text))
        }
        if (node !is Element || node.childNodeSize() == 0) return listOf(current to node.clone())
        val output = mutableListOf<Pair<T, Node>>()
        node.childNodes().flatMap(::split).forEach { (kind, child) ->
            val wrapper = if (output.lastOrNull()?.first == kind) output.last().second as Element
                else node.shallowClone().also { output += kind to it }
            wrapper.appendChild(child)
        }
        return output
    }
    return body.childNodes().flatMap(::split)
}
