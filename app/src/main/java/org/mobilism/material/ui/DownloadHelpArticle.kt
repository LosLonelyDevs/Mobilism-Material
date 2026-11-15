package org.mobilism.material.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import org.mobilism.material.designsystem.LoadingState

@Composable
internal fun DownloadHelpBlock(block: HelpBlock, id: String, baseUrl: String, onRelease: (Long) -> Unit,
    onImage: (String) -> Unit) {
    when (block) {
        is HelpBlock.Text -> ReleaseBody(block.html, baseUrl, onRelease,
            textStyle = MaterialTheme.typography.bodyMedium)
        is HelpBlock.Image -> SubcomposeAsyncImage(block.url, contentDescription = "Download guide screenshot",
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                .clickable(role = Role.Button, onClickLabel = "Enlarge screenshot") { onImage(block.url) },
            contentScale = ContentScale.FillWidth,
            loading = { LoadingState("Loading screenshot…") },
            error = { Text("Couldn't load screenshot. Tap to open it.", Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) })
        is HelpBlock.Section -> HelpGuideSection(block, id, baseUrl, onRelease, onImage)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HelpGuideSection(section: HelpBlock.Section, id: String, baseUrl: String,
    onRelease: (Long) -> Unit, onImage: (String) -> Unit) {
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f,
        MaterialTheme.motionScheme.fastSpatialSpec(), label = "help chevron")
    Column(Modifier.fillMaxWidth().testTag("help-section:$id")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.medium)
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .clickable(role = Role.Button) { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(section.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            Icon(Icons.Outlined.ExpandMore, null, Modifier.rotate(rotation), tint = MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(expanded,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                section.blocks.forEachIndexed { index, block ->
                    DownloadHelpBlock(block, "$id/$index", baseUrl, onRelease, onImage)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}
