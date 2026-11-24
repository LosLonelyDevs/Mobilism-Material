/*
 * Hero composition adapted from Orion Store by RookieEnough and contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 * Source: StoreScreen.kt, commit bffea4103b05120904a9e0d1a8f3f0cac594c553.
 * Modified for Mobilism releases, theme roles, accessibility, and manual paging.
 */
package org.mobilism.material.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mobilism.material.R
import org.mobilism.material.data.Alphabetical
import org.mobilism.material.data.ContentSection
import org.mobilism.material.data.DiscussionCategory
import org.mobilism.material.data.DiscussionTopic
import org.mobilism.material.data.FeedOrder
import org.mobilism.material.data.LibraryEntry
import org.mobilism.material.data.Release
import org.mobilism.material.data.ReleaseCategory
import org.mobilism.material.data.ReleaseTitles
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.LoadingState
import org.mobilism.material.designsystem.LocalReducedMotion
import org.mobilism.material.designsystem.StoreCard
import org.mobilism.material.designsystem.StoreShape

@Composable
fun HomeScreen(state: HomeState, onRetry: (HomeCollection) -> Unit, onRelease: (Long) -> Unit,
    onMore: (FeedOrder) -> Unit, onCategory: (ReleaseCategory) -> Unit, onCategories: () -> Unit,
    onTopic: (DiscussionTopic, DiscussionCategory) -> Unit, onForum: (DiscussionCategory) -> Unit,
    customCategories: List<org.mobilism.material.data.CustomCategory> = emptyList(),
    onCustomCategory: (org.mobilism.material.data.CustomCategory) -> Unit = {},
    onReleasesViewed: (ContentSection, List<Release>) -> Unit = { _, _ -> }, library: List<LibraryEntry> = emptyList(),
    installed: Map<String, String>? = emptyMap(), account: String? = null, hideAdult: Boolean = false) {
    val section = state.section
    val context = LocalContext.current
    val store = remember(section, account, hideAdult, context) {
        if (section == ContentSection.Apps && account != null)
            DailyHomeStore(context.getSharedPreferences("daily-home", android.content.Context.MODE_PRIVATE), "$hideAdult:$account") else null
    }
    var saved by remember(store, state.day) { mutableStateOf(store?.read(state.day)) }
    val ready = installed != null && listOf(state.discovery, state.latest, state.popular, state.categories).all { it.loaded || it.error != null }
    val apps = remember(section, ready, state.discovery.items, library, installed, state.latest.items, state.popular.items,
        state.categories.items, state.day, saved, account) {
        if (section == ContentSection.Apps && ready) discoverPicks(state.discovery.items, library, state.latest.items,
            state.popular.items, state.categories.items, state.day, installed.orEmpty(), saved, account?.hashCode() ?: 0) else null
    }
    LaunchedEffect(store, state.day, apps?.selection) {
        val selection = apps?.selection ?: return@LaunchedEffect
        if (store != null && saved == null && (selection.discover.isNotEmpty() || selection.featured.isNotEmpty()) &&
            state.discovery.loaded && state.popular.loaded && state.latest.loaded && state.categories.loaded) {
            store.save(state.day, selection)
            saved = selection
        }
    }
    val highlights = if (section == ContentSection.Apps) apps?.discover.orEmpty()
        else state.popular.items.ifEmpty { state.latest.items }.take(5)
    val latest = apps?.latest ?: state.latest.items
    val popular = apps?.popular ?: state.popular.items
    val scroll = rememberLazyListState()
    val latestScroll = rememberLazyListState()
    var latestVisible by remember { mutableStateOf(false) }
    ObserveVisibleReleases(latestScroll, section, latest,
        isVisible = { latestVisible && scroll.layoutInfo.visibleItemsInfo.any { it.key == "latest" } }, onViewed = onReleasesViewed)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 1000.dp).fillMaxSize().testTag("home-${section.name}"),
            state = scroll,
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (highlights.isNotEmpty() || state.popular.busy || state.latest.busy || state.discovery.busy) item(key = "hero") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val heading = stringResource(when (section) {
                        ContentSection.Apps -> R.string.home_discover_apps
                        ContentSection.Books -> R.string.home_discover_books
                        ContentSection.Games -> R.string.home_discover_games
                    })
                    Text(heading,
                        Modifier.padding(horizontal = 20.dp).semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
                    if (highlights.isNotEmpty()) HomeHero(highlights, section, onRelease)
                    else LoadingState(stringResource(when (section) {
                        ContentSection.Apps -> R.string.home_finding_apps
                        ContentSection.Books -> R.string.home_finding_books
                        ContentSection.Games -> R.string.home_finding_games
                    }), Modifier.fillMaxWidth().heightIn(min = 220.dp))
                }
            }
            if (apps != null && apps.featured.isNotEmpty() && apps.categoryNames.isNotEmpty()) item(key = "daily-category") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val names = apps.categoryNames
                    HomeHeading(if (names.size == 2) stringResource(R.string.home_top_apps_two_categories, names[0], names[1])
                        else stringResource(R.string.home_top_apps_in_category, names.first()), null)
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(apps.featured, key = { _, release -> release.id }) { index, release ->
                            DailyCategoryTile(release, index, onRelease)
                        }
                    }
                }
            }
            item(key = "categories") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeHeading(stringResource(R.string.home_categories), onCategories)
                    val choices = categoryChoices(state.categories.items, customCategories)
                    if (choices.isNotEmpty()) LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(choices, key = { _, item -> item.key }) { index, category ->
                            CategoryTile(category.name, section, index) {
                                category.forum?.let(onCategory)
                                category.custom?.let(onCustomCategory)
                            }
                        }
                    }
                    if (choices.isEmpty() || state.categories.busy || state.categories.error != null) {
                        HomeShelfStatus(state.categories, stringResource(R.string.home_empty_categories)) { onRetry(HomeCollection.Categories) }
                    }
                }
            }
            if (section != ContentSection.Apps || state.reviews.visibleOnHome()) item(key = "reviews") {
                HomeForumShelf(stringResource(R.string.home_reviews), state.reviews, true,
                    onRetry = { onRetry(HomeCollection.Reviews) }, onTopic, onForum)
            }
            if (section != ContentSection.Apps || state.discussions.visibleOnHome()) item(key = "discussions") {
                HomeForumShelf(stringResource(R.string.home_discussions), state.discussions, false,
                    onRetry = { onRetry(HomeCollection.Discussions) }, onTopic, onForum)
            }
            if (latest.isNotEmpty() || state.latest.items.isEmpty()) item(key = "latest") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeHeading(stringResource(when (section) {
                        ContentSection.Apps -> R.string.home_latest_apps
                        ContentSection.Books -> R.string.home_latest_books
                        ContentSection.Games -> R.string.home_latest_games
                    }), { onMore(FeedOrder.Latest) })
                    if (latest.isNotEmpty()) LazyRow(state = latestScroll,
                        modifier = Modifier.onGloballyPositioned {
                            latestVisible = it.boundsInWindow().let { bounds -> bounds.width > 0 && bounds.height > 0 }
                        },
                        contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(latest, key = { it.id }) { release ->
                            LatestTile(release, section, onRelease, isNew = release.id in state.newReleaseIds)
                        }
                    }
                    HomeShelfStatus(state.latest, stringResource(R.string.home_empty_releases)) { onRetry(HomeCollection.Latest) }
                }
            }
            if (popular.isNotEmpty() || state.popular.items.isEmpty()) item(key = "popular") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeHeading(stringResource(when (section) {
                        ContentSection.Apps -> R.string.home_popular_apps
                        ContentSection.Books -> R.string.home_popular_books
                        ContentSection.Games -> R.string.home_popular_games
                    }), { onMore(FeedOrder.Top) })
                    if (popular.isNotEmpty()) LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(popular, key = { _, item -> item.id }) { index, release -> PopularTile(release, section, index, onRelease) }
                    }
                    HomeShelfStatus(state.popular, stringResource(R.string.home_empty_popular)) { onRetry(HomeCollection.Popular) }
                }
            }
        }
    }
}

