package org.mobilism.material.ui

import android.graphics.Bitmap
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.mobilism.material.designsystem.LoadingState

@Composable
internal fun ForumCommentEditor(target: ForumCommentTarget, username: String, onDismiss: () -> Unit) {
    var browser by remember(target) { mutableStateOf<WebView?>(null) }
    var loading by remember(target) { mutableStateOf(true) }
    var error by remember(target) { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("forum-comment-editor")) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                Text(target.action.title, Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                Text("Use the forum form below. If asked, sign in as $username.",
                    Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
                Text(if (target.action == ForumCommentAction.Delete)
                    "The forum asks for confirmation before deleting. Some comments cannot be deleted."
                    else "Save your changes in the form before closing.",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                    // Always reopen with GET. Reloading a submitted form could repeat a write.
                    TextButton(onClick = { error = null; browser?.loadUrl(target.url) }) { Text("Open comment form") }
                }
                error?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
                if (loading) LoadingState("Loading forum form…", Modifier.fillMaxWidth())
                AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context ->
                    WebView(context).apply {
                        settings.userAgentString = org.mobilism.material.data.AppUserAgent.value(context)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val blocked = request.isForMainFrame && !target.accepts(request.url.toString())
                                if (blocked) error = "This link is outside the comment form. Close to return to the app."
                                return blocked
                            }

                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                loading = true
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                loading = false
                                CookieManager.getInstance().flush()
                            }

                            override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                                if (request.isForMainFrame) {
                                    loading = false
                                    error = "The forum could not load. Close and check your comment before trying again."
                                }
                            }

                            override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) {
                                dontResend.sendToTarget()
                                error = "The form was not sent again. Close and check your comment before trying again."
                            }
                        }
                        browser = this
                        loadUrl(target.url)
                    }
                }, onRelease = { view -> browser = null; view.stopLoading(); view.destroy() })
            }
        }
    }
}
