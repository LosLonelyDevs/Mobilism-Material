// Adapted from LuliReddit's CommentTree.kt, revision 9bd3386bd02d112aa4701ede508189ffd309918a.
// Copyright (c) 2026 Benny Barak. MIT license: core/designsystem/src/main/res/raw/lulireddit_mit.txt.
package org.mobilism.material.ui

import java.util.Locale
import org.jsoup.Jsoup
import org.mobilism.material.data.ReleaseComment

internal data class ThreadComment(val comment: ReleaseComment, val depth: Int, val replies: List<ThreadComment>)

internal object CommentTree {
    fun build(comments: List<ReleaseComment>): List<ThreadComment> {
        val ordered = comments.distinctBy { it.id }.sortedByDescending { it.publishedAt }
        val byId = ordered.associateBy { it.id }
        val content = ordered.associate { it.id to CommentContent.parse(it) }
        fun key(author: String, html: String) = author.trim().lowercase(Locale.ROOT) to
            Jsoup.parseBodyFragment(html).text().replace(Regex("[\\s\u00a0]+"), " ").trim()
        val byQuote = ordered.groupBy { key(it.author, content.getValue(it.id).html) }
        val parents = ordered.associate { comment ->
            val candidates = content.getValue(comment.id).quotes.mapNotNull { quote ->
                if (quote.postId != null) byId[quote.postId]?.takeUnless { it.id == comment.id }
                else {
                    val match = key(quote.author, quote.content.html)
                    if (match.first.isBlank() || match.second.isBlank()) null
                    else byQuote[match].orEmpty().filter { candidate ->
                        candidate.id != comment.id && if (candidate.publishedAt > 0 && comment.publishedAt > 0) {
                            candidate.publishedAt < comment.publishedAt ||
                                candidate.publishedAt == comment.publishedAt && candidate.id < comment.id
                        } else candidate.id < comment.id
                    }.maxWithOrNull(compareBy<ReleaseComment> { it.publishedAt }.thenBy { it.id })
                }
            }
            // Mobilism supplies quoted text; Reddit supplies parent IDs directly.
            comment.id to candidates.maxWithOrNull(compareBy<ReleaseComment> { it.publishedAt }.thenBy { it.id })?.id
        }
        val byParent = ordered.groupBy { parents[it.id] }
        val visited = mutableSetOf<Long>()
        fun attach(comment: ReleaseComment, depth: Int): ThreadComment? {
            if (!visited.add(comment.id)) return null
            val replies = if (depth < 128) byParent[comment.id].orEmpty().mapNotNull { attach(it, depth + 1) } else emptyList()
            return ThreadComment(comment, depth, replies)
        }
        val roots = byParent[null].orEmpty().mapNotNull { attach(it, 0) }.toMutableList()
        // Malformed cycles and excessive depth must not hide comments.
        ordered.forEach { attach(it, 0)?.let(roots::add) }
        return roots
    }

    fun flatten(nodes: List<ThreadComment>, collapsed: Set<Long>): List<ThreadComment> {
        val out = ArrayList<ThreadComment>(nodes.size * 2)
        fun walk(comment: ThreadComment) {
            out.add(comment)
            if (comment.comment.id !in collapsed) comment.replies.forEach(::walk)
        }
        nodes.forEach(::walk)
        return out
    }
}
