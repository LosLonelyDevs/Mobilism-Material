package org.mobilism.material.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class LatestAppsPage(val releases: List<Release>, val firstPage: Boolean, val adultFiltered: Boolean)
data class LatestAppsSnapshot(val outcome: CacheOutcome, val releases: List<Release> = emptyList(), val httpStatus: Int? = null)

class LatestAppsClient(usageEndpoint: String, private val client: OkHttpClient = OkHttpClient.Builder()
    .callTimeout(4, TimeUnit.SECONDS).followRedirects(false).retryOnConnectionFailure(false).build(),
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
    private val diagnostics: DiagnosticLogger = DiagnosticLogger.None) {
    private val endpoint = serviceEndpoint(usageEndpoint, "/api/latest-apps")
    val configured get() = endpoint != null

    suspend fun publish(page: LatestAppsPage) = withContext(Dispatchers.IO) {
        val url = endpoint ?: return@withContext
        val items = JSONArray()
        for (release in page.releases.take(20)) {
            val title = ReleaseTitles.parse(release.title, ContentSection.Apps)
            val version = title.version ?: continue
            if (release.section != ContentSection.Apps || release.id <= 0 || release.publishedAt <= 0 ||
                title.name.length !in 1..200 || version.length !in 1..80 || release.title.length !in 1..512 ||
                release.author.isBlank() || release.author.length > 100) continue
            items.put(JSONObject().put("id", release.id).put("name", title.name).put("version", version)
                .put("title", release.title.trim()).put("postedAt", release.publishedAt).put("author", release.author.trim()))
        }
        if (items.length() == 0) {
            diagnostics.recordCache(CacheDiagnostic(AppCache.Following, CacheOutcome.Empty, items = 0),
                DiagnosticOperation.CacheContribution)
            return@withContext
        }
        val body = JSONObject().put("firstPage", page.firstPage).put("adultFiltered", page.adultFiltered).put("items", items).toString()
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

    suspend fun snapshot(hideAdult: Boolean): LatestAppsSnapshot = withContext(Dispatchers.IO) {
        val url = endpoint ?: return@withContext LatestAppsSnapshot(CacheOutcome.Unconfigured)
        client.newCall(Request.Builder().url(url).build()).await().use { response ->
            if (!response.isSuccessful) return@use LatestAppsSnapshot(CacheOutcome.Failed, httpStatus = response.code)
            val source = response.body?.source() ?: throw IOException("Missing snapshot")
            source.request(524_289)
            if (source.buffer.size > 524_288) throw IOException("Oversized snapshot")
            val snapshot = JSONObject(source.readUtf8())
            if (now() - snapshot.getLong("observedAt") !in -300L..21_600L)
                return@use LatestAppsSnapshot(CacheOutcome.Stale, httpStatus = response.code)
            val items = snapshot.getJSONArray("items")
            require(items.length() <= 200)
            val valid = buildList {
                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    if (hideAdult && !item.optBoolean("adultFiltered")) continue
                    val release = Release(item.getLong("id"), item.getString("title"), item.getString("author"),
                        null, item.getLong("postedAt"), 0, 0, section = ContentSection.Apps)
                    val title = ReleaseTitles.parse(release.title, ContentSection.Apps)
                    if (release.id > 0 && release.publishedAt in 1..now() + 300 && title.version != null &&
                        title.version == item.getString("version") && title.name == item.getString("name")) add(release)
                }
            }
            LatestAppsSnapshot(CacheOutcome.Available, valid, response.code)
        }
    }
}
