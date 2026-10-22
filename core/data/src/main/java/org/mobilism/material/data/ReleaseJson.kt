package org.mobilism.material.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

fun webUrlOrNull(value: String): String? {
    val trimmed = value.trim()
    return (if (trimmed.startsWith("//")) "https:$trimmed" else trimmed).toHttpUrlOrNull()?.toString()
}

internal object ReleaseJson {
    fun parse(json: JSONObject): Release {
        val images = strings(json.optJSONArray("images")).mapNotNull(::webUrlOrNull).distinct()
        return Release(
            id = json.getLong("id"),
            title = json.optString("subject").ifBlank { "Untitled release" },
            author = json.optString("name"),
            // "preview" is a description excerpt. The legacy list uses images[0].
            thumbnail = images.firstOrNull(),
            publishedAt = json.optLong("date"),
            replies = json.optLong("replies"),
            views = json.optLong("views"),
            contentHtml = json.optString("content"),
            images = images,
            downloadLinks = strings(json.optJSONArray("url")).mapNotNull(::webUrlOrNull),
            browserUrl = webUrlOrNull(json.optString("browser_url")),
        )
    }

    private fun strings(array: JSONArray?): List<String> = array?.let {
        (0 until it.length()).mapNotNull { index -> (it.opt(index) as? String)?.takeIf(String::isNotBlank) }
    }.orEmpty()
}
