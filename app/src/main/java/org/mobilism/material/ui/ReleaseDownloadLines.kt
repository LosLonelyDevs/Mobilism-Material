package org.mobilism.material.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal data class DownloadLine(val html: String, val text: String, val styled: Boolean, val headingLevel: Int?)

/** Split at visible line boundaries while retaining nested formatting and full link targets. */
internal object ReleaseDownloadLines {
    val marker = Regex("""(?i)^Download\s+(?:Instructions|Links)\s*:?$""")
    private val styledTags = setOf("b", "strong", "u", "h1", "h2", "h3", "h4", "h5", "h6")

    fun read(html: String, baseUrl: String): List<DownloadLine> {
        val document = Jsoup.parseBodyFragment(html, baseUrl)
        document.outputSettings().prettyPrint(false)
        val body = document.body()
        val result = mutableListOf<DownloadLine>()
        var fragment = Element("div")
        val wrappers = mutableMapOf<Element, Element>()
        val visible = mutableListOf<TextNode>()
        fun ancestors(node: Node) = generateSequence(node.parent()) { it.parent() }.takeWhile { it != body }.filterIsInstance<Element>().toList()
        fun flush() {
            if (fragment.text().isNotBlank() || fragment.selectFirst("a[href], img[src]") != null) {
                result += DownloadLine(fragment.html(), fragment.text(),
                    visible.isNotEmpty() && visible.all { text -> ancestors(text).any { it.normalName() in styledTags } },
                    visible.firstOrNull()?.let { ancestors(it).firstOrNull { p -> p.normalName().matches(Regex("h[1-6]")) }
                        ?.normalName()?.last()?.digitToInt() })
            }
            fragment = Element("div")
            wrappers.clear()
            visible.clear()
        }
        fun append(node: Node) {
            var parent = fragment
            ancestors(node).asReversed().forEach { original ->
                val container = parent
                parent = wrappers.getOrPut(original) { original.shallowClone().also { clone ->
                    if (clone.hasAttr("href")) clone.attr("href", original.absUrl("href").ifBlank { original.attr("href") })
                    container.appendChild(clone)
                } }
            }
            parent.appendChild(node.clone())
            if (node is TextNode && !node.isBlank) visible += node
        }
        fun visit(node: Node) {
            when (node) {
                is TextNode -> append(node)
                is Element -> when {
                    node.normalName() in setOf("script", "style") -> Unit
                    node.normalName() in setOf("br", "hr") -> flush()
                    else -> {
                        val boundary = node.isBlock || (node.normalName() in styledTags &&
                            (node.selectFirst("br") != null || marker.matches(node.text())))
                        if (boundary) flush()
                        if (node.childNodeSize() == 0) append(node) else node.childNodes().forEach(::visit)
                        if (boundary) flush()
                    }
                }
            }
        }
        body.childNodes().forEach(::visit)
        flush()
        return result
    }
}
