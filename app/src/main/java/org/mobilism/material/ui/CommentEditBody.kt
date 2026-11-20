package org.mobilism.material.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode

/** The editor shows the author's reply. The forum still expects the original quote prefix on update. */
internal data class CommentEditBody(val quotes: String, val message: String) {
    companion object {
        fun fromHtml(html: String): String {
            val body = Jsoup.parseBodyFragment(html).body()
            body.select("blockquote, script, style").remove()
            for (element in body.select("b, strong, i, em, u, s, del, pre, code").asReversed()) {
                val tag = when (element.normalName()) {
                    "b", "strong" -> "b"
                    "i", "em" -> "i"
                    "u" -> "u"
                    "pre", "code" -> "code"
                    else -> "s"
                }
                if (tag == "code" && element.parents().any { it.normalName() == "pre" }) continue
                element.before(TextNode("[$tag]"))
                element.after(TextNode("[/$tag]"))
            }
            for (link in body.select("a[href]")) {
                val href = link.attr("href")
                val label = link.text()
                if (href.startsWith("https://") || href.startsWith("http://")) {
                    link.text(if (label == href || label.isBlank() ||
                        (label.startsWith("http") && (label.contains("...") || label.contains('…')))) href
                    else "[url=$href]$label[/url]")
                }
            }
            for (block in body.select("p, div, li, pre")) {
                val previous = block.previousSibling()
                if (previous != null && (previous !is TextNode || !previous.wholeText.endsWith('\n')) &&
                    previous.nodeName() != "br") block.before(TextNode("\n"))
                block.after(TextNode("\n"))
            }
            return body.wholeText().trim()
        }

        private val quoteTag = Regex("""\[(/?)quote(?:[=\s][^\]]*)?\]""", RegexOption.IGNORE_CASE)

        fun fromForum(source: String): CommentEditBody {
            var end = 0
            var depth = 0
            for (tag in quoteTag.findAll(source)) {
                val closing = tag.groupValues[1].isNotEmpty()
                if (depth == 0 && (closing || source.substring(end, tag.range.first).isNotBlank())) break
                depth += if (closing) -1 else 1
                if (depth == 0) end = tag.range.last + 1
            }
            if (end > 0) while (end < source.length && source[end].isWhitespace()) end++
            return CommentEditBody(source.substring(0, end), source.substring(end))
        }
    }
}
