package org.mobilism.material.data

import java.net.URI
import java.net.URLDecoder

enum class ForumCommentAction(val mode: String, val title: String) {
    Edit("edit", "Edit comment"), Delete("delete", "Delete comment"),
}

data class ForumCommentTarget(val topicId: Long, val postId: Long, val action: ForumCommentAction, val forumId: Long? = null) {
    init { require(topicId > 0 && postId > 0) }

    val url: String get() = "https://forum.mobilism.org/posting.php?mode=${action.mode}" +
        (forumId?.takeIf { it > 0 }?.let { "&f=$it" } ?: "") + "&p=$postId"

    fun accepts(url: String): Boolean = runCatching {
        val uri = URI(url)
        if (uri.scheme != "https" || uri.host != "forum.mobilism.org" || uri.rawUserInfo != null ||
            uri.port !in listOf(-1, 443)) return false
        val pairs = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.map {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }
        if (pairs.map { it.first }.distinct().size != pairs.size) return false
        val query = pairs.toMap()
        when (uri.path) {
            "/posting.php" -> query["mode"] == action.mode && query["p"] == postId.toString() &&
                (query["t"] == null || query["t"] == topicId.toString())
            "/ucp.php" -> query["mode"] == "login"
            "/viewtopic.php" -> query["t"] == topicId.toString() ||
                (query["t"] == null && query["p"] == postId.toString())
            "/", "/index.php" -> query.keys.all { it == "sid" }
            else -> false
        }
    }.getOrDefault(false)
}
