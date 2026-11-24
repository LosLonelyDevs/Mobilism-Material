package org.mobilism.material.data

import java.io.IOException
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

private val appWhitespace = Regex("[\\s\\p{Z}]+")
fun latestAppKey(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFKC)
    .trim().lowercase(Locale.ROOT).replace(appWhitespace, " ")

class LatestAppsClient(usageEndpoint: String, private val client: OkHttpClient = AppHttp.builder()
    .callTimeout(4, TimeUnit.SECONDS).followRedirects(false).retryOnConnectionFailure(false).build(),
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
    private val diagnostics: DiagnosticLogger = DiagnosticLogger.None,
    browseCache: okhttp3.Cache? = null) {
    private val browseClient = browseCache?.let { client.newBuilder().cache(it).build() } ?: client
    private val endpoint = serviceEndpoint(usageEndpoint, "/api/latest-apps")
    val configured get() = endpoint != null

    suspend fun publish(page: LatestAppsPage, categoryNames: Map<Long, String> = emptyMap()) = withContext(Dispatchers.IO) {
        val url = endpoint ?: return@withContext
        val items = JSONArray()
        for (release in page.releases.take(20)) {
            val title = release.appTitle
            val version = title.version ?: continue
            if (release.section != ContentSection.Apps || release.id <= 0 || release.publishedAt <= 0 ||
                title.name.length !in 1..200 || version.length !in 1..80 || release.title.length !in 1..512 ||
                release.author.isBlank() || release.author.length > 100) continue
            items.put(JSONObject().put("id", release.id).put("name", title.name).put("version", version)
                .put("title", release.title.trim()).put("postedAt", release.publishedAt).put("author", release.author.trim()).apply {
                    (release.storePackage ?: ReleaseVersions.packageName(release))?.takeIf { it.length <= 255 }?.let { put("packageName", it) }
                    release.categoryId?.takeIf { it > 0 }?.let { id ->
                        val name = (categoryNames[id] ?: release.categoryName)?.trim()
                            ?.takeIf { it.length in 1..120 && it.none(Char::isISOControl) }
                        put("category", JSONObject().put("id", id).put("name", name))
                    }
                })
        }
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
            }.toString()
        var status: Int? = null
        try {
            client.newCall(Request.Builder().url(url).post(body.toRequestBody("application/json".toMediaType())).build())
                .await().use { response ->
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
        request.post(body.toString().toRequestBody("application/json".toMediaType()))
        return read(request.build(), hideAdult)
    }

    suspend fun snapshot(hideAdult: Boolean): LatestAppsSnapshot {
        val url = endpoint ?: return LatestAppsSnapshot(CacheOutcome.Unconfigured)
        return read(Request.Builder().url(url).build(), hideAdult)
    }

    private fun browseRequest(view: String, hideAdult: Boolean) = endpoint?.toHttpUrl()?.newBuilder()
        ?.addQueryParameter("view", view)?.addQueryParameter("safe", if (hideAdult) "1" else "0")
        ?.build()?.let { Request.Builder().url(it).build() }

    suspend fun discover(hideAdult: Boolean): List<Release> {
        val request = browseRequest("discover", hideAdult) ?: return emptyList()
        return read(request, hideAdult, discovery = true).releases
    }

    suspend fun installedCatalog(hideAdult: Boolean): InstalledReleaseCatalog = withContext(Dispatchers.IO) {
        val request = browseRequest("installed", hideAdult) ?: return@withContext InstalledReleaseCatalog()
        browseClient.newCall(request).await().use { response ->
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

    private suspend fun read(request: Request, hideAdult: Boolean, discovery: Boolean = false): LatestAppsSnapshot = withContext(Dispatchers.IO) {
        (if (discovery) browseClient else client).newCall(request).await().use { response ->
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
                    val release = Release(item.getLong("id"), item.getString("title"), item.getString("author"),
                        null, item.getLong("postedAt"), 0, 0, section = ContentSection.Apps,
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
