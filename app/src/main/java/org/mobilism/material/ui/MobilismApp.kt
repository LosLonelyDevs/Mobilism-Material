package org.mobilism.material.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.mobilism.material.MobilismApplication
import org.mobilism.material.data.AuthState
import org.mobilism.material.data.DiscussionCategory
import org.mobilism.material.data.DiscussionTopic
import org.mobilism.material.data.FeedFilters
import org.mobilism.material.data.FeedOrder
import org.mobilism.material.designsystem.LoadingState
import org.mobilism.material.designsystem.screenMotion

@Composable
fun MobilismApp(app: MobilismApplication, notificationRelease: Long? = null, fromNotification: Boolean = true,
    onNotificationHandled: () -> Unit = {}) {
    val model: MobilismViewModel = viewModel(factory = viewModelFactory {
        initializer { MobilismViewModel(app, app.repository, app.appearance, app.debrid, app.library, app.contentPreferences, app.developer) }
    })
    val auth by model.auth.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Auth boundaries discard the previous account's navigation and screen state.
        key(auth) {
            when (auth) {
                AuthState.Restoring -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingState("Opening Mobilism…")
                }
                AuthState.SignedOut -> AccountNavigation(model, app.diagnostics)
                else -> MainNavigation(model, auth, notificationRelease, fromNotification, onNotificationHandled)
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
private fun MainNavigation(model: MobilismViewModel, auth: AuthState, notificationRelease: Long?, fromNotification: Boolean,
    onNotificationHandled: () -> Unit) {
    val nav = rememberNavController()
    val motion = screenMotion()
    val feed by model.feed.collectAsStateWithLifecycle()
    val categories by model.categories.collectAsStateWithLifecycle()
    val appearance by model.appearance.state.collectAsStateWithLifecycle()
    val debridState by model.debrid.state.collectAsStateWithLifecycle()
    val downloads by model.downloads.state.collectAsStateWithLifecycle()
    val developer by model.developer.state.collectAsStateWithLifecycle()
    val debrid = if (developer.debridEnabled) debridState else org.mobilism.material.data.DebridState(enabled = false)
    val library by model.library.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as MobilismApplication
    var homeForum by remember { mutableStateOf<Pair<DiscussionTopic?, DiscussionCategory>?>(null) }
    val start = androidx.compose.runtime.remember { if (model.preferredSection == null) "favourite" else "feed" }
    LaunchedEffect(Unit) {
        if (start == "feed") model.loadFeed(refresh = true)
        if (auth is AuthState.SignedIn) model.releaseAfterSignIn?.let {
            nav.navigate("release/$it") { launchSingleTop = true }
            model.releaseSignInHandled()
        }
        if (auth is AuthState.SignedIn && model.discussionAfterSignIn != null) nav.navigate("discussions") { launchSingleTop = true }
    }
    LaunchedEffect(notificationRelease) {
        notificationRelease?.takeIf { it > 0 }?.let { id ->
            if (fromNotification) nav.navigate("following") { launchSingleTop = true }
            nav.navigate("release/$id") { launchSingleTop = true }
            onNotificationHandled()
        }
    }
    NavHost(
        navController = nav, startDestination = start,
        enterTransition = { motion.enter }, exitTransition = { motion.exit },
        popEnterTransition = { motion.popEnter }, popExitTransition = { motion.popExit },
    ) {
        composable("favourite") {
            FavouriteScreen { section ->
                model.selectSection(section)
                nav.navigate("feed") { popUpTo("favourite") { inclusive = true }; launchSingleTop = true }
            }
        }
        composable("feed") { entry ->
            val home: HomeViewModel = viewModel(viewModelStoreOwner = entry, factory = viewModelFactory {
                initializer { HomeViewModel(app.repository, feed.section, model.contentPreferences.hideAdult) }
            })
            val homeState by home.state.collectAsStateWithLifecycle()
            var tab by rememberSaveable(feed.section) { mutableStateOf(BrowseTab.Home) }
            LaunchedEffect(feed.section) { home.selectSection(feed.section) }
            val openRelease: (Long) -> Unit = { nav.navigate("release/$it") { launchSingleTop = true } }
            val openFeed: (FeedOrder, FeedFilters) -> Unit = { order, filters ->
                model.selectOrder(order)
                model.applyFilters(filters)
                tab = if (order == FeedOrder.Top) BrowseTab.Top else BrowseTab.Latest
            }
            FeedScreen(
                state = feed, auth = auth,
                onSection = { home.selectSection(it); model.selectSection(it) }, onOrder = model::selectOrder,
                onRefresh = { model.loadFeed(refresh = true) }, onMore = { model.loadFeed() },
                onRelease = openRelease,
                onSettings = { nav.navigate("settings") { launchSingleTop = true } },
                onSearch = { nav.navigate("search") { launchSingleTop = true } },
                onLibrary = { nav.navigate("library") { launchSingleTop = true } },
                onFollowing = { nav.navigate("following") { launchSingleTop = true } },
                onMessages = { nav.navigate("messages") { launchSingleTop = true } },
                onDiscussions = { nav.navigate("discussions") { launchSingleTop = true } },
                onSignOut = model::signOut,
                categories = categories, onLoadCategories = model::loadCategories,
                onFilters = { filters -> openFeed(if (tab == BrowseTab.Top) FeedOrder.Top else FeedOrder.Latest, filters) },
                tab = tab, onTab = { selected ->
                    tab = selected
                    if (selected != BrowseTab.Home) model.selectOrder(if (selected == BrowseTab.Top) FeedOrder.Top else FeedOrder.Latest)
                },
                onHomeRefresh = home::refresh,
                homeContent = { openCategories ->
                    key(homeState.section) {
                        HomeScreen(homeState, home::retry, openRelease,
                            onMore = { openFeed(it, FeedFilters()) },
                            onCategory = { openFeed(FeedOrder.Latest, FeedFilters(categoryIds = setOf(it.id))) },
                            onCategories = openCategories,
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
                    onFiltersChanged = model::saveFilters, hideAdultContent = model.contentPreferences.hideAdult) }
            })
            val state by search.state.collectAsStateWithLifecycle()
            val searchCategories by search.categories.collectAsStateWithLifecycle()
            SearchScreen(state, search::editQuery, search::submit, search::selectScope, search::loadMore,
                categories = searchCategories, onLoadCategories = search::loadCategories, onFilters = search::applyFilters,
                onRelease = { nav.navigate("release/$it") }, onBack = { nav.popBackStack() })
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
                onRelease = { nav.navigate("release/$it") }, onBack = { nav.popBackStack() })
        }
        composable("following") { entry ->
            val followingModel: FollowingViewModel = viewModel(viewModelStoreOwner = entry, factory = viewModelFactory {
                initializer { FollowingViewModel(app) }
            })
            val installed by followingModel.installed.collectAsStateWithLifecycle()
            val check by followingModel.check.collectAsStateWithLifecycle()
            val error by followingModel.error.collectAsStateWithLifecycle()
            val preferences by followingModel.preferences.state.collectAsStateWithLifecycle()
            FollowingScreen(library, installed, check, error, preferences, followingModel.preferences,
                app.followNotifications, model.library, followingModel::refresh, followingModel::checkNow, followingModel::link,
                onRelease = { nav.navigate("release/$it") }, onBack = { nav.popBackStack() })
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
        composable("messages") { entry ->
            if (auth is AuthState.SignedIn) {
                val messages: MessagesViewModel = viewModel(viewModelStoreOwner = entry, factory = viewModelFactory {
                    initializer { MessagesViewModel(app.repository, auth.username, createSavedStateHandle()) }
                })
                val state by messages.state.collectAsStateWithLifecycle()
                MessagesScreen(state, auth.username, onFolder = messages::selectFolder,
                    onRefresh = { messages.loadMailbox(refresh = true) }, onMore = { messages.loadMailbox() },
                    onOpen = messages::openMessage, onRead = messages::markRead,
                    onBack = { if (state.conversationId != null) messages.closeConversation() else nav.popBackStack() },
                    onSignIn = model::signOut, onRefreshConversation = messages::loadConversation,
                    onReply = messages::openReply, onEdit = messages::editReply, onSend = messages::sendReply,
                    onCloseReply = messages::closeReply, onNoticeShown = messages::noticeShown,
                    onRelease = { nav.navigate("release/$it") })
            }
        }
        composable("settings") {
            val hideAdult by model.contentPreferences.hideAdult.collectAsStateWithLifecycle()
            SettingsScreen(debrid, model.debrid, onAppearance = { nav.navigate("appearance") },
                onDownloads = { nav.navigate("download-preferences") }, onBack = { nav.popBackStack() },
                hideAdult = hideAdult, onHideAdult = model.contentPreferences::hideAdult,
                onAbout = { nav.navigate("about") }, developer = developer,
                onDeveloperOptions = { nav.navigate("developer-options") }, diagnostics = app.diagnostics)
        }
        composable("developer-options") {
            LaunchedEffect(developer.enabled) {
                if (!developer.enabled) nav.popBackStack()
            }
            if (developer.enabled) DeveloperOptionsRoute(developer, app.diagnostics,
                onVerboseLogging = model.developer::setVerboseLogging, onDebridEnabled = model.developer::setDebridEnabled,
                onDisable = model.developer::disable, onBack = { nav.popBackStack() })
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
        composable("release/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = requireNotNull(entry.arguments).getLong("id")
            val detail: ReleaseViewModel = viewModel(viewModelStoreOwner = entry, factory = viewModelFactory {
                initializer { ReleaseViewModel(id, model.repository, createSavedStateHandle()) }
            })
            val state by detail.state.collectAsStateWithLifecycle()
            ReleaseScreen(state, debrid, downloads, onBack = { nav.popBackStack() }, onRetry = detail::load, onMoreComments = detail::loadComments,
                onLike = detail::toggleLike, onActionMessageShown = detail::actionMessageShown,
                auth = auth, onSignIn = { model.signInForRelease(id) },
                onWriteComment = detail::openComposer, onEditComment = detail::editComment,
                onPostComment = detail::postComment, onCloseComposer = detail::closeComposer,
                onRefreshComments = detail::refreshComments,
                onReplyToComment = detail::replyToComment, onRemoveReply = detail::clearCommentReply,
                onRelease = { topic -> if (topic != id) nav.navigate("release/$topic") { launchSingleTop = true } },
                onResolveDownload = model.debrid::resolve,
                watched = library.any { item -> item.watched && state.release?.let { org.mobilism.material.data.ReleaseVersions.sameApp(item.release, it) } == true },
                downloaded = library.any { it.downloaded && it.release.id == state.release?.id },
                onWatch = { enabled -> state.release?.let { model.library.watch(it, enabled) } },
                onDownloaded = { enabled -> state.release?.let {
                    model.library.downloaded(it, enabled, downloads.autoWatchDownloads)
                } })
        }
    }
}
