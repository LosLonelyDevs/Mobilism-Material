package org.mobilism.material.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.mobilism.material.members.MemberBadge

@Composable
internal fun MemberName(name: String, label: String = name, modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium, color: Color = MaterialTheme.colorScheme.primary,
    maxLines: Int = 1, fontWeight: FontWeight? = null, textAlign: TextAlign? = null,
    badges: Set<MemberBadge>) {
    val text = buildAnnotatedString {
        val end = label.indexOf(name, ignoreCase = true).takeIf { it >= 0 && name.isNotBlank() }?.plus(name.length) ?: label.length
        append(label.substring(0, end))
        for (badge in badges) appendInlineContent(badge.key, " (${badge.label})")
        append(label.substring(end))
    }
    Text(text, modifier, style = style, color = color, maxLines = maxLines,
        overflow = TextOverflow.Ellipsis, fontWeight = fontWeight, textAlign = textAlign,
        inlineContent = badges.associate { badge ->
            badge.key to InlineTextContent(Placeholder(1.3.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
                val colors = MaterialTheme.colorScheme
                val (icon, tint) = when (badge) {
                    MemberBadge.Administrator -> Icons.Outlined.Shield to colors.tertiary
                    MemberBadge.Moderator -> Icons.Outlined.VerifiedUser to colors.secondary
                    MemberBadge.Developer -> Icons.Outlined.Code to colors.primary
                    MemberBadge.MajorReleaser -> Icons.Outlined.WorkspacePremium to colors.secondary
                }
                Icon(icon, null, Modifier.fillMaxSize().padding(start = 3.dp).testTag("${badge.key}-badge-$name"), tint = tint)
            }
        })
}
