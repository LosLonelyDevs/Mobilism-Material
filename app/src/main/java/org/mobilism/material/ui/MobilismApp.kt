package org.mobilism.material.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.mobilism.material.MobilismApplication
import org.mobilism.material.R
import org.mobilism.material.data.AuthState
import org.mobilism.material.data.DiscussionCategory
import org.mobilism.material.data.DiscussionTopic
import org.mobilism.material.data.FeedFilters
import org.mobilism.material.data.FeedOrder
import org.mobilism.material.designsystem.LoadingState
import org.mobilism.material.designsystem.screenMotion

internal fun NavController.openReleaseTopic(id: Long) {
    if (isCurrentTopic(id)) return
    // Each topic owns its detail ViewModel and scroll position on the back stack.
    navigate("release/$id")
}

private fun NavController.isCurrentTopic(id: Long) =
    currentDestination?.route?.substringBefore('?') in setOf("release/{id}", "announcement/{forum}/{id}") &&
        currentBackStackEntry?.arguments?.getLong("id") == id

internal fun NavController.openForumTopic(topic: org.mobilism.material.ReleaseLinks.Topic) {
    if (!isCurrentTopic(topic.id)) navigate("release/${topic.id}?forum=${topic.forumId ?: 0}")
}

internal fun NavController.openAnnouncement(id: Long, forum: Long, repository: org.mobilism.material.data.DefaultMobilismRepository): Boolean {
    if (!repository.isAnnouncementForum(forum)) return false
    if (!isCurrentTopic(id)) navigate("announcement/$forum/$id")
    return true
}

