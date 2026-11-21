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
import androidx.compose.ui.platform.LocalDensity
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
private val chipVerticalPadding = 6.dp

@Composable
internal fun ReleaseMetadataFooter(release: Release, expanded: Boolean = false, spacing: Dp = 8.dp,
    singleRow: Boolean = false, showEmptyRow: Boolean = true, listCard: Boolean = false, metadata: @Composable () -> Unit) {
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
    if (!showEmptyRow && categories.isEmpty()) return
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
                Box(Modifier.padding(horizontal = 14.dp, vertical = chipVerticalPadding), contentAlignment = Alignment.Center) {
                    val style = MaterialTheme.typography.labelSmall
                    Text(category.name, style = style, fontSize = (style.fontSize.value - 1).sp,
                        maxLines = if (singleRow) 1 else if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    if (singleRow) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val categoryWidth = maxWidth * when {
                categories.size == 1 -> 0.38f
                listCard -> 0.42f
                else -> 0.34f
            }
            SingleLineMetadataRow(spacing, listCard, metadata) {
                if (categories.isNotEmpty()) Row(Modifier.widthIn(max = categoryWidth).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) { chips() }
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

@Composable
internal fun SingleLineMetadataRow(spacing: Dp, listCard: Boolean, metadata: @Composable () -> Unit,
    trailing: @Composable () -> Unit) {
    val chipInset = with(LocalDensity.current) { chipVerticalPadding.roundToPx() }
    val metadataAlignment = if (listCard) Alignment.Vertical { size, space ->
        // Align with the painted chip edge inside its larger touch target.
        (Alignment.CenterVertically.align(size, space) + chipInset).coerceAtMost(space - size)
    } else Alignment.CenterVertically
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (listCard) 4.dp else spacing)) {
        Row(Modifier.weight(1f).align(metadataAlignment).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing), verticalAlignment = Alignment.CenterVertically) { metadata() }
        trailing()
    }
}
