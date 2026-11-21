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
    private val identity = AppUserAgent.format("Fixture", "1.2.3", 27)
    @Test fun oneNamedRequestDeduplicatesAppsAndUsesUnrelatedDailyReceipts() = runBlocking {
        val requests = mutableListOf<okhttp3.Request>()
        var now = 172800L
        val client = LatestAppsClient("https://fixture.invalid/api/ping", AppHttp.builder(OkHttpClient()) { identity }.addInterceptor { chain ->
            requests.add(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body(JSONObject().put("observedAt", now).put("items", org.json.JSONArray()).toString().toResponseBody()).build()
        }.build(), now = { now })
        val names = (1..30).map { "App $it" } + "APP 1"
        suspend fun receipts(background: Boolean? = null): List<String> {
            assertEquals(CacheOutcome.Available, client.lookup(names, true, background) { "daily secret $it" }.outcome)
            val body = Buffer().also { requests.last().body!!.writeTo(it) }.readUtf8()
            assertFalse(body.contains("daily secret"))
            val apps = JSONObject(body).getJSONArray("apps")
            assertEquals(30, apps.length())
            return (0 until apps.length()).map { apps.getJSONObject(it).getString("receipt") }
        }
        val first = receipts()
        assertNull(requests.last().header("X-Following-Check"))
        assertEquals(30, first.toSet().size)
        assertEquals(first, receipts(true))
        assertEquals("background", requests.last().header("X-Following-Check"))
        now += 86400
        assertTrue(first.intersect(receipts(false).toSet()).isEmpty())
        assertEquals("manual", requests.last().header("X-Following-Check"))
        assertEquals(3, requests.size)
        client.lookup((1..513).map { "App $it" }, true, background = true) { error("Snapshot needs no receipt") }
        assertEquals(4, requests.size)
        assertEquals("GET", requests.last().method)
        requests.forEach { assertEquals(identity, it.header("User-Agent")) }
    }

    @Test fun acceptsFiveHundredAppsAndRejectsAnOversizedList() = runBlocking {
        val items = org.json.JSONArray()
        fun add(id: Int) { items.put(JSONObject().put("id", id).put("name", "App $id").put("title", "App $id v1.0")
            .put("version", "1.0").put("author", "Uploader").put("postedAt", 100)) }
        (1..500).forEach(::add)
        val client = LatestAppsClient("https://fixture.invalid/api/ping", AppHttp.builder(OkHttpClient()) { identity }.addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body(JSONObject().put("observedAt", 101).put("items", items).toString().toResponseBody()).build()
        }.build(), now = { 101 })
        assertEquals(500, client.snapshot(false).releases.size)
        add(501)
        assertTrue(runCatching { client.snapshot(false) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun sharesOnlyPublicFeedFieldsAndReadsBackTheSameRelease() = runBlocking {
        val posted = 1_700_000_000L
        var now = posted + 10
        val page = Release(72, "Fixture v2.1 build 9 [Premium]", "Uploader", null, posted, 0, 0,
            section = ContentSection.Apps, contentHtml = "private body", downloadLinks = listOf("private link"))
        var uploaded = JSONObject()
        val client = LatestAppsClient("https://fixture.invalid/api/ping", AppHttp.builder(OkHttpClient()) { identity }.addInterceptor { chain ->
            val request = chain.request()
            assertEquals(identity, request.header("User-Agent"))
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
