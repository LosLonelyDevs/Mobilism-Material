// Adapted from LuliReddit's PostDetailScreen.kt, revision 9bd3386bd02d112aa4701ede508189ffd309918a.
// Copyright (c) 2026 Benny Barak. MIT license: core/designsystem/src/main/res/raw/lulireddit_mit.txt.
package org.mobilism.material.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
internal fun CommentCollapsePill(expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val label = if (expanded) "Collapse" else "Expand"
    Row(modifier.heightIn(min = 48.dp).clip(MaterialTheme.shapes.extraLarge)
        .background(cs.secondaryContainer)
        .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (expanded) Icons.Outlined.UnfoldLess else Icons.Outlined.UnfoldMore, null,
            Modifier.size(16.dp), tint = cs.onSecondaryContainer)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            color = cs.onSecondaryContainer)
    }
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
internal fun Modifier.commentRail(depth: Int = 1): Modifier {
    val cs = MaterialTheme.colorScheme
    val rails = listOf(cs.primary, cs.tertiary, cs.secondary)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return drawBehind {
        val width = 2.dp.toPx()
        drawRect(rails[(depth - 1).coerceAtLeast(0) % rails.size],
            Offset(if (rtl) size.width - width else 0f, 0f), Size(width, size.height))
    }
}
