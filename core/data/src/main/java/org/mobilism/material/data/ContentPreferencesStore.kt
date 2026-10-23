package org.mobilism.material.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ContentPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences("content-preferences", Context.MODE_PRIVATE)
    private val mutableHideAdult = MutableStateFlow(preferences.getBoolean("hide-adult", false))
    val hideAdult = mutableHideAdult.asStateFlow()

    fun hideAdult(enabled: Boolean) {
        preferences.edit().putBoolean("hide-adult", enabled).apply()
        mutableHideAdult.value = enabled
    }
}
