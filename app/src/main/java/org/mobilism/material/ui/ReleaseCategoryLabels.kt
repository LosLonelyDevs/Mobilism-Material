package org.mobilism.material.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
internal fun ReleaseMetadataFooter(release: Release, expanded: Boolean = false, spacing: Dp = 8.dp,
    singleRow: Boolean = false, metadata: @Composable () -> Unit) {
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
    val open = LocalOpenReleaseCategory.current
    val chips: @Composable () -> Unit = {
        for (category in categories) {
            val scheme = MaterialTheme.colorScheme
            val (container, foreground) = when (Math.floorMod(category.name.lowercase(Locale.ROOT).hashCode(), 5)) {
                0 -> scheme.primaryContainer to scheme.onPrimaryContainer
                1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
                2 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
                3 -> scheme.primaryFixedDim to scheme.onPrimaryFixed
                else -> scheme.tertiaryFixedDim to scheme.onTertiaryFixed
            }
            Surface(
                onClick = { release.section?.let { open?.invoke(it, category.filters) } },
                enabled = open != null && release.section != null,
                modifier = Modifier.semantics { role = Role.Button },
                shape = MaterialTheme.shapes.large,
                color = container, contentColor = foreground,
            ) {
                Box(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                    val style = MaterialTheme.typography.labelSmall
                    Text(category.name, style = style, fontSize = (style.fontSize.value - 1).sp,
                        maxLines = if (singleRow) 1 else if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    if (singleRow) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val metadataWidth = if (categories.isEmpty()) maxWidth else maxWidth * 0.66f
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing)) {
                Row(Modifier.widthIn(max = metadataWidth).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing), verticalAlignment = Alignment.CenterVertically) { metadata() }
                if (categories.isNotEmpty()) Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) { chips() }
                }
            }
        }
    } else {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            metadata()
            if (categories.isNotEmpty()) FlowRow(Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) { chips() }
        }
    }
}
