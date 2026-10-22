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
fun MobilismApp(app: MobilismApplication) {
    val model: MobilismViewModel = viewModel(factory = viewModelFactory {
        initializer { MobilismViewModel(app, app.repository, app.appearance) }
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
                else -> MainNavigation(model, auth)
            }
        }
    }
}

@Composable
private fun AccountNavigation(model: MobilismViewModel) {
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
                state = login, onSignIn = model::signIn, onGuest = model::continueAsGuest,
                onRegister = { nav.navigate("register") { launchSingleTop = true } },
                onForgot = { nav.navigate("recover") { launchSingleTop = true } },
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
private fun MainNavigation(model: MobilismViewModel, auth: AuthState) {
    val nav = rememberNavController()
    val motion = screenMotion()
    val feed by model.feed.collectAsStateWithLifecycle()
    val appearance by model.appearance.state.collectAsStateWithLifecycle()
    val start = androidx.compose.runtime.remember { if (model.preferredSection == null) "favourite" else "feed" }
    LaunchedEffect(Unit) {
        if (start == "feed") model.loadFeed(refresh = true)
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
                onSettings = { nav.navigate("appearance") { launchSingleTop = true } },
                onSignOut = model::signOut,
            )
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
            ReleaseScreen(state, onBack = { nav.popBackStack() }, onRetry = detail::load)
        }
    }
}
