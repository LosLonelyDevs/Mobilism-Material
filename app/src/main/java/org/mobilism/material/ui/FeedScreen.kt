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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.ViewHeadline
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
import org.mobilism.material.designsystem.AppTextButton
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    onAnnouncements: () -> Unit = {},
    tab: BrowseTab = if (state.order == FeedOrder.Latest) BrowseTab.Latest else BrowseTab.Top,
    onTab: (BrowseTab) -> Unit = { onOrder(if (it == BrowseTab.Top) FeedOrder.Top else FeedOrder.Latest) },
    onHomeRefresh: () -> Unit = {},
    homeContent: (@Composable (() -> Unit) -> Unit)? = null,
    customCategories: org.mobilism.material.data.CustomCategoryStore? = null,
    onManageCategories: () -> Unit = {},
    compact: Boolean = false,
    onCompact: (Boolean) -> Unit = {},
    onReleasesViewed: (ContentSection, List<Release>) -> Unit = { _, _ -> },
    homeRefreshing: Boolean = false,
    homeBusy: Boolean = homeRefreshing,
) {
    val theme = LocalAppTheme.current
    val sectionStyle = state.section.style()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberSaveable(state.section, state.order, state.filters, saver = LazyListState.Saver) {
        LazyListState()
    }
    val savedTabs = rememberSaveableStateHolder()
    ObserveVisibleReleases(listState, state.section, state.items,
        enabled = tab == BrowseTab.Latest && state.order == FeedOrder.Latest, onViewed = onReleasesViewed)
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    var showFilters by remember { mutableStateOf(false) }
    val openFilters = { onLoadCategories(); showFilters = true }
    if (showFilters) CategoryFilterSheet(
        state = categories, initial = state.filters, onRetry = onLoadCategories,
        onApply = onFilters, onDismiss = { showFilters = false },
        customCategories = customCategories, onManageCategories = { showFilters = false; onManageCategories() },
    )
    LaunchedEffect(tab, listState, state.page, state.items.size, state.busy, state.canLoadMore, state.error, state.awaitingMore) {
        if (tab != BrowseTab.Home && !state.busy && state.canLoadMore && state.error == null && !state.awaitingMore) {
            if (state.items.isNotEmpty()) snapshotFlow { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.items.size - 3 }
                .distinctUntilChanged().filter { it }.collect { onMore() }
        }
    }
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(Modifier.fillMaxHeight().testTag("navigation-drawer")) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    AppHeading(stringResource(R.string.mobilism_name), HeadingLevel.Page, Modifier.padding(24.dp), style = MaterialTheme.typography.headlineMedium)
                    if (auth is AuthState.SignedIn) UsernameLink(auth.username, Modifier.padding(horizontal = 24.dp),
                        onOpen = { scope.launch { drawer.close() } })
                    else Text(stringResource(R.string.drawer_guest), Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.drawer_announcements)) }, selected = false,
                        icon = { Icon(Icons.Outlined.Campaign, null, tint = theme.featured) },
                        onClick = { scope.launch { drawer.close() }; onAnnouncements() },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    val discussionsInteraction = remember { MutableInteractionSource() }
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.drawer_discussions)) }, selected = false,
                        icon = { Icon(Icons.Outlined.ChatBubbleOutline, null, tint = theme.reading) },
                        onClick = { scope.launch { drawer.close() }; onDiscussions() },
                        interactionSource = discussionsInteraction,
                        modifier = Modifier.padding(horizontal = 12.dp).focusOutline(discussionsInteraction),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.drawer_following)) }, selected = false,
                        icon = { Icon(Icons.Outlined.Bookmarks, null, tint = theme.success) },
                        onClick = { scope.launch { drawer.close() }; onFollowing() },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.drawer_library)) }, selected = false,
                        icon = { Icon(Icons.Outlined.PhoneAndroid, null, tint = theme.featured) },
                        onClick = { scope.launch { drawer.close() }; onLibrary() },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    if (auth is AuthState.SignedIn) NavigationDrawerItem(
                        label = { Text(stringResource(R.string.drawer_messages)) }, selected = false,
                        icon = { Icon(Icons.Outlined.MailOutline, null, tint = theme.reading) },
                        onClick = { scope.launch { drawer.close() }; onMessages() },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    if (auth is AuthState.Guest) DrawerAccountAction(stringResource(R.string.drawer_login), onSignOut)
                }
                val settingsInteraction = remember { MutableInteractionSource() }
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.settings_title)) }, selected = false,
                    icon = { Icon(Icons.Outlined.Settings, null, tint = theme.personalization) },
                    onClick = { scope.launch { drawer.close() }; onSettings() },
                    interactionSource = settingsInteraction,
                    modifier = Modifier.padding(horizontal = 12.dp).focusOutline(settingsInteraction),
                )
                if (auth !is AuthState.Guest) DrawerAccountAction(stringResource(R.string.drawer_logout), onSignOut)
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
                    navigationIcon = { AppIconButton(Icons.Outlined.Menu, stringResource(R.string.drawer_open), { scope.launch { drawer.open() } }) },
                    actions = {
                        AppIconButton(Icons.Outlined.Search, stringResource(R.string.feed_search), onSearch)
                        if (tab != BrowseTab.Home) AppIconToggleButton(Icons.Outlined.ViewHeadline,
                            stringResource(if (compact) R.string.feed_use_detailed else R.string.feed_use_compact), compact, sectionStyle.accent, onCompact)
                        AppIconButton(Icons.Outlined.FilterList, stringResource(R.string.feed_filters), openFilters)
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
                            text = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
                StoreTabContent(tab, Modifier.weight(1f)) { selectedTab ->
                    savedTabs.SaveableStateProvider("${state.section}-$selectedTab") {
                        FeedRefreshContainer(
                            refreshing = if (selectedTab == BrowseTab.Home) homeRefreshing else state.refreshing,
                            busy = if (selectedTab == BrowseTab.Home) homeBusy else state.busy,
                            onRefresh = if (selectedTab == BrowseTab.Home) onHomeRefresh else onRefresh,
                        ) {
                        if (selectedTab == BrowseTab.Home && homeContent != null) homeContent(openFilters)
                        else Column {
                            ActiveFilterSummary(state.filters, openFilters, { onFilters(FeedFilters()) }, categories.items)
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.widthIn(max = 840.dp).fillMaxSize().testTag("feed-list"),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp,
                                        top = if (state.filters.isActive) 8.dp else 16.dp, bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp),
                                ) {
                                    items(state.items, key = { it.id }, contentType = { "release" }) { release ->
                                        ReleaseCard(release, sectionStyle.accent, compact,
                                            isNew = state.order == FeedOrder.Latest && release.id in state.newReleaseIds) { onRelease(release.id) }
                                    }
                                    if (state.busy && !state.refreshing)
                                        item(key = "loading") { LoadingState(stringResource(R.string.feed_loading), Modifier.fillMaxWidth()) }
                                    if (!state.busy && state.awaitingMore && state.error == null && state.canLoadMore)
                                        item(key = "continue") { AppTextButton(stringResource(R.string.feed_more), onMore, Modifier.fillMaxWidth()) }
                                    state.error?.let { error -> item(key = "error") {
                                        ErrorPanel(error, onRetry = if (state.items.isEmpty()) onRefresh else onMore)
                                    } }
                                    if (!state.busy && state.error == null && state.items.isEmpty() && !state.canLoadMore) item(key = "empty") {
                                        Text(stringResource(R.string.feed_empty), Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
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
}

@Composable
internal fun ReleaseCard(release: Release, accent: Color, compact: Boolean = false, isNew: Boolean = false, onClick: () -> Unit) {
    val title = remember(release.title, release.section) { ReleaseTitles.parse(release.title, release.section) }
    val thumbnailSize = if (compact) 88.dp else 128.dp
    val titleLines = 2
    val subtitleLines = if (compact) 1 else 2
    val titleStyle = MaterialTheme.typography.titleMedium
    val subtitleStyle = MaterialTheme.typography.bodySmall
    val subtitleGap = 4.dp
    val authorGap = 2.dp
    val textHeight = with(LocalDensity.current) {
        val subtitleHeight = subtitleStyle.lineHeight.toDp()
        val authorHeight = if (compact) subtitleHeight else maxOf(24.dp, subtitleHeight)
        titleStyle.lineHeight.toDp() * titleLines + subtitleHeight * subtitleLines + authorHeight + subtitleGap + authorGap
    }
    ExpressiveCard(onClick, Modifier.fillMaxWidth().testTag("release-${release.id}"), colors = CardDefaults.cardColors(
        containerColor = lerp(MaterialTheme.colorScheme.surfaceContainerHigh, accent, 0.06f),
    )) {
        val inset = if (compact) 12.dp else 16.dp
        Column(Modifier.padding(start = inset, top = inset, end = inset, bottom = 4.dp)) {
            Row(Modifier.fillMaxWidth().height(maxOf(thumbnailSize, textHeight)), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ReleaseArtwork(
                    model = release.thumbnail, contentDescription = null,
                    error = painterResource(R.drawable.ic_mobilism),
                    modifier = Modifier.size(thumbnailSize).clip(MaterialTheme.shapes.medium).testTag("release-artwork-${release.id}"),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppHeading(title.name, HeadingLevel.Subsection, Modifier.weight(1f), style = titleStyle,
                            maxLines = titleLines, overflow = TextOverflow.Ellipsis)
                        if (isNew) NewReleaseBadge(release.id)
                    }
                    if (title.subtitle.isNotBlank()) Text(title.subtitle, Modifier.padding(top = subtitleGap), style = subtitleStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = subtitleLines, overflow = TextOverflow.Ellipsis)
                    UsernameLink(release.author, Modifier.padding(top = authorGap), style = subtitleStyle,
                        showAvatar = !compact, lookupIdentity = !compact)
                }
            }
            ReleaseMetadataFooter(release, singleRow = true, spacing = 10.dp, listCard = true) {
                ReleasePostDate(release.publishedAt, textStyle = MaterialTheme.typography.labelSmall)
                ReleaseViewCount(release.views, numberOnly = true)
                if (release.replies > 0) {
                    val comments = pluralStringResource(R.plurals.feed_comment_count,
                        release.replies.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), release.replies)
                    Row(Modifier.clearAndSetSemantics { contentDescription = comments },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Text(release.replies.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerAccountAction(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    NavigationDrawerItem(
        label = { Text(label) }, selected = false,
        icon = { Icon(Icons.AutoMirrored.Outlined.ExitToApp, null) }, onClick = onClick,
        interactionSource = interaction, modifier = Modifier.padding(horizontal = 12.dp).focusOutline(interaction),
    )
}
