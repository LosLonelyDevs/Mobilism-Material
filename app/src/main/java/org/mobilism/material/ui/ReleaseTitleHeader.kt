package org.mobilism.material.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import org.mobilism.material.designsystem.LocalAppTheme
import org.mobilism.material.data.ReleaseTitles
import org.mobilism.material.data.ContentSection

@Composable
fun ReleaseTitleHeader(title: String, section: ContentSection? = null) {
    val parsed = remember(title, section) { ReleaseTitles.parse(title, section) }
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppHeading(parsed.name, HeadingLevel.Page, style = MaterialTheme.typography.headlineSmall)
        parsed.bookAuthor?.let { Text("by $it", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        parsed.series?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (parsed.version != null || parsed.tags.isNotEmpty()) FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            parsed.version?.let { MetadataBadge("v$it", MaterialTheme.colorScheme.tertiary) }
            parsed.build?.let { MetadataBadge("Build $it", theme.reading) }
            parsed.tags.forEach { MetadataBadge(it, theme.featured) }
        }
    }
}

@Composable
private fun MetadataBadge(label: String, accent: Color) {
    Surface(shape = MaterialTheme.shapes.large, color = lerp(MaterialTheme.colorScheme.surface, accent, 0.15f)) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = accent, style = MaterialTheme.typography.labelLarge)
    }
}
