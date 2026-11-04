package org.mobilism.material.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mobilism.material.data.MessageFolder
import org.mobilism.material.data.PrivateMessage
import org.mobilism.material.designsystem.AppTextButton
import org.mobilism.material.designsystem.ExpressiveCard

@Composable
internal fun MessageThreadCard(thread: MessageThread, folder: MessageFolder, marking: Set<Long>,
    onOpen: (PrivateMessage) -> Unit, onRead: (Long) -> Unit) {
    val message = thread.latest
    val incoming = folder == MessageFolder.Inbox
    val unread = incoming && thread.unreadIds.isNotEmpty()
    val person = if (incoming) message.from else message.to
    val colors = MaterialTheme.colorScheme
    ExpressiveCard({ onOpen(message) }, Modifier.fillMaxWidth().testTag("message-thread-${thread.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                MessageAvatar(person, message.avatar.takeIf { person.equals(message.from, ignoreCase = true) }, Modifier.size(32.dp))
                Text(if (incoming) person else "To $person", Modifier.weight(1f),
                    color = colors.primary, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (unread) Text("Unread", color = colors.primary, style = MaterialTheme.typography.labelMedium)
            }
            Text(message.subject.ifBlank { "No subject" }, color = colors.secondary,
                style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold)
            if (message.preview.isNotBlank()) Text(message.preview, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (message.publishedAt > 0) Icon(Icons.Outlined.Schedule, null, Modifier.size(14.dp), tint = colors.tertiary)
                MessageTimestamp(message.publishedAt, Modifier.testTag("message-thread-time-${thread.id}"), color = colors.tertiary)
            }
            if (unread) AppTextButton("Mark as read", { thread.unreadIds.forEach(onRead) },
                enabled = thread.unreadIds.none { it in marking })
        }
    }
}
