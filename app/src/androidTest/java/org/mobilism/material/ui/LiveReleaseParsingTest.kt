package org.mobilism.material.ui

import java.util.concurrent.TimeUnit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.ReleaseLinks
import org.mobilism.material.settings.DownloadPreferences

/** Read-only checks against the releases that exposed the parsing errors. */
@RunWith(AndroidJUnit4::class)
class LiveReleaseParsingTest {
    @Test fun verifiesReportedReleaseStructures() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveReleaseParsing") == "true")
        val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
        fun release(id: Long): JSONObject {
            val request = Request.Builder().url("https://app.mobilism.org/api-new/release/$id")
                .header("Accept", "application/json").build()
            return client.newCall(request).execute().use {
                assertTrue("Release read returned HTTP ${it.code}", it.isSuccessful)
                JSONObject(it.body!!.string()).getJSONObject("response")
            }
        }
        fun parse(json: JSONObject): ReleaseContent {
            val urls = json.optJSONArray("url")
            return ReleaseContentParser.parse(json.getString("content"),
                (0 until (urls?.length() ?: 0)).map { urls!!.getString(it) }, "https://forum.mobilism.org/")
        }
        val tv = release(6439280)
        val tvContent = parse(tv)
        val sections = ReleaseSections.parse(tvContent.bodyHtml, tv.getString("subject"))
        val mod = Jsoup.parse(sections.single { it.kind == ReleaseSection.Mod }.html).text()
        assertTrue(mod.contains("Custom Mod Info By"))
        assertTrue(mod.contains("Camera disabled"))
        assertTrue(mod.contains("Signature bypassed"))
        val description = Jsoup.parse(sections.single { it.kind == ReleaseSection.Description }.html)
        assertTrue(description.select("a[href]").any { ReleaseLinks.topicId(it.attr("href")) == 6286022L })
        val tvGroups = DownloadGroups.group(tvContent.downloads, DownloadPreferences())
        assertEquals(2, tvGroups.size)
        assertTrue(tvGroups.all { it.links.size == 2 && it.title.contains("Full Root Non-Root Support Added") })

        val navigation = parse(release(6433144))
        val groups = DownloadGroups.group(navigation.downloads, DownloadPreferences())
        assertEquals(4, groups.size)
        assertTrue(groups.all { it.links.size == 3 })
        assertEquals(2, groups.count { it.title.contains("GmsCore") })
        assertTrue(navigation.downloadNotesHtml.contains("649944"))

        val mobilism = parse(release(6437968))
        val mirrors = DownloadGroups.group(mobilism.downloads, DownloadPreferences()).single()
        assertEquals("Universal", mirrors.title)
        assertEquals(3, mirrors.links.size)
        assertTrue(mirrors.links.any { it.url.startsWith("https://github.com/") })
        assertTrue(mirrors.links.any { it.url.startsWith("https://send.now/") })
        assertTrue(mobilism.downloadNotesHtml.contains("1489943"))
    }
}
