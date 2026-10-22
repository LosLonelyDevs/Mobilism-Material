package org.mobilism.material.ui

import android.graphics.Typeface
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import org.mobilism.material.designsystem.AppFont
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.AppTextButton
import org.mobilism.material.designsystem.ExpressiveButton
import org.mobilism.material.designsystem.LoadingState
import org.mobilism.material.designsystem.LocalAppFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseScreen(state: ReleaseState, onBack: () -> Unit, onRetry: () -> Unit) {
    val context = LocalContext.current
    var linksOpen by rememberSaveable { mutableStateOf(false) }
    val release = state.release
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Release", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack) },
                actions = { release?.browserUrl?.let { url ->
                    AppIconButton(Icons.Outlined.OpenInBrowser, "Open in browser", { openWeb(context, url) })
                } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (state.busy) item { LoadingState("Loading release…", Modifier.fillMaxWidth()) }
                state.error?.let { item { ErrorPanel(it, onRetry = onRetry) } }
                if (release != null) {
                    item {
                        Text(Html.fromHtml(release.title, Html.FROM_HTML_MODE_LEGACY).toString(), style = MaterialTheme.typography.headlineMedium)
                    }
                    item {
                        Text("${release.author} · ${release.replies} replies", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (release.images.isNotEmpty()) item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(release.images.distinct(), key = { it }) { image ->
                                AsyncImage(
                                    model = image, contentDescription = "Release screenshot",
                                    modifier = Modifier.widthIn(min = 160.dp, max = 300.dp).heightIn(min = 180.dp, max = 360.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    }
                    item { ReleaseBody(release.contentHtml, release.browserUrl ?: "https://forum.mobilism.org/") }
                    if (release.downloadLinks.isNotEmpty()) item {
                        ExpressiveButton({ linksOpen = true }, Modifier.fillMaxWidth()) { Text("Download links") }
                    }
                }
            }
        }
    }
    if (linksOpen && release != null) {
        ModalBottomSheet(onDismissRequest = { linksOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp)) {
                item { Text("Download links", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp)) }
                items(release.downloadLinks.distinct(), key = { it }) { url ->
                    val host = android.net.Uri.parse(url).host ?: "Download"
                    AppTextButton("Open $host", {
                        openWeb(context, url)
                        linksOpen = false
                    }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Native text bridge preserves formatted forum posts without JavaScript or a WebView. */
@Composable
private fun ReleaseBody(html: String, baseUrl: String) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val textSize = MaterialTheme.typography.bodyLarge.fontSize.value
    val font = LocalAppFont.current
    val typeface = remember(context, font) {
        if (font == AppFont.Geist) context.resources.getFont(org.mobilism.material.designsystem.R.font.geist_regular)
        else Typeface.create("sans-serif", Typeface.NORMAL)
    }
    val richText = remember(html, baseUrl, context) {
        val allowed = Safelist.basic().removeProtocols("a", "href", "ftp", "mailto")
        val cleaned = Jsoup.clean(html, baseUrl, allowed)
        SpannableStringBuilder(Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY)).apply {
            getSpans(0, length, URLSpan::class.java).forEach { link ->
                val start = getSpanStart(link)
                val end = getSpanEnd(link)
                val flags = getSpanFlags(link)
                removeSpan(link)
                setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) { openWeb(context, link.url) }
                }, start, end, flags)
            }
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { viewContext -> TextView(viewContext).apply {
            setTextIsSelectable(true)
            movementMethod = LinkMovementMethod.getInstance()
        } },
        update = { view ->
            if (view.tag !== richText) { view.text = richText; view.tag = richText }
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            view.textSize = textSize
            view.typeface = typeface
        },
    )
}
