package org.mobilism.material.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject

fun webUrlOrNull(value: String): String? {
    val trimmed = value.trim()
    return (if (trimmed.startsWith("//")) "https:$trimmed" else trimmed).toHttpUrlOrNull()?.toString()
}

internal object ReleaseJson {
    fun page(array: JSONArray): ReleasePage {
        val items = (0 until array.length()).map { parse(array.getJSONObject(it)) }
        // The live API can cap pages below the requested limit. Only an empty
        // page ends pagination; the view model also stops repeated page IDs.
        return ReleasePage(items, items.isNotEmpty())
    }

    fun parse(json: JSONObject): Release {
        val images = strings(json.optJSONArray("images")).mapNotNull(::webUrlOrNull).distinct()
        val browserUrl = json.optString("browser_url").takeIf { it.isNotBlank() && it != "null" }
            ?.let { "https://forum.mobilism.org/".toHttpUrl().resolve(it) }
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
            browserUrl = browserUrl?.toString(),
            categoryId = json.optLong("category_id").takeIf { it > 0 }
                ?: json.optLong("forum_id").takeIf { it > 0 }
                ?: browserUrl?.queryParameter("f")?.toLongOrNull()?.takeIf { it > 0 },
            likeId = json.optLong("like_id").takeIf { it > 0 },
            liked = ReleaseLikes.liked(json),
            section = when (json.optString("section")) {
                "app", "apps" -> ContentSection.Apps
                "game", "games" -> ContentSection.Games
                "book", "books" -> ContentSection.Books
                else -> null
            },
        )
    }

    private fun strings(array: JSONArray?): List<String> = array?.let {
        (0 until it.length()).mapNotNull { index -> (it.opt(index) as? String)?.takeIf(String::isNotBlank) }
    }.orEmpty()
}
