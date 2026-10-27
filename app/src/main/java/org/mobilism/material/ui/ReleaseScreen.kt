package org.mobilism.material.ui

import android.content.ClipData
import android.graphics.Typeface
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.DownloadDone
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.mobilism.material.data.DebridState
import org.mobilism.material.designsystem.AppFont
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.ExpressiveButton
import org.mobilism.material.data.AuthState
import org.mobilism.material.data.ReleaseTitles
import org.mobilism.material.designsystem.AppIconToggleButton
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
    onLike: () -> Unit, onActionMessageShown: () -> Unit,
    onResolveDownload: suspend (String) -> String = {
        throw org.mobilism.material.data.DebridException("Debrid is not available. Try again.")
    },
    auth: AuthState = AuthState.Guest, onSignIn: () -> Unit = {},
    onWriteComment: () -> Unit = {}, onEditComment: (String) -> Unit = {},
    onPostComment: () -> Unit = {}, onCloseComposer: () -> Unit = {}, onRefreshComments: () -> Unit = {},
    onReplyToComment: (org.mobilism.material.data.ReleaseComment) -> Unit = {}, onRemoveReply: () -> Unit = {},
    onRelease: (Long) -> Unit = {},
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val release = state.release
    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let { snackbar.showSnackbar(it); onActionMessageShown() }
    }
    var gallery by remember(release?.id) { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val images = remember(release) {
        val embedded = Jsoup.parse(release?.contentHtml.orEmpty(), release?.browserUrl ?: "https://forum.mobilism.org/")
            .select("img[src]").mapNotNull { org.mobilism.material.data.webUrlOrNull(it.absUrl("src")) }
        (release?.images.orEmpty() + embedded).distinct()
    }
    val content = remember(release) {
        release?.let { ReleaseContentParser.parse(it.contentHtml, it.downloadLinks, it.browserUrl ?: "https://forum.mobilism.org/") }
    }
    val sections = remember(content, release?.title) {
        ReleaseSections.parse(content?.bodyHtml.orEmpty(), release?.title.orEmpty())
    }
    val groups = remember(content, preferences) { DownloadGroups.group(content?.downloads.orEmpty(), preferences) }
    gallery?.let { (urls, index) -> ReleaseImageViewer(urls, index, onDismiss = { gallery = null }) }
    if (state.composer.open && release != null && auth is AuthState.SignedIn) {
        ReleaseCommentComposer(state.composer, auth.username, ReleaseTitles.parse(release.title, release.section).name,
            onEditComment, onPostComment, onCloseComposer,
            onCheckComments = { onCloseComposer(); onRefreshComments() }, onRemoveReply = onRemoveReply)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { AppHeading("Release", HeadingLevel.Page, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack) },
                actions = { release?.let {
                    AppIconToggleButton(
                        if (it.liked == true) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        if (it.liked == true) "Remove thumbs up" else "Thumbs up",
                        it.liked == true, MaterialTheme.colorScheme.secondary,
                        onCheckedChange = { onLike() }, enabled = !state.likeBusy && !state.busy,
                    )
                    if (it.section == ContentSection.Apps) {
                        AppIconToggleButton(
                            if (watched) Icons.Filled.Visibility else Icons.Outlined.Visibility,
                            if (watched) "Unwatch app" else "Watch app", watched, theme.featured,
                            onCheckedChange = { enabled ->
                                onWatch(enabled)
                                scope.launch { snackbar.showSnackbar(if (enabled) "Watching app" else "App unwatched") }
                            },
                        )
                        AppIconToggleButton(
                            if (downloaded) Icons.Filled.DownloadDone else Icons.Outlined.Download,
                            if (downloaded) "Clear downloaded mark" else "Mark as downloaded", downloaded, theme.success,
                            onCheckedChange = { enabled ->
                                onDownloaded(enabled)
                                scope.launch { snackbar.showSnackbar(if (enabled) "Marked as downloaded" else "Downloaded mark cleared") }
                            },
                        )
                    }
                    val url = it.browserUrl ?: "https://forum.mobilism.org/viewtopic.php?t=${it.id}"
                    Box {
                        AppIconButton(Icons.Outlined.MoreVert, "Release options", { menu = true })
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.extraLarge) {
                            if (it.section == ContentSection.Apps) {
                                RoundedMenuItem(
                                    label = if (watched) "Unfollow app" else "Follow app", accent = theme.featured,
                                    icon = if (watched) Icons.Outlined.BookmarkRemove else Icons.Outlined.BookmarkAdd,
                                    onClick = {
                                        menu = false; onWatch(!watched)
                                        scope.launch { snackbar.showSnackbar(if (watched) "App unfollowed" else "App followed") }
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
            if (state.busy && release == null) {
                LoadingState("Loading release…", Modifier.align(Alignment.Center), indicatorSize = 96.dp)
            }
            LazyColumn(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxSize().testTag("release-content"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.busy && release != null) releaseDetailItem { LoadingState("Updating release…", Modifier.fillMaxWidth()) }
                state.error?.let { releaseDetailItem { ErrorPanel(it, onRetry = onRetry) } }
                if (release != null) {
                    releaseDetailItem {
                         ReleaseTitleHeader(release.title, release.section)
                    }
                    releaseDetailItem {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(release.author, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${release.replies} replies", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ReleasePostDate(release.publishedAt)
                        }
                    }
                    if (images.isNotEmpty()) releaseDetailItem("images-${release.id}") {
                        ReleaseImageCarousel(images) { index -> gallery = images to index }
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
                        Box(Modifier.padding(bottom = 12.dp)) {
                            CollapsibleSection("${release.id}-${section.kind}", section.kind.title, icon, accent,
                                headingStyle = MaterialTheme.typography.titleMedium) {
                                ReleaseBody(section.html, release.browserUrl ?: "https://forum.mobilism.org/", onRelease)
                            }
                        }
                    }
                    if (groups.isNotEmpty() || !content?.downloadNotesHtml.isNullOrBlank()) releaseDetailItem("downloads") {
                        CollapsibleSection("${release.id}-downloads", "Downloads", Icons.Outlined.Download, theme.success,
                            headingStyle = MaterialTheme.typography.titleMedium) {
                            content?.downloadNotesHtml?.takeIf { it.isNotBlank() }?.let {
                                ReleaseBody(it, release.browserUrl ?: "https://forum.mobilism.org/", onRelease)
                            }
                            groups.forEach { group ->
                                CollapsibleSection(
                                    "${release.id}-${group.id}", group.title, Icons.Outlined.Layers,
                                    if (group.collapsed) MaterialTheme.colorScheme.onSurfaceVariant else theme.reading,
                                    initiallyExpanded = !group.collapsed,
                                    headingLevel = HeadingLevel.Subsection,
                                    headingStyle = MaterialTheme.typography.titleSmall,
                                ) {
                                    if (group.notesHtml.isNotBlank()) {
                                        ReleaseBody(group.notesHtml, release.browserUrl ?: "https://forum.mobilism.org/", onRelease)
                                    }
                                    group.links.forEach { link ->
                                        DownloadLinkRow(link, debrid, resolveLink = onResolveDownload, onOpened = {
                                            if (release.section == ContentSection.Apps) onDownloaded(true)
                                        }) { snackbar.showSnackbar("Link copied") }
                                    }
                                }
                            }
                        }
                    }
                    item("comments-heading") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    AppHeading("Comments", HeadingLevel.Section, style = MaterialTheme.typography.titleLarge)
                                    Text("Newest first", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                AppIconButton(Icons.Outlined.Refresh, "Refresh comments", onRefreshComments,
                                    enabled = !state.comments.busy && !state.composer.posting)
                            }
                            ExpressiveButton(if (auth is AuthState.SignedIn) onWriteComment else onSignIn,
                                Modifier.fillMaxWidth().testTag("write-comment"),
                                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors()) {
                                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null)
                                Text(if (auth !is AuthState.SignedIn) "Sign in to comment"
                                    else if (state.composer.draft.isNotBlank()) "Continue your comment" else "Write a comment",
                                    Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    items(state.comments.items, key = { "comment-${it.id}" }) { comment ->
                        ReleaseCommentCard(comment, release.browserUrl ?: "https://forum.mobilism.org/", onRelease = onRelease,
                            onReply = { if (auth is AuthState.SignedIn) onReplyToComment(comment) else onSignIn() },
                            replyEnabled = !state.composer.posting,
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

private fun androidx.compose.foundation.lazy.LazyListScope.releaseDetailItem(key: Any? = null, content: @Composable () -> Unit) {
    item(key = key) { Box(Modifier.padding(bottom = 12.dp)) { content() } }
}

/** Native text bridge preserves formatted forum posts without JavaScript or a WebView. */
@Composable
internal fun ReleaseBody(html: String, baseUrl: String, onRelease: ((Long) -> Unit)? = null) {
    val context = LocalContext.current
    val currentOnRelease = androidx.compose.runtime.rememberUpdatedState(onRelease)
    val currentForumLinkHandler = androidx.compose.runtime.rememberUpdatedState(LocalForumLinkHandler.current)
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
        forumText(html, baseUrl).apply {
            getSpans(0, length, URLSpan::class.java).forEach { link ->
                val start = getSpanStart(link)
                val end = getSpanEnd(link)
                val flags = getSpanFlags(link)
                removeSpan(link)
                setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        val topic = org.mobilism.material.ReleaseLinks.topicId(link.url)
                        val navigate = currentOnRelease.value
                        val forumHandler = currentForumLinkHandler.value
                        if (forumHandler != null) forumHandler(link.url)
                        else if (topic != null && navigate != null) navigate(topic) else openWeb(context, link.url)
                    }
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
