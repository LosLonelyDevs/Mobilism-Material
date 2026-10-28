// Adapted from LuliReddit's PostDetailScreen.kt, revision 9bd3386bd02d112aa4701ede508189ffd309918a.
// Copyright (c) 2026 Benny Barak. MIT license: core/designsystem/src/main/res/raw/lulireddit_mit.txt.
package org.mobilism.material.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mobilism.material.designsystem.AppIconButton

@Composable
internal fun CommentCollapseButton(expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppIconButton(
        icon = if (expanded) Icons.Outlined.UnfoldLess else Icons.Outlined.UnfoldMore,
        label = if (expanded) "Collapse comment" else "Expand comment",
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
internal fun CommentAuthorDot(name: String) {
    val cs = MaterialTheme.colorScheme
    val palette = listOf(cs.primaryContainer to cs.onPrimaryContainer, cs.secondaryContainer to cs.onSecondaryContainer,
        cs.tertiaryContainer to cs.onTertiaryContainer)
    var hash = 0
    for (ch in name) hash = (hash * 31 + ch.code) and 0x7fffffff
    val (fill, text) = palette[hash % palette.size]
    Box(Modifier.size(28.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
        Text(name.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, color = text)
    }
}

@Composable
internal fun Modifier.commentRail(depth: Int = 1, railWidth: Dp = 2.dp): Modifier {
    val cs = MaterialTheme.colorScheme
    val rails = listOf(cs.primary, cs.tertiary, cs.secondary)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return drawBehind {
        val width = railWidth.toPx()
        drawRect(rails[(depth - 1).coerceAtLeast(0) % rails.size],
            Offset(if (rtl) size.width - width else 0f, 0f), Size(width, size.height))
    }
}

@Composable
internal fun CommentThreadRow(depth: Int, content: @Composable () -> Unit) {
    val indent = (depth.coerceIn(0, 6) * 8).dp
    Box(Modifier.fillMaxWidth().padding(start = indent)
        .then(if (depth > 0) Modifier.commentRail(depth, 4.dp).padding(start = 4.dp) else Modifier)) {
        content()
    }
}
