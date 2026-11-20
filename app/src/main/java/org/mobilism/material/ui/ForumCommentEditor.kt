package org.mobilism.material.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.mobilism.material.data.ApiFailure
import org.mobilism.material.data.CommentForm
import org.mobilism.material.data.FailureKind
import org.mobilism.material.data.UnconfirmedCommentChange
import org.mobilism.material.designsystem.AppTextButton
import org.mobilism.material.designsystem.ExpressiveButton
import org.mobilism.material.designsystem.LoadingState

@Composable
internal fun ForumCommentEditor(target: ForumCommentTarget, username: String, currentHtml: String,
    onClose: (refresh: Boolean) -> Unit) = key(target, username) {
    val gateway = LocalCommentGateway.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val edit = target.action == ForumCommentAction.Edit
    var form by remember { mutableStateOf<CommentForm?>(null) }
    val initialText = rememberSaveable { CommentEditBody.fromHtml(currentHtml) }
    var draft by rememberSaveable { mutableStateOf(initialText) }
    var password by remember { mutableStateOf("") }
    var attempt by rememberSaveable { mutableStateOf(false) }
    var openedBrowser by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var challenge by remember { mutableStateOf(false) }
    var loadKey by remember { mutableIntStateOf(0) }
    var review by remember { mutableStateOf<CommentEditBody?>(null) }
    var discard by remember { mutableStateOf(false) }
    val dismiss = { if (!busy) {
        if (edit && draft != initialText && !attempt) discard = true else onClose(attempt || openedBrowser)
    } }

    fun failed(failure: Exception) {
        if (failure is CancellationException) throw failure
        challenge = failure is ApiFailure && failure.kind == FailureKind.Challenge
        error = when {
            challenge -> "The forum requires a browser check before changing this comment."
            failure is ApiFailure && failure.httpStatus == 404 -> "The forum could not find this comment's form (404)."
            failure is ApiFailure -> failure.message
            else -> "Could not reach the forum. Check your connection and try again."
        }
    }

    LaunchedEffect(loadKey) {
        if (!edit && !attempt) {
            error = null
            challenge = false
            if (gateway == null) error = "Comment editing is unavailable."
            else try { form = gateway.load(target, username) }
            catch (failure: Exception) { failed(failure) }
        }
    }

    val loaded = form
    fun submitChange() {
        if (busy || attempt) return
        if (gateway == null) {
            error = "Comment editing is unavailable."
            return
        }
        busy = true
        error = null
        challenge = false
        scope.launch {
            try {
                val ready = when {
                    loaded == null -> gateway.load(target, username)
                    loaded.needsLogin -> gateway.login(loaded, password).also { password = "" }
                    else -> loaded
                }
                form = ready
                if (ready.needsLogin) return@launch
                val message = if (edit) {
                    val body = CommentEditBody.fromForum(checkNotNull(ready.source))
                    if (body.message.replace("\r\n", "\n").trim() != initialText && review != body) {
                        review = body
                        return@launch
                    }
                    body.quotes + draft
                } else {
                    if (loaded?.needsLogin == true) return@launch
                    ""
                }
                attempt = true
                gateway.submit(ready, message)
                onClose(true)
            } catch (failure: UnconfirmedCommentChange) {
                form = null
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                attempt = false
                if (loaded?.needsLogin != true || failure !is ApiFailure || failure.kind != FailureKind.Credentials) form = null
                review = null
                failed(failure)
            } finally { busy = false }
        }
    }
    AlertDialog(onDismissRequest = dismiss, modifier = Modifier.testTag("forum-comment-editor"),
        title = { AppHeading(target.action.title, HeadingLevel.Section, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (edit) {
                    OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth().testTag("edit-comment-text"),
                        readOnly = busy || attempt, label = { Text("Comment") }, minLines = 3, maxLines = 8,
                        shape = MaterialTheme.shapes.large)
                    review?.let {
                        Text("The forum copy differs from the comment you opened. Review it before replacing it with your changes.")
                        Text(it.message, style = MaterialTheme.typography.bodySmall)
                        AppTextButton("Use forum copy", { draft = it.message }, enabled = !busy && !attempt)
                    }
                } else if (initialText.isNotBlank()) Text(initialText, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (loaded?.needsLogin == true) {
                    Text("Sign in to the forum as $username to change this comment.")
                    OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), singleLine = true,
                        enabled = !busy, label = { Text("Forum password") }, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = MaterialTheme.shapes.large)
                } else if (!edit && loaded != null && !attempt) Text("Delete this comment? This cannot be undone.")
                if (busy || (!edit && loaded == null && error == null && !attempt)) LoadingState(
                    if (!busy) "Loading comment…" else if (loaded?.needsLogin == true) "Signing in…"
                    else if (edit) "Saving comment…" else "Deleting comment…")
                if (attempt && !busy) Text("The forum did not confirm the change. Check the comment before trying again.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (challenge) AppTextButton("Open in external browser", {
                    try {
                        val view = Intent(Intent.ACTION_VIEW, Uri.parse(target.url)).addCategory(Intent.CATEGORY_BROWSABLE)
                        val chooser = Intent.createChooser(view, "Open in external browser").putExtra(
                            Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, org.mobilism.material.MainActivity::class.java)))
                        context.startActivity(chooser)
                        openedBrowser = true
                    } catch (_: ActivityNotFoundException) { error = "No browser is available." }
                })
            }
        },
        confirmButton = {
            when {
                attempt && !busy -> AppTextButton("Check comment", { onClose(true) })
                !edit && loaded == null && error != null -> AppTextButton("Try again", { loadKey++ })
                edit || loaded != null -> ExpressiveButton(onClick = ::submitChange,
                    enabled = !busy && !attempt && (!edit || draft.isNotBlank() && draft != initialText) &&
                        (loaded?.needsLogin != true || password.isNotEmpty())) {
                    Text(when {
                        loaded?.needsLogin == true -> if (edit) "Sign in and update" else "Sign in"
                        edit -> "Update"
                        else -> "Delete"
                    })
                }
            }
        }, dismissButton = { AppTextButton("Cancel", dismiss, enabled = !busy) })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard changes?") },
        confirmButton = { AppTextButton("Discard", { onClose(openedBrowser) }) },
        dismissButton = { AppTextButton("Keep editing", { discard = false }) })
}
