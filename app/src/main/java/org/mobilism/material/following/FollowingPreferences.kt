package org.mobilism.material.following

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mobilism.material.data.ApiRequestLimits
import org.mobilism.material.data.latestAppKey

data class FollowPreferences(val backgroundChecks: Boolean = true, val notifications: Boolean = false, val intervalHours: Long = 0)

class FollowingPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("following-settings", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(FollowPreferences(preferences.getBoolean("background", true),
        preferences.getBoolean("notifications", false), preferences.getLong("hours", 0).takeIf { it in intervals } ?: 0))
    val state = mutableState.asStateFlow()

    internal fun beginCacheCheck(now: Long, limits: ApiRequestLimits, background: Boolean): Boolean {
        val key = if (background) "last-background-cache" else "last-manual-cache"
        val previous = preferences.getLong(key, 0)
        val minimum = if (background) limits.followCacheIntervalMinutes * 60_000L else limits.followCacheManualCooldownMs
        if (previous > 0 && now - previous in 0 until minimum) return false
        preferences.edit().putLong(key, now).apply()
        return true
    }

    internal fun backgroundBatch(names: List<String>, maximum: Int): List<String> {
        if (names.size <= maximum) return names
        val sorted = names.sortedBy(::latestAppKey)
        val cursor = preferences.getString("last-cache-name", null)
        val start = sorted.indexOfFirst { cursor == null || latestAppKey(it) > cursor }.coerceAtLeast(0)
        val batch = List(maximum) { sorted[(start + it) % sorted.size] }
        preferences.edit().putString("last-cache-name", latestAppKey(batch.last())).apply()
        return batch
    }

    @Synchronized internal fun dailyLookupSeed(day: String): String {
        if (preferences.getString("cache-day", null) == day)
            preferences.getString("cache-seed", null)?.let { return it }
        val seed = java.util.UUID.randomUUID().toString()
        preferences.edit().putString("cache-day", day).putString("cache-seed", seed).apply()
        return seed
    }

    internal fun beginCheck(now: Long, limits: ApiRequestLimits, background: Boolean = false): Boolean {
        val previous = preferences.getLong("last-check-attempt", 0)
        if (!FollowCheckPolicy.canAttempt(previous, now, limits)) return false
        val edit = preferences.edit()
        if (background) {
            val lastBackground = preferences.getLong("last-background-direct", previous)
            val interval = maxOf(FollowCheckPolicy.MIN_BACKGROUND_INTERVAL_HOURS, state.value.intervalHours) * 3_600_000
            if (lastBackground > 0 && now - lastBackground in 0 until interval) return false
            edit.putLong("last-background-direct", now)
        }
        edit.putLong("last-check-attempt", now).apply()
        return true
    }

    fun background(enabled: Boolean) = save(state.value.copy(backgroundChecks = enabled))
    fun notifications(enabled: Boolean) = save(state.value.copy(notifications = enabled))
    fun interval(hours: Long) { require(hours in intervals); save(state.value.copy(intervalHours = hours)) }
    private fun save(value: FollowPreferences) {
        preferences.edit().putBoolean("background", value.backgroundChecks).putBoolean("notifications", value.notifications)
            .putLong("hours", value.intervalHours).apply()
        mutableState.value = value
    }
    companion object { val intervals = listOf(0L, 1L, 6L, 12L, 24L) }
}
