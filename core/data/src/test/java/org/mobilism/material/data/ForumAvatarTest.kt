package org.mobilism.material.data

import org.junit.Assert.*
import org.junit.Test

class ForumAvatarTest {
    @Test fun acceptsForumRelativeAndExternalAvatarsButRejectsNonWebSchemes() {
        listOf("/download/file.php?avatar=1.gif", "./download/file.php?avatar=1.gif", "download/file.php?avatar=1.gif").forEach {
            assertEquals("https://forum.mobilism.org/download/file.php?avatar=1.gif", forumAvatarUrl(it))
        }
        assertEquals("https://example.org/avatar.gif", forumAvatarUrl("//example.org/avatar.gif"))
        listOf("", "null", "javascript:alert(1)", "file:///private/avatar").forEach { assertNull(forumAvatarUrl(it)) }
    }
}
