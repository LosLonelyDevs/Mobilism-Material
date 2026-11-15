package org.mobilism.material.ui

import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.AuthState
import org.mobilism.material.data.ReleaseComment

class ForumCommentActionTest {
    private val comment = ReleaseComment(123, "Reader", null, 0, "Comment")
    private val edit = ForumCommentTarget(42, 123, ForumCommentAction.Edit)

    @Test fun onlyTheSignedInAuthorGetsCommentActions() {
        assertTrue(canManageComment(comment, AuthState.SignedIn("reader")))
        assertFalse(canManageComment(comment, AuthState.SignedIn("Another reader")))
        assertFalse(canManageComment(comment, AuthState.Guest))
        assertFalse(canManageComment(comment, AuthState.SignedOut))
        assertFalse(canManageComment(comment, AuthState.Restoring))
        assertFalse(canManageComment(comment.copy(author = ""), AuthState.SignedIn("")))
        assertFalse(canManageComment(comment.copy(id = 0), AuthState.SignedIn("Reader")))
    }

    @Test fun eachActionUsesThePostIdAndAllowsLoginAndReturnToItsTopic() {
        assertEquals("https://forum.mobilism.org/posting.php?mode=edit&p=123", edit.url)
        assertTrue(edit.accepts(edit.url))
        val delete = edit.copy(action = ForumCommentAction.Delete)
        assertEquals("https://forum.mobilism.org/posting.php?mode=delete&p=123", delete.url)
        assertTrue(delete.accepts(delete.url + "&f=9&sid=session"))
        assertTrue(edit.accepts("https://forum.mobilism.org/ucp.php?mode=login"))
        assertTrue(edit.accepts("https://forum.mobilism.org/viewtopic.php?t=42&p=123#p123"))
        assertTrue(edit.accepts("https://forum.mobilism.org/viewtopic.php?p=123#p123"))
        assertTrue(delete.accepts("https://forum.mobilism.org/viewtopic.php?t=42&p=100#p100"))
        assertTrue(edit.accepts("https://forum.mobilism.org/index.php"))
    }

    @Test fun navigationCannotSwitchPostsActionsAccountsOrOrigins() {
        listOf(
            "http://forum.mobilism.org/posting.php?mode=edit&p=123",
            "https://forum.mobilism.org.evil.example/posting.php?mode=edit&p=123",
            "https://user@forum.mobilism.org/posting.php?mode=edit&p=123",
            "https://forum.mobilism.org:444/posting.php?mode=edit&p=123",
            "https://forum.mobilism.org/posting.php?mode=delete&p=123",
            "https://forum.mobilism.org/posting.php?mode=edit&p=999",
            "https://forum.mobilism.org/posting.php?mode=edit&p=123&t=99",
            "https://forum.mobilism.org/posting.php?mode=edit&p=123&p=999",
            "https://forum.mobilism.org/viewtopic.php?t=99&p=123",
            "https://forum.mobilism.org/viewtopic.php?p=999",
            "https://forum.mobilism.org/ucp.php?mode=logout",
            "https://forum.mobilism.org/ucp.php?mode=register",
            "file:///etc/passwd", "javascript:alert(1)", "malformed % URL",
        ).forEach { assertFalse(it, edit.accepts(it)) }
    }
}
