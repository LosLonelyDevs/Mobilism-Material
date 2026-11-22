package org.mobilism.material.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup

data class AppRelease(
    val version: String,
    val pageUrl: String,
    val apkUrl: String?,
    val apkSize: Long? = null,
    val apkSha256: String? = null,
) {
    val apk: ReleaseApk? get() = apkUrl?.let { url ->
        apkSize?.takeIf { it in 1..MAX_UPDATE_APK_BYTES }?.let { ReleaseApk(url, it, apkSha256) }
    }
}

const val MAX_UPDATE_APK_BYTES = 250L * 1024 * 1024
data class ReleaseApk(val url: String, val size: Long, val sha256: String? = null)

object AppVersions {
    private val stable = Regex("v?([0-9]+)\\.([0-9]+)\\.([0-9]+)")
    fun parts(version: String): List<Int>? {
        return stable.matchEntire(version)?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: return null }
    }

    fun newer(candidate: String, installed: String): Boolean {
        val next = parts(candidate) ?: return false
        val current = parts(installed) ?: return false
        return next.zip(current).firstOrNull { (a, b) -> a != b }?.let { (a, b) -> a > b } ?: false
    }
}

class AppReleaseUpdates(private val client: OkHttpClient = AppHttp.builder()
    .callTimeout(20, TimeUnit.SECONDS).build()) {
    suspend fun latest(): AppRelease = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(API_URL)
            .header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Update check failed (${response.code})")
            parse(JSONObject(response.body?.string() ?: throw IOException("Empty release response")))
        }
    }

    suspend fun notesFor(version: String): List<String> = withContext(Dispatchers.IO) {
        if (AppVersions.parts(version) == null) return@withContext emptyList()
        val tag = "v" + version.removePrefix("v")
        val request = Request.Builder().url("$RELEASES_URL/tags/$tag")
            .header("Accept", "application/vnd.github.html+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("Cache-Control", "no-cache").build()
        val call = client.newCall(request)
        call.timeout().timeout(5, TimeUnit.SECONDS)
        call.execute().use { response ->
            if (!response.isSuccessful) return@use emptyList()
            val source = response.body?.source() ?: return@use emptyList()
            if (source.request(256 * 1024L + 1)) return@use emptyList()
            val json = JSONObject(source.readUtf8())
            if (json.optString("tag_name") != tag || json.optBoolean("draft") || json.optBoolean("prerelease"))
                return@use emptyList()
            val html = json.opt("body_html") as? String ?: return@use emptyList()
            Jsoup.parseBodyFragment(html).body().children().flatMap { block ->
                if (block.normalName() in setOf("ul", "ol")) block.children() else listOf(block)
            }.map { it.text() }.filter { it.isNotBlank() }
        }
    }

    companion object {
        const val REPOSITORY_URL = "https://github.com/LosLonelyDevs/Mobilism-Material"
        private const val RELEASES_URL = "https://api.github.com/repos/LosLonelyDevs/Mobilism-Material/releases"
        const val API_URL = "$RELEASES_URL/latest"

        internal fun parse(json: JSONObject): AppRelease {
            val tag = json.getString("tag_name")
            if (json.optBoolean("draft") || json.optBoolean("prerelease") || AppVersions.parts(tag) == null)
                throw IOException("No stable release found")
            val version = tag.removePrefix("v")
            val pageUrl = "$REPOSITORY_URL/releases/tag/$tag"
            val apkName = "MobilismMaterial_v$version-release.apk"
            val expectedApkUrl = "$REPOSITORY_URL/releases/download/$tag/$apkName"
            val assets = json.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0)).mapNotNull { assets?.optJSONObject(it) }
                .firstOrNull { it.optString("name") == apkName && it.optString("browser_download_url") == expectedApkUrl }
            val size = apk?.optLong("size")?.takeIf { it in 1..MAX_UPDATE_APK_BYTES }
            val digest = apk?.optString("digest").orEmpty()
            val hash = digest.takeIf { it.matches(Regex("sha256:[a-fA-F0-9]{64}")) }?.substringAfter(':')?.lowercase()
            if (digest.isNotBlank() && digest != "null" && hash == null) throw IOException("Invalid APK checksum")
            return AppRelease(version, pageUrl, apk?.optString("browser_download_url"), size, hash)
        }
    }
}
