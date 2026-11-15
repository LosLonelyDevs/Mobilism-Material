package org.mobilism.material.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.mobilism.material.data.DownloadHelp

@Composable
internal fun DownloadNotes(html: String, baseUrl: String, onRelease: (Long) -> Unit,
    onBeforeHelp: () -> Unit = {}) {
    val openHelp = LocalDownloadHelpHandler.current
    val parts = remember(html, baseUrl) { DownloadNotesParser.parse(html, baseUrl) }
    Column {
        parts.forEach { part ->
            if (part.help && openHelp != null) DownloadHelpAction {
                onBeforeHelp()
                openHelp()
            } else ReleaseBody(part.html, baseUrl, onRelease)
        }
    }
}

@Composable
internal fun DownloadHelpAction(onOpen: () -> Unit) {
    var held by remember { mutableStateOf(false) }
    if (held) ForumLinkActions(DownloadHelp.URL, onDismiss = { held = false })
    Text(buildAnnotatedString {
        append("Trouble downloading? Read ")
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline)) { append("This") }
        append(".")
    }, modifier = Modifier.fillMaxWidth().testTag("download-help-action")
        .combinedClickable(role = Role.Button, onClickLabel = "Open download help",
            onLongClickLabel = "Show link options", onClick = onOpen, onLongClick = { held = true })
        .padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface)
}
