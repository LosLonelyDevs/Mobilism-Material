package org.mobilism.material.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.ViewHeadline
import androidx.compose.material3.Badge
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.mobilism.material.R
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Bookmarks
import org.mobilism.material.data.AuthState
import org.mobilism.material.data.ContentSection
import org.mobilism.material.data.FeedOrder
import org.mobilism.material.data.FeedFilters
import org.mobilism.material.data.Release
import org.mobilism.material.data.ReleaseTitles
import org.mobilism.material.data.Alphabetical
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.AppIconToggleButton
import org.mobilism.material.designsystem.ExpressiveCard
import org.mobilism.material.designsystem.LoadingState
import org.mobilism.material.designsystem.LocalAppTheme
import org.mobilism.material.designsystem.focusOutline
import org.mobilism.material.designsystem.StoreTabContent

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeedScreen(
    state: FeedState,
    auth: AuthState,
    onSection: (ContentSection) -> Unit,
    onOrder: (FeedOrder) -> Unit,
    onRefresh: () -> Unit,
    onMore: () -> Unit,
    onRelease: (Long) -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    onLibrary: () -> Unit,
    onFollowing: () -> Unit,
    onSignOut: () -> Unit,
    categories: CategoryState,
    onLoadCategories: () -> Unit,
    onFilters: (FeedFilters) -> Unit,
    onMessages: () -> Unit = {},
    onDiscussions: () -> Unit = {},
    tab: BrowseTab = if (state.order == FeedOrder.Latest) BrowseTab.Latest else BrowseTab.Top,
    onTab: (BrowseTab) -> Unit = { onOrder(if (it == BrowseTab.Top) FeedOrder.Top else FeedOrder.Latest) },
    onHomeRefresh: () -> Unit = {},
    homeContent: (@Composable (() -> Unit) -> Unit)? = null,
    customCategories: org.mobilism.material.data.CustomCategoryStore? = null,
    onManageCategories: () -> Unit = {},
    compact: Boolean = false,
    onCompact: (Boolean) -> Unit = {},
) {
    val theme = LocalAppTheme.current
    val sectionStyle = state.section.style()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberSaveable(state.section, state.order, state.filters, saver = LazyListState.Saver) {
        LazyListState()
    }
    val savedTabs = rememberSaveableStateHolder()
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    var showFilters by remember { mutableStateOf(false) }
    val openFilters = { onLoadCategories(); showFilters = true }
    if (showFilters) CategoryFilterSheet(
        state = categories, initial = state.filters, onRetry = onLoadCategories,
        onApply = onFilters, onDismiss = { showFilters = false },
        customCategories = customCategories, onManageCategories = { showFilters = false; onManageCategories() },
    )
    LaunchedEffect(tab, listState, state.items.size, state.busy, state.canLoadMore, state.error) {
        if (tab != BrowseTab.Home && state.items.isNotEmpty() && !state.busy && state.canLoadMore && state.error == null && !state.awaitingMore) {
            snapshotFlow { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.items.size - 3 }
                .distinctUntilChanged().filter { it }.collect { onMore() }
        }
    }
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(Modifier.fillMaxHeight().testTag("navigation-drawer")) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    AppHeading("Mobilism", HeadingLevel.Page, Modifier.padding(24.dp), style = MaterialTheme.typography.headlineMedium)
                    Text((auth as? AuthState.SignedIn)?.username ?: "Guest", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Alphabetical.sorted(ContentSection.entries) { it.label }.forEach { section ->
                        val style = section.style()
                        val interaction = remember { MutableInteractionSource() }
                        NavigationDrawerItem(
                            label = { Text(section.label) }, selected = state.section == section,
                            icon = { Icon(style.icon, null) },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = style.accent, selectedTextColor = style.onAccent,
                                selectedIconColor = style.onAccent, unselectedTextColor = style.accent,
                                unselectedIconColor = style.accent,
                            ),
                            onClick = { scope.launch { drawer.close() }; onSection(section) },
                            interactionSource = interaction,
                            modifier = Modifier.padding(horizontal = 12.dp).focusOutline(interaction),
                        )
                    }
                    HorizontalDivider(Modifier.padding(16.dp))
                    val discussionsInteraction = remember { MutableInteractionSource() }
                    NavigationDrawerItem(
                        label = { Text("Discussions") }, selected = false,
                        icon = { Icon(Icons.Outlined.ChatBubbleOutline, null, tint = theme.reading) },
                        onClick = { scope.launch { drawer.close() }; onDiscussions() },
                        interactionSource = discussionsInteraction,
                        modifier = Modifier.padding(horizontal = 12.dp).focusOutline(discussionsInteraction),
                    )
                    NavigationDrawerItem(
                        label = { Text("Following") }, selected = false,
                        icon = { Icon(Icons.Outlined.Bookmarks, null, tint = theme.success) },
                        onClick = { scope.launch { drawer.close() }; onFollowing() },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text("My Applications") }, selected = false,
                        icon = { Icon(Icons.Outlined.PhoneAndroid, null, tint = theme.featured) },
                        onClick = { scope.launch { drawer.close() }; onLibrary() },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    if (auth is AuthState.SignedIn) NavigationDrawerItem(
                        label = { Text("Messages") }, selected = false,
                        icon = { Icon(Icons.Outlined.MailOutline, null, tint = theme.reading) },
                        onClick = { scope.launch { drawer.close() }; onMessages() },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    val settingsInteraction = remember { MutableInteractionSource() }
                    NavigationDrawerItem(
                        label = { Text("Settings") }, selected = false,
                        icon = { Icon(Icons.Outlined.Settings, null, tint = theme.personalization) },
                        onClick = { scope.launch { drawer.close() }; onSettings() },
                        interactionSource = settingsInteraction,
                        modifier = Modifier.padding(horizontal = 12.dp).focusOutline(settingsInteraction),
                    )
                    val logoutInteraction = remember { MutableInteractionSource() }
                    NavigationDrawerItem(
                        label = { Text(if (auth is AuthState.Guest) "Login" else "Logout") }, selected = false,
                        icon = { Icon(Icons.AutoMirrored.Outlined.ExitToApp, null) }, onClick = onSignOut,
                        interactionSource = logoutInteraction,
                        modifier = Modifier.padding(horizontal = 12.dp).focusOutline(logoutInteraction),
                    )
                }
            }
        },
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        AppHeading(state.section.label, HeadingLevel.Page, style = MaterialTheme.typography.titleLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = { AppIconButton(Icons.Outlined.Menu, "Open navigation drawer", { scope.launch { drawer.open() } }) },
                    actions = {
                        AppIconButton(Icons.Outlined.Search, "Search releases", onSearch)
                        if (tab != BrowseTab.Home) AppIconToggleButton(Icons.Outlined.ViewHeadline,
                            if (compact) "Use detailed list" else "Use compact list", compact, sectionStyle.accent, onCompact)
                        AppIconButton(Icons.Outlined.FilterList, "Filters", openFilters)
                        AppIconButton(Icons.Outlined.Refresh, "Refresh", if (tab == BrowseTab.Home) onHomeRefresh else onRefresh)
                    },
                    scrollBehavior = scroll,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = lerp(MaterialTheme.colorScheme.surface, sectionStyle.accent, 0.1f),
                        scrolledContainerColor = lerp(MaterialTheme.colorScheme.surface, sectionStyle.accent, 0.16f),
                        actionIconContentColor = sectionStyle.accent,
                    ),
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding)) {
                val tabs = if (homeContent != null) BrowseTab.entries else listOf(BrowseTab.Latest, BrowseTab.Top)
                PrimaryTabRow(selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0)) {
                    tabs.forEach { item ->
                        val interaction = remember { MutableInteractionSource() }
                        Tab(
                            selected = tab == item, onClick = { onTab(item) },
                            selectedContentColor = sectionStyle.accent,
                            interactionSource = interaction, modifier = Modifier.focusOutline(interaction),
                            text = { Text(item.label) },
                        )
                    }
                }
                StoreTabContent(tab, Modifier.weight(1f)) { selectedTab ->
                    savedTabs.SaveableStateProvider("${state.section}-$selectedTab") {
                        if (selectedTab == BrowseTab.Home && homeContent != null) homeContent(openFilters)
                        else Column {
                            ActiveFilterSummary(state.filters, openFilters, { onFilters(FeedFilters()) })
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.widthIn(max = 840.dp).fillMaxSize().testTag("feed-list"),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp),
                                ) {
                                    items(state.items, key = { it.id }, contentType = { "release" }) { release ->
                                        ReleaseCard(release, sectionStyle.accent, compact,
                                            isNew = state.order == FeedOrder.Latest && release.id in state.newReleaseIds) { onRelease(release.id) }
                                    }
                                    if (state.busy) item(key = "loading") { LoadingState("Loading releases…", Modifier.fillMaxWidth()) }
                                    state.error?.let { error -> item(key = "error") {
                                        ErrorPanel(error, onRetry = if (state.items.isEmpty()) onRefresh else onMore)
                                    } }
                                    if (!state.busy && state.error == null && state.awaitingMore) item(key = "continue") {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("No matches in the posts just checked.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            org.mobilism.material.designsystem.ExpressiveButton(onMore, Modifier.fillMaxWidth()) { Text("Keep looking") }
                                        }
                                    }
                                    if (!state.busy && state.error == null && state.items.isEmpty() && !state.canLoadMore) item(key = "empty") {
                                        Text("No releases found.", Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReleaseCard(release: Release, accent: Color, compact: Boolean = false, isNew: Boolean = false, onClick: () -> Unit) {
    val title = remember(release.title, release.section) { ReleaseTitles.parse(release.title, release.section) }
    val theme = LocalAppTheme.current
    ExpressiveCard(onClick, Modifier.fillMaxWidth().testTag("release-${release.id}"), colors = CardDefaults.cardColors(
        containerColor = lerp(MaterialTheme.colorScheme.surfaceContainerHigh, accent, 0.06f),
    )) {
        Row(Modifier.padding(if (compact) 12.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = release.thumbnail, contentDescription = null,
                placeholder = painterResource(R.drawable.ic_mobilism), error = painterResource(R.drawable.ic_mobilism),
                modifier = Modifier.size(if (compact) 40.dp else 72.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppHeading(title.name, HeadingLevel.Subsection, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        maxLines = if (compact) 2 else 3, overflow = TextOverflow.Ellipsis)
                    if (isNew) Badge(containerColor = theme.success, contentColor = theme.onSuccess,
                        modifier = Modifier.testTag("new-release-${release.id}").semantics {
                            contentDescription = "New since your last visit"
                        }) { Text("NEW") }
                }
                if (title.subtitle.isNotBlank()) Text(
                    title.subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (compact) 1 else 3, overflow = TextOverflow.Ellipsis,
                )
                if (!compact) Text(release.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!compact) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Text("${release.replies} replies", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (!compact) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.Visibility, null, Modifier.size(14.dp), tint = theme.featured)
                        Text("${release.views} views", style = MaterialTheme.typography.labelSmall, color = theme.featured)
                    }
                    ReleasePostDate(release.publishedAt, textStyle = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
