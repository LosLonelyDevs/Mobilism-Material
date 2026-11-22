package org.mobilism.material.data

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppReleaseUpdatesTest {
    @Test fun reopenedChangelogReadsEditsForTheInstalledTag() = runBlocking {
        var html = "<ul><li>First <strong>note</strong></li><li>Second &amp; third</li></ul>"
        var tag = "v0.1.18"
        var status = 200
        val api = AppReleaseUpdates(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("https://api.github.com/repos/LosLonelyDevs/Mobilism-Material/releases/tags/v0.1.18", request.url.toString())
            assertEquals("application/vnd.github.html+json", request.header("Accept"))
            assertEquals("no-cache", request.header("Cache-Control"))
            val json = JSONObject().put("tag_name", tag).put("body_html", html)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Fixture")
                .body(json.toString().toResponseBody()).build()
        }.build())
        assertEquals(listOf("First note", "Second & third"), api.notesFor("0.1.18"))
        html = "<p>Edited after publication.</p>"
        assertEquals(listOf("Edited after publication."), api.notesFor("0.1.18"))
        html = ""
        assertTrue(api.notesFor("0.1.18").isEmpty())
        html = "<p>Different release.</p>"
        tag = "v0.1.19"
        assertTrue(api.notesFor("0.1.18").isEmpty())
        tag = "v0.1.18"
        status = 403
        assertTrue(api.notesFor("0.1.18").isEmpty())
    }

    @Test fun comparesStableVersionNumbersWithoutDowngrades() {
        assertTrue(AppVersions.newer("v0.1.100", "0.1.17"))
        assertTrue(AppVersions.newer("1.0.0", "0.99.999"))
        assertFalse(AppVersions.newer("0.1.17", "0.1.17"))
        assertFalse(AppVersions.newer("0.1.16", "0.1.17"))
        listOf("v0.1.18-beta", "latest", "0.1", "0.1.999999999999999").forEach {
            assertFalse(AppVersions.newer(it, "0.1.17"))
        }
    }

    @Test fun selectsTheReleaseApkFromTheOwningRepository() {
        val release = AppReleaseUpdates.parse(payload())
        assertEquals("0.1.18", release.version)
        assertEquals("${AppReleaseUpdates.REPOSITORY_URL}/releases/tag/v0.1.18", release.pageUrl)
        assertEquals("${AppReleaseUpdates.REPOSITORY_URL}/releases/download/v0.1.18/MobilismMaterial_v0.1.18-release.apk", release.apkUrl)
        assertEquals(123L, release.apk?.size)
    }

    @Test fun missingOrForeignApkFallsBackToTheReleasePage() {
        val json = payload()
        json.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://example.org/unrelated.apk")
        assertNull(AppReleaseUpdates.parse(json).apkUrl)
        json.remove("assets")
        assertNull(AppReleaseUpdates.parse(json).apkUrl)
    }

    @Test fun rejectsDraftsPrereleasesAndInvalidVersionTags() {
        listOf(payload().put("draft", true), payload().put("prerelease", true),
            payload().put("tag_name", "v0.1.18-beta")).forEach { json ->
            assertTrue(runCatching { AppReleaseUpdates.parse(json) }.exceptionOrNull() is IOException)
        }
    }

    @Test fun refusesUnboundedDownloadsAndMalformedChecksums() {
        for (size in listOf(0L, -1L, MAX_UPDATE_APK_BYTES + 1)) {
            val json = payload()
            json.getJSONArray("assets").getJSONObject(0).put("size", size)
            assertNull(AppReleaseUpdates.parse(json).apk)
        }
        val json = payload()
        val asset = json.getJSONArray("assets").getJSONObject(0)
        asset.put("digest", "sha256:" + "A".repeat(64))
        assertEquals("a".repeat(64), AppReleaseUpdates.parse(json).apk?.sha256)
        asset.put("digest", "sha256:broken")
        assertTrue(runCatching { AppReleaseUpdates.parse(json) }.exceptionOrNull() is IOException)
    }

    private fun payload() = JSONObject("""{
        "tag_name":"v0.1.18","draft":false,"prerelease":false,
        "assets":[{"name":"MobilismMaterial_v0.1.18-release.apk","size":123,
          "browser_download_url":"https://github.com/LosLonelyDevs/Mobilism-Material/releases/download/v0.1.18/MobilismMaterial_v0.1.18-release.apk"},
          {"name":"MobilismMaterial_v0.1.18-debug.apk","browser_download_url":"https://example.org/debug.apk"}]
    }""")
}
