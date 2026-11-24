package org.mobilism.material.ui

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal class DailyHomeStore(private val prefs: SharedPreferences, private val key: String) {
    fun read(day: Int): DailySelection? = runCatching {
        val data = JSONObject(prefs.getString(key, null) ?: return null)
        if (data.getInt("day") != day) return null
        fun names(field: String) = data.getJSONArray(field).let { a ->
            (0 until minOf(a.length(), 5)).map(a::getString)
        }
        val categories = data.getJSONArray("categories")
        DailySelection(names("discover"), (0 until minOf(categories.length(), 2)).map(categories::getLong), names("featured"))
    }.getOrNull()

    fun save(day: Int, picks: DailySelection) {
        val data = JSONObject().put("day", day).put("discover", JSONArray(picks.discover))
            .put("categories", JSONArray(picks.categoryIds)).put("featured", JSONArray(picks.featured))
        prefs.edit().putString(key, data.toString()).apply()
    }
}
