package org.mobilism.material.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeveloperPreferences(val enabled: Boolean = false, val verboseLogging: Boolean = false,
    val debridEnabled: Boolean = true, val customCategoriesEnabled: Boolean = false,
    val markdownSyntax: Boolean = false, val manualNoticeChecks: Boolean = false) {
    val customCategoriesActive: Boolean get() = enabled && customCategoriesEnabled
    val markdownSyntaxActive: Boolean get() = enabled && markdownSyntax
}

class DeveloperPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences("material-developer", Context.MODE_PRIVATE)
    private val enabled = preferences.getBoolean("enabled", false)
    private val mutableState = MutableStateFlow(DeveloperPreferences(enabled = enabled,
        verboseLogging = enabled && preferences.getBoolean("verbose-logging", false),
        debridEnabled = preferences.getBoolean("debrid-enabled", true),
        customCategoriesEnabled = preferences.getBoolean("custom-categories-enabled", false),
        markdownSyntax = preferences.getBoolean("markdown-syntax", false),
        manualNoticeChecks = enabled && preferences.getBoolean("manual-notice-checks", false)))
    val state = mutableState.asStateFlow()

    fun unlock() = save(mutableState.value.copy(enabled = true))

    fun disable() = save(mutableState.value.copy(enabled = false, verboseLogging = false, manualNoticeChecks = false))

    fun setVerboseLogging(enabled: Boolean) {
        if (mutableState.value.enabled) save(mutableState.value.copy(verboseLogging = enabled))
    }

    fun setDebridEnabled(enabled: Boolean) {
        if (mutableState.value.enabled) save(mutableState.value.copy(debridEnabled = enabled))
    }

    fun setCustomCategoriesEnabled(enabled: Boolean) {
        if (mutableState.value.enabled) save(mutableState.value.copy(customCategoriesEnabled = enabled))
    }

    fun setMarkdownSyntax(enabled: Boolean) {
        if (mutableState.value.enabled) save(mutableState.value.copy(markdownSyntax = enabled))
    }

    fun setManualNoticeChecks(enabled: Boolean) {
        if (mutableState.value.enabled) save(mutableState.value.copy(manualNoticeChecks = enabled))
    }

    private fun save(value: DeveloperPreferences) {
        preferences.edit().remove("unlocked").putBoolean("enabled", value.enabled)
            .putBoolean("verbose-logging", value.verboseLogging).putBoolean("debrid-enabled", value.debridEnabled)
            .putBoolean("custom-categories-enabled", value.customCategoriesEnabled)
            .putBoolean("markdown-syntax", value.markdownSyntax)
            .putBoolean("manual-notice-checks", value.manualNoticeChecks).apply()
        mutableState.value = value
    }
}
