package org.mobilism.material.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val snackbar = remember { SnackbarHostState() }
    val savedScreens = rememberSaveableStateHolder()
    var gallery by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
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
                    val url = state.topic?.browserUrl ?: state.category?.browserUrl
                    if (url != null) AppIconButton(Icons.Outlined.OpenInBrowser, "Open in browser", { openForumBrowser(context, url) })
                    AppIconButton(Icons.Outlined.Refresh, "Refresh", {
                        when { state.topic != null -> model.loadComments(true); state.category != null -> model.loadTopics(true); else -> model.loadCategories() }
                    }, enabled = !state.composer.posting && !state.loading && !state.topics.busy && !state.comments.busy)
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
                            Text(if (auth is AuthState.SignedIn) if (state.composer.draft.isNotEmpty()) "Continue draft" else "Write a comment"
                                else "Sign in to comment", Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
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
    Column(Modifier.fillMaxSize()) {
        AppChoiceTabs(DiscussionArea.entries.map { it.label }, state.area.ordinal) { onArea(DiscussionArea.entries[it]) }
        key(state.area) {
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().testTag("discussion-categories")) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppHeading(if (state.area == DiscussionArea.Community) "Around the community" else "${state.area.label} discussions",
                            HeadingLevel.Section, style = MaterialTheme.typography.headlineMedium)
                        Text("Find help, share ideas, and join a conversation.", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (state.loading) item { LoadingState("Loading forums…", Modifier.fillMaxWidth()) }
                state.error?.let { item { ErrorPanel(it, onRetry = onRetry) } }
                state.categories.groupBy { it.section }.forEach { (section, categories) ->
                    if (section.isNotBlank()) item(key = "section:$section") {
                        AppHeading(section, HeadingLevel.Section, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                    }
                    items(categories, key = { it.key }) { category ->
                        ExpressiveCard({ onCategory(category) }, Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Icon(Icons.Outlined.Forum, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(category.name, style = MaterialTheme.typography.titleMedium)
                                    Text(category.topics?.let { "$it topics" } ?: "Browse conversations",
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (state.submittedQuery.isBlank()) "Latest activity · pinned topics included" else "Results for “${state.submittedQuery}”",
                            Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.submittedQuery.isNotBlank()) AppTextButton("Clear", onClear)
                    }
                }
                items(state.topics.items, key = { it.key }) { topic ->
                    ExpressiveCard({ onTopic(topic) }, Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (topic.pinned) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.PushPin, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("Pinned", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(topic.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(topic.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("${topic.replies} replies", style = MaterialTheme.typography.labelMedium)
                                Text("${topic.views} views", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DiscussionDate(topic.activityAt)
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
        item { AppHeading("Comments · newest first", HeadingLevel.Section, style = MaterialTheme.typography.titleMedium) }
        items(state.comments.items, key = { it.id }) { comment ->
            ReleaseCommentCard(comment, topic.browserUrl, onReply = { onReply(comment) }, replyEnabled = !state.composer.posting, onImage = onImage)
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
