package org.mobilism.material.data

import java.io.IOException
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class LatestAppsPage(val releases: List<Release>, val firstPage: Boolean, val adultFiltered: Boolean,
    val readAt: Long = System.currentTimeMillis() / 1000, val baseline: List<Long>? = null)
data class LatestAppsSnapshot(val outcome: CacheOutcome, val releases: List<Release> = emptyList(), val httpStatus: Int? = null,
    val missingNames: Int? = null)

private val sharedArtwork = Regex("""https?://images\.mobilism\.org/\?dm=([A-Za-z0-9_-]{1,64})""", RegexOption.IGNORE_CASE)
private val artworkId = Regex("[A-Za-z0-9_-]{1,64}")
private val publicPackage = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")
private val appWhitespace = Regex("[\\s\\p{Z}]+")
fun latestAppKey(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFKC)
    .trim().lowercase(Locale.ROOT).replace(appWhitespace, " ")

class LatestAppsClient(usageEndpoint: String, private val client: OkHttpClient = AppHttp.builder()
    .callTimeout(4, TimeUnit.SECONDS).followRedirects(false).retryOnConnectionFailure(false).build(),
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
    private val diagnostics: DiagnosticLogger = DiagnosticLogger.None,
    private val browseCache: okhttp3.Cache? = null, private val contributionsEnabled: () -> Boolean = { true },
    loadObservations: () -> String? = { null }, private val saveObservations: (String?) -> Unit = {}) {
    private val browseClient = browseCache?.let { client.newBuilder().cache(it).build() } ?: client
    private val endpoint = serviceEndpoint(usageEndpoint, "/api/latest-apps")
    val configured get() = endpoint != null

    private class Observation(val id: Long, val item: JSONObject, val readAt: Long, val safe: Boolean)
    private val pending = LinkedHashMap<String, Observation>()
    private val observationLifetime = 30L * 86400
    private var lastBrowseAttempt: Long? = null

    init {
        runCatching {
            val saved = loadObservations()?.let(::JSONObject)
            lastBrowseAttempt = saved?.optLong("attempt")?.takeIf { it > 0 }
            val rows = saved?.optJSONArray("rows") ?: JSONArray()
            for (i in 0 until minOf(rows.length(), 64)) {
                val row = rows.getJSONObject(i)
                val item = row.getJSONObject("item")
                val at = row.getLong("at")
                if (now() - at in 0..observationLifetime) pending[latestAppKey(item.getString("name"))] =
                    Observation(item.getLong("id"), item, at, row.getBoolean("safe"))
            }
        }
    }

    private fun persist() {
        val rows = JSONArray(pending.values.map { JSONObject().put("item", it.item).put("at", it.readAt).put("safe", it.safe) })
        saveObservations(JSONObject().put("rows", rows).put("attempt", lastBrowseAttempt).toString())
    }

    @Synchronized fun clearObservations() {
        pending.clear()
        lastBrowseAttempt = null
        saveObservations(null)
    }

    private fun publicItem(release: Release, categoryNames: Map<Long, String>): JSONObject? {
        if (release.section != ContentSection.Apps || release.id <= 0 || release.publishedAt <= 0 ||
            release.author.isBlank() || release.author.length > 100 || release.title.length !in 1..512 ||
            release.title.trim().any(Char::isISOControl) || release.author.trim().any(Char::isISOControl)) return null
        val title = release.appTitle
        val version = title.version ?: return null
        if (title.name.length !in 1..200 || version.length !in 1..80) return null
        return JSONObject().put("id", release.id).put("name", title.name).put("version", version)
            .put("title", release.title.trim()).put("postedAt", release.publishedAt).put("author", release.author.trim()).apply {
                release.views.takeIf { it > 0 }?.let { put("topicViews", it) }
                val artwork = (sequenceOf(release.thumbnail) + release.images.asSequence()).filterNotNull()
                    .firstNotNullOfOrNull(sharedArtwork::matchEntire)
                artwork?.let { put("artworkId", it.groupValues[1]) }
                (release.storePackage ?: ReleaseVersions.packageName(release))?.takeIf { it.length <= 255 && publicPackage.matches(it) }?.let { put("packageName", it) }
                release.categoryId?.takeIf { it > 0 }?.let { id ->
                    val name = (categoryNames[id] ?: release.categoryName)?.trim()
                        ?.takeIf { it.length in 1..120 && it.none(Char::isISOControl) }
                    put("category", JSONObject().put("id", id).put("name", name))
                }
            }
    }

    @Synchronized
    fun observe(releases: List<Release>, safe: Boolean, categoryNames: Map<Long, String>) {
        if (!configured || !contributionsEnabled()) { if (pending.isNotEmpty()) clearObservations(); return }
        val at = now()
        pending.values.removeAll { at - it.readAt !in 0..observationLifetime }
        for (release in releases.take(20)) {
            if (release.publishedAt > at + 300) continue
            val item = publicItem(release, categoryNames) ?: continue
            val key = latestAppKey(item.getString("name"))
            val old = pending[key]
            if (old != null) {
                val comparison = ReleaseVersions.compare(release.appTitle, ReleaseTitles.parse(old.item.getString("title"), ContentSection.Apps)) ?: 0
                if (comparison < 0 || (comparison == 0 && release.publishedAt < old.item.getLong("postedAt"))) continue
            }
            if (old?.id == release.id) {
                val previous = old.item.optJSONObject("category")
                val category = item.optJSONObject("category")
                if (previous != null && (category == null || (category.optLong("id") == previous.optLong("id") && !category.has("name"))))
                    item.put("category", previous)
                if (!item.has("packageName")) old.item.optString("packageName").takeIf(String::isNotEmpty)?.let { item.put("packageName", it) }
                if (!item.has("artworkId") && old.item.has("artworkId")) item.put("artworkId", old.item.getString("artworkId"))
                maxOf(item.optLong("topicViews"), old.item.optLong("topicViews")).takeIf { it > 0 }?.let { item.put("topicViews", it) }
            }
            pending[key] = Observation(release.id, item, at, safe || (old?.id == release.id && old.safe))
        }
        while (pending.size > 64) pending.remove(pending.keys.first())
        persist()
    }

    @Synchronized
    private fun attachObservations(body: JSONObject): List<Observation> {
        if (!contributionsEnabled() && pending.isNotEmpty()) clearObservations()
        val at = now()
        if (pending.values.removeAll { at - it.readAt !in 0..observationLifetime }) persist()
        val first = pending.values.firstOrNull() ?: return emptyList()
        val batch = pending.values.filter { it.safe == first.safe && kotlin.math.abs(it.readAt - first.readAt) <= 300 }.take(10)
        if (batch.isNotEmpty()) body.put("observations", JSONObject().put("readAt", batch.minOf { it.readAt })
            .put("adultFiltered", batch.all { it.safe }).put("items", JSONArray(batch.map { it.item })))
        return batch
    }

    @Synchronized
    private fun acknowledge(batch: List<Observation>, response: okhttp3.Response) {
        if (response.header("X-App-Observations-Accepted") == "1" && pending.values.removeAll(batch.toSet())) persist()
    }

    fun usagePayload(report: String): Pair<String, (okhttp3.Response) -> Unit> {
        val envelope = JSONObject()
        val batch = attachObservations(envelope)
        if (batch.isEmpty()) return report to {}
        val payload = envelope.put("report", JSONObject(report)).toString()
        return if (payload.toByteArray(Charsets.UTF_8).size <= 262144) payload to { acknowledge(batch, it) }
            else report to {}
    }

    suspend fun publish(page: LatestAppsPage, categoryNames: Map<Long, String> = emptyMap()) = withContext(Dispatchers.IO) {
        val url = endpoint ?: return@withContext
        val items = JSONArray(page.releases.take(20).mapNotNull { publicItem(it, categoryNames) })
        val ids = page.releases.map { it.id }
        val complete = ids.size in 1..20 && ids.all { it > 0 } && ids.distinct().size == ids.size
        if (items.length() == 0 && !complete) {
            diagnostics.recordCache(CacheDiagnostic(AppCache.Following, CacheOutcome.Empty, items = 0),
                DiagnosticOperation.CacheContribution)
            return@withContext
        }
        val body = JSONObject().put("firstPage", page.firstPage).put("adultFiltered", page.adultFiltered)
            .put("readAt", page.readAt).put("items", items).apply {
                if (complete) {
                    put("ids", JSONArray(ids))
                    page.baseline?.let { put("baseline", JSONArray(it)) }
                }
            }
        val batch = attachObservations(body)
        var status: Int? = null
        try {
            client.newCall(Request.Builder().url(url).post(body.toString().toRequestBody("application/json".toMediaType())).build())
                .await().use { response ->
                    acknowledge(batch, response)
                    status = response.code
                    if (!response.isSuccessful) throw IOException("Latest apps unavailable")
                }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) {
            diagnostics.recordCache(CacheDiagnostic(AppCache.Following, CacheOutcome.Failed, items = items.length()),
                DiagnosticOperation.CacheContribution, status)
            throw failure
        }
        diagnostics.recordCache(CacheDiagnostic(AppCache.Following, CacheOutcome.Published, items = items.length()),
            DiagnosticOperation.CacheContribution, status)
    }

    suspend fun lookup(names: List<String>, hideAdult: Boolean, background: Boolean? = null, dailySeed: (String) -> String): LatestAppsSnapshot {
        val url = endpoint ?: return LatestAppsSnapshot(CacheOutcome.Unconfigured)
        val request = Request.Builder().url(url)
        if (background != null) request.header("X-Following-Check", if (background) "background" else "manual")
        val unique = names.distinctBy(::latestAppKey)
        // Very large libraries still fit in one read of the bounded shared snapshot.
        if (unique.size !in 1..512 || unique.any { it.length !in 1..200 || latestAppKey(it).length !in 1..200 ||
                it.any(Char::isISOControl) }) return read(request.build(), hideAdult)
        val day = LocalDate.ofEpochDay(Math.floorDiv(now(), 86400L)).toString()
        val seed = dailySeed(day)
        val apps = JSONArray()
        for (name in unique) {
            val receipt = UUID.nameUUIDFromBytes((seed + ":" + latestAppKey(name)).toByteArray(Charsets.UTF_8))
            apps.put(JSONObject().put("name", name).put("receipt", receipt.toString()))
        }
        val body = JSONObject().put("apps", apps).put("adultFiltered", hideAdult).put("day", day)
        val batch = attachObservations(body)
        request.post(body.toString().toRequestBody("application/json".toMediaType()))
        return read(request.build(), hideAdult, batch = batch)
    }

    suspend fun snapshot(hideAdult: Boolean): LatestAppsSnapshot {
        val url = endpoint ?: return LatestAppsSnapshot(CacheOutcome.Unconfigured)
        return read(Request.Builder().url(url).build(), hideAdult)
    }

    private fun browseRequest(view: String, hideAdult: Boolean) = endpoint?.toHttpUrl()?.newBuilder()
        ?.addQueryParameter("view", view)?.addQueryParameter("safe", if (hideAdult) "1" else "0")
        ?.build()?.let { Request.Builder().url(it).build() }

    private suspend fun browseResponse(request: Request): okhttp3.Response {
        fun due() = pending.isNotEmpty() && (lastBrowseAttempt?.let { now() - it in 0 until 3600L } != true)
        if (synchronized(this) { due() } && contributionsEnabled()) {
            if (browseCache != null && request.cacheControl.maxAgeSeconds != 0) {
                val local = browseClient.newCall(request.newBuilder().cacheControl(CacheControl.Builder().onlyIfCached().build()).build()).await()
                if (local.code != 504) return local
                local.close()
            }
            val body = JSONObject().put("browse", request.url.queryParameter("view") ?: "latest")
                .put("adultFiltered", request.url.queryParameter("safe") == "1")
            val batch = synchronized(this) {
                if (!due()) emptyList() else attachObservations(body).also {
                    if (it.isNotEmpty()) { lastBrowseAttempt = now(); persist() }
                }
            }
            if (batch.isNotEmpty()) {
                val post = Request.Builder().url(requireNotNull(endpoint)).post(body.toString().toRequestBody("application/json".toMediaType())).build()
                return client.newCall(post).await().also { acknowledge(batch, it) }
            }
        }
        return browseClient.newCall(request).await()
    }

    suspend fun discover(hideAdult: Boolean, refresh: Boolean = false): List<Release> {
        val request = browseRequest("discover", hideAdult) ?: return emptyList()
        val ranked = if (refresh) request.newBuilder().cacheControl(CacheControl.Builder().maxAge(0, TimeUnit.SECONDS).build()).build()
            else request
        return read(ranked, hideAdult, discovery = true).releases
    }

    suspend fun installedCatalog(hideAdult: Boolean): InstalledReleaseCatalog = withContext(Dispatchers.IO) {
        val request = browseRequest("installed", hideAdult) ?: return@withContext InstalledReleaseCatalog()
        browseResponse(request).use { response ->
            if (!response.isSuccessful) return@use InstalledReleaseCatalog()
            val source = response.body?.source() ?: return@use InstalledReleaseCatalog()
            val limit = 4L * 1024 * 1024
            source.request(limit + 1)
            require(source.buffer.size <= limit)
            val data = JSONObject(source.readUtf8())
            if (data.optString("collection") != "installed" || now() - data.optLong("observedAt") !in -300L..21_600L)
                return@use InstalledReleaseCatalog()
            val items = data.getJSONArray("items")
            require(items.length() <= 2000)
            InstalledReleaseCatalog(buildList {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    if (hideAdult && !item.optBoolean("adultFiltered")) continue
                    val id = item.optLong("id")
                    val name = item.optString("name")
                    val version = item.optString("version")
                    if (id > 0 && name.length in 1..200 && version.length in 1..80)
                        add(CachedInstalledRelease(id, name, version, item.opt("packageName") as? String))
                }
            })
        }
    }

    private suspend fun read(request: Request, hideAdult: Boolean, discovery: Boolean = false, batch: List<Observation> = emptyList()): LatestAppsSnapshot = withContext(Dispatchers.IO) {
        (if (request.method == "GET") browseResponse(request) else client.newCall(request).await()).use { response ->
            acknowledge(batch, response)
            if (!response.isSuccessful) return@use LatestAppsSnapshot(CacheOutcome.Failed, httpStatus = response.code)
            val source = response.body?.source() ?: throw IOException("Missing snapshot")
            val maxBytes = 2L * 1024 * 1024
            source.request(maxBytes + 1)
            if (source.buffer.size > maxBytes) throw IOException("Oversized snapshot")
            val snapshot = JSONObject(source.readUtf8())
            if (discovery && snapshot.optString("collection") != "discover") return@use LatestAppsSnapshot(CacheOutcome.Empty)
            if (now() - snapshot.getLong("observedAt") !in -300L..21_600L)
                return@use LatestAppsSnapshot(CacheOutcome.Stale, httpStatus = response.code)
            val items = snapshot.getJSONArray("items")
            require(items.length() <= 500)
            val valid = buildList {
                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    if (hideAdult && !item.optBoolean("adultFiltered")) continue
                    val category = item.optJSONObject("category")
                    val thumbnail = (item.opt("artworkId") as? String)?.takeIf(artworkId::matches)
                        ?.let { "https://images.mobilism.org/?dm=$it" }
                    val release = Release(item.getLong("id"), item.getString("title"), item.getString("author"),
                        thumbnail, item.getLong("postedAt"), 0, 0, section = ContentSection.Apps,
                        categoryId = category?.optLong("id")?.takeIf { it > 0 },
                        categoryName = (category?.opt("name") as? String)?.takeIf(String::isNotBlank),
                        storePackage = item.opt("packageName") as? String)
                    val title = release.appTitle
                    if (release.id > 0 && release.publishedAt in 1..now() + 300 && title.version != null &&
                        title.version == item.getString("version") && title.name == item.getString("name")) add(release)
                }
            }
            LatestAppsSnapshot(CacheOutcome.Available, valid, response.code,
                snapshot.optInt("misses", -1).takeIf { it in 0..512 })
        }
    }
}
