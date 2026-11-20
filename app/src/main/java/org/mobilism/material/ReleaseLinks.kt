package org.mobilism.material

import java.net.URI
import java.net.URLDecoder

internal object ReleaseLinks {
    data class Topic(val id: Long, val forumId: Long?)

    fun topicId(url: String?) = topic(url)?.id

    fun topic(url: String?): Topic? = runCatching {
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
        Topic(id("t") ?: return null, id("f"))
    }.getOrNull()
}
