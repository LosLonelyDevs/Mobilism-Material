package org.mobilism.material

import org.junit.Assert.*
import org.junit.Test

class ReleaseLinksTest {
    @Test fun acceptsTopicLinksWithExtraForumParametersAndAnchors() {
        assertEquals(123L, ReleaseLinks.topicId("https://forum.mobilism.org/viewtopic.php?f=429&t=123#p456"))
        assertEquals(123L, ReleaseLinks.topicId("http://forum.mobilism.org/viewtopic.php?t=123&start=20"))
        assertEquals(123L, ReleaseLinks.topicId("https://www.mobilism.org/viewtopic.php?t=%31%32%33"))
    }

    @Test fun rejectsOtherHostsAndInvalidOrAmbiguousTopics() {
        listOf(null, "https://forum.mobilism.org.evil.example/viewtopic.php?t=123",
            "https://forum.mobilism.org@evil.example/viewtopic.php?t=123",
            "https://evil.example@forum.mobilism.org/viewtopic.php?t=123",
            "javascript://forum.mobilism.org/viewtopic.php?t=123",
            "https://forum.mobilism.org/viewforum.php?t=123",
            "https://forum.mobilism.org/viewtopic.php?t=-1",
            "https://forum.mobilism.org/viewtopic.php?t=0",
            "https://forum.mobilism.org/viewtopic.php?t=99999999999999999999",
            "https://forum.mobilism.org/viewtopic.php?t=1&t=2",
            "https://forum.mobilism.org/viewtopic.php?p=123"
        ).forEach { assertNull(it, ReleaseLinks.topicId(it)) }
    }
}
