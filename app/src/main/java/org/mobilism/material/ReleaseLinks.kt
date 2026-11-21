package org.mobilism.material

import java.net.URI
import java.net.URLDecoder

internal object ReleaseLinks {
    data class Topic(val id: Long, val forumId: Long?)

    fun topicId(url: String?) = topic(url)?.id

    data class Comment(val postId: Long, val topicId: Long? = null) {
        val url: String get() = "https://forum.mobilism.org/viewtopic.php?" +
            (topicId?.let { "t=$it&" } ?: "") + "p=$postId#p$postId"
    }

    private data class Destination(val topic: Topic?, val comment: Comment?)

    fun topic(url: String?) = parse(url)?.topic
    fun comment(url: String?) = parse(url)?.comment

    private fun parse(url: String?): Destination? = runCatching {
        val uri = URI(url ?: return null)
        if (uri.scheme?.lowercase() !in setOf("https", "http") || uri.rawUserInfo != null ||
            uri.host?.lowercase() !in setOf("forum.mobilism.org", "mobilism.org", "www.mobilism.org") ||
            uri.path != "/viewtopic.php" || uri.port !in listOf(-1, 80, 443)) return null
        val params = uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
            val pair = part.split('=', limit = 2)
            if (pair.size != 2) null
            else URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
        }
        fun id(key: String) = params.singleOrNull { it.first == key }?.second
            ?.takeIf { it.all(Char::isDigit) }?.toLongOrNull()?.takeIf { it > 0 }
        val topic = id("t")
        if (topic == null && params.any { it.first == "t" }) return null
        val post = id("p")
        val anchor = uri.rawFragment?.let { Regex("p([1-9][0-9]*)").matchEntire(it)?.groupValues?.get(1)?.toLongOrNull() }
        val postId = when {
            params.any { it.first == "p" } -> post?.takeIf { anchor == null || anchor == it }
            topic != null -> anchor
            else -> null
        }
        Destination(topic?.let { Topic(it, id("f")) }, postId?.let { Comment(it, topic) })
    }.getOrNull()
}
