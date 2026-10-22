package org.mobilism.material.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import org.mobilism.material.data.webUrlOrNull
import org.mobilism.material.data.HostMatcher

data class DownloadLink(val url: String, val section: String? = null)
data class ReleaseContent(val bodyHtml: String, val downloads: List<DownloadLink>)

/** The API URL array can omit mirrors, so the download section is also parsed. */
object ReleaseContentParser {
    private val marker = Regex("""(?i)Download\s+(?:Instructions|Links)\s*:?\s*""")
    private val plainUrl = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private val architecture = Regex("""(?i)\b(arm64-v8a|armeabi-v7a|arm64|x86_64|x86|universal)\b""")

    fun parse(html: String, apiUrls: List<String>, baseUrl: String): ReleaseContent {
        val boundary = marker.find(html)
        val links = linkedMapOf<String, DownloadLink>()
        fun add(raw: String, section: String? = null) {
            val url = webUrlOrNull(raw) ?: return
            if (HostMatcher.matches(url, "mobilism.org")) return
            links.putIfAbsent(url, DownloadLink(url, section))
        }
        var notes: Element? = null
        val body = if (boundary != null) {
            val tail = Jsoup.parseBodyFragment(html.substring(boundary.range.last + 1), baseUrl)
            var section: String? = null
            NodeTraversor.traverse(object : NodeVisitor {
                override fun head(node: Node, depth: Int) {
                    if (node is Element && node.normalName() == "a") {
                        add(node.absUrl("href").ifBlank { node.attr("href") }, section)
                    } else if (node is TextNode && generateSequence(node.parent()) { it.parent() }
                            .none { it.normalName() == "a" }) {
                        val text = node.wholeText
                        architecture.find(text)?.let { section = it.value }
                        plainUrl.findAll(text).forEach { add(it.value.trimEnd('.', ',', ';'), section) }
                    }
                }
                override fun tail(node: Node, depth: Int) = Unit
            }, tail.body())
            notes = tail.body()
            Jsoup.parseBodyFragment(html.substring(0, boundary.range.first), baseUrl)
        } else {
            Jsoup.parseBodyFragment(html, baseUrl)
        }
        apiUrls.forEach { add(it) }

        // Preserve instructions, passwords, and forum help around the download URLs.
        val downloadArea = notes ?: body.body()
        downloadArea.select("a[href]").filter {
                webUrlOrNull(it.absUrl("href").ifBlank { it.attr("href") }) in links
            }.forEach { it.remove() }
        NodeTraversor.traverse(object : NodeVisitor {
                override fun head(node: Node, depth: Int) {
                    if (node is TextNode) node.text(plainUrl.replace(node.wholeText) {
                        if (webUrlOrNull(it.value.trimEnd('.', ',', ';')) in links) "" else it.value
                    }.let { text ->
                        if (notes != null && (architecture.matches(text.trim().trimEnd(':')) ||
                            text.trim().matches(Regex("(?i)mirrors?\\s*:?")))) "" else text
                    })
                }
                override fun tail(node: Node, depth: Int) = Unit
            }, downloadArea)
        notes?.let { body.body().append(it.html()) }
        return ReleaseContent(body.body().html(), links.values.toList())
    }
}
