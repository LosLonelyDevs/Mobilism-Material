package org.mobilism.material.ui

import android.net.Uri
import android.content.Context
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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONTokener

internal val LocalForumVerificationBrowser = staticCompositionLocalOf<(Context) -> WebView> { { WebView(it) } }

@Composable
internal fun ForumAccessVerification(url: String, onRead: (String) -> Boolean, onDismiss: () -> Unit) {
    val read by rememberUpdatedState(onRead)
    val dismiss by rememberUpdatedState(onDismiss)
    val target = remember(url) { Uri.parse(url) }
    val createBrowser = LocalForumVerificationBrowser.current
    var browser by remember { mutableStateOf<WebView?>(null) }
    var complete by remember { mutableStateOf(false) }
    fun accepts(uri: Uri): Boolean = uri.scheme == "https" && uri.host == "forum.mobilism.org" &&
        uri.userInfo == null && uri.port in listOf(-1, 443) && uri.path == target.path &&
        listOf("u", "t").all { target.getQueryParameter(it)?.let { expected -> uri.getQueryParameter(it) == expected } ?: true }
    fun readPage(view: WebView) {
        if (complete || view.url?.let { accepts(Uri.parse(it)) } != true) return
        view.evaluateJavascript("(function(){var html=document.documentElement.outerHTML;return html.length<=2097152?html:null})()") { raw ->
            if (browser !== view || complete) return@evaluateJavascript
            val html = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
            if (html != null && read(html)) {
                complete = true
                CookieManager.getInstance().flush()
                dismiss()
            }
        }
    }
    LaunchedEffect(browser) {
        val view = browser ?: return@LaunchedEffect
        while (isActive && !complete) { delay(1000); readPage(view) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                Text("Verify forum access", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary)
                Text("This page closes when the forum finishes checking.", Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium)
                Row {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { browser?.reload() }) { Text("Reload") }
                }
                AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context -> createBrowser(context).apply {
                    settings.userAgentString = org.mobilism.material.data.AppUserAgent.value(context)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            request.isForMainFrame && !accepts(request.url)
                        override fun onPageFinished(view: WebView, url: String) { readPage(view) }
                    }
                    browser = this
                    loadUrl(url)
                } }, onRelease = { view -> browser = null; view.stopLoading(); view.destroy() })
            }
        }
    }
}
