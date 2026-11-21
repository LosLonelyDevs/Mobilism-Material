package org.mobilism.material.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

@Composable
internal fun CommentSortMenu(sort: CommentSort, total: Long, onSelect: (CommentSortField) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val arrow = if (sort.newestFirst) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward
    val direction = if (sort.newestFirst) "Newest first" else "Oldest first"
    Row {
        Box(Modifier.alignByBaseline()) {
            TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = 8.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.testTag("comment-sort").semantics { stateDescription = direction }) {
                Text(sort.field.name, style = MaterialTheme.typography.labelSmall)
                Icon(arrow, null, Modifier.size(16.dp))
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }, shape = MaterialTheme.shapes.extraLarge) {
                CommentSortField.entries.forEach { field ->
                    DropdownMenuItem(text = { Text(field.name) }, onClick = {
                        expanded = false
                        onSelect(field)
                    }, trailingIcon = {
                        if (field == sort.field) Icon(arrow, direction, Modifier.size(18.dp))
                    })
                }
            }
        }
        Text(if (total == 1L) "1 comment" else "$total comments", Modifier.alignByBaseline(), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
