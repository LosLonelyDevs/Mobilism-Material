package org.mobilism.material.ui

import android.net.Uri
import android.os.Message
import android.view.View
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONObject
import org.json.JSONTokener
import org.mobilism.material.data.VerificationSite

@Composable
internal fun ConnectionVerification(site: VerificationSite, onDismiss: () -> Unit, onContinue: () -> Unit) {
    val context = LocalContext.current
    var browser by remember(site) { mutableStateOf<WebView?>(null) }
    var complete by remember(site) { mutableStateOf(false) }
    fun readPage(view: WebView) {
        if (site != VerificationSite.App || complete || view.url?.toUri()?.let {
                acceptsConnectionVerificationUrl(it, site) && it.path == site.url.toUri().path
            } != true) return
        view.evaluateJavascript("(function(){var text=document.body&&document.body.innerText;return text&&text.length<=2097152?text:null})()") { raw ->
            if (browser !== view) return@evaluateJavascript
            val response = runCatching {
                val text = JSONTokener(raw).nextValue() as? String ?: return@runCatching null
                JSONObject(text)
            }.getOrNull()
            if (response != null && listOf("app", "games", "books").all { response.optJSONArray(it) != null }) {
                CookieManager.getInstance().flush()
                complete = true
            }
        }
    }
    LaunchedEffect(browser, site) {
        val view = browser ?: return@LaunchedEffect
        while (isActive) { delay(1000); readPage(view) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                Text("Verify connection", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                Text("Complete the browser check, then tap Continue to try again. If the check keeps failing, try another network.",
                    Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
                FlowRow {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { browser?.reload() }) { Text("Reload") }
                    TextButton(onClick = { CookieManager.getInstance().flush(); onContinue() },
                        modifier = Modifier.testTag("continue-connection-verification")) { Text("Continue") }
                    TextButton(onClick = { openForumBrowser(context, "https://forum.mobilism.org/") }) { Text("Use forum in browser") }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(modifier = Modifier.fillMaxSize().clipToBounds(), factory = { viewContext -> WebView(viewContext).apply {
                        settings.userAgentString = org.mobilism.material.data.AppUserAgent.value(viewContext)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                request.isForMainFrame && !acceptsConnectionVerificationUrl(request.url, site)
                            override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) { dontResend.sendToTarget() }
                            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) { complete = false }
                            override fun onPageFinished(view: WebView, url: String) { readPage(view) }
                        }
                        browser = this
                        // Use a public read, never an interrupted account action or an API session token.
                        loadUrl(site.url)
                    } }, update = { view -> view.visibility = if (complete) View.INVISIBLE else View.VISIBLE },
                        onRelease = { view -> browser = null; view.stopLoading(); view.destroy() })
                    if (complete) Text("Browser check finished. Tap Continue to try again.", Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

internal fun acceptsConnectionVerificationUrl(uri: Uri, site: VerificationSite): Boolean {
    val target = site.url.toUri()
    return uri.scheme == "https" && uri.host == target.host && uri.userInfo == null &&
        uri.port in listOf(-1, 443) && (uri.path == target.path || uri.path.orEmpty().startsWith("/cdn-cgi/challenge-platform/"))
}
