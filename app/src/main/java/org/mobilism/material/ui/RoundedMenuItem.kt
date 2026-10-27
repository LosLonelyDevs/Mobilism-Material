package org.mobilism.material.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import org.mobilism.material.data.DebridState

internal fun DebridState.supportLabel(url: String): String? = supporting(url)
    .map { it.provider.label }.takeIf { it.isNotEmpty() }?.joinToString(" · ")

@Composable
internal fun RoundedMenuItem(
    label: String, onClick: () -> Unit, accent: Color,
    icon: ImageVector? = null, supportLabel: String? = null,
    singleLine: Boolean = false,
) {
    DropdownMenuItem(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(lerp(MaterialTheme.colorScheme.surfaceContainerHighest, accent, 0.12f))
            .heightIn(min = 56.dp),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall,
                    maxLines = if (singleLine) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
                supportLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (singleLine) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis) }
            }
        },
        leadingIcon = icon?.let { { Icon(it, null, tint = accent) } },
        onClick = onClick,
    )
}
