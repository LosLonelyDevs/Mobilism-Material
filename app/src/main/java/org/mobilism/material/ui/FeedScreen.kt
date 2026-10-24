package org.mobilism.material.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Visibility
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.ExpressiveCard
import org.mobilism.material.designsystem.LoadingState
import org.mobilism.material.designsystem.LocalAppTheme
import org.mobilism.material.designsystem.focusOutline

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
) {
    val theme = LocalAppTheme.current
    val sectionStyle = state.section.style()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    var showFilters by remember { mutableStateOf(false) }
    val openFilters = { onLoadCategories(); showFilters = true }
    LaunchedEffect(state.section, state.order, state.filters) { listState.scrollToItem(0) }
    if (showFilters) CategoryFilterSheet(
        state = categories, initial = state.filters, onRetry = onLoadCategories,
        onApply = onFilters, onDismiss = { showFilters = false },
    )
    LaunchedEffect(listState, state.items.size, state.busy, state.canLoadMore, state.error) {
        if (state.items.isNotEmpty() && !state.busy && state.canLoadMore && state.error == null && !state.awaitingMore) {
            snapshotFlow { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.items.size - 3 }
                .distinctUntilChanged().filter { it }.collect { onMore() }
        }
    }
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                AppHeading("Mobilism", HeadingLevel.Page, Modifier.padding(24.dp), style = MaterialTheme.typography.headlineMedium)
                Text((auth as? AuthState.SignedIn)?.username ?: "Guest", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                ContentSection.entries.forEach { section ->
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
                        AppIconButton(Icons.Outlined.FilterList, "Filters", openFilters)
                        AppIconButton(Icons.Outlined.Refresh, "Refresh", onRefresh)
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
                PrimaryTabRow(selectedTabIndex = state.order.ordinal) {
                    FeedOrder.entries.forEach { order ->
                        val interaction = remember { MutableInteractionSource() }
                        Tab(
                            selected = state.order == order, onClick = { onOrder(order) },
                            selectedContentColor = if (order == FeedOrder.Latest) MaterialTheme.colorScheme.tertiary else theme.featured,
                            interactionSource = interaction, modifier = Modifier.focusOutline(interaction),
                            text = { Text(order.label) },
                        )
                    }
                }
                ActiveFilterSummary(state.filters, openFilters, { onFilters(FeedFilters()) })
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.items, key = { it.id }, contentType = { "release" }) { release ->
                            ReleaseCard(release, sectionStyle.accent, { onRelease(release.id) })
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

@Composable
internal fun ReleaseCard(release: Release, accent: Color, onClick: () -> Unit) {
    val title = remember(release.title) { ReleaseTitles.parse(release.title) }
    val theme = LocalAppTheme.current
    ExpressiveCard(onClick, Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = lerp(MaterialTheme.colorScheme.surfaceContainerHigh, accent, 0.06f),
    )) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = release.thumbnail, contentDescription = null,
                placeholder = painterResource(R.drawable.ic_mobilism), error = painterResource(R.drawable.ic_mobilism),
                modifier = Modifier.size(72.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppHeading(title.name, HeadingLevel.Subsection, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val details = listOfNotNull(title.version?.let { "v$it" }, title.build?.let { "build $it" }) + title.tags
                if (details.isNotEmpty()) Text(
                    details.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
                Text(release.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Text("${release.replies} replies", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.Visibility, null, Modifier.size(14.dp), tint = theme.featured)
                        Text("${release.views} views", style = MaterialTheme.typography.labelSmall, color = theme.featured)
                    }
                    ReleasePostDate(release.publishedAt, textStyle = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
