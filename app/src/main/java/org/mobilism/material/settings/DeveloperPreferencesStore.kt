package org.mobilism.material.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeveloperPreferences(val enabled: Boolean = false, val verboseLogging: Boolean = false,
    val debridEnabled: Boolean = true)

class DeveloperPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences("material-developer", Context.MODE_PRIVATE)
    private val enabled = preferences.getBoolean("enabled", false)
    private val mutableState = MutableStateFlow(DeveloperPreferences(enabled = enabled,
        verboseLogging = enabled && preferences.getBoolean("verbose-logging", false),
        debridEnabled = preferences.getBoolean("debrid-enabled", true)))
    val state = mutableState.asStateFlow()

    fun unlock() = save(mutableState.value.copy(enabled = true))

    fun disable() = save(mutableState.value.copy(enabled = false, verboseLogging = false))

    fun setVerboseLogging(enabled: Boolean) {
        if (mutableState.value.enabled) save(mutableState.value.copy(verboseLogging = enabled))
    }

    fun setDebridEnabled(enabled: Boolean) {
        if (mutableState.value.enabled) save(mutableState.value.copy(debridEnabled = enabled))
    }

    private fun save(value: DeveloperPreferences) {
        preferences.edit().remove("unlocked").putBoolean("enabled", value.enabled)
            .putBoolean("verbose-logging", value.verboseLogging).putBoolean("debrid-enabled", value.debridEnabled).apply()
        mutableState.value = value
    }
}
