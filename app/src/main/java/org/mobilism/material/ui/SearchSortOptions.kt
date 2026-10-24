package org.mobilism.material.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.mobilism.material.data.SearchSort

@Composable
internal fun SearchSortOptions(selected: SearchSort, onSelect: (SearchSort) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppHeading("Sort search results", HeadingLevel.Section, style = MaterialTheme.typography.titleLarge)
        SearchSort.entries.forEach { sort ->
            Surface(shape = MaterialTheme.shapes.large, color = if (sort == selected)
                MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().selectable(sort == selected, role = Role.RadioButton,
                    onClick = { onSelect(sort) }).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RadioButton(selected = sort == selected, onClick = null)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(sort.label, style = MaterialTheme.typography.titleMedium)
                        Text(when (sort) {
                            SearchSort.Newest -> "Post date, newest first"
                            SearchSort.Oldest -> "Post date, oldest first"
                            SearchSort.Activity -> "Most recent reply or post"
                        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text("Sorting by post date may take longer for broad searches.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
