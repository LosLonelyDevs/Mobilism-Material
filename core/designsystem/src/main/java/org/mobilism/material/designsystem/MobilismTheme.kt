package org.mobilism.material.designsystem

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

val LocalReducedMotion = staticCompositionLocalOf { false }
val LocalAppFont = staticCompositionLocalOf { AppFont.Geist }
val LocalAppTheme = staticCompositionLocalOf { ThemeCatalog.default }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private object InstantMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MobilismTheme(
    theme: ThemeDefinition = ThemeCatalog.default,
    font: AppFont = AppFont.Geist,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemReducedMotion = systemAnimationsDisabled()
    val reduced = reduceMotion || systemReducedMotion
    val type = remember(font) { appTypography(font) }
    CompositionLocalProvider(LocalReducedMotion provides reduced, LocalAppFont provides font, LocalAppTheme provides theme) {
        MaterialExpressiveTheme(
            colorScheme = theme.colors,
            typography = type,
            motionScheme = if (reduced) InstantMotionScheme else MotionScheme.expressive(),
            content = content,
        )
    }
}

@Composable
private fun systemAnimationsDisabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var disabled by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { disabled = read() }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return disabled
}
