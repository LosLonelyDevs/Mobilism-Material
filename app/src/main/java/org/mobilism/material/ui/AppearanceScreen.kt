package org.mobilism.material.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.mobilism.material.designsystem.AppFont
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.ExpressiveCard
import org.mobilism.material.designsystem.LocalAppTheme
import org.mobilism.material.settings.AppearanceState
import org.mobilism.material.settings.AppearanceStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(state: AppearanceState, store: AppearanceStore, onBack: () -> Unit) {
    val activeTheme = LocalAppTheme.current
    Scaffold(topBar = {
        TopAppBar(
            title = { AppHeading("Appearance", HeadingLevel.Page) },
            navigationIcon = { AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack) },
        )
    }) { insets ->
        LazyColumn(
            modifier = Modifier.padding(insets),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { AppHeading("Theme", HeadingLevel.Section, style = MaterialTheme.typography.headlineSmall) }
            item {
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.themes.forEach { theme ->
                        val active = theme.id == state.theme.id
                        ExpressiveCard(
                            onClick = { store.selectTheme(theme) },
                            modifier = Modifier.fillMaxWidth().semantics { selected = active; role = Role.RadioButton },
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = active, onClick = null)
                                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppHeading(theme.name, HeadingLevel.Subsection, style = MaterialTheme.typography.titleMedium)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(theme.colors.primary, theme.colors.secondary, theme.colors.tertiary, theme.reading,
                                            theme.featured, theme.personalization, theme.success).forEach { color ->
                                            Box(Modifier.size(24.dp).clip(MaterialTheme.shapes.small).background(color))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { AppHeading("Font", HeadingLevel.Section, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp)) }
            item {
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppFont.entries.forEach { font ->
                        ExpressiveCard(
                            onClick = { store.selectFont(font) },
                            modifier = Modifier.fillMaxWidth().semantics { selected = state.font == font; role = Role.RadioButton },
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = state.font == font, onClick = null)
                                Text(if (font == AppFont.Geist) "Geist" else "System sans serif", Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                }
            }
            item { AppHeading("Motion", HeadingLevel.Section, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp)) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Reduce motion", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Use immediate transitions. Your system's disabled animation setting is also respected.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.reduceMotion, onCheckedChange = store::reduceMotion,
                        modifier = Modifier.semantics { contentDescription = "Reduce motion" },
                    )
                }
            }
        }
    }
}
