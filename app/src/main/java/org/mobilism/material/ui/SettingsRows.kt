package org.mobilism.material.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsSectionLabel(title: String) {
    Text(title, Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun SettingsTitle(title: String, modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleSmall) {
    Text(title, modifier.semantics { heading() }, style = style, color = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun SettingsNavigationRow(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    SettingsRow(title, description, icon, Modifier.clickable(role = Role.Button, onClick = onClick))
}

@Composable
internal fun SettingsToggleRow(title: String, description: String, icon: ImageVector,
    checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SettingsRow(title, description, icon, Modifier.toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)) {
        Switch(checked, onCheckedChange = null, thumbContent = if (checked) {
            { Icon(Icons.Outlined.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
        } else null)
    }
}

@Composable
internal fun SettingsRow(title: String, description: String, icon: ImageVector, modifier: Modifier,
    trailing: @Composable () -> Unit = { Icon(Icons.Outlined.ChevronRight, null) }) {
    Row(Modifier.clip(MaterialTheme.shapes.large).then(modifier).fillMaxWidth().heightIn(min = 72.dp)
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SettingsTitle(title)
            SettingsSubtitle(description)
        }
        trailing()
    }
}
