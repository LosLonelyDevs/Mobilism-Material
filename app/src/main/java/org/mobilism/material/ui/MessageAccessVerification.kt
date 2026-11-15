package org.mobilism.material.ui

import android.net.Uri
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.mobilism.material.data.VerificationSite

@Composable
internal fun MessageAccessVerification(onDismiss: () -> Unit, onContinue: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                Text("Verify message access", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                Text("Complete the browser check, then tap Continue. This only checks access; it won't send your reply.",
                    Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
                Row {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { CookieManager.getInstance().flush(); onContinue() },
                        modifier = Modifier.testTag("continue-message-verification")) { Text("Continue") }
                }
                AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context -> WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            request.isForMainFrame && (request.method != "GET" || !acceptsMessageVerificationUrl(request.url))
                        override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) { dontResend.sendToTarget() }
                    }
                    // The app API has its own clearance. No account token or message text enters this browser.
                    loadUrl(VerificationSite.App.url)
                } }, onRelease = { view -> view.stopLoading(); view.destroy() })
            }
        }
    }
}

internal fun acceptsMessageVerificationUrl(uri: Uri): Boolean =
    acceptsConnectionVerificationUrl(uri, VerificationSite.App)
