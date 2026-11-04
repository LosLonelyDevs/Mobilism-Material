package org.mobilism.material.settings

import android.content.Context
import java.security.MessageDigest
import java.util.Locale

class MessageReadStore(context: Context, private val onRead: (String, Set<Long>) -> Unit = { _, _ -> }) {
    private val preferences = context.getSharedPreferences("message-read-history", Context.MODE_PRIVATE)
    private val histories = mutableMapOf<String, MessageReadHistory>()

    @Synchronized fun forAccount(username: String): MessageReadHistory {
        val key = MessageDigest.getInstance("SHA-256")
            .digest(username.trim().lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return histories.getOrPut(key) { MessageReadHistory(
            load = { preferences.getStringSet(key, emptySet()).orEmpty().mapNotNull(String::toLongOrNull).toSet() },
            save = { ids ->
                preferences.edit().putStringSet(key, ids.map(Long::toString).toSet()).apply()
                onRead(username, ids)
            },
        ) }
    }
}
