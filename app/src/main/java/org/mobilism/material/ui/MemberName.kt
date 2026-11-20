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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.em
import org.mobilism.material.designsystem.LocalAppTheme
import org.mobilism.material.members.MemberBadge

@Composable
internal fun ProfileTouchBounds(content: @Composable () -> Unit) {
    val config = LocalViewConfiguration.current
    val exact = remember(config) {
        object : ViewConfiguration by config {
            override val minimumTouchTargetSize = DpSize.Zero
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides exact, content = content)
}

@Composable
internal fun MemberName(name: String, label: String = name, modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium, color: Color = MaterialTheme.colorScheme.primary,
    maxLines: Int = 1, fontWeight: FontWeight? = null, textAlign: TextAlign? = null,
    badges: Set<MemberBadge>, prefix: String = "", suffix: String = "", onProfile: (() -> Unit)? = null) {
    val text = buildAnnotatedString {
        append(prefix)
        if (onProfile == null) append(label)
        else withLink(LinkAnnotation.Clickable("profile", styles = TextLinkStyles(SpanStyle(color = color)),
            linkInteractionListener = { onProfile() })) { append(label) }
        for (badge in badges) appendInlineContent(badge.key, " (${badge.label})")
        append(suffix)
    }
    ProfileTouchBounds {
        Text(text, modifier, style = style, color = color, maxLines = maxLines,
            overflow = TextOverflow.Ellipsis, fontWeight = fontWeight, textAlign = textAlign,
            inlineContent = badges.associate { badge ->
                badge.key to InlineTextContent(Placeholder(1.3.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
                    val colors = MaterialTheme.colorScheme
                    val (icon, tint) = when (badge) {
                        MemberBadge.Administrator -> Icons.Outlined.Shield to colors.tertiary
                        MemberBadge.Moderator -> Icons.Outlined.VerifiedUser to colors.secondary
                        MemberBadge.Developer -> {
                            val accent = LocalAppTheme.current.success
                            Icons.Outlined.Code to if (accent == color) colors.error else accent
                        }
                        MemberBadge.MajorReleaser -> Icons.Outlined.WorkspacePremium to colors.secondary
                    }
                    Icon(icon, null, Modifier.fillMaxSize().padding(start = 3.dp).testTag("${badge.key}-badge-$name"), tint = tint)
                }
            })
    }
}
