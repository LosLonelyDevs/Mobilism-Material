package org.mobilism.material.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.mobilism.material.designsystem.ExpressiveCard
import org.mobilism.material.settings.LandingPage

@Composable
internal fun LandingPageSetting(selected: LandingPage, onSelect: (LandingPage) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExpressiveCard({ expanded = true }, Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Outlined.Home, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppHeading("Landing page", HeadingLevel.Section, style = MaterialTheme.typography.titleLarge)
                SettingsSubtitle(selected.label)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
    if (expanded) AlertDialog(
        onDismissRequest = { expanded = false },
        title = { Text("Default landing page") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsSubtitle("Choose where the app opens.")
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).selectableGroup()) {
                    LandingPage.options.forEach { page ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(page == selected, role = Role.RadioButton, onClick = {
                                onSelect(page)
                                expanded = false
                            }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RadioButton(page == selected, onClick = null)
                            Text(page.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ expanded = false }) { Text("Close") } },
        shape = MaterialTheme.shapes.extraLarge,
    )
}
