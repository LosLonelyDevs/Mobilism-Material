package org.mobilism.material.data

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CustomCategoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("custom-categories", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(preferences.getString("state", null)?.let {
        runCatching { CustomCategoryJson.decode(it) }.getOrNull()
    } ?: CustomCategoryState())
    val state = mutableState.asStateFlow()

    @Synchronized
    private fun change(transform: (CustomCategoryState) -> CustomCategoryState) {
        val next = transform(mutableState.value)
        if (next == mutableState.value) return
        preferences.edit().putString("state", CustomCategoryJson.encode(next)).apply()
        mutableState.value = next
    }

    fun create(section: ContentSection, name: String, description: String = "", icon: String = "tag"): CustomCategory {
        var created: CustomCategory? = null
        change { current ->
            val category = CustomCategory(UUID.randomUUID().toString(), section,
                CustomCategories.validateName(current, section, name), description.trim(), icon)
            created = category
            current.copy(categories = current.categories + category)
        }
        return checkNotNull(created)
    }

    fun update(id: String, name: String, description: String, icon: String? = null) = change { current ->
        val category = current.categories.first { it.id == id }
        val valid = CustomCategories.validateName(current, category.section, name, id)
        current.copy(categories = current.categories.map {
            if (it.id == id) it.copy(name = valid, description = description.trim(), icon = icon ?: it.icon) else it
        })
    }

    fun delete(id: String) = change { CustomCategories.delete(it, id) }
    fun assign(release: Release, categories: Set<String>) = change { CustomCategories.assign(it, release, categories) }
    fun observe(release: Release) = change { CustomCategories.observe(it, release) }
    fun remove(itemId: String, categoryId: String) = change { current -> current.copy(
        items = current.items.map { if (it.id == itemId) it.copy(categoryIds = it.categoryIds - categoryId) else it }
            .filter { it.categoryIds.isNotEmpty() },
    ) }
}
