// Adapted from LuliReddit's CommentTree.kt, revision 9bd3386bd02d112aa4701ede508189ffd309918a.
// Copyright (c) 2026 Benny Barak. MIT license: core/designsystem/src/main/res/raw/lulireddit_mit.txt.
package org.mobilism.material.ui

import java.util.Locale
import java.util.UUID
import org.jsoup.Jsoup
import org.mobilism.material.data.ReleaseComment

internal data class ThreadComment(
    val comment: ReleaseComment,
    val depth: Int,
    val replies: List<ThreadComment>,
    val content: CommentContent,
    val placeholder: Boolean = false,
    val latestActivity: Long = comment.publishedAt,
)

internal object CommentTree {
    private val chronological = compareBy<ReleaseComment> { it.publishedAt }.thenBy { it.id }

    fun build(comments: List<ReleaseComment>): List<ThreadComment> {
        val ordered = comments.distinctBy { it.id }.sortedWith(chronological)
        val byId = ordered.associateBy { it.id }.toMutableMap()
        val content = ordered.associate { it.id to CommentContent.parse(it) }.toMutableMap()
        val byQuote = ordered.groupBy { key(it.author, content.getValue(it.id).html) }
        val placeholders = mutableSetOf<Long>()
        val parents = mutableMapOf<Long, Long?>()
        val pending = ArrayDeque(ordered.map { it.id })

        fun quotedParent(comment: ReleaseComment, quote: QuotedComment): ReleaseComment? {
            if (quote.postId != null) return byId[quote.postId]?.takeUnless { it.id == comment.id }
            val match = key(quote.author, quote.content.html)
            if (match.first.isBlank() || match.second.isBlank()) return null
            return byQuote[match].orEmpty().filter { candidate ->
                candidate.id != comment.id && (comment.id in placeholders ||
                    if (candidate.publishedAt > 0 && comment.publishedAt > 0) chronological.compare(candidate, comment) < 0
                    else candidate.id < comment.id)
            }.maxWithOrNull(chronological)
        }

        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            val comment = byId.getValue(id)
            val quote = content.getValue(id).quotes.filter { quote ->
                quote.postId != id && (quote.postId != null ||
                    quote.author.isNotBlank() && key(quote.author, quote.content.html).second.isNotBlank())
            }.maxWithOrNull(compareBy<QuotedComment> { it.postId != null }
                .thenBy { quotedParent(comment, it)?.publishedAt ?: 0 }
                .thenBy { quotedParent(comment, it)?.id ?: 0 })
            if (quote == null) {
                parents[id] = null
                continue
            }
            val existing = quotedParent(comment, quote)
            val parentId = existing?.id ?: quote.postId ?: run {
                val (author, text) = key(quote.author, quote.content.html)
                UUID.nameUUIDFromBytes("$author\n$text".toByteArray(Charsets.UTF_8)).mostSignificantBits or Long.MIN_VALUE
            }
            parents[id] = parentId.takeUnless { it == id }
            if (parentId !in byId) {
                // Newest-first pages often arrive before their parents. Keep replies nested meanwhile.
                byId[parentId] = ReleaseComment(parentId, quote.author.ifBlank { "Earlier comment" }, null, 0, quote.content.html)
                content[parentId] = quote.content
                placeholders += parentId
                pending.addLast(parentId)
            }
        }

        val checked = mutableSetOf<Long>()
        for (id in byId.keys) {
            val path = mutableSetOf<Long>()
            var current: Long? = id
            while (current != null && current !in checked) {
                if (!path.add(current)) {
                    parents[current] = null
                    break
                }
                current = parents[current]
            }
            checked += path
        }

        val byParent = byId.values.groupBy { parents[it.id] }.mapValues { (_, values) -> values.sortedWith(chronological) }
        val quoteKeys = byId.mapValues { (id, comment) -> key(comment.author, content.getValue(id).html) }
        val built = mutableMapOf<Long, ThreadComment>()
        val roots = byParent[null].orEmpty()
        val stack = ArrayDeque<Triple<ReleaseComment, Int, Boolean>>()
        roots.forEach { stack.addLast(Triple(it, 0, false)) }
        while (stack.isNotEmpty()) {
            val (comment, depth, visited) = stack.removeLast()
            val children = byParent[comment.id].orEmpty()
            if (!visited) {
                stack.addLast(Triple(comment, depth, true))
                children.forEach { stack.addLast(Triple(it, (depth + 1).coerceAtMost(128), false)) }
            } else {
                val ancestors = mutableSetOf<Long>()
                var parent = parents[comment.id]
                while (parent != null && ancestors.add(parent)) parent = parents[parent]
                val ancestorQuotes = ancestors.map { quoteKeys.getValue(it) }.toSet()
                val body = content.getValue(comment.id)
                val replies = children.map { built.getValue(it.id) }
                built[comment.id] = ThreadComment(comment, depth, replies,
                    content = body.copy(quotes = body.quotes.filterNot { quote ->
                        if (quote.postId != null) quote.postId in ancestors
                        else key(quote.author, quote.content.html) in ancestorQuotes
                    }),
                    placeholder = comment.id in placeholders,
                    latestActivity = maxOf(comment.publishedAt, replies.maxOfOrNull { it.latestActivity } ?: 0),
                )
            }
        }
        return roots.map { built.getValue(it.id) }
            .sortedWith(compareByDescending<ThreadComment> { it.latestActivity }.thenByDescending { it.comment.id })
    }

    fun flatten(nodes: List<ThreadComment>, collapsed: Set<Long>): List<ThreadComment> {
        val out = ArrayList<ThreadComment>(nodes.size * 2)
        val pending = ArrayDeque(nodes.reversed())
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            out.add(node)
            if (node.comment.id !in collapsed) node.replies.asReversed().forEach(pending::addLast)
        }
        return out
    }

    private fun key(author: String, html: String) = author.trim().lowercase(Locale.ROOT) to
        Jsoup.parseBodyFragment(html).text().replace(Regex("[\\s\u00a0]+"), " ").trim()
}
