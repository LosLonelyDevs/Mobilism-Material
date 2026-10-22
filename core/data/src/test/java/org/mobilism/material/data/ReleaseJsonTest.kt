package org.mobilism.material.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReleaseJsonTest {
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
