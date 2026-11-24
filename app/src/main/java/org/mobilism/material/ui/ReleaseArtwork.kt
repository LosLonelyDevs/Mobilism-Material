package org.mobilism.material.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import coil3.request.ImageRequest
import org.mobilism.material.images.releaseArtworkKey
import org.mobilism.material.designsystem.LocalReducedMotion

@Composable
internal fun releaseArtworkRequest(url: String?, originalSize: Boolean = false): ImageRequest {
    val context = LocalContext.current
    return remember(context, url, originalSize) {
        ImageRequest.Builder(context).data(url).apply {
            extras[releaseArtworkKey] = true
            if (originalSize) size(coil3.size.Size.ORIGINAL)
        }.build()
    }
}

@Composable
internal fun ReleaseArtwork(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    error: Painter? = null,
    alternate: String? = null,
) {
    var source by remember(model, alternate) { mutableStateOf(model) }
    var loading by remember(source) { mutableStateOf(false) }
    Box(modifier) {
        AsyncImage(
            model = releaseArtworkRequest(source), contentDescription = contentDescription, modifier = Modifier.fillMaxSize(),
            contentScale = contentScale, error = error, fallback = error,
            onLoading = { loading = !source.isNullOrBlank() },
            onSuccess = { loading = false }, onError = {
                loading = false
                if (alternate != null && source == model) source = alternate
            },
        )
        if (loading) ReleaseImageShimmer(Modifier.matchParentSize())
    }
}

@Composable
internal fun ReleaseImageShimmer(modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        if (LocalReducedMotion.current) {
            Spacer(Modifier.fillMaxSize())
        } else {
            val sweep = rememberInfiniteTransition(label = "Release artwork").animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "Shimmer",
            )
            val highlight = MaterialTheme.colorScheme.primary
            Spacer(Modifier.fillMaxSize().drawWithCache {
                val width = size.width * 0.8f
                val band = Size(width, size.height)
                val fade = highlight.copy(alpha = 0f)
                val brush = Brush.horizontalGradient(
                    listOf(fade, highlight.copy(alpha = 0.20f), fade), endX = width.coerceAtLeast(1f),
                )
                onDrawBehind {
                    translate(left = (size.width + width) * sweep.value - width) { drawRect(brush, size = band) }
                }
            })
        }
    }
}
