package org.mobilism.material.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

/** M3 state layers remain native; one visible keyboard focus ring is shared by controls. */
fun Modifier.focusOutline(source: MutableInteractionSource): Modifier = composed {
    val focused by source.collectIsFocusedAsState()
    val color = MaterialTheme.colorScheme.primary
    drawWithContent {
        drawContent()
        if (focused) {
            val offset = 2.dp.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(-offset, -offset),
                size = Size(size.width + offset * 2, size.height + offset * 2),
                cornerRadius = CornerRadius(16.dp.toPx()),
                style = Stroke(2.dp.toPx()),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    compact: Boolean = false,
    minHeight: androidx.compose.ui.unit.Dp = if (compact) 48.dp else 56.dp,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        modifier = modifier.heightIn(min = minHeight).focusOutline(interaction),
        interactionSource = interaction,
        enabled = enabled,
        colors = colors,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun AppTextButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick, enabled = enabled, interactionSource = interaction,
        modifier = modifier.heightIn(min = 48.dp).focusOutline(interaction),
    ) { Text(label) }
}

@Composable
fun AppIconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick, interactionSource = interaction, enabled = enabled,
        modifier = modifier.size(48.dp).focusOutline(interaction),
    ) { Icon(icon, contentDescription = label) }
}

@Composable
fun AppIconToggleButton(icon: ImageVector, label: String, checked: Boolean, accent: Color,
    onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    FilledTonalIconToggleButton(
        checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
        interactionSource = interaction, modifier = modifier.size(48.dp).focusOutline(interaction),
        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = accent.copy(alpha = 0.18f), checkedContentColor = accent,
        ),
    ) { Icon(icon, contentDescription = label) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    border: BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && !LocalReducedMotion.current) 0.98f else 1f,
        MaterialTheme.motionScheme.fastSpatialSpec(), label = "card press",
    )
    Card(
        onClick = onClick, interactionSource = interaction,
        shape = MaterialTheme.shapes.large,
        colors = colors,
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp, pressedElevation = 1.dp,
            focusedElevation = 6.dp, hoveredElevation = 6.dp),
        modifier = modifier.focusOutline(interaction).graphicsLayer { scaleX = scale; scaleY = scale },
        content = content,
    )
}

@Composable
fun AppSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit,
) {
    Surface(modifier = modifier, shape = shape, color = color, contentColor = contentColorFor(color),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 3.dp, content = content)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingState(label: String, modifier: Modifier = Modifier, indicatorSize: Dp? = null) {
    Column(
        modifier = modifier.padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        if (!LocalReducedMotion.current) ContainedLoadingIndicator(
            modifier = if (indicatorSize != null) Modifier.size(indicatorSize) else Modifier,
        )
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
