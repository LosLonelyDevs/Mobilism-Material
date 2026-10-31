package org.mobilism.material.ui

import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.mobilism.material.data.ReleaseComment

internal data class CommentContent(val html: String, val quotes: List<QuotedComment> = emptyList()) {
    companion object {
        fun parse(comment: ReleaseComment): CommentContent {
            val content = parse(comment.contentHtml)
            val quotes = comment.quotes.map { QuotedComment(it.author, parse(it.html), it.postId) } + content.quotes
            return content.copy(quotes = distinctQuotes(quotes))
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
            return CommentContent(body.html().trim(), distinctQuotes(quotes))
        }

        private data class QuoteIdentity(val author: String, val text: String, val links: List<String>,
            val images: List<String>, val nested: List<QuoteIdentity>)

        private fun identity(quote: QuotedComment): QuoteIdentity {
            val body = Jsoup.parseBodyFragment(quote.content.html)
            return QuoteIdentity(quote.author.trim().lowercase(Locale.ROOT),
                body.text().replace(Regex("[\\s\u00a0]+"), " ").trim(),
                body.select("a[href]").map { it.attr("href") }, body.select("img[src]").map { it.attr("src") },
                quote.content.quotes.map(::identity))
        }

        private fun distinctQuotes(quotes: List<QuotedComment>): List<QuotedComment> =
            quotes.groupBy(::identity).values.flatMap { matches ->
                // API metadata can repeat the inline quote with different formatting or no post ID.
                matches.filter { it.postId != null }.distinctBy { it.postId }.ifEmpty { listOf(matches.first()) }
            }

        private val wrote = Regex("^(.+?)\\s+wrote\\s*:", RegexOption.IGNORE_CASE)
        private val postLink = Regex("(?:[?&]p=|#p)([0-9]+)")
    }
}

internal data class QuotedComment(val author: String, val content: CommentContent, val postId: Long? = null)
