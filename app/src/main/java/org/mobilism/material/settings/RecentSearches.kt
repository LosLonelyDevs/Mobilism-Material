package org.mobilism.material.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.mobilism.material.messages.MessageAlertStore

class RecentSearches(load: () -> List<String> = { emptyList() }, private val save: (List<String>) -> Unit = {}) {
    private val mutableQueries = MutableStateFlow(clean(load()))
    val queries = mutableQueries.asStateFlow()
    fun record(query: String) = update(listOf(query) + mutableQueries.value)
    fun remove(query: String) = update(mutableQueries.value.filterNot { it.equals(query, ignoreCase = true) })
    fun clear() = update(emptyList())
    private fun update(values: List<String>) {
        val next = clean(values)
        if (next != mutableQueries.value) { mutableQueries.value = next; save(next) }
    }
    private fun clean(values: List<String>): List<String> = values.map(String::trim).filter(String::isNotEmpty)
        .distinctBy { it.lowercase(java.util.Locale.ROOT) }.take(10)
}

class RecentSearchStore(context: Context) {
    private val preferences = context.getSharedPreferences("recent-searches", Context.MODE_PRIVATE)
    private val accounts = mutableMapOf<String, RecentSearches>()
    @Synchronized fun forAccount(username: String?): RecentSearches {
        val key = MessageAlertStore.accountKey(username?.let { "user:$it" } ?: "guest")
        return accounts.getOrPut(key) { RecentSearches(
            load = { runCatching {
                val array = JSONArray(preferences.getString(key, "[]"))
                (0 until array.length()).map { array.getString(it) }
            }.getOrDefault(emptyList()) },
            save = { preferences.edit().putString(key, JSONArray(it).toString()).apply() },
        ) }
    }
}
