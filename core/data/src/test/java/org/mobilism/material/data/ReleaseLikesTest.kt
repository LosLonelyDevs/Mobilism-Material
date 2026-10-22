package org.mobilism.material.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReleaseLikesTest {
    @Test fun missingStateIsUnknownRatherThanUnliked() {
        assertNull(ReleaseLikes.liked(JSONObject()))
        assertNull(ReleaseLikes.liked(JSONObject("""{"liked":null}""")))
        assertNull(ReleaseLikes.liked(JSONObject("""{"liked":"unexpected"}""")))
        assertEquals(true, ReleaseLikes.liked(JSONObject("""{"liked":true}""")))
        assertEquals(false, ReleaseLikes.liked(JSONObject("""{"liked":false}""")))
        assertEquals(true, ReleaseLikes.liked(JSONObject("""{"liked":1}""")))
        assertEquals(false, ReleaseLikes.liked(JSONObject("""{"liked":"0"}""")))
    }

    @Test fun firstForumPostIsUsedInsteadOfTopicOrReplyIds() {
        val html = """<a href="viewtopic.php?t=6429197">Topic</a><span id="p5">Quote</span>
            <div class="post bg1" id="p12709470">Release</div><div class="post bg2" id="p12709816">Reply</div>"""
        assertEquals(12709470L, ReleaseLikes.postId(html))
        assertNull(ReleaseLikes.postId("<div id='p123'>Not a post</div>"))
        assertNull(ReleaseLikes.postId("<div class='post' id='p0'>Invalid</div>"))
        assertNull(ReleaseLikes.postId("<html>Security challenge</html>"))
    }
}
