package org.mobilism.material.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class AppRelease(val version: String, val pageUrl: String, val apkUrl: String?)

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

class AppReleaseUpdates(private val client: OkHttpClient = OkHttpClient.Builder()
    .callTimeout(20, TimeUnit.SECONDS).build()) {
    suspend fun latest(): AppRelease = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(API_URL)
            .header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Update check failed (${response.code})")
            parse(JSONObject(response.body?.string() ?: throw IOException("Empty release response")))
        }
    }

    companion object {
        const val REPOSITORY_URL = "https://github.com/LosLonelyDevs/Mobilism-Material"
        const val API_URL = "https://api.github.com/repos/LosLonelyDevs/Mobilism-Material/releases/latest"

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
            return AppRelease(version, pageUrl, apk?.optString("browser_download_url"))
        }
    }
}
