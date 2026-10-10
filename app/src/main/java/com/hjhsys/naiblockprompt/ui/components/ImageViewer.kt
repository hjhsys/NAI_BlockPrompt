package com.hjhsys.naiblockprompt.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.hjhsys.naiblockprompt.R
import com.hjhsys.naiblockprompt.domain.model.GeneratedPromptSnapshot
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

data class ImageCompareCandidate(
    val id: String,
    val reference: String,
    val thumbnailReference: String,
    val dateLabel: String,
    val modelLabel: String?,
    val promptPreview: String,
    val prompt: GeneratedPromptSnapshot? = null,
)

@Composable
private fun ImageComparisonPicker(
    candidates: List<ImageCompareCandidate>,
    onBack: () -> Unit,
    onSelect: (ImageCompareCandidate) -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Surface(shadowElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                    Text(stringResource(R.string.image_compare_choose), style = MaterialTheme.typography.titleLarge)
                }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                items(candidates, key = { it.reference }) { candidate ->
                    ElevatedCard(
                        Modifier.fillMaxWidth().clickable { onSelect(candidate) },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = imageReferenceModel(candidate.thumbnailReference),
                                contentDescription = stringResource(R.string.generated_image),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(112.dp).clip(MaterialTheme.shapes.medium),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    candidate.modelLabel ?: "—",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    candidate.dateLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    candidate.promptPreview.ifBlank { stringResource(R.string.image_compare_prompt_unavailable) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageViewer(
    reference: String,
    comparisonCandidates: List<ImageCompareCandidate> = emptyList(),
    initialComparison: ImageCompareCandidate? = null,
    startFullscreen: Boolean = false,
    onComparePrompts: ((ImageCompareCandidate) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var mode by remember(reference, startFullscreen) {
        mutableStateOf(if (startFullscreen) ImageViewerMode.FULLSCREEN else ImageViewerMode.CONTEXTUAL)
    }
    var loadFailed by remember(reference) { mutableStateOf(false) }
    var viewportSize by remember(reference) { mutableStateOf(IntSize.Zero) }
    var sourceSize by remember(reference) { mutableStateOf(IntSize.Zero) }
    var zoom by remember(reference) { mutableFloatStateOf(1f) }
    var panX by remember(reference) { mutableFloatStateOf(0f) }
    var panY by remember(reference) { mutableFloatStateOf(0f) }
    var chooseComparison by remember(reference) { mutableStateOf(false) }
    var comparisonCandidate by remember(reference, initialComparison?.id) { mutableStateOf(initialComparison) }
    val comparisonReference = comparisonCandidate?.reference
    var showingComparison by remember(reference, initialComparison?.id) { mutableStateOf(initialComparison != null) }
    val displayedReference = if (showingComparison) comparisonReference ?: reference else reference
    val fullscreen = mode == ImageViewerMode.FULLSCREEN
    val maximumZoom = ImageViewerPolicy.maximumOriginalPixelZoom(viewportSize, sourceSize)
    LaunchedEffect(fullscreen) {
        if (!fullscreen) {
            zoom = 1f
            panX = 0f
            panY = 0f
        }
    }
    LaunchedEffect(displayedReference) {
        zoom = 1f
        panX = 0f
        panY = 0f
    }

    Dialog(
        onDismissRequest = { if (chooseComparison) chooseComparison = false else onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = if (chooseComparison) false else ImageViewerPolicy.usePlatformDefaultWidth(mode),
            decorFitsSystemWindows = if (chooseComparison) false else ImageViewerPolicy.decorFitsSystemWindows(mode),
        ),
    ) {
        if (chooseComparison) {
            ImageComparisonPicker(
                candidates = comparisonCandidates,
                onBack = { chooseComparison = false },
                onSelect = { candidate ->
                    comparisonCandidate = candidate
                    showingComparison = true
                    chooseComparison = false
                },
            )
            return@Dialog
        }
        BoxWithConstraints(
            modifier = if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().fillMaxHeight(0.94f),
            contentAlignment = Alignment.Center,
        ) {
            val sourceAspectHeight = if (sourceSize.width > 0 && sourceSize.height > 0) {
                sourceSize.height.toFloat() / sourceSize.width.toFloat()
            } else {
                1216f / 832f
            }
            val contextualHeight = (maxWidth * sourceAspectHeight + 64.dp).coerceAtMost(maxHeight)
        Box(
            modifier = (if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(contextualHeight))
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
                model = imageReferenceModel(displayedReference),
                contentDescription = stringResource(R.string.generated_image),
                contentScale = ContentScale.Fit,
                onLoading = { loadFailed = false },
                onSuccess = {
                    loadFailed = false
                    sourceSize = IntSize(it.result.image.width, it.result.image.height)
                },
                onError = { loadFailed = true },
                modifier = Modifier.fillMaxSize()
                    .then(if (fullscreen) Modifier else Modifier.padding(top = 64.dp))
                    .clickable {
                        if (comparisonReference == null) onDismiss()
                        else showingComparison = !showingComparison
                    }
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
            if (comparisonReference != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart)
                        .then(if (fullscreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                        .padding(12.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(if (showingComparison) "B" else "A", Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
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
                    if (comparisonReference == null && comparisonCandidates.isNotEmpty()) {
                        IconButton(onClick = { chooseComparison = true }) {
                            Icon(Icons.Default.CompareArrows, contentDescription = stringResource(R.string.image_compare))
                        }
                    }
                    if (comparisonCandidate?.prompt != null && onComparePrompts != null) {
                        IconButton(onClick = { onComparePrompts(comparisonCandidate!!) }) {
                            Icon(Icons.Default.Description, contentDescription = stringResource(R.string.switch_to_prompt_comparison))
                        }
                    }
                    if (comparisonReference != null) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    }
                }
            }
        }
        }
    }
}