@Composable
fun MobilismApp(app: MobilismApplication, notificationRelease: Long? = null, fromNotification: Boolean = true,
    notificationMessage: org.mobilism.material.messages.MessageNotificationTarget? = null,
    linkedPost: Long? = null,
    onNotificationHandled: () -> Unit = {}) {
    val model: MobilismViewModel = viewModel(factory = viewModelFactory {
        initializer { MobilismViewModel(app, app.repository, app.appearance, app.debrid, app.library, app.contentPreferences, app.developer, app.customCategories) }
    })
    val auth by model.auth.collectAsStateWithLifecycle()
    val updater: org.mobilism.material.updates.AppUpdateViewModel = viewModel(factory = viewModelFactory {
        initializer { org.mobilism.material.updates.AppUpdateViewModel(app) }
    })
    val changelogStore = remember(app) { org.mobilism.material.updates.AppChangelogStore(app) }
    var changelog by remember(changelogStore) { mutableStateOf(changelogStore.pending()) }
    AppUpdateHost(app.appUpdates, updater, app.remoteControl, changelog, onChangelogDismiss = {
        changelog?.let { changelogStore.markSeen(it.version) }
        changelog = null
    }) { onUpdates ->
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            DownloadCompletionHost {
                // Auth boundaries discard the previous account's navigation and screen state.
                key(auth) {
                    when (auth) {
                        AuthState.Restoring -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingState(stringResource(R.string.opening_mobilism))
                        }
                        AuthState.SignedOut -> AccountNavigation(model, app.diagnostics)
                        else -> MainNavigation(model, auth, notificationRelease, notificationMessage, fromNotification, linkedPost, onNotificationHandled, onUpdates)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountNavigation(model: MobilismViewModel, diagnostics: org.mobilism.material.settings.DiagnosticLogStore) {
    val autofill = LocalAutofillManager.current
    val nav = rememberNavController()
    val motion = screenMotion()
    val login by model.login.collectAsStateWithLifecycle()
    NavHost(
        navController = nav, startDestination = "login",
        enterTransition = { motion.enter }, exitTransition = { motion.exit },
        popEnterTransition = { motion.popEnter }, popExitTransition = { motion.popExit },
    ) {
        composable("login") {
            LoginScreen(
                state = login,
                onSignIn = { username, password -> model.signIn(username, password) { autofill?.commit() } },
                onGuest = { autofill?.cancel(); model.continueAsGuest() },
                onRegister = { autofill?.cancel(); nav.navigate("register") { launchSingleTop = true } },
                onForgot = { autofill?.cancel(); nav.navigate("recover") { launchSingleTop = true } },
                diagnostics = diagnostics,
            )
        }
        composable("register") {
            AccountHelpScreen(register = true, model.repository, onBack = { nav.popBackStack() })
        }
        composable("recover") {
            AccountHelpScreen(register = false, model.repository, onBack = { nav.popBackStack() })
        }
    }
}

@Composable
private fun MainNavigation(model: MobilismViewModel, auth: AuthState, notificationRelease: Long?,
    notificationMessage: org.mobilism.material.messages.MessageNotificationTarget?, fromNotification: Boolean, linkedPost: Long?,
    onNotificationHandled: () -> Unit, onUpdates: () -> Unit) {
    val nav = rememberNavController()
    val motion = screenMotion()
    val feed by model.feed.collectAsStateWithLifecycle()
    val categories by model.categories.collectAsStateWithLifecycle()
    val appearance by model.appearance.state.collectAsStateWithLifecycle()
    val debridState by model.debrid.state.collectAsStateWithLifecycle()
    val downloads by model.downloads.state.collectAsStateWithLifecycle()
    val developer by model.developer.state.collectAsStateWithLifecycle()
    val localCategories by model.customCategories.state.collectAsStateWithLifecycle()
    val categoryStore = model.customCategories.takeIf { developer.customCategoriesActive }
    val debrid = if (developer.debridEnabled) debridState else org.mobilism.material.data.DebridState(enabled = false)
    val library by model.library.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val followPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val app = context.applicationContext as MobilismApplication
    val catalogs by app.repository.categoryCatalogs.collectAsStateWithLifecycle()
    val hideAdult by model.contentPreferences.hideAdult.collectAsStateWithLifecycle()
    val catalog = catalogs[hideAdult]
    val forumNames = remember(catalog) { catalog.orEmpty().values.flatten().associate { it.id to it.name } }
    if (auth is AuthState.SignedIn) MessageNotificationPermissionPrompt(app)
    var homeForum by remember { mutableStateOf<Pair<DiscussionTopic?, DiscussionCategory>?>(null) }
    var openCategoryFeed by rememberSaveable { mutableStateOf(false) }
    var openingTab by rememberSaveable {
        mutableStateOf<BrowseTab?>(when (model.landingPage.state.value.order) {
            FeedOrder.Latest -> BrowseTab.Latest
            FeedOrder.Top -> BrowseTab.Top
            null -> BrowseTab.Home
        })
    }
    LaunchedEffect(Unit) {
        if (auth is AuthState.SignedIn) model.releaseAfterSignIn?.let {
            nav.openReleaseTopic(it)
            model.releaseSignInHandled()
        }
        if (auth is AuthState.SignedIn && model.discussionAfterSignIn != null) nav.navigate("discussions") { launchSingleTop = true }
    }
    fun openComment(post: Long, topic: Long?) {
        val current = nav.currentBackStackEntry
        if (current?.destination?.route == "comment/{post}?topic={topic}" && current.arguments?.getLong("post") == post) return
        nav.navigate("comment/$post?topic=${topic ?: 0}")
    }
    LaunchedEffect(notificationRelease, linkedPost) {
        if (linkedPost != null) {
            openComment(linkedPost, notificationRelease)
            onNotificationHandled()
        } else notificationRelease?.takeIf { it > 0 }?.let { id ->
            if (fromNotification) nav.navigate("following") { launchSingleTop = true }
            if (id == org.mobilism.material.data.DownloadHelp.TOPIC_ID) nav.navigate("download-help") { launchSingleTop = true }
            else nav.openReleaseTopic(id)
            onNotificationHandled()
        }
    }
    LaunchedEffect(notificationMessage, auth) {
        notificationMessage?.let { target ->
            if (auth is AuthState.SignedIn && target.accountKey == org.mobilism.material.messages.MessageAlertStore.accountKey(auth.username)) {
                nav.navigate("messages") { launchSingleTop = true }
            } else onNotificationHandled()
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalCommentGateway provides app.repository.commentGateway,
        LocalMemberIdentities provides app.memberIdentities,
        LocalForumCategoryNames provides forumNames,
        LocalReleaseAssignments provides localCategories.takeIf { categoryStore != null },
        LocalOpenReleaseCategory provides { section, filters ->
            model.selectSection(section)
            model.applyFilters(filters)
            openCategoryFeed = true
            if (nav.currentDestination?.route != "feed") nav.popBackStack("feed", false)
        },
        LocalForumFormatting provides ForumFormattingOptions(appearance.releaseTextCleanup, developer.markdownSyntaxActive),
        LocalDownloadHelpHandler provides { nav.navigate("download-help") { launchSingleTop = true } },
        LocalAnnouncementLinkHandler provides { id, forum -> nav.openAnnouncement(id, forum, app.repository) },
        LocalLinkedTopicHandler provides { topic ->
            if (topic.forumId in forumNames) nav.openReleaseTopic(topic.id) else nav.openForumTopic(topic)
        },
        LocalCommentLinkHandler provides { link -> openComment(link.postId, link.topicId); true },
        LocalProfileHandler provides { reference ->
            nav.navigate("profile?user=${reference.id ?: 0}&name=${android.net.Uri.encode(reference.username.orEmpty())}") { launchSingleTop = true }
        },
    ) {
    NavHost(
        navController = nav, startDestination = "feed",
        enterTransition = { motion.enter }, exitTransition = { motion.exit },
        popEnterTransition = { motion.popEnter }, popExitTransition = { motion.popExit },
    ) {
        composable("feed") { entry ->
            val home: HomeViewModel = viewModel(viewModelStoreOwner = entry, factory = viewModelFactory {
                initializer { HomeViewModel(app.repository, feed.section, model.contentPreferences.hideAdult,
                    observeNewReleases = model::observeNewReleases) }
            })
            val homeState by home.state.collectAsStateWithLifecycle()
            var tab by rememberSaveable(feed.section) { mutableStateOf(openingTab ?: BrowseTab.Home) }
            LaunchedEffect(openCategoryFeed) {
                if (openCategoryFeed) {
                    tab = if (feed.order == FeedOrder.Top) BrowseTab.Top else BrowseTab.Latest
                    openCategoryFeed = false
                }
            }
            LaunchedEffect(tab, feed.section, hideAdult) {
                if (tab != BrowseTab.Home && catalog == null) model.loadCategories()
            }
            LaunchedEffect(Unit) { openingTab = null }
            DisposableEffect(tab, feed.section, feed.order, feed.filters) {
                home.selectSection(feed.section)
                home.setVisible(tab == BrowseTab.Home)
                model.setFeedVisible(tab != BrowseTab.Home)
                onDispose { home.setVisible(false); model.setFeedVisible(false) }
            }
            val openRelease: (Long) -> Unit = nav::openReleaseTopic
            val openFeed: (FeedOrder, FeedFilters) -> Unit = { order, filters ->
                model.selectOrder(order)
                model.applyFilters(filters)
                tab = if (order == FeedOrder.Top) BrowseTab.Top else BrowseTab.Latest
            }
            FeedScreen(
                state = feed, auth = auth,
                compact = appearance.compactFeed, onCompact = model.appearance::compactFeed,
                onReleasesViewed = model::markReleasesViewed,
                onSection = model::selectSection, onOrder = model::selectOrder,
                onRefresh = { model.loadFeed(refresh = true) }, onMore = { model.loadFeed() },
                onRelease = openRelease,
                onSettings = { nav.navigate("settings") { launchSingleTop = true } },
                onSearch = { nav.navigate("search") { launchSingleTop = true } },
                onLibrary = { nav.navigate("library") { launchSingleTop = true } },
                onFollowing = { nav.navigate("following") { launchSingleTop = true } },
                onMessages = { nav.navigate("messages") { launchSingleTop = true } },
                onDiscussions = { nav.navigate("discussions") { launchSingleTop = true } },
                onAnnouncements = { nav.navigate("announcements") { launchSingleTop = true } },
                onSignOut = model::signOut,
                categories = categories, onLoadCategories = model::loadCategories,
                customCategories = categoryStore,
                onManageCategories = { nav.navigate("categories/${feed.section.name}") },
                onFilters = { filters -> openFeed(if (tab == BrowseTab.Top) FeedOrder.Top else FeedOrder.Latest, filters) },
                tab = tab, onTab = { selected ->
                    tab = selected
                    if (selected != BrowseTab.Home) {
                        val order = if (selected == BrowseTab.Top) FeedOrder.Top else FeedOrder.Latest
                        model.selectOrder(order)
                    }
                },
                onHomeRefresh = home::refresh,
                homeRefreshing = homeState.busy,
                homeContent = { openCategories ->
                    key(homeState.section) {
                        HomeScreen(homeState, home::retry, openRelease,
                            onReleasesViewed = model::markReleasesViewed,
                            onMore = { openFeed(it, FeedFilters()) },
                            onCategory = { openFeed(FeedOrder.Latest, FeedFilters(categoryIds = setOf(it.id))) },
                            onCategories = openCategories,
                            customCategories = if (categoryStore != null) localCategories.categories(homeState.section) else emptyList(),
                            onCustomCategory = { openFeed(FeedOrder.Latest, FeedFilters(customCategoryIds = setOf(it.id))) },
                            onTopic = { topic, category ->
                                homeForum = topic to category
                                nav.navigate("discussions") { launchSingleTop = true }
                            },
                            onForum = { category ->
                                homeForum = null to category
                                nav.navigate("discussions") { launchSingleTop = true }
                            })
                    }
                },
            )
        }
        composable("search?query={query}", arguments = listOf(navArgument("query") { defaultValue = "" })) { entry ->
            val query = entry.arguments?.getString("query").orEmpty()
            val search: SearchViewModel = viewModel(viewModelStoreOwner = entry, factory = viewModelFactory {
                initializer { SearchViewModel(model.repository,
                    if (query.isNotEmpty()) org.mobilism.material.data.SearchScope.Apps
                    else org.mobilism.material.data.SearchScope.entries.first { it.section == feed.section }, query,
                    initialFilters = org.mobilism.material.data.ContentSection.entries.associateWith(model::filtersFor),
                    onFiltersChanged = model::saveFilters, hideAdultContent = model.contentPreferences.hideAdult,
                    customCategories = model.customCategories, developer = model.developer,
                    history = app.recentSearches.forAccount((auth as? AuthState.SignedIn)?.username)) }
            })
            val state by search.state.collectAsStateWithLifecycle()
            val searchCategories by search.categories.collectAsStateWithLifecycle()
            val recentQueries by search.recentQueries.collectAsStateWithLifecycle()
            SearchScreen(state, search::editQuery, search::submit, search::selectScope, search::loadMore,
                categories = searchCategories, onLoadCategories = search::loadCategories, onFilters = search::applyFilters,
                recentQueries = recentQueries, onRecentQuery = search::searchRecent,
                onRemoveRecent = search::removeRecent, onClearRecent = search::clearRecent,
                customCategories = categoryStore,
                onManageCategories = { state.scope.section?.let { nav.navigate("categories/${it.name}") } },
                onRelease = nav::openReleaseTopic, onBack = { nav.popBackStack() })
        }
        composable("library") { entry ->
            val libraryModel: LibraryViewModel = viewModel(viewModelStoreOwner = entry, factory = viewModelFactory {
                initializer { LibraryViewModel(context) }
            })
            val installed by libraryModel.installed.collectAsStateWithLifecycle()
            LibraryScreen(library, installed, model.library,
                onRefresh = libraryModel::refreshInstalled,
                onSearch = { nav.navigate("search?query=${android.net.Uri.encode(it)}") },
                onFollowing = { nav.navigate("following") },
                onRelease = nav::openReleaseTopic, onBack = { nav.popBackStack() })
        }
        composable("following") { entry ->
            val followingModel: FollowingViewModel = viewModel(viewModelStoreOwner = entry, factory = viewModelFactory {
                initializer { FollowingViewModel(app) }
            })
            val installed by followingModel.installed.collectAsStateWithLifecycle()
            val check by followingModel.check.collectAsStateWithLifecycle()
            val error by followingModel.error.collectAsStateWithLifecycle()
            val preferences by followingModel.preferences.state.collectAsStateWithLifecycle()
            val limits by followingModel.limits.collectAsStateWithLifecycle()
            FollowingScreen(library, installed, check, error, preferences, followingModel.preferences,
                app.followNotifications, model.library, followingModel::refresh, followingModel::checkNow, followingModel::link,
                onRelease = nav::openReleaseTopic, onBack = { nav.popBackStack() }, limits = limits)
        }
        composable("discussions") { entry ->
            val discussions: DiscussionsViewModel = viewModel(viewModelStoreOwner = entry, factory = viewModelFactory {
                initializer {
                    val pending = model.discussionAfterSignIn ?: homeForum
                    homeForum = null
                    model.discussionSignInHandled()
                    DiscussionsViewModel(app.repository, (auth as? AuthState.SignedIn)?.username, createSavedStateHandle(), pending?.first, pending?.second)
                }
            })
            DiscussionsScreen(discussions, auth, onBack = { nav.popBackStack() }, onSignIn = model::signInForDiscussion)
        }
        composable("announcements") { entry ->
            val announcements: DiscussionsViewModel = viewModel(viewModelStoreOwner = entry, factory = viewModelFactory {
                initializer { DiscussionsViewModel(app.repository, (auth as? AuthState.SignedIn)?.username,
                    createSavedStateHandle(), announcementsOnly = true) }
            })
            DiscussionsScreen(announcements, auth, onBack = { nav.popBackStack() }, onSignIn = model::signInForDiscussion)
        }
        composable("announcement/{forum}/{id}", arguments = listOf(navArgument("forum") { type = NavType.LongType },
            navArgument("id") { type = NavType.LongType })) { entry ->
            val args = requireNotNull(entry.arguments)
            val id = args.getLong("id")
            val forum = args.getLong("forum")
            val reader: DiscussionsViewModel = viewModel(viewModelStoreOwner = entry, factory = viewModelFactory {
                initializer { DiscussionsViewModel(app.repository, (auth as? AuthState.SignedIn)?.username,
                    createSavedStateHandle(), announcementsOnly = true, linkedAnnouncement = { app.repository.announcement(id, forum) }) }
            })
            DiscussionsScreen(reader, auth, onBack = { nav.popBackStack() }, onSignIn = model::signInForDiscussion,
                linkedUrl = "https://forum.mobilism.org/viewtopic.php?f=$forum&t=$id")
        }
        composable("comment/{post}?topic={topic}", arguments = listOf(navArgument("post") { type = NavType.LongType },
            navArgument("topic") { type = NavType.LongType; defaultValue = 0L })) { entry ->
            val args = requireNotNull(entry.arguments)
            val post = args.getLong("post")
            val topic = args.getLong("topic").takeIf { it > 0 }
            val reader: LinkedCommentViewModel = viewModel(viewModelStoreOwner = entry, factory = viewModelFactory {
                initializer { LinkedCommentViewModel { app.repository.linkedComment(post, topic) } }
            })
            LinkedCommentScreen(reader, post, { nav.popBackStack() }, nav::openReleaseTopic)
        }
        composable("messages") { entry ->
            if (auth is AuthState.SignedIn) {
                val messages: MessagesViewModel = viewModel(key = "messages-${auth.username}", viewModelStoreOwner = entry, factory = viewModelFactory {
                    initializer { MessagesViewModel(app.repository, auth.username, createSavedStateHandle(),
                        readHistory = app.messageReads.forAccount(auth.username)) }
                })
                val state by messages.state.collectAsStateWithLifecycle()
                val actionState by messages.actions.state.collectAsStateWithLifecycle()
                LaunchedEffect(notificationMessage, state.sending, actionState.busy) {
                    notificationMessage?.takeIf {
                        !state.sending && !actionState.busy && it.accountKey == org.mobilism.material.messages.MessageAlertStore.accountKey(auth.username)
                    }?.let {
                        messages.openConversation(it.conversationId, it.messageId)
                        onNotificationHandled()
                    }
                }
                MessagesScreen(state, auth.username, onFolder = messages::selectFolder,
                    onRefresh = { messages.loadMailbox(refresh = true) }, onMore = { messages.loadMailbox() },
                    onOpen = messages::openMessage, onRead = messages::markRead,
                    onBack = { if (state.conversationId != null) messages.closeConversation() else nav.popBackStack() },
                    onSignIn = model::signOut, onRefreshConversation = messages::loadConversation,
                    onReply = messages::openReply, onEdit = messages::editReply, onSend = messages::sendReply,
                    onCloseReply = messages::closeReply, onNoticeShown = messages::noticeShown,
                    onCheckReply = messages::checkReplyDelivery, onRetryReply = messages::retryReply,
                    onNewReply = messages::newReply, actions = messages.actions,
                    onRelease = nav::openReleaseTopic)
            }
        }
        composable("settings") {
            val dailyUsageEnabled by app.dailyUsage.enabled.collectAsStateWithLifecycle()
            val hideAdult by model.contentPreferences.hideAdult.collectAsStateWithLifecycle()
            val landingPage by model.landingPage.state.collectAsStateWithLifecycle()
            SettingsScreen(debrid, model.debrid, onAppearance = { nav.navigate("appearance") },
                onDownloads = { nav.navigate("download-preferences") }, onBack = { nav.popBackStack() },
                hideAdult = hideAdult, onHideAdult = model.contentPreferences::hideAdult,
                onAbout = { nav.navigate("about") }, developer = developer,
                onDeveloperOptions = { nav.navigate("developer-options") }, diagnostics = app.diagnostics,
                preferredHosts = downloads.preferredHosts, landingPage = landingPage,
                onLandingPage = model.landingPage::select, dailyUsageEnabled = dailyUsageEnabled,
                onDailyUsage = { app.dailyUsage.setEnabled(app, it) }, onUpdates = onUpdates,
                messageNotifications = { MessageNotificationSetting(app) })
        }
        composable("developer-options") {
            LaunchedEffect(developer.enabled) {
                if (!developer.enabled) nav.popBackStack()
            }
            if (developer.enabled) DeveloperOptionsScreen(developer,
                onVerboseLogging = model.developer::setVerboseLogging,
                onDisable = model.developer::disable, onBack = { nav.popBackStack() },
                onCustomCategories = model.developer::setCustomCategoriesEnabled,
                onMarkdownSyntax = model.developer::setMarkdownSyntax,
                onManualNoticeChecks = model.developer::setManualNoticeChecks,
                onCheckNotices = {
                    app.remoteControl.refresh(force = true)
                    when {
                        app.remoteControl.notice.value != null -> null
                        app.remoteControl.hasConfiguration -> R.string.updates_no_notices
                        else -> R.string.updates_notices_failed
                    }
                })
        }
        composable("categories/{section}") { entry ->
            LaunchedEffect(developer.customCategoriesActive) {
                if (!developer.customCategoriesActive) nav.popBackStack()
            }
            if (developer.customCategoriesActive) CustomCategoriesScreen(
                org.mobilism.material.data.ContentSection.valueOf(requireNotNull(entry.arguments?.getString("section"))),
                model.customCategories, onBack = { nav.popBackStack() },
                onRelease = nav::openReleaseTopic)
        }
        composable("about") {
            AboutScreen(onBack = { nav.popBackStack() }, developerMode = developer.enabled,
                onEnableDeveloperMode = model.developer::unlock)
        }
        composable("download-preferences") {
            DownloadSettingsScreen(downloads, model.downloads, model.debrid, onBack = { nav.popBackStack() })
        }
        composable("appearance") {
            AppearanceScreen(appearance, model.appearance, onBack = { nav.popBackStack() })
        }
        composable("download-help") {
            DownloadHelpScreen(app.repository::downloadHelp, onBack = { nav.popBackStack() },
                onRelease = nav::openReleaseTopic)
        }
        composable("profile?user={user}&name={name}", arguments = listOf(
            navArgument("user") { type = NavType.LongType; defaultValue = 0L },
            navArgument("name") { defaultValue = "" },
        )) { entry ->
            val args = requireNotNull(entry.arguments)
            val reference = org.mobilism.material.data.ProfileReference(args.getLong("user").takeIf { it > 0 },
                args.getString("name")?.takeIf { it.isNotBlank() })
            UserProfileScreen(reference, app.repository::profile, onBack = { nav.popBackStack() })
        }
        composable("release/{id}?forum={forum}", arguments = listOf(navArgument("id") { type = NavType.LongType },
            navArgument("forum") { type = NavType.LongType; defaultValue = -1L })) { entry ->
            val args = requireNotNull(entry.arguments)
            val id = args.getLong("id")
            val forum = args.getLong("forum").takeIf { it >= 0 }
            val detail: ReleaseViewModel = viewModel(viewModelStoreOwner = entry, factory = viewModelFactory {
                initializer { ReleaseViewModel(id, model.repository, createSavedStateHandle()) { app.repository.openTopic(it, forum) } }
            })
            val state by detail.state.collectAsStateWithLifecycle()
            val discussion = state.discussion
            if (discussion != null) {
                val reader: DiscussionsViewModel = viewModel(viewModelStoreOwner = entry, factory = viewModelFactory {
                    initializer { DiscussionsViewModel(app.repository, (auth as? AuthState.SignedIn)?.username,
                        createSavedStateHandle(), linkedAnnouncement = { discussion }) }
                })
                DiscussionsScreen(reader, auth, onBack = { nav.popBackStack() }, onSignIn = model::signInForDiscussion,
                    linkedUrl = discussion.topic.browserUrl)
                return@composable
            }
            val descriptionExpanded by app.releaseDisplay.descriptionExpanded.collectAsStateWithLifecycle()
            LaunchedEffect(state.release?.categoryId, hideAdult) {
                if (forum == null && state.release?.categoryId != null && catalog == null) model.loadCategories()
            }
            var assigningCategories by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(state.release, developer.customCategoriesActive) {
                if (developer.customCategoriesActive) state.release?.let(model.customCategories::observe)
                else assigningCategories = false
            }
            if (assigningCategories && categoryStore != null) state.release?.let {
                CategoryAssignmentSheet(it, categoryStore, onDismiss = { assigningCategories = false })
            }
            ReleaseScreen(state, debrid, downloads, onBack = { nav.popBackStack() }, onRetry = detail::load, onMoreComments = detail::loadComments,
                onCategories = if (categoryStore != null && state.release?.section != null) ({ assigningCategories = true }) else null,
                onLike = detail::toggleLike, onActionMessageShown = detail::actionMessageShown,
                onLikePostVerified = detail::likePostVerified, onDismissLikeVerification = detail::dismissLikeVerification,
                auth = auth, onSignIn = { model.signInForRelease(id) },
                onWriteComment = detail::openComposer, onEditComment = detail::editComment,
                onPostComment = detail::postComment, onCloseComposer = detail::closeComposer,
                onRefreshComments = detail::refreshComments,
                onRefresh = detail::refresh,
                onReplyToComment = detail::replyToComment, onRemoveReply = detail::clearCommentReply,
                onRelease = nav::openReleaseTopic,
                onResolveDownload = model.debrid::resolve,
                descriptionExpanded = descriptionExpanded, onDescriptionExpanded = app.releaseDisplay::setDescriptionExpanded,
                onDownloadHosts = model.downloads::discover,
                watched = library.any { item -> item.watched && state.release?.let { org.mobilism.material.data.ReleaseVersions.sameApp(item.release, it) } == true },
                downloaded = library.any { it.downloaded && it.release.id == state.release?.id },
                onWatch = { enabled -> state.release?.let {
                    model.library.watch(it, enabled)
                    if (enabled && Build.VERSION.SDK_INT >= 33 &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                        followPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } },
                onDownloaded = { enabled -> state.release?.let {
                    model.library.downloaded(it, enabled, downloads.autoWatchDownloads)
                } })
        }
    }
    }
}
