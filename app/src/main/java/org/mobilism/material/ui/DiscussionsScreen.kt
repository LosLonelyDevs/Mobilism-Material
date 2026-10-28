package org.mobilism.material.ui

import android.content.ClipData
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.jsoup.Jsoup
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.*
import java.text.DateFormat
import java.util.Date

@Composable
fun DiscussionsScreen(model: DiscussionsViewModel, auth: AuthState, onBack: () -> Unit,
    onSignIn: (DiscussionTopic, DiscussionCategory?) -> Unit) {
    val context = LocalContext.current
    CompositionLocalProvider(LocalForumLinkHandler provides { url -> openForumBrowser(context, url); Unit }) {
        DiscussionsContent(model, auth, onBack, onSignIn)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscussionsContent(model: DiscussionsViewModel, auth: AuthState, onBack: () -> Unit,
    onSignIn: (DiscussionTopic, DiscussionCategory?) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val savedScreens = rememberSaveableStateHolder()
    var menu by remember(state.destination) { mutableStateOf(false) }
    var gallery by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val busy = when { state.topic != null -> state.comments.busy; state.category != null -> state.topics.busy; else -> state.loading }
    val refresh = {
        if (!state.composer.posting && !busy) {
            when { state.topic != null -> model.loadComments(true); state.category != null -> model.loadTopics(true); else -> model.loadCategories() }
        }
    }
    val back = { if (!model.back()) onBack() }
    BackHandler(onBack = back)
    LaunchedEffect(state.notice) {
        state.notice?.let { snackbar.showSnackbar(it); model.noticeShown() }
    }
    gallery?.let { (urls, index) -> ReleaseImageViewer(urls, index, onDismiss = { gallery = null }) }
    if (state.composer.open) ReleaseCommentComposer(state.composer, (auth as? AuthState.SignedIn)?.username.orEmpty(),
        state.topic?.title.orEmpty(), model::editDraft, model::post, model::closeComposer,
        onCheckComments = { model.closeComposer(); model.loadComments(refresh = true) }, onRemoveReply = model::removeReply)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(title = { AppHeading(if (state.topic != null) "Discussion" else state.category?.name ?: "Discussions",
                HeadingLevel.Page, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", back, enabled = !state.composer.posting) },
                actions = {
                    val url = state.topic?.browserUrl ?: state.category?.browserUrl ?: "https://forum.mobilism.org/"
                    Box {
                        AppIconButton(Icons.Outlined.MoreVert, "Discussion options", { menu = true })
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.extraLarge) {
                            RoundedMenuItem("Copy URL", accent = LocalAppTheme.current.featured, icon = Icons.Outlined.ContentCopy, onClick = {
                                menu = false
                                scope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Discussion URL", url)))
                                    snackbar.showSnackbar("URL copied")
                                }
                            })
                            RoundedMenuItem("Open in browser", accent = MaterialTheme.colorScheme.tertiary, icon = Icons.Outlined.OpenInBrowser,
                                onClick = { menu = false; openForumBrowser(context, url) })
                        }
                    }
                })
        },
        bottomBar = {
            state.topic?.let { topic ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center) {
                        ExpressiveButton({ if (auth is AuthState.SignedIn) model.openComposer() else onSignIn(topic, state.category) },
                            Modifier.widthIn(max = 680.dp).fillMaxWidth(), enabled = !state.composer.posting) {
                            Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(20.dp))
                            Text(if (auth is AuthState.SignedIn) if (state.composer.draft.isNotEmpty()) "Continue draft" else "Comment"
                                else "Sign in to comment", Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            PullToRefreshBox(isRefreshing = busy, onRefresh = refresh, indicator = {},
                modifier = Modifier.widthIn(max = 760.dp).fillMaxSize().semantics {
                    customActions = listOf(CustomAccessibilityAction("Refresh") {
                        if (state.composer.posting || busy) false else { refresh(); true }
                    })
                }) {
                AppScreenContent(state, { it.destination }, state.forward) { screen ->
                    savedScreens.SaveableStateProvider(screen.destination) {
                        when {
                            screen.topic != null -> DiscussionReader(screen, model::loadComments,
                                onReply = { if (auth is AuthState.SignedIn) model.openComposer(it) else onSignIn(screen.topic, screen.category) },
                                onImage = { urls, index -> gallery = urls to index })
                            screen.category != null -> DiscussionTopics(screen, model::openTopic, model::loadTopics,
                                model::editQuery, onSearch = { keyboard?.hide(); model.search() }, model::clearSearch)
                            else -> DiscussionCategories(screen, model::selectArea, model::openCategory, model::loadCategories)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscussionCategories(state: DiscussionsState, onArea: (DiscussionArea) -> Unit,
    onCategory: (DiscussionCategory) -> Unit, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val accents = listOf(colors.primary, colors.tertiary, colors.secondary)
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(60_000); value = System.currentTimeMillis() }
    }
    Column(Modifier.fillMaxSize()) {
        AppChoiceTabs(DiscussionArea.entries.map { it.label }, state.area.ordinal) { onArea(DiscussionArea.entries[it]) }
        key(state.area) {
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().testTag("discussion-categories")) {
                if (state.loading) item { LoadingState("Loading forums…", Modifier.fillMaxWidth()) }
                state.error?.let { item { ErrorPanel(it, onRetry = onRetry) } }
                state.categories.groupBy { it.section }.forEach { (section, categories) ->
                    if (section.isNotBlank()) item(key = "section:$section") {
                        AppHeading(section, HeadingLevel.Section, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                    }
                    itemsIndexed(categories, key = { _, category -> category.key }) { index, category ->
                        val accent = accents[index % accents.size]
                        ExpressiveCard({ onCategory(category) }, Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))) {
                            Row(Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Surface(color = accent.copy(alpha = 0.14f), shape = MaterialTheme.shapes.medium) {
                                    Icon(Icons.Outlined.Forum, null, Modifier.padding(12.dp).size(28.dp), tint = accent)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(category.name, style = MaterialTheme.typography.titleMedium, color = accent)
                                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(category.topics?.let { "$it topics" } ?: "Browse conversations",
                                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        state.categoryActivity[category.key]?.let { timestamp ->
                                            Text(DateUtils.getRelativeTimeSpanString(timestamp * 1000, now,
                                                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString(),
                                                modifier = Modifier.weight(1.5f).testTag("discussion-activity:${category.key}"),
                                                style = MaterialTheme.typography.bodySmall, color = accent, textAlign = TextAlign.End,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (!state.loading && state.error == null && state.categories.isEmpty()) item {
                    DiscussionEmpty("No forums available", "Refresh to check again.")
                }
            }
        }
    }
}

@Composable
private fun DiscussionTopics(state: DiscussionsState, onTopic: (DiscussionTopic) -> Unit, onMore: (Boolean) -> Unit,
    onQuery: (String) -> Unit, onSearch: () -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AppSearchField(state.query, onQuery, "Search topics",
            if (state.category?.id == 12L) "Search all discussions" else "Search this forum", onSearch,
            Icons.Outlined.Search, Icons.AutoMirrored.Outlined.ArrowForward,
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp).testTag("discussion-search"))
        key(state.submittedQuery) {
            LazyColumn(state = rememberLazyListState(), modifier = Modifier.fillMaxSize().testTag("discussion-topics"),
                contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.submittedQuery.isNotBlank()) item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Results for “${state.submittedQuery}”",
                            Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AppTextButton("Clear", onClear)
                    }
                }
                items(state.topics.items, key = { it.key }) { topic ->
                    val preview = remember(topic.contentHtml) { Jsoup.parse(topic.contentHtml).text() }
                    ExpressiveCard({ onTopic(topic) }, Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(topic.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                                fontSize = (MaterialTheme.typography.titleMedium.fontSize.value + 1).sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("discussion-title:${topic.key}"))
                            if (preview.isNotBlank()) Text(preview, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("discussion-preview:${topic.key}"))
                            Text(topic.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                DiscussionDate(topic.activityAt)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text("${topic.replies} replies", Modifier.weight(1f, fill = false),
                                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${topic.views} views", Modifier.weight(1f, fill = false),
                                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (topic.pinned) Surface(color = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer, shape = MaterialTheme.shapes.small) {
                                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.PushPin, null, Modifier.size(14.dp))
                                            Text("Pinned", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (state.topics.busy) item { LoadingState("Loading topics…", Modifier.fillMaxWidth()) }
                state.topics.error?.let { item { ErrorPanel(it, onRetry = { onMore(false) }) } }
                if (!state.topics.busy && state.topics.error == null) {
                    if (state.topics.items.isEmpty()) item {
                        DiscussionEmpty(if (state.submittedQuery.isBlank()) "No topics yet" else "No matching topics",
                            if (state.submittedQuery.isBlank()) "Check back later or open this forum in your browser." else "Try another search or clear the search.")
                    } else if (state.topics.canLoadMore) item { AppTextButton("Load more topics", { onMore(false) }, Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

@Composable
private fun DiscussionReader(state: DiscussionsState, onMore: (Boolean) -> Unit, onReply: (ReleaseComment) -> Unit,
    onImage: (List<String>, Int) -> Unit) {
    val topic = state.topic ?: return
    var collapsedComments by rememberSaveable(topic.key) { mutableStateOf(emptyList<Long>()) }
    val commentTree = remember(state.comments.items) { CommentTree.build(state.comments.items) }
    val commentRows = remember(commentTree, collapsedComments) { CommentTree.flatten(commentTree, collapsedComments.toSet()) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().testTag("discussion-reader")) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (topic.pinned) Text("Pinned discussion", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                AppHeading(topic.title, HeadingLevel.Section, style = MaterialTheme.typography.headlineMedium)
                Text("${topic.replies} replies · ${topic.views} views", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppHeading("Original post", HeadingLevel.Subsection, style = MaterialTheme.typography.titleSmall)
                ReleaseCommentCard(topic.originalPost, topic.browserUrl, onImage = onImage)
            }
        }
        item { AppHeading("Comments · newest threads first", HeadingLevel.Section, style = MaterialTheme.typography.titleMedium) }
        items(commentRows, key = { it.comment.id }) { row ->
            val comment = row.comment
            CommentThreadRow(row.depth) {
                ReleaseCommentCard(comment, topic.browserUrl, onReply = { onReply(comment) }, replyEnabled = !state.composer.posting,
                    collapsed = comment.id in collapsedComments,
                    onToggleThread = {
                        collapsedComments = if (comment.id in collapsedComments) collapsedComments - comment.id
                            else collapsedComments + comment.id
                    }, onImage = onImage)
            }
        }
        if (state.comments.busy) item { LoadingState("Loading comments…", Modifier.fillMaxWidth()) }
        state.comments.error?.let { item { ErrorPanel(it, onRetry = { onMore(false) }) } }
        if (!state.comments.busy && state.comments.error == null) {
            if (state.comments.items.isEmpty()) item { DiscussionEmpty("Start the conversation", "Be the first to comment on this topic.") }
            else if (state.comments.canLoadMore) item { AppTextButton("Load more comments", { onMore(false) }, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun DiscussionEmpty(title: String, detail: String) {
    AppSurface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DiscussionDate(timestamp: Long) {
    if (timestamp > 0) Text(remember(timestamp) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp * 1000)) },
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
