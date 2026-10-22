package org.mobilism.material.data

import org.json.JSONArray
import org.json.JSONObject

data class LibraryEntry(
    val release: Release,
    val watched: Boolean = false,
    val downloaded: Boolean = false,
    val savedAt: Long = System.currentTimeMillis(),
    val checkedAt: Long? = null,
    val update: Release? = null,
    val linkedPackage: String? = null,
    val latest: Release? = null,
    val lastNotifiedVersion: String? = null,
    val checkError: String? = null,
)

/** Local release records. A watch follows one app; downloaded history keeps individual releases. */
object AppLibrary {
    fun watch(entries: List<LibraryEntry>, release: Release, enabled: Boolean): List<LibraryEntry> {
        val previous = entries.firstOrNull { it.watched && ReleaseVersions.sameApp(it.release, release) }
        val result = entries.map { entry ->
            if (ReleaseVersions.sameApp(entry.release, release)) entry.copy(watched = false, update = null, checkedAt = null) else entry
        }.toMutableList()
        if (enabled) {
            val index = result.indexOfFirst { it.release.id == release.id }
            val entry = (result.getOrNull(index) ?: LibraryEntry(compact(release))).copy(
                watched = true, linkedPackage = previous?.linkedPackage ?: ReleaseVersions.packageName(release),
                lastNotifiedVersion = previous?.lastNotifiedVersion,
            )
            if (index >= 0) result[index] = entry else result.add(0, entry)
        }
        return result.filter { it.watched || it.downloaded }
    }

    fun downloaded(entries: List<LibraryEntry>, release: Release, enabled: Boolean): List<LibraryEntry> {
        val result = entries.toMutableList()
        val index = result.indexOfFirst { it.release.id == release.id }
        val entry = (result.getOrNull(index) ?: LibraryEntry(compact(release))).copy(downloaded = enabled)
        if (index >= 0) result[index] = entry else if (enabled) result.add(0, entry)
        return result.filter { it.watched || it.downloaded }
    }

    fun compact(release: Release): Release {
        val appPackage = ReleaseVersions.packageName(release)
        return release.copy(
            contentHtml = appPackage?.let { "<a href=\"https://play.google.com/store/apps/details?id=$it\">Store</a>" }.orEmpty(),
            images = emptyList(), downloadLinks = emptyList(),
        )
    }

    fun restore(entries: List<LibraryEntry>, entry: LibraryEntry): List<LibraryEntry> {
        val existing = entries.firstOrNull { it.release.id == entry.release.id }
        val otherWatch = entries.any { it.release.id != entry.release.id && it.watched && ReleaseVersions.sameApp(it.release, entry.release) }
        val restored = entry.copy(watched = existing?.watched == true || (entry.watched && !otherWatch),
            downloaded = existing?.downloaded == true || entry.downloaded)
        return (listOf(restored) + entries.filterNot { it.release.id == entry.release.id }).filter { it.watched || it.downloaded }
    }
}

internal object LibraryJson {
    fun encode(entries: List<LibraryEntry>): String = JSONArray().apply {
        entries.forEach { entry -> put(JSONObject().apply {
            put("release", releaseJson(entry.release)); put("watched", entry.watched); put("downloaded", entry.downloaded)
            put("savedAt", entry.savedAt); put("checkedAt", entry.checkedAt)
            put("update", entry.update?.let(::releaseJson))
            put("linkedPackage", entry.linkedPackage); put("latest", entry.latest?.let(::releaseJson))
            put("lastNotifiedVersion", entry.lastNotifiedVersion); put("checkError", entry.checkError)
        }) }
    }.toString()

    fun decode(value: String): List<LibraryEntry> {
        val array = JSONArray(value)
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                LibraryEntry(
                    release = release(item.getJSONObject("release")), watched = item.optBoolean("watched"),
                    downloaded = item.optBoolean("downloaded"), savedAt = item.getLong("savedAt"),
                    checkedAt = if (item.has("checkedAt")) item.getLong("checkedAt") else null,
                    update = item.optJSONObject("update")?.let(::release),
                    linkedPackage = item.optString("linkedPackage").takeIf(String::isNotBlank),
                    latest = item.optJSONObject("latest")?.let(::release),
                    lastNotifiedVersion = item.optString("lastNotifiedVersion").takeIf(String::isNotBlank),
                    checkError = item.optString("checkError").takeIf(String::isNotBlank),
                )
            }.getOrNull()
        }.filter { it.watched || it.downloaded }.distinctBy { it.release.id }
    }

    private fun releaseJson(value: Release) = JSONObject().apply {
        put("id", value.id); put("title", value.title); put("author", value.author); put("thumbnail", value.thumbnail)
        put("publishedAt", value.publishedAt); put("replies", value.replies); put("views", value.views)
        put("content", AppLibrary.compact(value).contentHtml); put("url", value.browserUrl); put("section", value.section?.name)
    }

    private fun release(value: JSONObject) = Release(
        id = value.getLong("id"), title = value.getString("title"), author = value.optString("author"),
        thumbnail = value.optString("thumbnail").takeIf(String::isNotBlank), publishedAt = value.optLong("publishedAt"),
        replies = value.optLong("replies"), views = value.optLong("views"), contentHtml = value.optString("content"),
        browserUrl = value.optString("url").takeIf(String::isNotBlank),
        section = ContentSection.entries.firstOrNull { it.name == value.optString("section") },
    )
}
