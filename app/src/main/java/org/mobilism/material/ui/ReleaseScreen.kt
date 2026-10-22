package org.mobilism.material.ui

import android.content.ClipData
import android.graphics.Typeface
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import org.mobilism.material.data.DebridState
import org.mobilism.material.designsystem.AppFont
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.LoadingState
import org.mobilism.material.designsystem.LocalAppFont
import org.mobilism.material.designsystem.LocalAppTheme
import org.mobilism.material.settings.DownloadPreferences
import org.mobilism.material.data.ContentSection
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.DownloadDone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseScreen(
    state: ReleaseState, debrid: DebridState, preferences: DownloadPreferences, onBack: () -> Unit,
    onRetry: () -> Unit, onMoreComments: () -> Unit, watched: Boolean, downloaded: Boolean,
    onWatch: (Boolean) -> Unit, onDownloaded: (Boolean) -> Unit,
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val release = state.release
    var gallery by remember(release?.id) { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val images = remember(release) {
        val embedded = Jsoup.parse(release?.contentHtml.orEmpty(), release?.browserUrl ?: "https://forum.mobilism.org/")
            .select("img[src]").mapNotNull { org.mobilism.material.data.webUrlOrNull(it.absUrl("src")) }
        (release?.images.orEmpty() + embedded).distinct()
    }
    val content = remember(release) {
        release?.let { ReleaseContentParser.parse(it.contentHtml, it.downloadLinks, it.browserUrl ?: "https://forum.mobilism.org/") }
    }
    val sections = remember(content) { ReleaseSections.parse(content?.bodyHtml.orEmpty()) }
    val groups = remember(content, preferences) { DownloadGroups.group(content?.downloads.orEmpty(), preferences) }
    gallery?.let { (urls, index) -> ReleaseImageViewer(urls, index, onDismiss = { gallery = null }) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { AppHeading("Release", HeadingLevel.Page, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack) },
                actions = { release?.let {
                    val url = it.browserUrl ?: "https://forum.mobilism.org/viewtopic.php?t=${it.id}"
                    Box {
                        AppIconButton(Icons.Outlined.MoreVert, "Release options", { menu = true })
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.extraLarge) {
                            if (it.section == ContentSection.Apps) {
                                RoundedMenuItem(
                                    label = if (watched) "Stop watching" else "Watch this version", accent = theme.featured,
                                    icon = if (watched) Icons.Outlined.BookmarkRemove else Icons.Outlined.BookmarkAdd,
                                    onClick = {
                                        menu = false; onWatch(!watched)
                                        scope.launch { snackbar.showSnackbar(if (watched) "Removed from watchlist" else "Added to watchlist") }
                                    },
                                )
                                RoundedMenuItem(
                                    label = if (downloaded) "Clear downloaded mark" else "Mark as downloaded", accent = theme.success,
                                    icon = Icons.Outlined.DownloadDone,
                                    onClick = {
                                        menu = false; onDownloaded(!downloaded)
                                        scope.launch { snackbar.showSnackbar(if (downloaded) "Downloaded mark cleared" else "Marked as downloaded") }
                                    },
                                )
                            }
                            RoundedMenuItem(
                                label = "Copy release URL", accent = theme.featured, icon = Icons.Outlined.ContentCopy,
                                onClick = {
                                    menu = false
                                    scope.launch {
                                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Release URL", url)))
                                        snackbar.showSnackbar("Release URL copied")
                                    }
                                },
                            )
                            RoundedMenuItem(
                                label = "Open in browser", accent = MaterialTheme.colorScheme.tertiary, icon = Icons.Outlined.OpenInBrowser,
                                onClick = { menu = false; openWeb(context, url) },
                            )
                        }
                    }
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
                        ReleaseTitleHeader(release.title)
                    }
                    item {
                        Text("${release.author} · ${release.replies} replies", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (images.isNotEmpty()) item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(images, key = { it }) { image ->
                                AsyncImage(
                                    model = image, contentDescription = "Open release image ${images.indexOf(image) + 1}",
                                    modifier = Modifier.widthIn(min = 160.dp, max = 300.dp).heightIn(min = 180.dp, max = 360.dp).clip(MaterialTheme.shapes.large)
                                        .clickable { gallery = images to images.indexOf(image) },
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    }
                    items(sections, key = { it.kind }) { section ->
                        val accent = when (section.kind) {
                            ReleaseSection.Description -> MaterialTheme.colorScheme.primary
                            ReleaseSection.Changes -> theme.featured
                            ReleaseSection.Mod -> MaterialTheme.colorScheme.secondary
                        }
                        val icon = when (section.kind) {
                            ReleaseSection.Description -> Icons.Outlined.Description
                            ReleaseSection.Changes -> Icons.Outlined.NewReleases
                            ReleaseSection.Mod -> Icons.Outlined.Tune
                        }
                        CollapsibleSection("${release.id}-${section.kind}", section.kind.title, icon, accent,
                            headingStyle = MaterialTheme.typography.titleMedium) {
                            ReleaseBody(section.html, release.browserUrl ?: "https://forum.mobilism.org/")
                        }
                    }
                    if (groups.isNotEmpty()) item("downloads") {
                        CollapsibleSection("${release.id}-downloads", "Downloads", Icons.Outlined.Download, theme.success,
                            headingStyle = MaterialTheme.typography.titleMedium) {
                            groups.forEach { group ->
                                CollapsibleSection(
                                    "${release.id}-${group.title}", group.title, Icons.Outlined.Memory,
                                    if (group.collapsed) MaterialTheme.colorScheme.onSurfaceVariant else theme.reading,
                                    initiallyExpanded = !group.collapsed,
                                    headingLevel = HeadingLevel.Subsection,
                                    headingStyle = MaterialTheme.typography.titleSmall,
                                ) {
                                    group.links.forEach { link ->
                                        DownloadLinkRow(link, debrid) { snackbar.showSnackbar("Link copied") }
                                    }
                                }
                            }
                        }
                    }
                    item("comments-heading") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AppHeading("Comments", HeadingLevel.Section, style = MaterialTheme.typography.titleMedium)
                            Text("Newest first", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(state.comments.items, key = { "comment-${it.id}" }) { comment ->
                        ReleaseCommentCard(comment, release.browserUrl ?: "https://forum.mobilism.org/",
                            onImage = { urls, index -> gallery = urls to index })
                    }
                    item("comments-status") {
                        val comments = state.comments
                        when {
                            comments.error != null -> ErrorPanel(comments.error, onRetry = onMoreComments)
                            comments.busy -> LoadingState("Loading comments…", Modifier.fillMaxWidth())
                            comments.canLoadMore -> LaunchedEffect(comments.page) { onMoreComments() }
                            comments.items.isEmpty() -> Text("No comments yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Native text bridge preserves formatted forum posts without JavaScript or a WebView. */
@Composable
internal fun ReleaseBody(html: String, baseUrl: String) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val textSize = MaterialTheme.typography.bodyLarge.fontSize.value
    val font = LocalAppFont.current
    val resources = LocalResources.current
    val typeface = remember(resources, font) {
        if (font == AppFont.Geist) resources.getFont(org.mobilism.material.designsystem.R.font.geist_regular)
        else Typeface.create("sans-serif", Typeface.NORMAL)
    }
    val richText = remember(html, baseUrl, context) {
        val allowed = Safelist.basic().removeProtocols("a", "href", "ftp", "mailto")
        val cleaned = Jsoup.clean(html, baseUrl, allowed)
        SpannableStringBuilder(Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY)).apply {
            // Removed download anchors can leave many empty forum markup lines.
            Regex("\n(?:[ \\t\\u00a0]*\n){2,}").findAll(this).toList().asReversed().forEach {
                replace(it.range.first, it.range.last + 1, "\n\n")
            }
            while (isNotEmpty() && last().isWhitespace()) delete(length - 1, length)
            while (isNotEmpty() && first().isWhitespace()) delete(0, 1)
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
