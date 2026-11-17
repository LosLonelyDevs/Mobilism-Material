package org.mobilism.material.images

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class AppImageUrlTest {
    @Test fun oldMobilismImagesUseHttpsBeforeAndroidChecksCleartextPolicy() {
        listOf("images.mobilism.org/?dm=2W3WO076", "forum.mobilism.org/download/file.php?avatar=123.png").forEach {
            assertEquals("https://$it", secureMobilismImageUrl("http://$it".toHttpUrl()).toString())
        }
    }

    @Test fun unrelatedHostsAndCustomAuthoritiesAreUnchanged() {
        listOf("https://images.mobilism.org/?dm=UP6E", "http://images.mobilism.org.evil.example/image.png",
            "http://example.org/image.png", "http://images.mobilism.org:8080/image.png",
            "http://user@images.mobilism.org/image.png").forEach {
            assertEquals(it.toHttpUrl(), secureMobilismImageUrl(it.toHttpUrl()))
        }
    }
}
