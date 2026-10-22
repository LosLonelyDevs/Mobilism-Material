package org.mobilism.material.data

import org.json.JSONObject
import org.jsoup.Jsoup

internal object ReleaseLikes {
    fun liked(json: JSONObject): Boolean? = when (val value = json.opt("liked")) {
        is Boolean -> value
        1, "1", "true" -> true
        0, "0", "false" -> false
        else -> null
    }

    fun postId(html: String): Long? = Jsoup.parse(html).select("div.post[id]")
        .firstNotNullOfOrNull { post ->
            Regex("p([1-9][0-9]*)").matchEntire(post.id())?.groupValues?.get(1)?.toLongOrNull()
        }
}
