package org.mobilism.material.messages

import android.content.Context
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mobilism.material.settings.MessageReadHistory

class MessageAlertStore(context: Context) {
    private val preferences = context.getSharedPreferences("message-alerts", Context.MODE_PRIVATE)
    private val mutableEnabled = MutableStateFlow(preferences.getBoolean("enabled", true))
    val enabled = mutableEnabled.asStateFlow()
    private val histories = mutableMapOf<String, MessageReadHistory>()
    var permissionRequested: Boolean
        get() = preferences.getBoolean("permission-requested", false)
        set(value) { preferences.edit().putBoolean("permission-requested", value).apply() }

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("enabled", enabled).apply()
        mutableEnabled.value = enabled
    }

    @Synchronized fun history(username: String): MessageReadHistory {
        val key = accountKey(username)
        return histories.getOrPut(key) { MessageReadHistory(
            load = { preferences.getStringSet(key, emptySet()).orEmpty().mapNotNull(String::toLongOrNull).toSet() },
            save = { preferences.edit().putStringSet(key, it.map(Long::toString).toSet()).apply() },
        ) }
    }

    companion object {
        fun accountKey(username: String): String = MessageDigest.getInstance("SHA-256")
            .digest(username.trim().lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
