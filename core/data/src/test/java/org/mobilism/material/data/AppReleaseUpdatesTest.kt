package org.mobilism.material.data

import java.io.IOException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppReleaseUpdatesTest {
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

    private fun payload() = JSONObject("""{
        "tag_name":"v0.1.18","draft":false,"prerelease":false,
        "assets":[{"name":"MobilismMaterial_v0.1.18-release.apk",
          "browser_download_url":"https://github.com/LosLonelyDevs/Mobilism-Material/releases/download/v0.1.18/MobilismMaterial_v0.1.18-release.apk"},
          {"name":"MobilismMaterial_v0.1.18-debug.apk","browser_download_url":"https://example.org/debug.apk"}]
    }""")
}
