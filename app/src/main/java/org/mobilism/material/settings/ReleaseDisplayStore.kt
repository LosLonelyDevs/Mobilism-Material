package org.mobilism.material.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ReleaseDisplayStore internal constructor(initiallyExpanded: Boolean, private val save: (Boolean) -> Unit) {
    constructor(context: Context) : this(
        context.getSharedPreferences("material-release-display", Context.MODE_PRIVATE).getBoolean("description-expanded", true),
        { expanded -> context.getSharedPreferences("material-release-display", Context.MODE_PRIVATE)
            .edit().putBoolean("description-expanded", expanded).apply() },
    )

    private val mutableExpanded = MutableStateFlow(initiallyExpanded)
    val descriptionExpanded = mutableExpanded.asStateFlow()

    fun setDescriptionExpanded(expanded: Boolean) {
        if (mutableExpanded.value == expanded) return
        save(expanded)
        mutableExpanded.value = expanded
    }
}
