package org.mobilism.material.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.mobilism.material.designsystem.LocalAppTheme

@Composable
internal fun ReleaseViewCount(views: Long, modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.labelSmall, color: Color = LocalAppTheme.current.featured,
    numberOnly: Boolean = false) {
    // Missing API counts and forum-only releases both use zero. Do not present it as a measured count.
    if (views <= 0) return
    val label = "$views ${if (views == 1L) "view" else "views"}"
    Row(modifier.clearAndSetSemantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Visibility, null, Modifier.size(14.dp), tint = color)
        Text(if (numberOnly) views.toString() else label, style = textStyle, color = color)
    }
}
