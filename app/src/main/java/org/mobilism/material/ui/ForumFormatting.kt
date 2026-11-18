package org.mobilism.material.ui

import androidx.compose.runtime.staticCompositionLocalOf
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal data class ForumFormattingOptions(val cleanUpReleaseText: Boolean = false, val markdown: Boolean = false)
internal val LocalForumFormatting = staticCompositionLocalOf { ForumFormattingOptions() }

/** Display transforms only. Stored content and outgoing posts retain their original syntax. */
internal object ForumFormatting {
    private val parser = Parser.builder().build()
    private val renderer = HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).softbreak("<br />\n").build()
    private val protectedTags = setOf("pre", "code", "tt", "a", "img", "script", "style")
    private val containers = setOf("body", "div", "section", "article", "p", "blockquote", "li", "td", "th")

    fun format(html: String, baseUrl: String, options: ForumFormattingOptions): String {
        if ((!options.markdown && !options.cleanUpReleaseText) || html.length > 200_000) return html
        val document = Jsoup.parseBodyFragment(html, baseUrl)
        document.outputSettings().prettyPrint(false)
        val pending = ArrayDeque<Pair<Element, Int>>()
        pending.add(document.body() to 0)
        while (pending.isNotEmpty()) {
            val (element, depth) = pending.removeLast()
            if (depth > 32) return html
            element.children().forEach { pending.add(it to depth + 1) }
        }
        if (options.markdown) markdown(document.body(), 0)
        if (options.cleanUpReleaseText) cleanUp(document.body(), baseUrl, 0)
        return document.body().html()
    }

    private fun markdown(container: Element, depth: Int) {
        if (depth >= 32 || container.normalName() in protectedTags) return
        container.children().toList().forEach { markdown(it, depth + 1) }
        val output = Element("div")
        val run = mutableListOf<Node>()
        fun flush() {
            if (run.isEmpty()) return
            val originals = mutableMapOf<String, Node>()
            // Choose tokens absent from the source, so user text cannot restore another node.
            var prefix = "\uE000forum"
            val original = run.joinToString("") { it.outerHtml() }
            while (prefix in original) prefix += "x"
            val source = run.joinToString("") { node ->
                when {
                    node is TextNode -> node.wholeText
                    node is Element && node.normalName() == "br" -> "\n"
                    else -> "${prefix}${originals.size}\uE001".also { originals[it] = node }
                }
            }
            val parsed = parser.parse(source)
            parsed.accept(object : AbstractVisitor() {
                override fun visit(image: Image) {
                    // Markdown images become links. Enabling formatting must not fetch new images.
                    val link = Link(image.destination, image.title)
                    while (image.firstChild != null) link.appendChild(image.firstChild)
                    if (link.firstChild == null) link.appendChild(Text("Image"))
                    image.insertBefore(link)
                    image.unlink()
                }
            })
            val renderedDocument = Jsoup.parseBodyFragment(renderer.render(parsed))
            renderedDocument.outputSettings().prettyPrint(false)
            // phpBB may linkify a URL before returning the user's Markdown destination.
            renderedDocument.select("a[href]").forEach { link ->
                val destination = link.attr("href")
                val originalLink = originals[destination] as? Element
                if (originalLink?.normalName() == "a") {
                    link.attr("href", originalLink.absUrl("href").ifBlank { originalLink.attr("href") })
                } else if (prefix in destination) link.removeAttr("href")
            }
            val token = Regex("${Regex.escape(prefix)}[0-9]+\uE001")
            val rendered = token.replace(renderedDocument.body().html()) { originals[it.value]?.outerHtml() ?: it.value }
            val fragment = Jsoup.parseBodyFragment(rendered).body()
            if (container.normalName() !in setOf("body", "div", "section", "article", "blockquote") &&
                fragment.childrenSize() == 1 && fragment.child(0).normalName() == "p") {
                output.appendChildren(fragment.child(0).childNodes().toList())
            } else output.appendChildren(fragment.childNodes().toList())
            run.clear()
        }
        container.childNodes().toList().forEach { node ->
            if (node is Element && node.isBlock && node.normalName() != "br") {
                flush()
                output.appendChild(node)
            } else run += node
        }
        flush()
        if (container.normalName() !in containers && output.children().any { it.isBlock }) {
            // An inline wrapper may enclose a whole forum post, including authored line breaks.
            container.tagName("div")
        } else if (container.normalName() == "p" && output.children().any { it.isBlock }) container.tagName("div")
        container.empty().appendChildren(output.childNodes().toList())
    }

    private fun cleanUp(container: Element, baseUrl: String, depth: Int) {
        if (depth >= 32 || container.normalName() in protectedTags ||
            container.normalName() in setOf("ul", "ol") || container.normalName().matches(Regex("h[1-6]"))) return
        val output = Element("div")
        val run = mutableListOf<Node>()
        fun flush() {
            if (run.isEmpty()) return
            val html = run.joinToString("") { it.outerHtml() }
            val lines = ReleaseDownloadLines.read(html, baseUrl)
            var list: Element? = null
            var descriptiveItem: Element? = null
            lines.forEachIndexed { index, line ->
                val marker = bullet.find(line.text)
                val adjacentBullet = list != null || lines.getOrNull(index + 1)?.text?.let { bullet.containsMatchIn(it) } == true
                val explicitBullet = marker?.value?.any { it in "•·➤►▪●" } == true
                if (marker != null && (adjacentBullet || explicitBullet)) {
                    val target = list ?: output.appendElement("ul").also { list = it }
                    val item = target.appendElement("li").html(line.html)
                    removePrefix(item, marker.value.length)
                    descriptiveItem = item.takeIf { marker.value.any { it in "➤►" } }
                } else if (descriptiveItem != null && !isHeading(line)) {
                    descriptiveItem?.appendElement("p")?.html(line.html)
                } else {
                    list = null
                    descriptiveItem = null
                    output.appendElement(if (isHeading(line)) "h4" else "p").html(line.html)
                }
            }
            run.clear()
        }
        container.childNodes().toList().forEach { node ->
            if (node is Element && node.isBlock && node.normalName() != "br") {
                flush()
                cleanUp(node, baseUrl, depth + 1)
                output.appendChild(node)
            } else run += node
        }
        flush()
        if (container.normalName() == "p" && output.children().any { it.isBlock }) container.tagName("div")
        container.empty().appendChildren(output.childNodes().toList())
    }

    private val bullet = Regex("""^\s*(?:[-*](?:\s+|(?=\p{L}))|[•·➤►▪●]\s*)(?=\S)""")
    private val metadata = Regex("""(?i)^(?:requirements?|version|build|size|sha[- ]?\d*|md5|https?)\s*:|^[a-f0-9]{24,}$""")
    private val smallWords = setOf("a", "an", "and", "at", "by", "for", "in", "of", "on", "or", "the", "to", "with", "&")

    private fun isHeading(line: DownloadLine): Boolean {
        val text = line.text.trim()
        if (text.length !in 3..100 || metadata.containsMatchIn(text) || text.contains("://") ||
            text.endsWith('.') || text.endsWith('!') || text.contains(Regex(""":\s+\S"""))) return false
        val words = text.split(Regex("""\s+"""))
        if (words.size > 12) return false
        if (line.styled || line.headingLevel != null) return true
        if (text.any(Char::isLetter) && text.filter(Char::isLetter).all(Char::isUpperCase)) return true
        return words.size in 2..7 && words.count { it.firstOrNull()?.isUpperCase() == true } >= 2 &&
            words.all { it in smallWords || it.firstOrNull()?.isUpperCase() == true }
    }

    private fun removePrefix(element: Element, count: Int) {
        var remaining = count
        fun visit(node: Node) {
            if (remaining == 0) return
            if (node is TextNode) {
                val take = minOf(remaining, node.wholeText.length)
                node.text(node.wholeText.drop(take))
                remaining -= take
            } else node.childNodes().forEach(::visit)
        }
        visit(element)
    }
}
