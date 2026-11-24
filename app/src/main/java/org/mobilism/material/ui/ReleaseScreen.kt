package org.mobilism.material.ui

import org.mobilism.material.R
import org.mobilism.material.data.hasPremiumAccount
import android.content.ClipData
import android.graphics.Typeface
import android.text.method.LinkMovementMethod
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
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
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
        throw IllegalStateException()
    },
    auth: AuthState = AuthState.Guest, onSignIn: () -> Unit = {},
    onWriteComment: () -> Unit = {}, onEditComment: (String) -> Unit = {},
    onPostComment: () -> Unit = {}, onCloseComposer: () -> Unit = {}, onRefreshComments: () -> Unit = {},
    onReplyToComment: (org.mobilism.material.data.ReleaseComment) -> Unit = {}, onRemoveReply: () -> Unit = {},
    onRelease: (Long) -> Unit = {},
    onDownloadHosts: (List<String>) -> Unit = {},
    onRefresh: () -> Unit = onRetry,
    onCategories: (() -> Unit)? = null,
    onLikePostVerified: (Long) -> Unit = {}, onDismissLikeVerification: () -> Unit = {},
    descriptionExpanded: Boolean? = null, onDescriptionExpanded: ((Boolean) -> Unit)? = null,
) {
    val theme = LocalAppTheme.current
    val cleanUpHeadings = LocalForumFormatting.current.cleanUpReleaseText
    val context = LocalContext.current
    val resources = LocalResources.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val release = state.release
    var downloadsOpen by rememberSaveable(release?.id) { mutableStateOf(false) }
    var managedCommentId by rememberSaveable(release?.id) { mutableStateOf<Long?>(null) }
    var commentAction by rememberSaveable(release?.id) { mutableStateOf(ForumCommentAction.Edit.name) }
    if (release != null && auth is AuthState.SignedIn) managedCommentId?.let { postId ->
        val comment = state.comments.items.firstOrNull { it.id == postId && canManageComment(it, auth) }
        if (comment != null) ForumCommentEditor(ForumCommentTarget(release.id, postId, ForumCommentAction.valueOf(commentAction)),
            auth.username, comment.contentHtml) { refresh ->
            managedCommentId = null
            if (refresh) onRefreshComments()
        }
    }
    val downloadRequests = remember(release?.id) { DownloadRequestTracker() }
    if (state.likeNeedsVerification && release != null) {
        var verifying by rememberSaveable(release.id) { mutableStateOf(false) }
        if (verifying) {
            ForumPostVerification(release.id, onLikePostVerified, onDismissLikeVerification)
        } else {
            LaunchedEffect(release.id) {
                val result = snackbar.showSnackbar(resources.getString(R.string.release_verify_like),
                    actionLabel = resources.getString(R.string.release_verify), withDismissAction = true, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) verifying = true else onDismissLikeVerification()
            }
        }
    }
    var collapsedComments by rememberSaveable(release?.id) { mutableStateOf(emptyList<Long>()) }
    val commentTree = remember(state.comments.items) { CommentTree.build(state.comments.items) }
    var sort by rememberSaveable(release?.id) { mutableStateOf(CommentSort()) }
    val ordered = remember(commentTree, sort) { sort.order(commentTree) }
    val commentRows = remember(ordered, collapsedComments) { CommentTree.flatten(ordered, collapsedComments.toSet()) }
    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let { snackbar.showSnackbar(it); onActionMessageShown() }
    }
    var gallery by remember(release?.id) { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val images = release?.galleryImages.orEmpty()
    val content = remember(release) {
        release?.let { ReleaseContentParser.parse(it.contentHtml, it.downloadLinks, it.browserUrl ?: "https://forum.mobilism.org/") }
    }
    LaunchedEffect(content) { onDownloadHosts(content?.downloads.orEmpty().map { it.url }) }
    val sections = remember(content, release?.title, cleanUpHeadings) {
        ReleaseSections.parse(content?.bodyHtml.orEmpty(), release?.title.orEmpty(), cleanUp = cleanUpHeadings)
    }
    val groups = remember(content, preferences, debrid) { DownloadGroups.group(content?.downloads.orEmpty(), preferences, debrid) }
    val downloadOpened = { if (release?.section == ContentSection.Apps) onDownloaded(true) }
    val downloadSheetState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val requestDownloadNotifications = rememberDownloadNotificationRequest()
    val downloadFile: (suspend (String, suspend () -> String) -> Unit)? =
        if (preferences.internalDownloads && release != null && auth is AuthState.SignedIn && debrid.hasPremiumAccount(auth)) { original, direct ->
            val folder = preferences.downloadFolder ?: throw org.mobilism.material.downloads.DownloadProblem(
                resources.getString(R.string.download_choose_folder))
            requestDownloadNotifications()
            val started = org.mobilism.material.downloads.ReleaseDownloads.enqueue(context.applicationContext, original, release,
                folder, preferences.extractZips, auth.username, direct)
            downloadsOpen = false
            org.mobilism.material.downloads.showDownloadToast(context, if (started) resources.getString(R.string.release_downloading) else resources.getString(R.string.release_already_downloading))
        } else null
    if (downloadsOpen && release != null) downloadSheetState.SaveableStateProvider(release.id) {
        ReleaseDownloadsSheet(release.id, ReleaseTitles.parse(release.title, release.section).name,
            groups, content?.downloadNotesHtml.orEmpty(), release.browserUrl ?: "https://forum.mobilism.org/",
            debrid, downloadRequests, onResolveDownload, downloadOpened,
            onRelease = onRelease, onDismiss = { downloadsOpen = false }, downloadFile = downloadFile)
    }
    val canRefresh = !state.busy && !state.likeBusy && !state.composer.posting
    val refresh = { if (canRefresh) onRefresh() }
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
                title = { AppHeading(resources.getString(R.string.release_page_title), HeadingLevel.Page, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, resources.getString(R.string.action_back), onBack) },
                actions = { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { release?.let {
                    AppIconToggleButton(
                        if (it.liked == true) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        if (it.liked == true) resources.getString(R.string.release_remove_like) else resources.getString(R.string.release_like),
                        it.liked == true, MaterialTheme.colorScheme.secondary,
                        onCheckedChange = { onLike() }, enabled = !state.likeBusy && !state.busy,
                    )
                    if (it.section == ContentSection.Apps) {
                        AppIconToggleButton(
                            if (watched) Icons.Filled.Visibility else Icons.Outlined.Visibility,
                            if (watched) resources.getString(R.string.release_unwatch) else resources.getString(R.string.release_watch), watched, theme.featured,
                            onCheckedChange = { enabled ->
                                onWatch(enabled)
                                scope.launch { snackbar.showSnackbar(if (enabled) resources.getString(R.string.release_watching) else resources.getString(R.string.release_unwatched)) }
                            },
                        )
                        AppIconToggleButton(
                            if (downloaded) Icons.Filled.DownloadDone else Icons.Outlined.Download,
                            if (downloaded) resources.getString(R.string.release_clear_mark) else resources.getString(R.string.release_mark_downloaded), downloaded, theme.success,
                            onCheckedChange = { enabled ->
                                onDownloaded(enabled)
                                scope.launch { snackbar.showSnackbar(if (enabled) resources.getString(R.string.release_marked_downloaded) else resources.getString(R.string.release_mark_cleared)) }
                            },
                        )
                    }
                    val url = it.browserUrl ?: "https://forum.mobilism.org/viewtopic.php?t=${it.id}"
                    Box {
                        AppIconButton(Icons.Outlined.MoreVert, resources.getString(R.string.release_options), { menu = true })
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.extraLarge) {
                            if (onCategories != null) RoundedMenuItem(
                                label = resources.getString(R.string.release_categories), accent = theme.featured, icon = Icons.Outlined.Category,
                                onClick = { menu = false; onCategories() },
                            )
                            if (it.section == ContentSection.Apps) {
                                RoundedMenuItem(
                                    label = if (watched) resources.getString(R.string.release_unfollow) else resources.getString(R.string.release_follow), accent = theme.featured,
                                    icon = if (watched) Icons.Outlined.BookmarkRemove else Icons.Outlined.BookmarkAdd,
                                    onClick = {
                                        menu = false; onWatch(!watched)
                                        scope.launch { snackbar.showSnackbar(if (watched) resources.getString(R.string.release_unfollowed) else resources.getString(R.string.release_followed)) }
                                    },
                                )
                                RoundedMenuItem(
                                    label = if (downloaded) resources.getString(R.string.release_clear_mark) else resources.getString(R.string.release_mark_downloaded), accent = theme.success,
                                    icon = Icons.Outlined.DownloadDone,
                                    onClick = {
                                        menu = false; onDownloaded(!downloaded)
                                        scope.launch { snackbar.showSnackbar(if (downloaded) resources.getString(R.string.release_mark_cleared) else resources.getString(R.string.release_marked_downloaded)) }
                                    },
                                )
                            }
                            RoundedMenuItem(
                                label = resources.getString(R.string.release_copy_url), accent = theme.featured, icon = Icons.Outlined.ContentCopy,
                                onClick = {
                                    menu = false
                                    scope.launch {
                                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(resources.getString(R.string.release_url_label), url)))
                                        snackbar.showSnackbar(resources.getString(R.string.release_url_copied))
                                    }
                                },
                            )
                            RoundedMenuItem(
                                label = resources.getString(R.string.release_open_browser), accent = MaterialTheme.colorScheme.tertiary, icon = Icons.Outlined.OpenInBrowser,
                                onClick = { menu = false; openForumBrowser(context, url) },
                            )
                        }
                    }
                } } },
            )
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.busy, onRefresh = refresh, indicator = {},
            modifier = Modifier.padding(padding).fillMaxSize().semantics {
                customActions = listOf(CustomAccessibilityAction(resources.getString(R.string.release_refresh)) {
                    if (!canRefresh) false else { onRefresh(); true }
                })
            }, contentAlignment = Alignment.TopCenter) {
            if (state.busy && release == null) {
                LoadingState(resources.getString(R.string.release_loading), Modifier.align(Alignment.Center), indicatorSize = 96.dp)
            }
            val list = androidx.compose.foundation.lazy.rememberLazyListState()
            CommentJumpList(release?.id, ordered, commentRows, collapsedComments, { collapsedComments = it }, list,
                firstRow = { list.layoutInfo.totalItemsCount - commentRows.size - 1 },
                modifier = Modifier.widthIn(max = 840.dp).fillMaxSize().testTag("release-content"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
            ) { highlight, followingLink ->
                if (state.busy && release != null) releaseDetailItem { LoadingState(resources.getString(R.string.release_updating), Modifier.fillMaxWidth()) }
                state.error?.let { releaseDetailItem { ErrorPanel(it, onRetry = onRetry) } }
                if (release != null) {
                    releaseDetailItem(bottomPadding = 4.dp) {
                        ReleaseTitleHeader(release.title, release.section)
                    }
                    releaseDetailItem {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            UsernameLink(release.author, color = theme.originalPoster)
                            ReleaseMetadataFooter(release, expanded = true, spacing = 12.dp) {
                                ReleasePostDate(release.publishedAt)
                                Text(resources.getQuantityString(R.plurals.release_replies, release.replies.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), release.replies), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ReleaseViewCount(release.views, textStyle = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (groups.isNotEmpty()) releaseDetailItem("quick-downloads") {
                        ExpressiveButton({ downloadsOpen = true }, Modifier.fillMaxWidth().testTag("open-downloads"),
                            colors = ButtonDefaults.filledTonalButtonColors()) {
                            Icon(Icons.Outlined.Download, null)
                            Text(resources.getString(R.string.settings_downloads), Modifier.padding(start = 8.dp))
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
                                compactHeaderBottom = cleanUpHeadings && (section.kind == ReleaseSection.Changes || section.startsWithAdvertisingNotice),
                                expanded = descriptionExpanded.takeIf { section.kind == ReleaseSection.Description },
                                onExpandedChange = onDescriptionExpanded.takeIf { section.kind == ReleaseSection.Description },
                                headingStyle = MaterialTheme.typography.titleMedium,
                                headingColor = if (cleanUpHeadings) releaseHeadingColor(MaterialTheme.colorScheme.onSurface, accent)
                                    else Color.Unspecified) {
                                if (section.changelogPlaceholder != null) {
                                    Text(section.changelogPlaceholder, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else if (cleanUpHeadings && section.kind == ReleaseSection.Mod) {
                                    ModInfoContent(section.html, release.browserUrl ?: "https://forum.mobilism.org/", onRelease)
                                } else ReleaseBody(section.html, release.browserUrl ?: "https://forum.mobilism.org/", onRelease, cleanUp = true,
                                    descriptionTitle = release.title.takeIf { section.kind == ReleaseSection.Description })
                            }
                        }
                    }
                    if (groups.isNotEmpty() || !content?.downloadNotesHtml.isNullOrBlank()) releaseDetailItem("downloads") {
                        CollapsibleSection("${release.id}-downloads", resources.getString(R.string.settings_downloads), Icons.Outlined.Download, theme.success,
                            compactHeaderBottom = cleanUpHeadings,
                            headingStyle = MaterialTheme.typography.titleMedium.let { style ->
                                if (cleanUpHeadings) style.copy(fontSize = (style.fontSize.value + 1).sp) else style
                            },
                            headingColor = if (cleanUpHeadings) releaseHeadingColor(MaterialTheme.colorScheme.onSurface, theme.success)
                                else Color.Unspecified) {
                            Column(verticalArrangement = Arrangement.spacedBy(if (cleanUpHeadings) 0.dp else 12.dp)) {
                                content?.downloadNotesHtml?.takeIf { it.isNotBlank() }?.let {
                                    DownloadNotes(it, release.browserUrl ?: "https://forum.mobilism.org/", onRelease)
                                }
                                if (groups.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    groups.forEach { group ->
                                        ReleaseDownloadGroup(release.id, group, release.browserUrl ?: "https://forum.mobilism.org/",
                                            debrid, downloadRequests, onResolveDownload, downloadOpened,
                                            snackbar = snackbar, onRelease = onRelease,
                                            downloadFile = downloadFile)
                                    }
                                }
                            }
                        }
                    }
                    item("comments-heading") {
                        val label = if (auth !is AuthState.SignedIn) resources.getString(R.string.release_sign_in_comment)
                            else if (state.composer.draft.isNotBlank()) resources.getString(R.string.release_continue_comment) else resources.getString(R.string.release_comment)
                        val write = if (auth is AuthState.SignedIn) onWriteComment else onSignIn
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    AppHeading(resources.getString(R.string.release_comments), HeadingLevel.Section, Modifier.weight(1f),
                                        style = if (cleanUpHeadings) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
                                    if (cleanUpHeadings) AppIconButton(Icons.Outlined.ChatBubbleOutline, label, write,
                                        Modifier.testTag("write-comment"))
                                }
                                CommentSortMenu(sort, maxOf(release.replies, state.comments.items.size.toLong())) { sort = sort.select(it) }
                            }
                            if (!cleanUpHeadings) ExpressiveButton(write, Modifier.fillMaxWidth().testTag("write-comment"),
                                colors = ButtonDefaults.filledTonalButtonColors()) {
                                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null)
                                Text(label, Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    items(commentRows, key = { "comment-${it.comment.id}" }) { row ->
                        val comment = row.comment
                        val canManage = !row.placeholder && canManageComment(comment, auth)
                        CommentThreadRow(row.depth) {
                            ReleaseCommentCard(comment, release.browserUrl ?: "https://forum.mobilism.org/viewtopic.php?t=${release.id}", onRelease = onRelease,
                            onReply = if (row.placeholder) null else ({ if (auth is AuthState.SignedIn) onReplyToComment(comment) else onSignIn() }),
                            replyEnabled = !state.composer.posting, threadContent = row.content, earlierComment = row.placeholder,
                            originalPoster = release.author, highlighted = highlight == comment.id, topicId = release.id,
                            onEdit = if (canManage) ({ commentAction = ForumCommentAction.Edit.name; managedCommentId = comment.id }) else null,
                            onDelete = if (canManage) ({ commentAction = ForumCommentAction.Delete.name; managedCommentId = comment.id }) else null,
                            actionsEnabled = !state.composer.posting,
                            collapsed = comment.id in collapsedComments,
                            onToggleThread = {
                                collapsedComments = if (comment.id in collapsedComments) collapsedComments - comment.id
                                    else collapsedComments + comment.id
                            },
                            onImage = { urls, index -> gallery = urls to index })
                        }
                    }
                    item("comments-status") {
                        val comments = state.comments
                        when {
                            comments.error != null -> ErrorPanel(comments.error, onRetry = onMoreComments)
                            comments.busy -> LoadingState(resources.getString(R.string.release_loading_comments), Modifier.fillMaxWidth())
                            comments.canLoadMore && followingLink -> org.mobilism.material.designsystem.AppTextButton(resources.getString(R.string.release_load_more_comments), onMoreComments)
                            comments.canLoadMore -> LaunchedEffect(comments.page) { onMoreComments() }
                            comments.items.isEmpty() -> Text(resources.getString(R.string.release_empty_comments), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.releaseDetailItem(
    key: Any? = null, bottomPadding: Dp = 12.dp, content: @Composable () -> Unit,
) {
    item(key = key) { Box(Modifier.padding(bottom = bottomPadding)) { content() } }
}

/** Native text bridge preserves formatted forum posts without JavaScript or a WebView. */
@Composable
internal fun ReleaseBody(html: String, baseUrl: String, onRelease: ((Long) -> Unit)? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    cleanUp: Boolean = false, allowMarkdown: Boolean = true, messageQuoteColor: Color = Color.Unspecified,
    descriptionTitle: String? = null) {
    val context = LocalContext.current
    val options = LocalForumFormatting.current.let {
        ForumFormattingOptions(cleanUp && it.cleanUpReleaseText, allowMarkdown && it.markdown)
    }
    val currentCommentHandler = androidx.compose.runtime.rememberUpdatedState(LocalCommentLinkHandler.current)
    val commentHold = LocalCommentHold.current
    val currentOnRelease = androidx.compose.runtime.rememberUpdatedState(onRelease)
    val currentLinkedTopic = androidx.compose.runtime.rememberUpdatedState(LocalLinkedTopicHandler.current)
    val currentForumLinkHandler = androidx.compose.runtime.rememberUpdatedState(LocalForumLinkHandler.current)
    val currentAnnouncementHandler = androidx.compose.runtime.rememberUpdatedState(LocalAnnouncementLinkHandler.current)
    val currentDownloadHelpHandler = androidx.compose.runtime.rememberUpdatedState(LocalDownloadHelpHandler.current)
    val currentProfileHandler = androidx.compose.runtime.rememberUpdatedState(LocalProfileHandler.current)
    var heldUrl by remember(html, baseUrl) { mutableStateOf<String?>(null) }
    val currentOnHold = androidx.compose.runtime.rememberUpdatedState<(String) -> Unit>({ heldUrl = it })
    heldUrl?.let { ForumLinkActions(it, onDismiss = { heldUrl = null }) }
    val textColor = contentColor.toArgb()
    val linkArgb = linkColor.toArgb()
    val headingArgb = releaseHeadingColor(contentColor, MaterialTheme.colorScheme.primary).toArgb()
    val requirementsArgb = lerp(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.secondary, 0.28f).toArgb()
    val textSize = textStyle.fontSize.value
    val density = androidx.compose.ui.platform.LocalDensity.current
    val font = LocalAppFont.current
    val resources = LocalResources.current
    val typeface = remember(resources, font) {
        if (font == AppFont.Geist) resources.getFont(org.mobilism.material.designsystem.R.font.geist_regular)
        else Typeface.create("sans-serif", Typeface.NORMAL)
    }
    val quoteGuide = MaterialTheme.colorScheme.primary.toArgb()
    val richText = remember(html, baseUrl, context, options, linkArgb, textSize, density, headingArgb, requirementsArgb,
        messageQuoteColor, quoteGuide, descriptionTitle) {
        forumText(html, baseUrl, options, density.density, linkArgb, with(density) { textStyle.fontSize.toPx() },
            headingColor = headingArgb, requirementsColor = requirementsArgb, descriptionTitle = descriptionTitle).apply {
            if (messageQuoteColor != Color.Unspecified) {
                val size = with(density) { (textSize - 1f).coerceAtLeast(1f).sp.roundToPx() }
                styleMessageQuotes(density.density, quoteGuide, messageQuoteColor.toArgb(), size)
            }
            getSpans(0, length, URLSpan::class.java).forEach { link ->
                val start = getSpanStart(link)
                val end = getSpanEnd(link)
                val flags = getSpanFlags(link)
                removeSpan(link)
                setSpan(object : URLSpan(link.url) {
                    override fun onClick(widget: View) {
                        val comment = org.mobilism.material.ReleaseLinks.comment(link.url)
                        if (comment != null && currentCommentHandler.value?.invoke(comment) == true) return
                        val topic = org.mobilism.material.ReleaseLinks.topic(link.url)
                        val navigate = currentOnRelease.value
                        val forumHandler = currentForumLinkHandler.value
                        val help = currentDownloadHelpHandler.value
                        val profile = org.mobilism.material.data.ProfileReference.fromUrl(link.url)
                        val openProfile = currentProfileHandler.value
                        if (profile != null && openProfile != null) openProfile(profile)
                        else if (topic?.id == org.mobilism.material.data.DownloadHelp.TOPIC_ID && help != null) help()
                        else if (topic?.forumId != null && currentAnnouncementHandler.value?.invoke(topic.id, topic.forumId) == true) return
                        else if (topic != null && navigate != null) navigate(topic.id)
                        else if (topic != null && currentLinkedTopic.value != null) currentLinkedTopic.value?.invoke(topic)
                        else if (forumHandler != null) forumHandler(link.url)
                        else openWeb(context, link.url)
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
            setOnTouchListener(ForumLinkTouchListener(this) { currentOnHold.value(it) })
        } },
        update = { view ->
            view.setOnLongClickListener(commentHold?.let { hold -> View.OnLongClickListener { hold(); true } })
            if (view.tag !== richText) { view.text = richText; view.tag = richText }
            view.setTextColor(textColor)
            view.setLinkTextColor(linkArgb)
            view.textSize = textSize
            view.typeface = typeface
        },
    )
}

private fun releaseHeadingColor(body: Color, accent: Color): Color = lerp(body, accent, 0.38f)
