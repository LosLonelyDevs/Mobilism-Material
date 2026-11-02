package org.mobilism.material.ui

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun ForumPostVerification(topicId: Long, onVerified: (Long) -> Unit, onDismiss: () -> Unit) {
    val verified by rememberUpdatedState(onVerified)
    var browser by remember { mutableStateOf<WebView?>(null) }
    var message by remember { mutableStateOf("Complete the forum check, then tap Continue.") }
    fun readPost(view: WebView, reportWaiting: Boolean = false) {
        val uri = view.url?.let(Uri::parse) ?: return
        if (!isForumTopic(uri, topicId)) return
        view.evaluateJavascript(FIRST_POST_SCRIPT) { result ->
            val postId = result?.removeSurrounding("\"")?.toLongOrNull()?.takeIf { it > 0 }
            if (postId != null) verified(postId)
            else if (reportWaiting) message = "The forum check is not finished. Complete it, then tap Continue."
        }
    }
    LaunchedEffect(browser, topicId) {
        val view = browser ?: return@LaunchedEffect
        // Verification can replace the page content without another page-finished callback.
        while (isActive) {
            delay(1000)
            readPost(view)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Text("Verify forum connection", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                Text(message, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { browser?.reload() }) { Text("Reload") }
                    TextButton(onClick = { browser?.let { readPost(it, reportWaiting = true) } }) { Text("Continue") }
                }
                AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context ->
                    WebView(context).apply {
                        // Browser verification needs JavaScript. Navigation stays on this forum topic.
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                request.isForMainFrame && !isForumTopic(request.url, topicId)

                            override fun onPageFinished(view: WebView, url: String) { readPost(view) }
                        }
                        browser = this
                        loadUrl("https://forum.mobilism.org/viewtopic.php?t=$topicId")
                    }
                }, onRelease = { view ->
                    browser = null
                    view.stopLoading()
                    view.destroy()
                })
            }
        }
    }
}

private fun isForumTopic(uri: Uri, topicId: Long): Boolean =
    uri.scheme == "https" && uri.host == "forum.mobilism.org" && uri.userInfo == null && uri.port in listOf(-1, 443) &&
        uri.path == "/viewtopic.php" && uri.getQueryParameter("t") == topicId.toString() &&
        uri.getQueryParameter("start").let { it == null || it == "0" } && uri.getQueryParameter("sd") != "d"

private val FIRST_POST_SCRIPT = """
    (function() {
        var posts = document.querySelectorAll('div[id]');
        for (var i = 0; i < posts.length; i++) {
            var post = posts[i];
            if (/^p[1-9][0-9]*$/.test(post.id) &&
                (post.classList.contains('post') || post.querySelector('article .postbody'))) {
                return post.id.substring(1);
            }
        }
        return null;
    })()
""".trimIndent()
