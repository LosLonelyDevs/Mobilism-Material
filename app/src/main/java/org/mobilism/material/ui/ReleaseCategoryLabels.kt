package org.mobilism.material.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import org.mobilism.material.data.CustomCategoryState
import org.mobilism.material.data.Release

internal val LocalForumCategoryNames = compositionLocalOf { emptyMap<Long, String>() }
internal val LocalReleaseAssignments = compositionLocalOf<CustomCategoryState?> { null }

@Composable
internal fun ReleaseCategoryLabels(release: Release, expanded: Boolean = false, color: Color = LocalContentColor.current) {
    val forum = LocalForumCategoryNames.current[release.categoryId]
    val assignments = LocalReleaseAssignments.current
    val personal = remember(release, assignments) {
        val section = release.section
        if (section == null || assignments == null) emptyList() else {
            val ids = assignments.memberships(release)
            assignments.categories(section).filter { it.id in ids }.map { it.name }
        }
    }
    val style = MaterialTheme.typography.labelSmall
    val lines = if (expanded) Int.MAX_VALUE else 1
    if (forum != null) Text(forum, style = style, color = color, maxLines = lines, overflow = TextOverflow.Ellipsis)
    if (personal.isNotEmpty()) Text("Custom: " + personal.joinToString(), style = style, color = color,
        maxLines = lines, overflow = TextOverflow.Ellipsis)
}
