package org.mobilism.material.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mobilism.material.updates.AppChangelog

@Composable
internal fun AppChangelogDialog(entry: AppChangelog, onDismiss: () -> Unit) {
    AppUpdateModal("What's new", entry.version, Icons.Outlined.NewReleases, "Got it", Icons.Outlined.Check,
        onConfirm = onDismiss, onDismiss = onDismiss) {
        entry.bullets.forEach { bullet ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.padding(top = 8.dp).size(7.dp), shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary) { }
                Text(bullet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
