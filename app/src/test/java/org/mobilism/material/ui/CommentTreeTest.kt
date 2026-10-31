package org.mobilism.material.ui

import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.CommentQuote
import org.mobilism.material.data.ReleaseComment

class CommentTreeTest {
    private fun comment(id: Long, text: String = "Comment $id", author: String = "User$id",
        parent: ReleaseComment? = null) = ReleaseComment(id, author, null, id, text,
        quotes = parent?.let { listOf(CommentQuote(it.author, it.contentHtml)) }.orEmpty())

    @Test fun interleavedRepliesStayUnderParentsWhileRootsFollowNewestActivity() {
        val parent = comment(1)
        val reply = comment(2, parent = parent)
        val unrelated = comment(3)
        val nested = comment(4, parent = reply)
        val rows = CommentTree.flatten(CommentTree.build(listOf(nested, unrelated, reply, parent)), emptySet())
        assertEquals(listOf(1L, 2L, 4L, 3L), rows.map { it.comment.id })
        assertEquals(listOf(0, 1, 2, 0), rows.map { it.depth })
    }

    @Test fun collapsingACommentHidesItsWholeSubtree() {
        val parent = comment(1)
        val reply = comment(2, parent = parent)
        val nested = comment(4, parent = reply)
        val tree = CommentTree.build(listOf(nested, comment(3), reply, parent))
        assertEquals(listOf(1L, 3L), CommentTree.flatten(tree, setOf(1)).map { it.comment.id })
        assertEquals(listOf(1L, 2L, 3L), CommentTree.flatten(tree, setOf(2)).map { it.comment.id })
        assertEquals(listOf(1L, 2L, 4L, 3L), CommentTree.flatten(tree, emptySet()).map { it.comment.id })
    }

    @Test fun authorAndNormalizedQuoteTextIdentifyParentsWithoutMatchingAuthorAlone() {
        val parent = comment(1, "<p>Hello &amp; goodbye</p>", "Reader")
        val reply = comment(2).copy(quotes = listOf(CommentQuote("reader", "<div> Hello &amp; goodbye </div>")))
        val unrelated = comment(3).copy(quotes = listOf(CommentQuote("Reader", "A different message")))
        val rows = CommentTree.flatten(CommentTree.build(listOf(unrelated, reply, parent)), emptySet())
        assertTrue(rows.first().placeholder)
        assertEquals("A different message", rows.first().content.html)
        assertEquals(listOf(3L, 1L, 2L), rows.filterNot { it.placeholder }.map { it.comment.id })
        assertEquals(listOf(0, 1, 0, 1), rows.map { it.depth })
    }

    @Test fun quotedPostIdWinsOverRepeatedTextAndKeepsNestedQuoteAuthors() {
        val original = comment(1, "Same message", "Reader")
        val repeated = comment(3, "Same message", "Reader")
        val reply = comment(4, """<blockquote><cite><a href="viewtopic.php?p=1#p1">Reader wrote: ↑</a></cite>Same message</blockquote>Answer""")
        val content = CommentContent.parse(reply)
        assertEquals("Reader", content.quotes.single().author)
        assertEquals(1L, content.quotes.single().postId)
        val roots = CommentTree.build(listOf(reply, repeated, original))
        assertEquals(listOf(1L, 3L), roots.map { it.comment.id })
        assertEquals(4L, roots.first().replies.single().comment.id)
        assertTrue(roots.first().replies.single().content.quotes.isEmpty())
    }

    @Test fun missingParentsRemainVisibleUntilTheirPageArrives() {
        val parent = comment(10, "Parent", "Reader").copy(publishedAt = 1)
        val duplicate = comment(5, "Parent", "Reader")
        val reply = comment(20, """<blockquote><cite><a href="#p10">Reader wrote:</a></cite>Parent</blockquote>Answer""")
        val before = CommentTree.flatten(CommentTree.build(listOf(reply, duplicate)), emptySet())
        assertEquals(listOf(10L, 20L, 5L), before.map { it.comment.id })
        assertTrue(before.first().placeholder)
        assertEquals(1, before.single { it.comment.id == 20L }.depth)
        val roots = CommentTree.build(listOf(reply, duplicate, parent))
        assertEquals(listOf(10L, 5L), roots.map { it.comment.id })
        assertEquals(20L, roots.first().replies.single().comment.id)
        assertFalse(roots.first().placeholder)
    }

