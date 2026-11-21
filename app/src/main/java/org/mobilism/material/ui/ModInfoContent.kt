package org.mobilism.material.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

@Composable
internal fun ModInfoContent(html: String, baseUrl: String, onRelease: (Long) -> Unit) {
    val markdown = LocalForumFormatting.current.markdown
    val parts = remember(html, baseUrl, markdown) { ModInfoFields.parse(html, baseUrl, markdown) }
    if (parts.isEmpty()) {
        ReleaseBody(html, baseUrl, onRelease, cleanUp = true)
        return
    }
    for ((index, part) in parts.withIndex()) {
        if (part !is ModInfoPart.Body && parts.getOrNull(index - 1) is ModInfoPart.Body) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        when (part) {
            is ModInfoPart.Body -> ReleaseBody(part.html, baseUrl, onRelease, cleanUp = true, allowMarkdown = false)
            is ModInfoPart.Densities -> ModInfoValue("Screen densities", part.values.joinToString(" · ", postfix = " dpi"))
            is ModInfoPart.Languages -> if (part.expandable) ModInfoLanguages(part.values)
                else ModInfoValue("Languages", part.values.joinToString(", "))
        }
    }
}

@Composable
private fun ModInfoValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ModInfoLanguages(values: List<String>) {
    var expanded by rememberSaveable(values) { mutableStateOf(false) }
    Column {
        Surface(onClick = { expanded = !expanded }, color = Color.Transparent, shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Languages", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text("${values.size} locales", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null,
                    Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
            }
        }
        AnimatedVisibility(expanded) {
            Text(values.joinToString(", "), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
