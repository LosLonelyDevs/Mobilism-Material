package org.mobilism.material.data

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

enum class DiscussionArea(val label: String, internal val categoryKey: String) {
    Apps("Apps", "app|game"), Games("Games", "app|game"), Books("Books", "books"), Community("Community", ""),
}

enum class DiscussionKind(val path: String) { Discussion("discussion"), Other("other") }

data class DiscussionCategory(
    val id: Long, val name: String, val area: DiscussionArea, val section: String = "",
    val topics: Long? = null, val posts: Long? = null,
) {
    // The original category picker uses 12 for the section feed and 13 for announcements.
    val kind: DiscussionKind get() = if (area == DiscussionArea.Community || id == 13L) DiscussionKind.Other else DiscussionKind.Discussion
    val apiId: String get() = if (id == 12L && kind == DiscussionKind.Discussion) area.name.lowercase() else id.toString()
    val key: String get() = "${area.name}:$id"
    val browserUrl: String get() = "https://forum.mobilism.org/viewforum.php?f=$id"
}

data class DiscussionTopic(
    val id: Long, val title: String, val author: String, val contentHtml: String,
    val kind: DiscussionKind, val forumId: Long, val publishedAt: Long = 0,
    val activityAt: Long = 0, val replies: Long = 0, val views: Long = 0,
    val pinned: Boolean = false, val browserUrl: String = "https://forum.mobilism.org/viewtopic.php?t=$id",
    val avatar: String? = null, val images: List<String> = emptyList(),
) {
    val key: String get() = "${kind.path}:$id"
    val originalPost: ReleaseComment get() = ReleaseComment(id, author, avatar, publishedAt, contentHtml, images)
}

data class DiscussionPage(val items: List<DiscussionTopic>, val canLoadMore: Boolean)

interface DiscussionsRepository {
    suspend fun discussionCategories(area: DiscussionArea): List<DiscussionCategory>
    suspend fun discussionLastActivity(category: DiscussionCategory): Long?
    suspend fun discussionTopics(category: DiscussionCategory, page: Int, query: String = ""): DiscussionPage
    suspend fun discussionComments(topic: DiscussionTopic, page: Int): CommentPage
    suspend fun postDiscussionComment(topic: DiscussionTopic, text: String, replyTo: Long? = null): Long
}

internal object DiscussionJson {
    fun categories(json: JSONObject, area: DiscussionArea): List<DiscussionCategory> {
        if (area != DiscussionArea.Community) {
            return categories(json.getJSONObject("response").getJSONArray(area.categoryKey), area, "")
        }
        return listOf("announcements" to "Announcements", "contests" to "Contests", "talk" to "Chitchat",
            "services" to "Services", "feedback" to "Suggestions & Feedback").flatMap { (key, label) ->
            if (!json.has(key)) throw ApiFailure(FailureKind.InvalidResponse, "The forum categories could not be read.")
            categories(if (json.isNull(key)) JSONArray() else json.getJSONArray(key), area, label)
        }
    }

    private fun categories(array: JSONArray, area: DiscussionArea, section: String): List<DiscussionCategory> =
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val id = item.getLong("id")
            require(id > 0)
            DiscussionCategory(id, plain(item.optString("name")).ifBlank { section.ifBlank { "Forum $id" } }, area, section,
                item.optLong("topics").takeIf { item.has("topics") && it >= 0 },
                item.optLong("posts").takeIf { item.has("posts") && it >= 0 })
        }.distinctBy { it.id }

    fun page(json: JSONObject, kind: DiscussionKind, search: Boolean = false): DiscussionPage {
        val key = if (search) if (kind == DiscussionKind.Other) "others" else "discussions" else "response"
        if (!json.has(key)) throw ApiFailure(FailureKind.InvalidResponse, "The forum topics could not be read.")
        val array = if (json.isNull(key)) JSONArray() else json.getJSONArray(key)
        val items = (0 until array.length()).map { topic(array.getJSONObject(it), kind) }.distinctBy { it.id }
        return DiscussionPage(items, array.length() > 0)
    }

    private fun topic(item: JSONObject, kind: DiscussionKind): DiscussionTopic {
        val id = item.getLong("id")
        require(id > 0)
        val images = item.optJSONArray("images")
        val rawUrl = item.optString("browser_url")
        return DiscussionTopic(id,
            plain(item.optString("subject")).ifBlank { plain(item.optString("title")).ifBlank { "Topic $id" } },
            plain(item.optString("name")), CommentMarkup.html(item.optString("content")), kind,
            item.optLong("forum_id"), item.optLong("date"), item.optLong("topic_last_post_time", item.optLong("date")),
            item.optLong("replies").coerceAtLeast(0), item.optLong("views").coerceAtLeast(0), item.optInt("status") in 1..2,
            webUrlOrNull(if (rawUrl.startsWith("/")) "https://forum.mobilism.org$rawUrl" else rawUrl)
                ?: "https://forum.mobilism.org/viewtopic.php?t=$id",
            webUrlOrNull(item.optString("avatar")),
            (0 until (images?.length() ?: 0)).mapNotNull { webUrlOrNull(images!!.optString(it)) }.distinct())
    }

    private fun plain(text: String) = Jsoup.parse(text).text()
}
