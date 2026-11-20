package org.mobilism.material.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LatestAppsClientTest {
    @Test fun sharesOnlyPublicFeedFieldsAndReadsBackTheSameRelease() = runBlocking {
        val posted = 1_700_000_000L
        var now = posted + 10
        val page = Release(72, "Fixture v2.1 build 9 [Premium]", "Uploader", null, posted, 0, 0,
            section = ContentSection.Apps, contentHtml = "private body", downloadLinks = listOf("private link"))
        var uploaded = JSONObject()
        val client = LatestAppsClient("https://fixture.invalid/api/ping", OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("https://fixture.invalid/api/latest-apps", request.url.toString())
            assertNull(request.header("Authorization"))
            val reply = if (request.method == "POST") {
                val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
                assertFalse(body.contains("private"))
                uploaded = JSONObject(body)
                "{}"
            } else JSONObject().put("observedAt", posted).put("items", uploaded.getJSONArray("items")).toString()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body(reply.toResponseBody()).build()
        }.build(), now = { now })
        client.publish(LatestAppsPage(listOf(page), firstPage = true, adultFiltered = false))
        assertTrue(uploaded.getBoolean("firstPage"))
        val row = uploaded.getJSONArray("items").getJSONObject(0)
        assertEquals(setOf("id", "name", "title", "postedAt", "version", "author"), row.keys().asSequence().toSet())
        assertEquals("Fixture", row.getString("name"))
        assertEquals("2.1", row.getString("version"))
        assertEquals(page.title, client.snapshot(hideAdult = false).releases.single().title)
        assertTrue(client.snapshot(hideAdult = true).releases.isEmpty())
        now = posted + 24 * 3600
        assertEquals(CacheOutcome.Stale, client.snapshot(hideAdult = false).outcome)
    }
}
