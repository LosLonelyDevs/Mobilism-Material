package org.mobilism.material.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.mobilism.material.data.AppNotice

@Composable
internal fun RemoteNoticeDialog(notice: AppNotice, onDismiss: () -> Unit, onUpdate: () -> Unit,
    onRefresh: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var linkError by remember(notice.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!notice.required) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !notice.required, dismissOnClickOutside = !notice.required),
        icon = { Icon(if (notice.mode == "notice") Icons.Outlined.Info else Icons.Outlined.SystemUpdate, null) },
        title = { Text(notice.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val message = buildAnnotatedString {
                    append(notice.message)
                    notice.linkUrl?.let { url ->
                        append(" ")
                        withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary)),
                            linkInteractionListener = { linkError = runCatching { uriHandler.openUri(url) }.isFailure })) {
                            append(notice.linkText)
                        }
                    }
                }
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (notice.required) Text("Update the app to continue.", style = MaterialTheme.typography.bodyMedium)
                if (linkError) Text("No browser is available.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(if (notice.mode == "notice") onDismiss else onUpdate) {
                Text(if (notice.mode == "notice") "Got it" else "Open updates")
            }
        },
        dismissButton = {
            if (notice.required) TextButton(onRefresh) { Text("Refresh notice") }
            else if (notice.mode != "notice") TextButton(onDismiss) { Text("Later") }
        },
    )
}