    @Test fun malformedCyclesAndDuplicateIdsNeverLoseOrRepeatComments() {
        val one = comment(1, """<blockquote><cite><a href="#p2">User2 wrote:</a></cite>Two</blockquote>One""")
        val two = comment(2, """<blockquote><cite><a href="#p1">User1 wrote:</a></cite>One</blockquote>Two""")
        val rows = CommentTree.flatten(CommentTree.build(listOf(two, one, one)), emptySet())
        assertEquals(setOf(1L, 2L), rows.map { it.comment.id }.toSet())
        assertEquals(2, rows.size)
    }

    @Test fun excessiveThreadDepthRemainsBoundedAndEveryCommentStaysAvailable() {
        val comments = mutableListOf(comment(1))
        for (id in 2L..160L) comments += comment(id, parent = comments.last())
        val rows = CommentTree.flatten(CommentTree.build(comments.reversed()), emptySet())
        assertEquals(160, rows.size)
        assertEquals(160, rows.map { it.comment.id }.distinct().size)
        assertTrue(rows.all { it.depth <= 128 })
        assertEquals(1, rows.count { it.depth == 0 })
        assertEquals(listOf(1L), CommentTree.flatten(CommentTree.build(comments), setOf(1)).map { it.comment.id })
    }

    @Test fun siblingsReadOldestFirstAndEachReplyAppearsOnlyOnce() {
        val parent = comment(1)
        val older = comment(2, parent = parent)
        val newer = comment(4, parent = parent)
        val nested = comment(5, parent = older)
        val rows = CommentTree.flatten(CommentTree.build(listOf(newer, nested, older, parent, newer)), emptySet())
        assertEquals(listOf(1L, 2L, 5L, 4L), rows.map { it.comment.id })
        assertEquals(listOf(0, 1, 2, 1), rows.map { it.depth })
        assertTrue(rows.all { it.content.quotes.isEmpty() })
    }

    @Test fun repliesShareOneMissingParentAndMoveUnderItsLoadedPost() {
        val parent = comment(1, "Earlier message", "Reader")
        val first = comment(2, parent = parent)
        val second = comment(3, parent = parent)
        val pending = CommentTree.build(listOf(second, first)).single()
        assertTrue(pending.placeholder)
        assertEquals(listOf(2L, 3L), pending.replies.map { it.comment.id })
        val loaded = CommentTree.build(listOf(second, first, parent)).single()
        assertFalse(loaded.placeholder)
        assertEquals(1L, loaded.comment.id)
        assertEquals(listOf(2L, 3L), loaded.replies.map { it.comment.id })
    }

    @Test fun explicitParentIdSurvivesPostingEvenWhenQuoteTextRepeats() {
        val parent = comment(1, "Thanks", "Reader")
        val repeated = comment(2, "Thanks", "Reader")
        val reply = comment(3).copy(quotes = listOf(CommentQuote(parent.author, parent.contentHtml, parent.id)))
        val roots = CommentTree.build(listOf(reply, repeated, parent))
        assertEquals(1L, roots.first().comment.id)
        assertEquals(3L, roots.first().replies.single().comment.id)
    }

    @Test fun quotedAncestorsRemainAChainWhenAllEarlierPagesAreMissing() {
        val reply = comment(3, """<blockquote><cite><a href="#p2">User2 wrote:</a></cite>
            <blockquote><cite><a href="#p1">User1 wrote:</a></cite>First</blockquote>Second</blockquote>Third""")
        val rows = CommentTree.flatten(CommentTree.build(listOf(reply)), emptySet())
        assertEquals(listOf(1L, 2L, 3L), rows.map { it.comment.id })
        assertEquals(listOf(0, 1, 2), rows.map { it.depth })
        assertEquals(listOf(true, true, false), rows.map { it.placeholder })
        assertTrue(rows.all { it.content.quotes.isEmpty() })
    }
}
