package org.mobilism.material.following

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FollowPreferences(val backgroundChecks: Boolean = true, val notifications: Boolean = false, val intervalHours: Long = 6)

class FollowingPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("following-settings", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(FollowPreferences(preferences.getBoolean("background", true),
        preferences.getBoolean("notifications", false), preferences.getLong("hours", 6).takeIf { it in intervals } ?: 6))
    val state = mutableState.asStateFlow()

    fun background(enabled: Boolean) = save(state.value.copy(backgroundChecks = enabled))
    fun notifications(enabled: Boolean) = save(state.value.copy(notifications = enabled))
    fun interval(hours: Long) { require(hours in intervals); save(state.value.copy(intervalHours = hours)) }
    private fun save(value: FollowPreferences) {
        preferences.edit().putBoolean("background", value.backgroundChecks).putBoolean("notifications", value.notifications)
            .putLong("hours", value.intervalHours).apply()
        mutableState.value = value
    }
    companion object { val intervals = listOf(1L, 6L, 12L, 24L) }
}
