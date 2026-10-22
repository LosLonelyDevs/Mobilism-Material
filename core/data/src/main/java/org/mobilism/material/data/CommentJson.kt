package org.mobilism.material.data

import org.json.JSONArray

internal object CommentJson {
    fun page(array: JSONArray): CommentPage {
        val items = (0 until array.length()).map { index ->
            val json = array.getJSONObject(index)
            val images = json.optJSONArray("images")
            val quotes = json.optJSONArray("replies")
            ReleaseComment(
                id = json.getLong("id"), author = json.optString("name"),
                avatar = webUrlOrNull(json.optString("avatar")), publishedAt = json.optLong("date"),
                contentHtml = json.optString("content"),
                images = (0 until (images?.length() ?: 0)).mapNotNull { webUrlOrNull(images!!.optString(it)) }.distinct(),
                quotes = (0 until (quotes?.length() ?: 0)).mapNotNull {
                    quotes!!.optJSONObject(it)?.let { quote -> CommentQuote(quote.optString("user"), quote.optString("message")) }
                },
            )
        }.distinctBy { it.id }
        return CommentPage(items, items.isNotEmpty())
    }
}
