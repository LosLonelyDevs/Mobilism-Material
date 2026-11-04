package org.mobilism.material.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mobilism.material.data.PrivateMessage
import org.mobilism.material.designsystem.AppTextButton
import org.mobilism.material.designsystem.LocalReducedMotion
import org.mobilism.material.designsystem.focusOutline
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MessageBubble(message: PrivateMessage, username: String, onRead: () -> Unit,
    marking: Boolean, onRelease: (Long) -> Unit) {
    val outgoing = message.from.equals(username, ignoreCase = true)
    var expanded by rememberSaveable(message.id) { mutableStateOf(true) }
    val toggle = { expanded = !expanded }
    val bodyState = rememberSaveableStateHolder()
    val interaction = remember { MutableInteractionSource() }
    val reduced = LocalReducedMotion.current
    val colors = MaterialTheme.colorScheme
    val fill = if (outgoing) colors.primaryContainer else colors.tertiaryContainer
    val foreground = if (outgoing) colors.onPrimaryContainer else colors.onTertiaryContainer
    val accent = if (outgoing) colors.primary else colors.tertiary
    val onAccent = if (outgoing) colors.onPrimary else colors.onTertiary
    Box(Modifier.fillMaxWidth(), contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(Modifier.widthIn(max = 620.dp).fillMaxWidth(0.9f).testTag("message-${message.id}"),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp,
                bottomStart = if (outgoing) 20.dp else 4.dp, bottomEnd = if (outgoing) 4.dp else 20.dp),
            color = fill, contentColor = foreground) {
            Column {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("message-toggle-${message.id}")
                    .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                    .focusOutline(interaction).clickable(interactionSource = interaction, indication = ripple(), role = Role.Button,
                        onClickLabel = if (expanded) "Collapse message" else "Expand message", onClick = toggle)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MessageAvatar(message.from, message.avatar, Modifier.size(28.dp), color = accent, contentColor = onAccent)
                    Column(Modifier.weight(1f)) {
                        UsernameLink(message.from, label = if (outgoing) "You" else message.from,
                            style = MaterialTheme.typography.titleSmall, color = accent, fontWeight = FontWeight.SemiBold)
                        if (!expanded) MessageTimestamp(message.publishedAt, Modifier.testTag("message-time-${message.id}"))
                    }
                    if (!expanded) CommentCollapseButton(false, toggle, label = "Expand message")
                }
                AnimatedVisibility(expanded,
                    enter = if (reduced) EnterTransition.None else expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                    exit = if (reduced) ExitTransition.None else shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec())) {
                    bodyState.SaveableStateProvider("body") {
                        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp).testTag("message-body-${message.id}"),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.testTag("message-content-${message.id}")) {
                                ReleaseBody(message.contentHtml, "https://forum.mobilism.org/", onRelease,
                                    contentColor = foreground, linkColor = foreground)
                            }
                            if (!message.read && message.to.equals(username, ignoreCase = true)) AppTextButton("Mark as read",
                                onRead, enabled = !marking)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    MessageTimestamp(message.publishedAt, Modifier.testTag("message-time-${message.id}"))
                                }
                                CommentCollapseButton(true, toggle, label = "Collapse message")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MessageTimestamp(timestamp: Long, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    if (timestamp <= 0) return
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val use24Hour = android.text.format.DateFormat.is24HourFormat(context)
    val timeZone = TimeZone.getDefault().id
    val label = remember(timestamp, locale, use24Hour, timeZone) {
        val date = Date(timestamp * 1000)
        val day = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(date)
        val time = android.text.format.DateFormat.getTimeFormat(context).format(date)
        "$day · $time"
    }
    Text(label, modifier, color = color, style = MaterialTheme.typography.labelSmall)
}
