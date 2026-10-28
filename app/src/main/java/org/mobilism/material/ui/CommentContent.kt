package org.mobilism.material.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.mobilism.material.data.ReleaseComment

internal data class CommentContent(val html: String, val quotes: List<QuotedComment> = emptyList()) {
    companion object {
        fun parse(comment: ReleaseComment): CommentContent {
            val content = parse(comment.contentHtml)
            val quotes = comment.quotes.map { QuotedComment(it.author, parse(it.html)) } + content.quotes
            return content.copy(quotes = quotes.groupBy { it.author to it.content }.values.map { matches ->
                matches.firstOrNull { it.postId != null } ?: matches.first()
            })
        }

        fun parse(html: String): CommentContent = extract(Jsoup.parseBodyFragment(html).body(), 0)

        private fun extract(body: Element, depth: Int): CommentContent {
            // Bound malformed nesting while keeping all text available to the reader.
            if (depth >= 32) return CommentContent(Element("p").text(body.text()).outerHtml())
            val blocks = body.select("blockquote").filter { block ->
                block != body && block.parents().takeWhile { it != body }.none { it.normalName() == "blockquote" }
            }
            val quotes = blocks.map { block ->
                val attribution = block.select("cite, .quotetitle, b, strong").firstOrNull { candidate ->
                    candidate.parents().firstOrNull { it.normalName() == "blockquote" } == block &&
                        (candidate.normalName() == "cite" || candidate.hasClass("quotetitle") || wrote.containsMatchIn(candidate.text()))
                }
                val attributionText = attribution?.text().orEmpty()
                val author = (wrote.find(attributionText)?.groupValues?.get(1) ?: attributionText).trim()
                val postId = block.attr("data-post-id").toLongOrNull()?.takeIf { it > 0 }
                    ?: attribution?.select("a[href]")?.firstNotNullOfOrNull { link ->
                        postLink.find(link.attr("href"))?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 }
                    }
                attribution?.remove()
                val content = extract(block, depth + 1)
                block.remove()
                QuotedComment(author, content, postId)
            }
            return CommentContent(body.html().trim(), quotes)
        }

        private val wrote = Regex("^(.+?)\\s+wrote\\s*:", RegexOption.IGNORE_CASE)
        private val postLink = Regex("(?:[?&]p=|#p)([0-9]+)")
    }
}

internal data class QuotedComment(val author: String, val content: CommentContent, val postId: Long? = null)
