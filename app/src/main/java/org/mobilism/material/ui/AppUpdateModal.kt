package org.mobilism.material.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun AppUpdateModal(title: String, version: String, icon: ImageVector, confirmText: String,
    confirmIcon: ImageVector, onConfirm: () -> Unit, onDismiss: () -> Unit, dismissText: String? = null,
    content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 620.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer,
                            tonalElevation = 6.dp, shadowElevation = 3.dp) {
                            Icon(icon, null, Modifier.padding(10.dp).size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(title, style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text("v$version", Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (dismissText != null) TextButton(onDismiss) { Text(dismissText) }
                        Button(onConfirm, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                            Icon(confirmIcon, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(confirmText)
                        }
                    }
                }
            }
        }
    }
}
