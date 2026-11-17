package org.mobilism.material.messages

import android.content.Context
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mobilism.material.settings.MessageReadHistory
import org.mobilism.material.data.ApiRequestLimits

class MessageAlertStore(context: Context, limits: () -> ApiRequestLimits = { ApiRequestLimits.Defaults }) {
    private val preferences = context.getSharedPreferences("message-alerts", Context.MODE_PRIVATE)
    private val mutableEnabled = MutableStateFlow(preferences.getBoolean("enabled", true))
    val enabled = mutableEnabled.asStateFlow()
    private val histories = mutableMapOf<String, MessageReadHistory>()
    val polls = MessagePollPolicy(limits = limits, load = { account ->
        val key = "poll-${accountKey(account)}"
        if (!preferences.contains(key)) null else MessagePollPolicy.Attempt(preferences.getLong(key, 0),
            preferences.getLong("$key-next", 0), preferences.getInt("$key-failures", 0).coerceIn(0, 5))
    }, save = { account, attempt ->
        val key = "poll-${accountKey(account)}"
        preferences.edit().putLong(key, attempt.startedAt).putLong("$key-next", attempt.nextAt)
            .putInt("$key-failures", attempt.failures).commit()
    })
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
