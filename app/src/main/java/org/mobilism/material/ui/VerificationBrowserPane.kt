package org.mobilism.material.ui

import android.content.Context
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mobilism.material.designsystem.LoadingState

internal open class VerificationPageClient(
    private val onPageVisible: () -> Unit,
    private val readFinishedPage: (WebView) -> Unit = {},
) : WebViewClient() {
    override fun onPageCommitVisible(view: WebView, url: String) = onPageVisible()

    override fun onPageFinished(view: WebView, url: String) {
        onPageVisible()
        readFinishedPage(view)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) onPageVisible()
        super.onReceivedError(view, request, error)
    }
}

@Composable
internal fun VerificationBrowserPane(
    modifier: Modifier,
    factory: (context: Context, onPageVisible: () -> Unit) -> WebView,
    update: (WebView) -> Unit = {},
    onRelease: (WebView) -> Unit,
) {
    var opening by remember { mutableStateOf(true) }
    VerificationBrowserFrame(opening, modifier) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { context -> factory(context) { opening = false } },
            update = update, onRelease = onRelease)
    }
}

@Composable
internal fun VerificationBrowserFrame(opening: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier) {
        content()
        if (opening) Surface(Modifier.align(Alignment.Center), shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            LoadingState("Opening verification…")
        }
    }
}
