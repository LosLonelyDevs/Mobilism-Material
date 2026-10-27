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

    @Test fun currentForumLayoutFindsOriginalPostWithoutAThumbsUpLink() {
        val html = """<div id="p12">Unrelated anchor</div>
            <div id="p12725286" class="row-fluid blue-bar"><article role="article">
              <div class="well well-small mobil-default"><div class="postbody">
                <a href="viewtopic.php?p=12725286#p12725286">Post</a>
                <div class="content">Release <blockquote><div id="p99">Quote</div></blockquote></div>
              </div></div></article></div>
            <div id="p12726994" class="row-fluid blue-bar"><article><div class="postbody">Reply</div></article></div>"""
        assertEquals(12725286L, ReleaseLikes.postId(html))
        assertNull(ReleaseLikes.postId("<div class='row-fluid blue-bar' id='p123'>Unrelated row</div>"))
    }
}
