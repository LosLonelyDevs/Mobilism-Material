package org.mobilism.material.data

import org.json.JSONObject
import org.jsoup.Jsoup

enum class MessageFolder(val label: String, val apiPath: String) {
    Inbox("Inbox", "to"), Sent("Sent", "from"),
}

data class PrivateMessage(
    val id: Long, val messageId: Long, val subject: String, val from: String, val to: String,
    val contentHtml: String, val preview: String, val publishedAt: Long, val read: Boolean,
) {
    fun recipient(username: String): String = if (to.equals(username, ignoreCase = true)) from else to
    override fun toString(): String = "PrivateMessage(id=$id, messageId=$messageId, read=$read)"
}

data class MessagePage(val items: List<PrivateMessage>, val canLoadMore: Boolean)

interface MessagesRepository {
    suspend fun messages(folder: MessageFolder, page: Int): MessagePage
    suspend fun conversation(id: Long): List<PrivateMessage>
    suspend fun markMessageRead(id: Long)
    suspend fun replyToMessage(message: PrivateMessage, text: String)
}

object PrivateMessages {
    const val MAX_LENGTH = 10_000

    internal fun parse(response: JSONObject): List<PrivateMessage> {
        val array = response.getJSONArray("response")
        return (0 until array.length()).map { index ->
            val json = array.getJSONObject(index)
            val id = json.getLong("id")
            require(id > 0)
            fun text(key: String) = if (json.isNull(key)) "" else json.optString(key)
            PrivateMessage(
                id = id, messageId = json.optLong("msg_id").takeIf { it > 0 } ?: id,
                subject = Jsoup.parse(text("subject")).text(), from = text("from_user"), to = text("to_user"),
                contentHtml = CommentMarkup.html(text("content")), preview = Jsoup.parse(CommentMarkup.html(text("preview"))).text(),
                publishedAt = json.optLong("times"), read = json.optInt("readed") == 1,
            )
        }.distinctBy { it.messageId }
    }

    internal fun confirm(response: JSONObject) {
        if (response.optString("status") != "success") throw ApiFailure(
            FailureKind.InvalidResponse, "The server did not confirm the message action.",
        )
    }
}
