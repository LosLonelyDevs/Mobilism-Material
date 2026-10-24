package org.mobilism.material.data

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SearchJsonTest {
    @Test fun readsRootEnvelopeAndSortsByLatestActivity() {
        val page = SearchJson.page(JSONObject("""{"releases":[{"id":1,"subject":"Older","date":500,"topic_last_post_time":600},{"id":2,"subject":"Active","date":100,"topic_last_post_time":900,"images":["https://example.com/image.png"]}],"discussions":[],"others":[]}"""))
        assertEquals(listOf(2L, 1L), page.items.map { it.id })
        assertEquals("https://example.com/image.png", page.items.first().thumbnail)
        assertTrue(page.canLoadMore)
        assertNull(page.nextActivityAtMost)
    }

    @Test fun exposesActivityBoundaryOnlyForOrderedCompleteTimestamps() {
        val ordered = SearchJson.page(JSONObject("""{"releases":[{"id":1,"date":800,"topic_last_post_time":900},{"id":2,"date":100,"topic_last_post_time":600}]}"""))
        assertEquals(600L, ordered.nextActivityAtMost)
        val missing = SearchJson.page(JSONObject("""{"releases":[{"id":1,"date":800}]}"""))
        assertNull(missing.nextActivityAtMost)
        val inconsistent = SearchJson.page(JSONObject("""{"releases":[{"id":1,"date":800,"topic_last_post_time":700}]}"""))
        assertNull(inconsistent.nextActivityAtMost)
    }

    @Test fun handlesEmptyAndNullListsWithoutInventingResults() {
        assertFalse(SearchJson.page(JSONObject("""{"releases":[],"discussions":[],"others":[]}""")).canLoadMore)
        assertTrue(SearchJson.page(JSONObject("""{"releases":null}""")).items.isEmpty())
    }

    @Test(expected = JSONException::class) fun rejectsWrongEnvelope() {
        SearchJson.page(JSONObject("""{"response":[]}"""))
    }
}
