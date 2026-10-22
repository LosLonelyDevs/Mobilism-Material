package org.mobilism.material.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.mobilism.material.data.ContentSection
import org.mobilism.material.designsystem.LocalAppTheme

internal data class SectionStyle(val accent: Color, val onAccent: Color, val icon: ImageVector)

@Composable
internal fun ContentSection.style(): SectionStyle = when (this) {
    ContentSection.Apps -> SectionStyle(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, Icons.Outlined.Apps)
    ContentSection.Games -> SectionStyle(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary, Icons.Outlined.SportsEsports)
    ContentSection.Books -> SectionStyle(LocalAppTheme.current.reading, LocalAppTheme.current.onReading, Icons.Outlined.AutoStories)
}
