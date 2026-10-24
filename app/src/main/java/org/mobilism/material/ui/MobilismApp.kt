package org.mobilism.material.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                AuthState.SignedOut -> AccountNavigation(model)
                else -> MainNavigation(model, auth, notificationRelease, fromNotification, onNotificationHandled)
            }
        }
    }
}

@Composable
private fun AccountNavigation(model: MobilismViewModel) {
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
    val start = androidx.compose.runtime.remember { if (model.preferredSection == null) "favourite" else "feed" }
    LaunchedEffect(Unit) {
        if (start == "feed") model.loadFeed(refresh = true)
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
        composable("feed") {
            FeedScreen(
                state = feed, auth = auth,
                onSection = model::selectSection, onOrder = model::selectOrder,
                onRefresh = { model.loadFeed(refresh = true) }, onMore = { model.loadFeed() },
                onRelease = { id -> nav.navigate("release/$id") { launchSingleTop = true } },
                onSettings = { nav.navigate("settings") { launchSingleTop = true } },
                onSearch = { nav.navigate("search") { launchSingleTop = true } },
                onLibrary = { nav.navigate("library") { launchSingleTop = true } },
                onFollowing = { nav.navigate("following") { launchSingleTop = true } },
                onSignOut = model::signOut,
                categories = categories, onLoadCategories = model::loadCategories, onFilters = model::applyFilters,
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
        composable("settings") {
            val hideAdult by model.contentPreferences.hideAdult.collectAsStateWithLifecycle()
            SettingsScreen(debrid, model.debrid, onAppearance = { nav.navigate("appearance") },
                onDownloads = { nav.navigate("download-preferences") }, onBack = { nav.popBackStack() },
                hideAdult = hideAdult, onHideAdult = model.contentPreferences::hideAdult,
                onAbout = { nav.navigate("about") }, developer = developer,
                onDeveloperOptions = { nav.navigate("developer-options") })
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
                initializer { ReleaseViewModel(id, model.repository) }
            })
            val state by detail.state.collectAsStateWithLifecycle()
            ReleaseScreen(state, debrid, downloads, onBack = { nav.popBackStack() }, onRetry = detail::load, onMoreComments = detail::loadComments,
                onLike = detail::toggleLike, onActionMessageShown = detail::actionMessageShown,
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
