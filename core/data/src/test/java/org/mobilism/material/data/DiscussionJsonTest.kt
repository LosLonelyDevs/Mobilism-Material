package org.mobilism.material.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DiscussionJsonTest {
    @Test fun categoriesDecodeNamesAndPreserveLegacyAggregateRoutes() {
        val json = JSONObject("""{"response":{"app|game":[{"id":12,"name":"All discussions"},{"id":13,"name":"Announcements"},{"id":500,"name":"Help &amp; support","topics":42}],"books":[]}}""")
        val apps = DiscussionJson.categories(json, DiscussionArea.Apps)
        assertEquals("apps", apps[0].apiId)
        assertEquals("games", DiscussionJson.categories(json, DiscussionArea.Games)[0].apiId)
        assertEquals(DiscussionKind.Other, apps[1].kind)
        assertEquals("Help & support", apps[2].name)
        assertEquals(42L, apps[2].topics)
        assertNull(apps[0].topics)
        assertTrue(DiscussionJson.categories(json, DiscussionArea.Books).isEmpty())
    }

    @Test fun communityUsesTopLevelGroupsAndRealForumIds() {
        val items = DiscussionJson.categories(JSONObject("""{"announcements":[],"contests":null,"talk":[{"id":55,"name":"General chat"}],"services":[],"feedback":[{"id":66,"name":"Feedback"}]}"""), DiscussionArea.Community)
        assertEquals(listOf("Chitchat", "Suggestions & Feedback"), items.map { it.section })
        assertEquals("55", items[0].apiId)
        assertTrue(items.all { it.kind == DiscussionKind.Other })
    }

    @Test fun sharedAppGameCategoriesSeparateDedicatedForumsAndPreserveMetadata() {
        val json = JSONObject("""{"response":{"app|game":[
            {"id":100,"name":"Reviews"},
            {"id":101,"name":"General Talk"},
            {"id":102,"name":"Software","topics":873},
            {"id":103,"name":"Games","topics":375},
            {"id":104,"name":"Game Codes","topics":27}
        ]}}""")
        val apps = DiscussionJson.categories(json, DiscussionArea.Apps)
        val games = DiscussionJson.categories(json, DiscussionArea.Games)
        assertEquals(listOf(100L, 101L, 102L), apps.map { it.id })
        assertEquals(listOf(100L, 101L, 103L, 104L), games.map { it.id })
        assertEquals(27L, games.last().topics)
        assertEquals("104", games.last().apiId)
        assertTrue(apps.all { it.area == DiscussionArea.Apps })
        assertTrue(games.all { it.area == DiscussionArea.Games })
    }

    @Test fun categoryScopeUsesDecodedWordsAndKeepsSharedAndBookForums() {
        val json = JSONObject("""{"response":{"app|game":[
            {"id":12,"name":"All games"},
            {"id":13,"name":"App announcements"},
            {"id":200,"name":"<b>GAMING</b> help"},
            {"id":201,"name":"Application support"},
            {"id":202,"name":"Apps &amp; Games"},
            {"id":203,"name":"Gameloft news"}
        ],"books":[{"id":300,"name":"Game guides"}]}}""")
        assertEquals(listOf(12L, 13L, 201L, 202L, 203L),
            DiscussionJson.categories(json, DiscussionArea.Apps).map { it.id })
        assertEquals(listOf(12L, 13L, 200L, 202L, 203L),
            DiscussionJson.categories(json, DiscussionArea.Games).map { it.id })
        assertEquals(listOf(300L), DiscussionJson.categories(json, DiscussionArea.Books).map { it.id })
    }

    @Test fun topicPreservesOriginalPostAndStickyStatusWithoutConfusingItWithLocked() {
        val topic = DiscussionJson.page(JSONObject("""{"response":[{"id":80,"subject":"Help &amp; tips","name":"Reader","content":"<p>Original https://example.org</p>","status":2,"forum_id":500,"date":12,"topic_last_post_time":99,"replies":4,"views":9,"browser_url":"javascript:alert(1)","images":["https://example.org/image.png","javascript:alert(1)"]}]}"""), DiscussionKind.Discussion).items.single()
        assertEquals("Help & tips", topic.title)
        assertTrue(topic.pinned)
        assertEquals(99, topic.activityAt)
        assertEquals("https://forum.mobilism.org/viewtopic.php?t=80", topic.browserUrl)
        assertEquals(listOf("https://example.org/image.png"), topic.images)
        assertEquals(topic.contentHtml, topic.originalPost.contentHtml)
    }

    @Test fun searchSeparatesDiscussionsFromOtherResultsAndDeduplicates() {
        val json = JSONObject("""{"discussions":[{"id":1,"subject":"Discussion"},{"id":1}],"others":[{"id":2,"title":"Community"}]}""")
        assertEquals(listOf(1L), DiscussionJson.page(json, DiscussionKind.Discussion, true).items.map { it.id })
        val other = DiscussionJson.page(json, DiscussionKind.Other, true).items.single()
        assertEquals("Community", other.title)
        assertEquals(DiscussionKind.Other, other.kind)
    }

    @Test fun missingOrMalformedDataCannotLookLikeAnEmptyForum() {
        assertTrue(runCatching { DiscussionJson.page(JSONObject("{}"), DiscussionKind.Discussion) }.isFailure)
        assertTrue(runCatching { DiscussionJson.categories(JSONObject("{}"), DiscussionArea.Community) }.isFailure)
        assertTrue(runCatching { DiscussionJson.page(JSONObject("""{"response":[{"id":0}]}"""), DiscussionKind.Other) }.isFailure)
        assertFalse(DiscussionJson.page(JSONObject("""{"response":[]}"""), DiscussionKind.Other).canLoadMore)
    }
}
