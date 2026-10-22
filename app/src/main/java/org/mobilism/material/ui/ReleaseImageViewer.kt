package org.mobilism.material.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import kotlinx.coroutines.launch
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.LoadingState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReleaseImageViewer(images: List<String>, initialPage: Int, onDismiss: () -> Unit) {
    val pager = rememberPagerState(initialPage = initialPage.coerceIn(images.indices)) { images.size }
    val scope = rememberCoroutineScope()
    var zoomed by remember { mutableStateOf(false) }
    val motion = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f)).safeDrawingPadding()) {
            HorizontalPager(pager, Modifier.fillMaxSize().padding(vertical = 64.dp), userScrollEnabled = !zoomed) { page ->
                ZoomableImage(images[page], page == pager.currentPage, { if (page == pager.currentPage) zoomed = it })
            }
            Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${pager.currentPage + 1} / ${images.size}", Modifier.padding(start = 16.dp), color = Color.White)
                AppIconButton(Icons.Outlined.Close, "Close image viewer", onDismiss)
            }
            if (images.size > 1) Row(Modifier.align(Alignment.BottomCenter).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (pager.currentPage > 0) AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Previous image", {
                    scope.launch { pager.animateScrollToPage(pager.currentPage - 1, animationSpec = motion) }
                })
                if (pager.currentPage < images.lastIndex) AppIconButton(Icons.AutoMirrored.Outlined.ArrowForward, "Next image", {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1, animationSpec = motion) }
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ZoomableImage(url: String, active: Boolean, onZoom: (Boolean) -> Unit) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    fun clamp(value: Offset, zoom: Float): Offset {
        if (imageSize.width == 0 || viewport.width == 0) return Offset.Zero
        val fit = minOf(viewport.width.toFloat() / imageSize.width, viewport.height.toFloat() / imageSize.height)
        val maxX = ((imageSize.width * fit * zoom - viewport.width) / 2f).coerceAtLeast(0f)
        val maxY = ((imageSize.height * fit * zoom - viewport.height) / 2f).coerceAtLeast(0f)
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }
    val transform = rememberTransformableState { centroid, zoom, pan, _ ->
        val previous = scale
        scale = (previous * zoom).coerceIn(1f, 5f)
        val focal = if (centroid.x.isFinite() && centroid.y.isFinite()) {
            centroid - Offset(viewport.width / 2f, viewport.height / 2f)
        } else Offset.Zero
        offset = clamp((offset - focal) * (scale / previous) + focal + pan, scale)
    }
    val animatedScale by animateFloatAsState(scale,
        if (transform.isTransformInProgress) snap() else MaterialTheme.motionScheme.fastSpatialSpec(), label = "image zoom")
    LaunchedEffect(active) { if (!active) { scale = 1f; offset = Offset.Zero } }
    LaunchedEffect(scale, active) { if (active) onZoom(scale > 1.01f) }
    val context = LocalContext.current
    val request = remember(url, context) { ImageRequest.Builder(context).data(url).size(Size.ORIGINAL).build() }
    Box(Modifier.fillMaxSize().onSizeChanged { viewport = it }.clipToBounds()
        .transformable(transform, canPan = { scale > 1f })
        .pointerInput(url) { detectTapGestures(onDoubleTap = {
            scale = if (scale > 1f) 1f else 2.5f
            offset = Offset.Zero
        }) }, contentAlignment = Alignment.Center) {
        SubcomposeAsyncImage(
            model = request, contentDescription = "Full size release image. Pinch or double tap to zoom.",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = animatedScale; scaleY = animatedScale; translationX = offset.x; translationY = offset.y
            },
            onSuccess = { imageSize = IntSize(it.result.image.width, it.result.image.height) },
            loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingState("Loading image…") } },
            error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Image unavailable", color = Color.White) } },
        )
    }
}
