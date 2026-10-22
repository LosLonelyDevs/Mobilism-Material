package org.mobilism.material.ui

import android.text.Html
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import org.mobilism.material.data.AuthState
import org.mobilism.material.data.ContentSection
import org.mobilism.material.data.FeedOrder
import org.mobilism.material.data.Release
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.ExpressiveCard
import org.mobilism.material.designsystem.LoadingState
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
    onSignOut: () -> Unit,
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    LaunchedEffect(state.section, state.order) { listState.scrollToItem(0) }
    LaunchedEffect(listState, state.items.size, state.busy, state.canLoadMore, state.error) {
        if (state.items.isNotEmpty() && !state.busy && state.canLoadMore && state.error == null) {
            snapshotFlow { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.items.size - 3 }
                .distinctUntilChanged().filter { it }.collect { onMore() }
        }
    }
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Text("Mobilism", Modifier.padding(24.dp), style = MaterialTheme.typography.headlineMedium)
                Text((auth as? AuthState.SignedIn)?.username ?: "Guest", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                ContentSection.entries.forEach { section ->
                    val interaction = remember { MutableInteractionSource() }
                    NavigationDrawerItem(
                        label = { Text(section.label) }, selected = state.section == section,
                        onClick = { scope.launch { drawer.close() }; onSection(section) },
                        interactionSource = interaction,
                        modifier = Modifier.padding(horizontal = 12.dp).focusOutline(interaction),
                    )
                }
                HorizontalDivider(Modifier.padding(16.dp))
                val settingsInteraction = remember { MutableInteractionSource() }
                NavigationDrawerItem(
                    label = { Text("Settings") }, selected = false,
                    icon = { Icon(Icons.Outlined.Settings, null) },
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
                MediumFlexibleTopAppBar(
                    title = { Text(state.section.label) },
                    subtitle = { Text(if (state.order == FeedOrder.Latest) "Latest releases" else "Most viewed releases") },
                    navigationIcon = { AppIconButton(Icons.Outlined.Menu, "Open navigation drawer", { scope.launch { drawer.open() } }) },
                    actions = { AppIconButton(Icons.Outlined.Refresh, "Refresh", onRefresh) },
                    scrollBehavior = scroll,
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding)) {
                PrimaryTabRow(selectedTabIndex = state.order.ordinal) {
                    FeedOrder.entries.forEach { order ->
                        val interaction = remember { MutableInteractionSource() }
                        Tab(
                            selected = state.order == order, onClick = { onOrder(order) },
                            interactionSource = interaction, modifier = Modifier.focusOutline(interaction),
                            text = { Text(if (order == FeedOrder.Latest) "Home" else "Top") },
                        )
                    }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.items, key = { it.id }, contentType = { "release" }) { release ->
                            ReleaseCard(release, { onRelease(release.id) })
                        }
                        if (state.busy) item(key = "loading") { LoadingState("Loading releases…", Modifier.fillMaxWidth()) }
                        state.error?.let { error -> item(key = "error") {
                            ErrorPanel(error, onRetry = if (state.items.isEmpty()) onRefresh else onMore)
                        } }
                        if (!state.busy && state.error == null && state.items.isEmpty()) item(key = "empty") {
                            Text("No releases found.", Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: Release, onClick: () -> Unit) {
    val title = remember(release.title) { Html.fromHtml(release.title, Html.FROM_HTML_MODE_LEGACY).toString() }
    ExpressiveCard(onClick, Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = release.thumbnail, contentDescription = null,
                placeholder = painterResource(R.drawable.ic_mobilism), error = painterResource(R.drawable.ic_mobilism),
                modifier = Modifier.size(72.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(release.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${release.replies} replies · ${release.views} views", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
