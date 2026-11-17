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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.mobilism.material.data.AppNotice
import org.mobilism.material.remote.NoticeDismissalGate

@Composable
internal fun RemoteNoticeDialog(notice: AppNotice, onDismiss: () -> Unit, onUpdate: () -> Unit,
    onRefresh: () -> Unit, onRendered: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val onRenderedCurrent by rememberUpdatedState(onRendered)
    val key = notice.dismissalKey
    val gate = remember(key) { NoticeDismissalGate(android.os.SystemClock::elapsedRealtime) }
    var laidOut by remember(key) { mutableStateOf(false) }
    var logged by remember(key) { mutableStateOf(false) }
    var remaining by remember(key) { mutableIntStateOf(3) }
    var linkError by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key, laidOut, lifecycle) {
        if (!laidOut) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                withFrameNanos { }
                gate.rendered()
                if (!logged) { onRenderedCurrent(); logged = true }
                do { remaining = gate.remainingSeconds; delay(100) } while (remaining > 0)
                kotlinx.coroutines.awaitCancellation()
            } finally { gate.pause(); remaining = gate.remainingSeconds }
        }
    }
    val unlocked = remaining == 0
    val dismiss = { if (gate.canDismiss(notice.required)) onDismiss() }
    AlertDialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(dismissOnBackPress = unlocked && !notice.required,
            dismissOnClickOutside = unlocked && !notice.required),
        icon = { Icon(if (notice.mode == "notice") Icons.Outlined.Info else Icons.Outlined.SystemUpdate, null) },
        title = { Text(notice.title) },
        text = {
            Column(Modifier.onGloballyPositioned { if (it.size.width > 0 && it.size.height > 0) laidOut = true }
                .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val message = buildAnnotatedString {
                    append(notice.message)
                    notice.linkUrl?.let { url ->
                        append(" ")
                        withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary)),
                            linkInteractionListener = { if (gate.remainingSeconds == 0) linkError = runCatching { uriHandler.openUri(url) }.isFailure })) {
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
            TextButton(onClick = { if (notice.mode == "notice") dismiss() else if (gate.remainingSeconds == 0) onUpdate() }, enabled = unlocked) {
                val label = if (notice.mode == "notice") "Got it" else "Open updates"
                Text(if (unlocked) label else "$label ($remaining)")
            }
        },
        dismissButton = {
            if (notice.required) TextButton(onRefresh, enabled = unlocked) { Text("Refresh notice") }
            else if (notice.mode != "notice") TextButton(dismiss, enabled = unlocked) { Text("Later") }
        },
    )
}
