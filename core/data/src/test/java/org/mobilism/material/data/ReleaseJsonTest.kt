package org.mobilism.material.data

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class ReleaseJsonTest {
    @Test fun serverCappedPageStillAllowsAnotherPage() {
        val response = JSONArray().apply { repeat(10) { put(JSONObject().put("id", it + 1)) } }
        val page = ReleaseJson.page(response)
        assertEquals(10, page.items.size)
        assertTrue(page.canLoadMore)
    }

    @Test fun emptyPageEndsPagination() {
        val page = ReleaseJson.page(JSONArray())
        assertTrue(page.items.isEmpty())
        assertFalse(page.canLoadMore)
    }

    @Test fun descriptionPreviewIsNotAnImage() {
        val release = ReleaseJson.parse(JSONObject("""
            {"id":42,"subject":"Example","preview":"<b>A description</b>",
             "images":["https://images.example.org/icon.png","https://images.example.org/screen.png"]}
        """.trimIndent()))
        assertEquals("https://images.example.org/icon.png", release.thumbnail)
        assertEquals(2, release.images.size)
    }

    @Test fun unusableImagesDoNotHideTheNextValidImage() {
        val release = ReleaseJson.parse(JSONObject("""
            {"id":42,"images":[null,"javascript:alert(1)","//images.example.org/icon.png"]}
        """.trimIndent()))
        assertEquals("https://images.example.org/icon.png", release.thumbnail)
        assertEquals(listOf(release.thumbnail), release.images)
    }

    @Test fun missingImageUsesPlaceholderEvenIfPreviewLooksLikeAUrl() {
        val release = ReleaseJson.parse(JSONObject("""{"id":42,"preview":"https://example.org/description"}"""))
        assertNull(release.thumbnail)
    }
}
