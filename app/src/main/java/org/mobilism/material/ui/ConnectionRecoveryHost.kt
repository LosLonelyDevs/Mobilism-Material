package org.mobilism.material.ui

import android.content.Context
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.mobilism.material.data.VerificationSite
import org.mobilism.material.designsystem.AppTextButton

internal val LocalConnectionRecovery = staticCompositionLocalOf<ConnectionRecoveryController?> { null }

@Composable
internal fun ConnectionRecoveryHost(controller: ConnectionRecoveryController,
    createBrowser: (Context) -> WebView = { WebView(it) },
    readExpiry: ((VerificationSite) -> ClearanceExpiry)? = null,
    quietTimeoutMillis: Long = 8_000,
    pollMillis: Long = 30_000,
    content: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val attempt by controller.attempt.collectAsState()
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            controller.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        controller.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.setForeground(false)
        }
    }
    val policy = remember(controller, context) {
        ClearanceRenewalPolicy(context.getSharedPreferences("attempted-cookie-expiry", Context.MODE_PRIVATE))
    }
    LaunchedEffect(controller, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                VerificationSite.entries.forEach { site ->
                    val expiry = runCatching { readExpiry?.invoke(site) ?: ClearanceCookies.read(site) }.getOrNull()
                    if (expiry != null && controller.canPrepare(site) &&
                        policy.shouldRenew(site, expiry.expiresAt, expiry.present, System.currentTimeMillis())) {
                        controller.prepare(site)
                    }
                }
                delay(pollMillis)
            }
        }
    }
    val browser = remember(attempt?.id) { attempt?.let { runCatching { createBrowser(context) }.getOrNull() } }
    var opening by remember(browser) { mutableStateOf(true) }
    val releaseBrowser = remember(browser) {
        var released = false
        {
            if (!released) {
                released = true
                browser?.stopLoading()
                (browser?.parent as? ViewGroup)?.removeView(browser)
                browser?.destroy()
            }
        }
    }
    DisposableEffect(browser) {
        onDispose { releaseBrowser() }
    }
    LaunchedEffect(attempt?.id, browser) {
        val current = attempt ?: return@LaunchedEffect
        val view = browser ?: run { controller.finish(current.id, false); return@LaunchedEffect }
        val site = current.site
        var browserPageAccepted = false
        fun readPage() {
            if (browserPageAccepted || controller.attempt.value?.id != current.id) return
            val url = view.url?.toUri() ?: return
            if (!isConnectionVerificationPageUrl(url, site)) return
            view.evaluateJavascript("(function(){var html=document.documentElement.outerHTML;return html.length<=2097152?html:null})()") { raw ->
                if (browserPageAccepted || controller.attempt.value?.id != current.id) return@evaluateJavascript
                val html = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull() ?: return@evaluateJavascript
                if (verificationPageReady(site, html)) {
                    browserPageAccepted = true
                    CookieManager.getInstance().flush()
                    controller.browserPageReady(current.id)
                }
            }
        }
        view.settings.userAgentString = org.mobilism.material.data.AppUserAgent.value(context)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.allowFileAccess = false
        view.settings.allowContentAccess = false
        view.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        view.webViewClient = object : VerificationPageClient({ opening = false }, { readPage() }) {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.isForMainFrame && !acceptsConnectionVerificationUrl(request.url, site)
            override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) { dontResend.sendToTarget() }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) controller.finish(current.id, false)
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                controller.finish(current.id, false)
                releaseBrowser()
                return true
            }
        }
        // Verification receives no credentials, API token, or account action to replay.
        view.loadUrl(site.url)
        val started = android.os.SystemClock.elapsedRealtime()
        while (isActive) {
            delay(500)
            readPage()
            if (android.os.SystemClock.elapsedRealtime() - started >= quietTimeoutMillis &&
                controller.attempt.value?.interactive == false) controller.offerInteraction(current.id)
        }
    }
    CompositionLocalProvider(LocalConnectionRecovery provides controller) {
        Box(Modifier.fillMaxSize()) {
            if (attempt?.interactive == false && browser != null) RecoveryBrowser(browser, false,
                Modifier.fillMaxSize().alpha(0f).clearAndSetSemantics {})
            content()
            attempt?.takeIf { it.needsAttention && !it.interactive }?.let { pending ->
                val accessibility = LocalAccessibilityManager.current
                LaunchedEffect(pending.id) {
                    delay(accessibility?.calculateRecommendedTimeoutMillis(30_000L, containsText = true, containsControls = true) ?: 30_000L)
                    controller.defer(pending.id)
                }
                Snackbar(modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(12.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface,
                    action = { AppTextButton("Verify", { controller.showInteraction(pending.id) }) },
                    dismissAction = { AppTextButton("Later", { controller.defer(pending.id) }) }) {
                    Text("Verification needed to finish loading.")
                }
            }
        }
        val current = attempt
        if (current?.interactive == true && browser != null) {
            Dialog(onDismissRequest = { controller.defer(current.id) },
                properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                        Text("Verify connection", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                        Text("Complete the browser check. Loading will resume automatically.", Modifier.padding(horizontal = 16.dp))
                        FlowRow {
                            TextButton({ controller.defer(current.id) }) { Text("Cancel") }
                            TextButton({ browser.reload() }) { Text("Reload") }
                            TextButton({ openForumBrowser(context, "https://forum.mobilism.org/") }) { Text("Use forum in browser") }
                        }
                        VerificationBrowserFrame(opening, Modifier.weight(1f).fillMaxWidth()) {
                            RecoveryBrowser(browser, true, Modifier.fillMaxSize().testTag("interactive-connection-browser"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecoveryBrowser(browser: WebView, interactive: Boolean, modifier: Modifier) {
    AndroidView(modifier = modifier.clipToBounds(), factory = {
        (browser.parent as? ViewGroup)?.removeView(browser)
        browser
    }, update = {
        it.isFocusable = interactive
        it.isFocusableInTouchMode = interactive
        it.importantForAccessibility = if (interactive) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }, onRelease = {})
}

internal fun verificationPageReady(site: VerificationSite, html: String): Boolean {
    val page = Jsoup.parse(html)
    return when (site) {
        VerificationSite.App -> runCatching { JSONObject(page.body().wholeText()) }.getOrNull()?.let { response ->
            listOf("app", "games", "books").all { response.optJSONArray(it) != null }
        } == true
        VerificationSite.Forum -> page.select("a[href*=viewforum.php]").isNotEmpty() &&
            page.select("a[href*=ucp.php], form[action*=search.php]").isNotEmpty()
    }
}