private fun HomeForum.visibleOnHome() = busy || error != null || !topics?.items.isNullOrEmpty()

@Composable
private fun HomeHeading(title: String, onMore: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        if (onMore != null) AppIconButton(Icons.AutoMirrored.Outlined.ArrowForward, stringResource(R.string.home_more, title), onMore)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeHero(releases: List<Release>, section: ContentSection, onRelease: (Long) -> Unit) {
    val pager = rememberPagerState { releases.size }
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val reduced = LocalReducedMotion.current
    val windowWidth = LocalWindowInfo.current.containerSize.width
    val wide = with(LocalDensity.current) { windowWidth.toDp() } >= 600.dp
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
    val heroHeight = ((if (section == ContentSection.Apps) 172 else 220) * fontScale).dp
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalPager(pager, Modifier.fillMaxWidth().height(heroHeight).testTag("home-hero"),
            contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp,
            pageSize = if (wide) PageSize.Fixed(520.dp) else PageSize.Fill, key = { releases[it].id }) { index ->
            val release = releases[index]
            val title = remember(release.title, section) { ReleaseTitles.parse(release.title, section) }
            val tone = homeTone(index, prominent = true)
            StoreCard({ onRelease(release.id) }, Modifier.fillMaxSize(), color = tone.first, contentColor = tone.second) {
                val distance = { if (reduced) 0f else pager.getOffsetDistanceInPages(index).coerceIn(-1f, 1f) }
                StoreShape(index, 220.dp, tone.second.copy(alpha = 0.1f), Modifier.align(Alignment.BottomEnd).offset(x = 50.dp, y = 55.dp)
                    .graphicsLayer { translationX = distance() * 50.dp.toPx(); rotationZ = distance() * 25f })
                val images = remember(release) { (release.images + listOfNotNull(release.thumbnail)).distinct().take(2) }
                Box(Modifier.align(Alignment.BottomEnd).fillMaxWidth(0.4f).fillMaxHeight(0.84f).padding(end = 16.dp)
                    .offset(y = 16.dp).graphicsLayer { translationX = distance() * 28.dp.toPx() }) {
                    if (section == ContentSection.Apps) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(title.name.take(1), color = tone.second.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.displayLarge, modifier = Modifier.clearAndSetSemantics {})
                    } else {
                        images.take(1).takeIf { images.size > 1 }?.forEach { url ->
                            HomeArtwork(url, Modifier.fillMaxSize().offset(x = (-22).dp, y = 8.dp).graphicsLayer { rotationZ = -9f }, section)
                        }
                        HomeArtwork(images.lastOrNull(), Modifier.fillMaxSize().graphicsLayer { rotationZ = if (section == ContentSection.Books) 6f else 4f }, section)
                    }
                }
                Column(Modifier.fillMaxHeight().fillMaxWidth(0.59f).padding(20.dp)
                    .graphicsLayer { translationX = -distance() * 16.dp.toPx() }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(section.style().icon, null, Modifier.size(28.dp), tint = tone.second)
                    Spacer(Modifier.weight(1f))
                    Text(title.name, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 3, overflow = TextOverflow.Ellipsis, color = tone.second)
                    if (section == ContentSection.Apps) {
                        val detail = listOfNotNull(release.categoryName, title.version?.let { stringResource(R.string.follow_version, it) })
                            .joinToString(" · ")
                        if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.labelLarge, color = tone.second,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    } else if (section == ContentSection.Books && title.subtitle.isNotBlank()) Text(title.subtitle,
                        style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = tone.second)
                    else UsernameLink(release.author, style = MaterialTheme.typography.labelMedium, maxLines = 2, color = tone.second)
                    ReleaseViewCount(release.views, color = tone.second)
                }
            }
        }
        if (releases.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            releases.indices.forEach { index ->
                val label = stringResource(R.string.home_featured_position, index + 1, releases.size)
                // Full touch targets keep the small pager marks accessible.
                StoreCard({ scope.launch { if (reduced) pager.scrollToPage(index) else pager.animateScrollToPage(index, animationSpec = motion) } },
                    Modifier.size(48.dp).semantics {
                        contentDescription = label
                        selected = pager.currentPage == index
                    },
                    color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.align(Alignment.Center).height(6.dp).width(if (pager.currentPage == index) 24.dp else 6.dp)
                        .clip(MaterialTheme.shapes.extraLarge).background(if (pager.currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DailyCategoryTile(release: Release, index: Int, onRelease: (Long) -> Unit) {
    val title = release.appTitle
    val tone = homeTone(index)
    StoreCard({ onRelease(release.id) }, Modifier.width(184.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StoreShape(index, 36.dp, tone.first) {
                Text(title.name.take(1), Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleSmall, color = tone.second)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title.name, style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                title.version?.let { Text(stringResource(R.string.follow_version, it), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun LatestTile(release: Release, section: ContentSection, onRelease: (Long) -> Unit, isNew: Boolean = false) {
    val title = remember(release.title, section) { ReleaseTitles.parse(release.title, section) }
    StoreCard({ onRelease(release.id) }, Modifier.width(if (section == ContentSection.Books) 144.dp else 160.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box {
                HomeArtwork(release.thumbnail, Modifier.fillMaxWidth().aspectRatio(if (section == ContentSection.Books) 0.68f else 1f), section)
                if (isNew) NewReleaseBadge(release.id, Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
            Text(title.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            if (section == ContentSection.Books && title.subtitle.isNotBlank()) Text(title.subtitle,
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            else UsernameLink(release.author, style = MaterialTheme.typography.labelMedium)
            ReleasePostDate(release.publishedAt, textStyle = MaterialTheme.typography.labelSmall)
            ReleaseMetadataFooter(release, singleRow = true, showEmptyRow = release.views > 0) {
                ReleaseViewCount(release.views, numberOnly = true)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PopularTile(release: Release, section: ContentSection, index: Int, onRelease: (Long) -> Unit) {
    val title = remember(release.title, section) { ReleaseTitles.parse(release.title, section) }
    val tone = homeTone(index)
    StoreCard({ onRelease(release.id) }, Modifier.width(200.dp), color = tone.first, contentColor = tone.second) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StoreShape(index, 160.dp, tone.second.copy(alpha = 0.09f)) {
                HomeArtwork(release.thumbnail, Modifier.width(if (section == ContentSection.Books) 84.dp else 106.dp)
                    .height(if (section == ContentSection.Books) 126.dp else 106.dp), section)
            }
            Text(title.name, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            ReleasePostDate(release.publishedAt, textStyle = MaterialTheme.typography.labelSmall)
            ReleaseMetadataFooter(release, singleRow = true, showEmptyRow = release.views > 0) {
                ReleaseViewCount(release.views, numberOnly = true, textStyle = MaterialTheme.typography.labelMedium, color = tone.second)
            }
        }
    }
}

@Composable
private fun CategoryTile(name: String, section: ContentSection, index: Int, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tone = homeTone(index)
    StoreCard(onClick, Modifier.width(120.dp), color = MaterialTheme.colorScheme.background, interaction = interaction) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StoreShape(index, 80.dp, tone.first, pressed = pressed) {
                Icon(section.style().icon, null, Modifier.size(32.dp), tint = tone.second)
            }
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HomeArtwork(url: String?, modifier: Modifier, section: ContentSection) {
    if (url == null) Box(modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Icon(section.style().icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    } else ReleaseArtwork(url, null, modifier.clip(MaterialTheme.shapes.large), contentScale = ContentScale.Crop,
        error = painterResource(R.drawable.ic_mobilism))
}

@Composable
private fun HomeForumShelf(title: String, state: HomeForum, reviews: Boolean, onRetry: () -> Unit,
    onTopic: (DiscussionTopic, DiscussionCategory) -> Unit, onForum: (DiscussionCategory) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeHeading(title, state.topics?.category?.let { category -> { onForum(category) } })
        val topics = state.topics
        if (topics != null && topics.items.isNotEmpty()) Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            topics.items.forEach { topic ->
                StoreCard({ onTopic(topic, topics.category) }, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (reviews) Icons.Outlined.RateReview else Icons.Outlined.ChatBubbleOutline, null,
                            Modifier.size(24.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(topic.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            UsernameLink(topic.author, suffix = pluralStringResource(R.plurals.home_reply_suffix,
                                topic.replies.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(), topic.replies),
                                style = MaterialTheme.typography.labelMedium)
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (state.busy) LoadingState(stringResource(if (reviews) R.string.home_loading_reviews else R.string.home_loading_discussions), Modifier.fillMaxWidth())
        state.error?.let { ErrorPanel(it, Modifier.padding(horizontal = 20.dp), onRetry) }
        if (!state.busy && state.error == null && state.loaded && topics?.items.isNullOrEmpty()) {
            Text(stringResource(if (reviews) R.string.home_empty_reviews else R.string.home_empty_discussions),
                Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun <T> HomeShelfStatus(state: HomeShelf<T>, empty: String, onRetry: () -> Unit) {
    if (state.busy) LoadingState(stringResource(R.string.home_loading), Modifier.fillMaxWidth())
    state.error?.let { ErrorPanel(it, Modifier.padding(horizontal = 20.dp), onRetry) }
    if (state.loaded && !state.busy && state.error == null && state.items.isEmpty()) Text(empty, Modifier.padding(horizontal = 20.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun homeTone(index: Int, prominent: Boolean = false): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return if (prominent) when (index % 3) {
        0 -> colors.primary to colors.onPrimary
        1 -> colors.tertiary to colors.onTertiary
        else -> colors.secondary to colors.onSecondary
    } else when (index % 3) {
        0 -> colors.secondaryContainer to colors.onSecondaryContainer
        1 -> colors.tertiaryContainer to colors.onTertiaryContainer
        else -> colors.primaryContainer to colors.onPrimaryContainer
    }
}
