package org.mobilism.material.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mobilism.material.data.ContentSection
import org.mobilism.material.data.FeedOrder

data class LandingPage(val section: ContentSection = ContentSection.Apps, val order: FeedOrder? = null) {
    val label: String get() = "${section.label} · ${order?.label ?: "Home"}"

    companion object {
        val options = ContentSection.entries.flatMap { section ->
            listOf(LandingPage(section)) + FeedOrder.entries.map { LandingPage(section, it) }
        }

        fun parse(section: String?, order: String?) = LandingPage(
            ContentSection.entries.firstOrNull { it.name == section } ?: ContentSection.Apps,
            FeedOrder.entries.firstOrNull { it.name == order },
        )
    }
}

class LandingPageStore(context: Context) {
    private val preferences = context.getSharedPreferences("material-navigation", Context.MODE_PRIVATE)
    // Keep a saved section from earlier versions until a landing page is chosen.
    private val mutableState = MutableStateFlow(LandingPage.parse(
        preferences.getString("landing-section", null) ?: preferences.getString("section", null),
        preferences.getString("landing-order", null),
    ))
    val state = mutableState.asStateFlow()

    fun select(value: LandingPage) {
        preferences.edit().putString("landing-section", value.section.name)
            .putString("landing-order", value.order?.name ?: "Home").apply()
        mutableState.value = value
    }
}
