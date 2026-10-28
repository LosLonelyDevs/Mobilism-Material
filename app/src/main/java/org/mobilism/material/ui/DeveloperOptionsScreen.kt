package org.mobilism.material.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import org.mobilism.material.designsystem.AppSurface
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.settings.DeveloperPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperOptionsScreen(state: DeveloperPreferences, onVerboseLogging: (Boolean) -> Unit,
    onDisable: () -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { AppHeading("Developer options", HeadingLevel.Page) }, navigationIcon = {
            AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
        })
    }) { insets ->
        LazyColumn(Modifier.padding(insets), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                DeveloperToggle("Developer mode", "Turn off to hide these options",
                    state.enabled, { if (!it) onDisable() })
            }
            item {
                DeveloperToggle("Verbose logging", "Record diagnostic details",
                    state.verboseLogging, onVerboseLogging)
            }
        }
    }
}

@Composable
private fun DeveloperToggle(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    AppSurface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppHeading(title, HeadingLevel.Section, style = MaterialTheme.typography.titleLarge)
                SettingsSubtitle(description)
            }
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}
