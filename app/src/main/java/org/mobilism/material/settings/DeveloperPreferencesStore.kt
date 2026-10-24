package org.mobilism.material.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeveloperPreferences(val unlocked: Boolean = false, val enabled: Boolean = false)

class DeveloperPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences("material-developer", Context.MODE_PRIVATE)
    private val unlocked = preferences.getBoolean("unlocked", false)
    private val mutableState = MutableStateFlow(DeveloperPreferences(
        unlocked = unlocked,
        enabled = unlocked && preferences.getBoolean("enabled", false),
    ))
    val state = mutableState.asStateFlow()

    fun unlock() {
        preferences.edit().putBoolean("unlocked", true).putBoolean("enabled", true).apply()
        mutableState.value = DeveloperPreferences(unlocked = true, enabled = true)
    }

    fun setEnabled(enabled: Boolean) {
        if (!mutableState.value.unlocked) return
        preferences.edit().putBoolean("enabled", enabled).apply()
        mutableState.value = mutableState.value.copy(enabled = enabled)
    }
}
