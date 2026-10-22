package org.mobilism.material.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal object SearchJson {
    fun page(json: JSONObject): ReleasePage {
        if (!json.has("releases")) throw JSONException("Search response is missing releases")
        val array = if (json.isNull("releases")) JSONArray() else json.getJSONArray("releases")
        // Legacy search presents each page by latest post activity, not original release date.
        val items = (0 until array.length()).map { array.getJSONObject(it) }
            .sortedByDescending { it.optLong("topic_last_post_time", it.optLong("date")) }
            .map(ReleaseJson::parse).distinctBy { it.id }
        return ReleasePage(items, items.isNotEmpty())
    }
}
