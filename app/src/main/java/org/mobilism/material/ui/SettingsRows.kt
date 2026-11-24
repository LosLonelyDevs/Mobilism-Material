package org.mobilism.material.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.mobilism.material.R

@Composable
internal fun SettingsList(modifier: Modifier = Modifier, state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit) {
    LazyColumn(modifier, state = state, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

@Composable
internal fun SettingsSectionLabel(title: String, modifier: Modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)) {
    Text(title, modifier.semantics { heading() },
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsExpandableSection(title: String, initiallyExpanded: Boolean = true, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    val stateLabel = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    val motion = MaterialTheme.motionScheme
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, motion.fastSpatialSpec())
    Column {
        Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button) { expanded = !expanded }
            .semantics {
                heading()
                stateDescription = stateLabel
            }
            .heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            SettingsSectionLabel(title, Modifier.weight(1f))
            Icon(Icons.Outlined.ExpandMore, null, Modifier.rotate(rotation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(expanded, enter = expandVertically(motion.defaultSpatialSpec()),
            exit = shrinkVertically(motion.defaultSpatialSpec())) { content() }
    }
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
internal fun SettingsToggleRow(title: String, description: String?, icon: ImageVector?,
    checked: Boolean, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit) {
    SettingsRow(title, description, icon, modifier.toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)) {
        Switch(checked, onCheckedChange = null, thumbContent = if (checked) {
            { Icon(Icons.Outlined.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
        } else null)
    }
}

@Composable
internal fun SettingsRow(title: String, description: String?, icon: ImageVector?, modifier: Modifier,
    trailing: @Composable () -> Unit = { Icon(Icons.Outlined.ChevronRight, null) }) {
    Row(Modifier.clip(MaterialTheme.shapes.large).then(modifier).fillMaxWidth().heightIn(min = 72.dp)
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (icon != null) Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SettingsTitle(title)
            description?.let { SettingsSubtitle(it) }
        }
        trailing()
    }
}
