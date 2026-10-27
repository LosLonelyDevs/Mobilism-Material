/*
 * Portions adapted from Orion Store, Copyright Orion Store contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 * Source: RookieEnough/Orion-Store bffea4103b05120904a9e0d1a8f3f0cac594c553
 * Common.kt MorphShape and StoreScreen.kt category morph / pager artwork patterns.
 * Modified for Mobilism theme roles, shared focus feedback, and reduced motion.
 */
package org.mobilism.material.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath

private class StoreMorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress.coerceIn(0f, 1f))
        path.transform(android.graphics.Matrix().apply { setScale(size.width, size.height) })
        return Outline.Generic(path.asComposePath())
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StoreShape(index: Int, size: Dp, color: Color, modifier: Modifier = Modifier,
    pressed: Boolean = false, content: @Composable BoxScope.() -> Unit = {}) {
    val shapes = remember { listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.Clover4Leaf,
        MaterialShapes.Cookie6Sided, MaterialShapes.Pentagon, MaterialShapes.Flower, MaterialShapes.Gem) }
    val morph = remember(index) { Morph(shapes[Math.floorMod(index, shapes.size)], MaterialShapes.Circle) }
    val reduceMotion = LocalReducedMotion.current
    val progress by animateFloatAsState(if (pressed && !reduceMotion) 1f else 0f,
        MaterialTheme.motionScheme.fastSpatialSpec(), label = "category shape")
    Box(modifier.size(size).background(color, StoreMorphShape(morph, progress)), contentAlignment = Alignment.Center) { content() }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StoreCard(onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface, interaction: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit) {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !LocalReducedMotion.current) 0.97f else 1f,
        MaterialTheme.motionScheme.fastSpatialSpec(), label = "store card press")
    Surface(onClick, modifier.focusOutline(interaction).graphicsLayer { scaleX = scale; scaleY = scale },
        shape = MaterialTheme.shapes.extraLarge, color = color, contentColor = contentColor,
        interactionSource = interaction) { Box(content = content) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> StoreTabContent(tab: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val motion = MaterialTheme.motionScheme
    val reduced = LocalReducedMotion.current
    AnimatedContent(tab, modifier, transitionSpec = {
        if (reduced) (androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None).using(null)
        else (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())).using(null)
    }, label = "browse tab") { content(it) }
}
