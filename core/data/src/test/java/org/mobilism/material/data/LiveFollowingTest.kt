package org.mobilism.material.data

import java.util.concurrent.TimeUnit
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class LiveFollowingTest {
    @Test fun televizoSearchSelectsNewerSuffixedReleaseAndReplacesStaleSavedVersion() {
        assumeTrue(System.getenv("MOBILISM_LIVE_FOLLOWING") == "true")
        val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
        fun read(path: String, form: FormBody? = null): JSONObject {
            val request = Request.Builder().url("https://app.mobilism.org/api-new/$path")
                .header("Accept", "application/json").apply { form?.let { post(it) } }.build()
            return client.newCall(request).execute().use { response ->
                assertTrue("Forum read returned HTTP ${response.code}", response.isSuccessful)
                JSONObject(checkNotNull(response.body).string())
            }
        }
        val followed = ReleaseJson.parse(read("release/6362786").getJSONObject("response"))
        val packageName = checkNotNull(ReleaseVersions.packageName(followed))
        val installed = InstalledVersion(packageName, "Televizo", "1.9.9.31-g", 19931)
        val stale = followed.copy(id = 4694627, title = "Televizo - IPTV player v1.9.3.21 [Final] [Pro] [Mod]")
        val entry = LibraryEntry(followed, watched = true, linkedPackage = packageName, latest = stale)
        val candidates = mutableListOf<Release>()
        for (page in 1..3) {
            val form = FormBody.Builder().add("search", ReleaseTitles.parse(followed.title).name)
                .add("count", page.toString()).add("limits", "20").add("isrelease", "1")
                .add("isdiscussion", "0").add("isother", "0").add("id", SearchScope.Apps.apiId).build()
            val response = SearchJson.page(read("search", form))
            candidates += response.items
            if (!response.canLoadMore) break
        }
        val result = FollowingVersions.evaluate(entry, installed, candidates)
        assertTrue(result.comparable)
        val latest = checkNotNull(result.latest) { "No comparable Televizo release found" }
        val title = ReleaseTitles.parse(latest.title)
        assertTrue("Expected Televizo 1.9.9.43 or newer, found ${title.version}",
            (ReleaseVersions.compare(title, ReleaseTitle("Televizo", "1.9.9.43")) ?: -1) >= 0)
        assertEquals(latest.id, result.update?.id)
        val saved = entry.copy(latest = AppLibrary.compact(latest), update = result.update?.let(AppLibrary::compact))
        val restored = LibraryJson.decode(LibraryJson.encode(listOf(saved))).single()
        assertEquals(latest.id, restored.latest?.id)
        assertEquals(title.version, ReleaseTitles.parse(checkNotNull(restored.latest).title).version)
        assertEquals(latest.id, FollowingVersions.evaluate(restored, installed, emptyList()).update?.id)
        assertNull(FollowingVersions.evaluate(restored, installed.copy(version = title.version!!), emptyList()).update)
        println("Installed ${installed.version}; latest found ${title.version}; topic ${latest.id}")
    }
}
