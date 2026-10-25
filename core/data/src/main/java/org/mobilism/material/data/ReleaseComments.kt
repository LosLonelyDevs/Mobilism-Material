package org.mobilism.material.data

import org.json.JSONObject
import org.jsoup.nodes.Entities

object ReleaseComments {
    const val MAX_LENGTH = 10_000

    internal fun confirmedId(response: JSONObject): Long =
        response.optJSONObject("response")?.optLong("comment_id")?.takeIf { it > 0 }
            ?: throw ApiFailure(FailureKind.InvalidResponse, "The server did not confirm the comment.")

    fun posted(id: Long, author: String, text: String, timestamp: Long): ReleaseComment = ReleaseComment(
        id = id, author = author, avatar = null, publishedAt = timestamp,
        contentHtml = Entities.escape(text.trim()).replace("\r\n", "\n").replace("\n", "<br>"),
    )
}
