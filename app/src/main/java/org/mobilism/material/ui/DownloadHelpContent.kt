package org.mobilism.material.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.mobilism.material.data.webUrlOrNull

internal sealed interface HelpBlock {
    data class Text(val html: String) : HelpBlock
    data class Image(val url: String) : HelpBlock
    data class Section(val title: String, val blocks: List<HelpBlock>) : HelpBlock
}

internal object DownloadHelpContent {
    fun parse(html: String, baseUrl: String): List<HelpBlock> {
        val document = Jsoup.parseBodyFragment(ForumCodeBlocks.normalize(html, baseUrl), baseUrl)
        document.outputSettings().prettyPrint(false)
        document.select("script, style, iframe, form").remove()
        // Mobilism's spoiler controls are browser-only. Retain their content as native sections.
        document.select(".quotecontent").forEach { content ->
            val box = content.parent() ?: return@forEach
            val header = box.children().firstOrNull() ?: return@forEach
            if (header == content || !header.text().startsWith("Expand:", ignoreCase = true)) return@forEach
            var previous: Node? = box.previousSibling()
            while (previous is TextNode && previous.isBlank || previous is Element && previous.normalName() == "br") {
                previous = previous?.previousSibling()
            }
            val title = (previous as? Element)?.takeIf { it.normalName() in setOf("b", "strong", "span", "p") &&
                it.text().length in 1..100 && it.selectFirst("img, .quotecontent") == null }
            val label = title?.text()?.trim()?.trimEnd(':') ?: "More instructions"
            title?.remove()
            box.tagName("details").clearAttributes()
            header.remove()
            box.prependElement("summary").text(label)
        }
        return blocks(document.body())
    }

    private fun blocks(root: Element): List<HelpBlock> {
        val result = mutableListOf<HelpBlock>()
        var pending = Element("div")
        val wrappers = mutableMapOf<Element, Element>()
        fun flush() {
            if (pending.text().isNotBlank()) result += HelpBlock.Text(pending.html())
            pending = Element("div")
            wrappers.clear()
        }
        fun append(node: Node) {
            var parent = pending
            generateSequence(node.parent()) { it.parent() }.takeWhile { it != root }
                .filterIsInstance<Element>().toList().asReversed().forEach { ancestor ->
                    val container = parent
                    parent = wrappers.getOrPut(ancestor) { ancestor.shallowClone().also { container.appendChild(it) } }
                }
            parent.appendChild(node.clone())
        }
        fun visit(node: Node) {
            when {
                node is Element && node.normalName() == "details" -> {
                    flush()
                    val copy = node.clone()
                    val title = copy.selectFirst("summary")?.text().orEmpty().ifBlank { "More instructions" }
                    copy.selectFirst("summary")?.remove()
                    result += HelpBlock.Section(title, blocks(copy))
                }
                node is Element && node.normalName() == "img" -> {
                    webUrlOrNull(node.absUrl("src"))?.let { url -> flush(); result += HelpBlock.Image(url) }
                }
                node is Element && node.childNodeSize() > 0 -> node.childNodes().forEach(::visit)
                else -> append(node)
            }
        }
        root.childNodes().forEach(::visit)
        flush()
        return result
    }
}
