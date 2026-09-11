package com.hjhsys.naiblockprompt.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.hjhsys.naiblockprompt.R
import java.io.File
import kotlin.math.min
import kotlin.math.max

internal enum class ImageViewerMode { CONTEXTUAL, FULLSCREEN }

internal object ImageViewerPolicy {
    fun toggle(mode: ImageViewerMode): ImageViewerMode = when (mode) {
        ImageViewerMode.CONTEXTUAL -> ImageViewerMode.FULLSCREEN
        ImageViewerMode.FULLSCREEN -> ImageViewerMode.CONTEXTUAL
    }

    fun usePlatformDefaultWidth(mode: ImageViewerMode): Boolean = mode == ImageViewerMode.CONTEXTUAL
    fun decorFitsSystemWindows(mode: ImageViewerMode): Boolean = mode == ImageViewerMode.CONTEXTUAL

    fun maximumOriginalPixelZoom(viewport: IntSize, image: IntSize): Float {
        if (viewport.width <= 0 || viewport.height <= 0 || image.width <= 0 || image.height <= 0) return 1f
        val fitScale = min(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)
        // A phone can already display a modest source above 1:1 physical pixels at Fit.
        // Keep a real gesture range while still guaranteeing at least source-pixel scale.
        return max(3f, 1f / fitScale)
    }

    fun maximumPan(viewport: IntSize, image: IntSize, zoom: Float): Pair<Float, Float> {
        if (viewport.width <= 0 || viewport.height <= 0 || image.width <= 0 || image.height <= 0) return 0f to 0f
        val fitScale = min(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)
        val width = image.width * fitScale * zoom
        val height = image.height * fitScale * zoom
        return max(0f, (width - viewport.width) / 2f) to max(0f, (height - viewport.height) / 2f)
    }
}

internal fun imageReferenceModel(reference: String): Any {
    val uri = Uri.parse(reference)
    return if (uri.scheme == null) File(reference) else uri
}

@Composable
fun ImageViewer(reference: String, onDismiss: () -> Unit) {
    var mode by remember(reference) { mutableStateOf(ImageViewerMode.CONTEXTUAL) }
    var loadFailed by remember(reference) { mutableStateOf(false) }
    var viewportSize by remember(reference) { mutableStateOf(IntSize.Zero) }
    var sourceSize by remember(reference) { mutableStateOf(IntSize.Zero) }
    var zoom by remember(reference) { mutableFloatStateOf(1f) }
    var panX by remember(reference) { mutableFloatStateOf(0f) }
    var panY by remember(reference) { mutableFloatStateOf(0f) }
    val fullscreen = mode == ImageViewerMode.FULLSCREEN
    val maximumZoom = ImageViewerPolicy.maximumOriginalPixelZoom(viewportSize, sourceSize)
    LaunchedEffect(fullscreen) {
        if (!fullscreen) {
            zoom = 1f
            panX = 0f
            panY = 0f
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = ImageViewerPolicy.usePlatformDefaultWidth(mode),
            decorFitsSystemWindows = ImageViewerPolicy.decorFitsSystemWindows(mode),
        ),
    ) {
        Box(
            modifier = (if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().fillMaxHeight(0.82f))
                .clip(if (fullscreen) RectangleShape else MaterialTheme.shapes.large)
                .background(Color(0xFF111111))
                .onSizeChanged { viewportSize = it }
                .then(if (fullscreen) Modifier.pointerInput(maximumZoom, viewportSize, sourceSize) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        val nextZoom = (zoom * gestureZoom).coerceIn(1f, maximumZoom)
                        val (maxX, maxY) = ImageViewerPolicy.maximumPan(viewportSize, sourceSize, nextZoom)
                        zoom = nextZoom
                        panX = if (nextZoom == 1f) 0f else (panX + pan.x).coerceIn(-maxX, maxX)
                        panY = if (nextZoom == 1f) 0f else (panY + pan.y).coerceIn(-maxY, maxY)
                    }
                } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageReferenceModel(reference),
                contentDescription = stringResource(R.string.generated_image),
                contentScale = ContentScale.Fit,
                onLoading = { loadFailed = false },
                onSuccess = {
                    loadFailed = false
                    sourceSize = IntSize(it.result.image.width, it.result.image.height)
                },
                onError = { loadFailed = true },
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = panX
                        translationY = panY
                    },
            )
            if (loadFailed) {
                Text(
                    stringResource(R.string.original_missing),
                    color = Color.White,
                    modifier = Modifier.padding(24.dp),
                )
            }
            Surface(
                modifier = Modifier.align(Alignment.TopEnd)
                    .then(if (fullscreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                    .padding(8.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.58f),
                contentColor = Color.White,
            ) {
                Row {
                    IconButton(onClick = { mode = ImageViewerPolicy.toggle(mode) }) {
                        Icon(
                            if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = stringResource(if (fullscreen) R.string.image_viewer_exit_fullscreen else R.string.image_viewer_fullscreen),
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
            }
        }
    }
}
