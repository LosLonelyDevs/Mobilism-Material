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

    @Test fun recentPublicObservationsRideExistingPostsAndClearOnlyAfterAcknowledgment() = runBlocking {
        var enabled = true
        var now = 100L
        var accept = false
        val requests = mutableListOf<JSONObject>()
        val client = LatestAppsClient("https://fixture.invalid/api/ping", AppHttp.builder(OkHttpClient()) { identity }.addInterceptor { chain ->
            val builder = Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            chain.request().body?.let { body -> requests += JSONObject(Buffer().also { body.writeTo(it) }.readUtf8()) }
            if (accept) builder.header("X-App-Observations-Accepted", "1")
            builder.body(JSONObject().put("observedAt", now).put("items", org.json.JSONArray()).toString().toResponseBody()).build()
        }.build(), now = { now }, contributionsEnabled = { enabled })
        val release = Release(7, "Camera v2", "Uploader", "https://external.invalid/poster", 80, 0, 4, section = ContentSection.Apps, categoryId = 7,
            contentHtml = "private body", downloadLinks = listOf("https://private.invalid/file"),
            images = listOf("invalid", "http://images.mobilism.org/?dm=sample1"))
        fun observe() = client.observe(listOf(release), true, mapOf(7L to "Photography"))
        suspend fun lookup() { client.lookup(listOf("Camera"), true) { "day" } }
        observe()
        client.snapshot(false)
        assertTrue(requests.isEmpty())
        lookup()
        val payload = requests.last().getJSONObject("observations")
        assertEquals("Photography", payload.getJSONArray("items").getJSONObject(0).getJSONObject("category").getString("name"))
        assertEquals("sample1", payload.getJSONArray("items").getJSONObject(0).getString("artworkId"))
        assertEquals(4, payload.getJSONArray("items").getJSONObject(0).getInt("topicViews"))
        assertFalse(payload.toString().contains("private"))
        accept = true
        client.publish(LatestAppsPage(listOf(release), true, true, now))
        assertTrue(requests.last().has("observations"))
        lookup()
        assertFalse(requests.last().has("observations"))
        observe()
        now += 21_601
        lookup()
        assertFalse(requests.last().has("observations"))
        observe()
        enabled = false
        lookup()
        assertFalse(requests.last().has("observations"))
    }

    @Test fun installedCatalogMatchesPrivatelyByPackageOrUniqueExactLabel() = runBlocking {
        val rows = org.json.JSONArray()
        fun row(id: Int, name: String, pkg: String?, safe: Boolean = true) {
            rows.put(JSONObject().put("id", id).put("name", name).put("version", "2").put("packageName", pkg).put("adultFiltered", safe))
        }
        row(1, "Camera Deluxe", "org.camera")
        row(2, "Listen", null)
        row(3, "Hidden", null, false)
        val reply = JSONObject().put("observedAt", 100).put("items", rows)
        var requests = 0
        val client = LatestAppsClient("https://fixture.invalid/api/ping", AppHttp.builder(OkHttpClient()) { identity }
            .addInterceptor { chain ->
                requests++
                assertEquals("GET", chain.request().method)
                assertEquals("view=installed&safe=1", chain.request().url.query)
                assertNull(chain.request().body)
                assertNull(chain.request().header("X-Following-Check"))
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                    .body(reply.toString().toResponseBody()).build()
            }.build(), now = { 110 })
        assertNull(client.installedCatalog(true).match("org.camera", "Camera", true))
        reply.put("collection", "installed")
        val catalog = client.installedCatalog(true)
        assertEquals(1L, catalog.match("org.camera", "Renamed", false)?.id)
        assertNull(catalog.match("com.different", "Camera Deluxe", true))
        assertEquals(2L, catalog.match("org.listen", "LISTEN", true)?.id)
        assertNull(catalog.match("org.listen", "Listen", false))
        assertNull(catalog.match("org.hidden", "Hidden", true))
        assertEquals(2, requests)
    }

    @Test fun pullingRefreshesDiscoverWhileOrdinaryVisitsReuseTheDiskCache() = runBlocking {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        val reads = java.util.concurrent.atomic.AtomicInteger()
        server.createContext("/api/latest-apps") { exchange ->
            val count = reads.incrementAndGet()
            val items = org.json.JSONArray((1..count).map { id ->
                JSONObject().put("id", id).put("title", "App $id v1").put("name", "App $id").put("version", "1")
                    .put("postedAt", 100).put("author", "Member").put("adultFiltered", true)
            })
            val body = JSONObject().put("collection", "discover").put("observedAt", 200).put("items", items).toString().toByteArray()
            exchange.responseHeaders.add("Cache-Control", "public, max-age=300")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val directory = java.nio.file.Files.createTempDirectory("discover-cache").toFile()
        val cache = okhttp3.Cache(directory, 1_000_000)
        try {
            val transport = AppHttp.builder(OkHttpClient()) { identity }.proxy(java.net.Proxy.NO_PROXY).addInterceptor { chain ->
                val url = chain.request().url.newBuilder().scheme("http").host("127.0.0.1").port(server.address.port).build()
                chain.proceed(chain.request().newBuilder().url(url).build())
            }.build()
            val client = LatestAppsClient("https://fixture.invalid/api/ping", transport, now = { 200 }, browseCache = cache)
            assertEquals(1, client.discover(true).size)
            assertEquals(1, client.discover(true).size)
            assertEquals(2, client.discover(true, refresh = true).size)
            assertEquals(2, client.discover(true).size)
            assertEquals(2, reads.get())
        } finally {
            server.stop(0)
            cache.delete()
            directory.delete()
        }
    }

    @Test fun discoveryReadsOnlyASharedRankingAndHonorsTheAdultFilterOnDevice() = runBlocking {
        val requests = mutableListOf<okhttp3.Request>()
        val response = JSONObject().put("observedAt", 200).put("items", org.json.JSONArray((1..2).map { id ->
            JSONObject().put("id", id).put("title", "App $id v1").put("name", "App $id").put("version", "1")
                .put("postedAt", 100).put("author", "Member").put("adultFiltered", id == 1)
                .put("artworkId", if (id == 1) "9WX" else "https://other.invalid/")
        }))
        val client = LatestAppsClient("https://fixture.invalid/api/ping", AppHttp.builder(OkHttpClient()) { identity }
            .addInterceptor { chain ->
                requests += chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                    .body(response.toString().toResponseBody()).build()
            }.build(), now = { 200 })
        assertTrue(client.discover(false).isEmpty())
        response.put("collection", "discover")
        assertEquals(listOf(1L), client.discover(true).map { it.id })
        val unfiltered = client.discover(false)
        assertEquals(listOf(1L, 2L), unfiltered.map { it.id })
        assertEquals(listOf("https://images.mobilism.org/?dm=9WX", null), unfiltered.map { it.thumbnail })
        assertEquals(listOf("0", "1", "0"), requests.map { it.url.queryParameter("safe") })
        assertTrue(requests.all { it.method == "GET" && it.body == null && it.url.queryParameter("view") == "discover" &&
            it.url.queryParameterNames == setOf("view", "safe") && it.header("X-Following-Check") == null })
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
            section = ContentSection.Apps, contentHtml = "private body", downloadLinks = listOf("private link"), categoryId = 20)
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
        client.publish(LatestAppsPage(listOf(page), firstPage = true, adultFiltered = false), mapOf(20L to "Photography"))
        assertTrue(uploaded.getBoolean("firstPage"))
        val row = uploaded.getJSONArray("items").getJSONObject(0)
        assertEquals(setOf("id", "name", "title", "postedAt", "version", "author", "category"), row.keys().asSequence().toSet())
        assertEquals(20L, row.getJSONObject("category").getLong("id"))
        assertEquals("Fixture", row.getString("name"))
        assertEquals("2.1", row.getString("version"))
        val restored = client.snapshot(hideAdult = false).releases.single()
        assertEquals(page.title, restored.title)
        assertEquals(page.categoryId, restored.categoryId)
        assertEquals("Photography", restored.categoryName)
        assertTrue(client.snapshot(hideAdult = true).releases.isEmpty())
        now = posted + 24 * 3600
        assertEquals(CacheOutcome.Stale, client.snapshot(hideAdult = false).outcome)
    }
}
