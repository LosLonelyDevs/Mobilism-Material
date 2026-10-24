package org.mobilism.material

import java.net.URI
import java.net.URLDecoder

internal object ReleaseLinks {
    fun topicId(url: String?): Long? = runCatching {
        val uri = URI(url ?: return null)
        if (uri.scheme?.lowercase() !in setOf("https", "http") || uri.rawUserInfo != null ||
            uri.host?.lowercase() !in setOf("forum.mobilism.org", "mobilism.org", "www.mobilism.org") ||
            uri.path != "/viewtopic.php" || uri.port !in listOf(-1, 80, 443)) return null
        val ids = uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
            val pair = part.split('=', limit = 2)
            if (pair.size != 2 || URLDecoder.decode(pair[0], "UTF-8") != "t") null
            else URLDecoder.decode(pair[1], "UTF-8")
        }
        ids.singleOrNull()?.takeIf { it.all(Char::isDigit) }?.toLongOrNull()?.takeIf { it > 0 }
    }.getOrNull()
}
