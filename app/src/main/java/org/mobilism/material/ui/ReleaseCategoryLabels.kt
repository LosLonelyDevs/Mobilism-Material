package org.mobilism.material.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.mobilism.material.data.Alphabetical
import org.mobilism.material.data.ContentSection
import org.mobilism.material.data.CustomCategoryState
import org.mobilism.material.data.FeedFilters
import org.mobilism.material.data.Release

internal val LocalForumCategoryNames = compositionLocalOf { emptyMap<Long, String>() }
internal val LocalReleaseAssignments = compositionLocalOf<CustomCategoryState?> { null }
internal val LocalOpenReleaseCategory = compositionLocalOf<((ContentSection, FeedFilters) -> Unit)?> { null }

private data class NamedCategory(val name: String, val filters: FeedFilters)

@Composable
internal fun ReleaseCategoryLine(release: Release, expanded: Boolean = false, showLeadingWhenEmpty: Boolean = true,
    leading: @Composable () -> Unit) {
    val forum = LocalForumCategoryNames.current[release.categoryId]
    val assignments = LocalReleaseAssignments.current
    val categories = remember(release, forum, assignments) {
        val names = mutableMapOf<String, NamedCategory>()
        if (forum != null && release.categoryId != null) {
            names[forum.lowercase(Locale.ROOT)] = NamedCategory(forum, FeedFilters(categoryIds = setOfNotNull(release.categoryId)))
        }
        if (assignments != null) {
            val ids = assignments.memberships(release)
            for (category in assignments.categories) {
                if (category.id !in ids || category.section != release.section) continue
                val key = category.name.lowercase(Locale.ROOT)
                val matching = names[key] ?: NamedCategory(category.name, FeedFilters())
                names[key] = matching.copy(filters = matching.filters.copy(customCategoryIds = setOf(category.id)))
            }
        }
        Alphabetical.sorted(names.values) { it.name }
    }
    if (categories.isEmpty()) {
        if (showLeadingWhenEmpty) leading()
        return
    }
    val open = LocalOpenReleaseCategory.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { leading() }
        FlowRow(Modifier.weight(1.75f), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) {
            for (category in categories) {
                val scheme = MaterialTheme.colorScheme
                val (container, foreground) = when (Math.floorMod(category.name.lowercase(Locale.ROOT).hashCode(), 5)) {
                    0 -> scheme.primaryContainer to scheme.onPrimaryContainer
                    1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
                    2 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
                    3 -> scheme.primaryFixedDim to scheme.onPrimaryFixed
                    else -> scheme.tertiaryFixedDim to scheme.onTertiaryFixed
                }
                SuggestionChip(
                    onClick = { release.section?.let { open?.invoke(it, category.filters) } },
                    enabled = open != null && release.section != null,
                    label = { Text(category.name, style = MaterialTheme.typography.labelSmall,
                        maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis) },
                    shape = MaterialTheme.shapes.large,
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = container, labelColor = foreground,
                        disabledContainerColor = container, disabledLabelColor = foreground),
                    border = null,
                )
            }
        }
    }
}
