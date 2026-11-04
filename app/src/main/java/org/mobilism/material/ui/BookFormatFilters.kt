package org.mobilism.material.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import org.mobilism.material.data.BookFormat

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BookFormatFilters(selected: Set<BookFormat>, onChange: (Set<BookFormat>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterSectionHeader("File types", "Match formats listed in release titles.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BookFormat.entries.forEach { format ->
                val checked = format in selected
                val background by animateColorAsState(
                    if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(), label = "format selection")
                FilterChip(selected = checked, onClick = { onChange(if (checked) selected - format else selected + format) },
                    label = { Text(format.label) }, shape = MaterialTheme.shapes.large, border = null,
                    colors = FilterChipDefaults.filterChipColors(containerColor = background, selectedContainerColor = background),
                    leadingIcon = if (checked) ({ Icon(Icons.Outlined.Check, null) }) else null)
            }
        }
    }
}
