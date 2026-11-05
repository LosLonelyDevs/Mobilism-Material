package org.mobilism.material.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
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

@Composable
internal fun MemberName(name: String, label: String = name, modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium, color: Color = MaterialTheme.colorScheme.primary,
    maxLines: Int = 1, fontWeight: FontWeight? = null, textAlign: TextAlign? = null,
    administrator: Boolean = memberIdentity(name)?.administrator == true,
    majorAndroidReleaser: Boolean = memberIdentity(name)?.majorAndroidReleaser == true) {
    val text = buildAnnotatedString {
        val end = label.indexOf(name, ignoreCase = true).takeIf { it >= 0 && name.isNotBlank() }?.plus(name.length) ?: label.length
        append(label.substring(0, end))
        if (administrator) appendInlineContent("admin", " (Administrator)")
        if (majorAndroidReleaser) appendInlineContent("major-android-releaser", " (Major Android Releaser)")
        append(label.substring(end))
    }
    Text(text, modifier, style = style, color = color, maxLines = maxLines,
        overflow = TextOverflow.Ellipsis, fontWeight = fontWeight, textAlign = textAlign,
        inlineContent = mapOf("admin" to InlineTextContent(Placeholder(1.3.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
            Icon(Icons.Outlined.Shield, null, Modifier.fillMaxSize().padding(start = 3.dp).testTag("admin-badge-$name"),
                tint = MaterialTheme.colorScheme.tertiary)
        }, "major-android-releaser" to InlineTextContent(Placeholder(1.3.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
            Icon(Icons.Outlined.WorkspacePremium, null,
                Modifier.fillMaxSize().padding(start = 3.dp).testTag("major-android-releaser-badge-$name"),
                tint = MaterialTheme.colorScheme.secondary)
        }))
}
