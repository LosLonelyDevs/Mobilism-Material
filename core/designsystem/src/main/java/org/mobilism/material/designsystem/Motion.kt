package org.mobilism.material.designsystem

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/** Shared-axis motion follows screen hierarchy. Reduced motion preserves immediate feedback. */
@Immutable
data class ScreenMotion(
    val enter: EnterTransition,
    val exit: ExitTransition,
    val popEnter: EnterTransition,
    val popExit: ExitTransition,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun screenMotion(): ScreenMotion {
    if (LocalReducedMotion.current) return ScreenMotion(
        EnterTransition.None, ExitTransition.None, EnterTransition.None, ExitTransition.None,
    )
    val scheme = MaterialTheme.motionScheme
    val incoming = fadeIn(scheme.defaultEffectsSpec())
    val outgoing = fadeOut(scheme.fastEffectsSpec())
    return ScreenMotion(
        enter = incoming + slideInHorizontally(scheme.defaultSpatialSpec()) { it / 12 },
        exit = outgoing + slideOutHorizontally(scheme.defaultSpatialSpec()) { -it / 12 },
        popEnter = incoming + slideInHorizontally(scheme.defaultSpatialSpec()) { -it / 12 },
        popExit = outgoing + slideOutHorizontally(scheme.defaultSpatialSpec()) { it / 12 },
    )
}
