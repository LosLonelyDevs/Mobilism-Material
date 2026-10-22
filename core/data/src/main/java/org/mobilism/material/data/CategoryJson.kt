package org.mobilism.material.data

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.parser.Parser

internal object CategoryJson {
    fun parse(json: JSONObject, section: ContentSection): List<ReleaseCategory> {
        val key = when (section) {
            ContentSection.Apps -> "app"
            ContentSection.Games -> "games"
            ContentSection.Books -> "books"
        }
        return buildList {
            fun visit(array: JSONArray, group: String? = null) {
                for (index in 0 until array.length()) {
                    val category = array.getJSONObject(index)
                    val name = Parser.unescapeEntities(category.getString("name"), false).trim()
                    val children = category.optJSONArray("child")
                    if (children != null && children.length() > 0) visit(children, name)
                    else {
                        val id = category.getLong("id")
                        if (id > 0 && name.isNotEmpty()) add(ReleaseCategory(id, name, group))
                    }
                }
            }
            visit(json.getJSONArray(key))
        }.distinctBy { it.id }
    }
}
