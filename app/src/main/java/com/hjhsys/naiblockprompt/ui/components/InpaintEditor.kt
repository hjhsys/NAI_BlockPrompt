package com.hjhsys.naiblockprompt.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hjhsys.naiblockprompt.R
import com.hjhsys.naiblockprompt.domain.image.ImageDisplayMapping
import com.hjhsys.naiblockprompt.domain.image.InpaintMask
import com.hjhsys.naiblockprompt.domain.model.ImageInputMode
import com.hjhsys.naiblockprompt.domain.model.ImageInputState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/** Existing entry-point adapter. The editor itself returns only [InpaintMask] and never generates. */
@Composable
fun InpaintEditor(
    reference: String,
    initial: ImageInputState? = null,
    onDismiss: () -> Unit,
    onDone: (ImageInputState, Boolean) -> Unit,
) {
    val context = LocalContext.current
    var source by remember(reference) { mutableStateOf<Bitmap?>(null) }
    var initialMask by remember(reference, initial?.maskPngBase64) { mutableStateOf<InpaintMask?>(null) }
    var failed by remember(reference) { mutableStateOf(false) }

    LaunchedEffect(reference, initial?.maskPngBase64) {
        runCatching {
            withContext(Dispatchers.IO) {
                val uri = Uri.parse(reference).let { if (it.scheme == null) Uri.fromFile(File(reference)) else it }
                val bytes = requireNotNull(context.contentResolver.openInputStream(uri)?.use { it.readBytes() })
                val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                require(bitmap.width.toLong() * bitmap.height <= MAX_SOURCE_PIXELS)
                val restored = initial?.maskPngBase64?.let { encoded -> decodeMask(encoded, bitmap.width, bitmap.height) }
                    ?: InpaintMask.black(bitmap.width, bitmap.height)
                source = bitmap
                initialMask = restored
            }
        }.onFailure { failed = true }
    }
    DisposableEffect(source) {
        val bitmapForEffect = source
        onDispose { bitmapForEffect?.recycle() }
    }

    val bitmap = source
    val mask = initialMask
    if (bitmap != null && mask != null) {
        InpaintMaskEditor(bitmap, mask, onDismiss) { edited ->
            onDone(
                (initial ?: ImageInputState(reference, ImageInputMode.INPAINT, strength = .7f))
                    .copy(uri = reference, mode = ImageInputMode.INPAINT,
                        maskPngBase64 = Base64.encodeToString(edited.toPngBytes(), Base64.NO_WRAP)),
                false,
            )
        }
    } else {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) {
                    if (failed) Text(stringResource(R.string.inpaint_error), color = MaterialTheme.colorScheme.error)
                    else CircularProgressIndicator()
                }
            }
        }
    }
}

/** Mask-only editor. [mask] is copied so cancellation never mutates the caller's value. */
@Composable
fun InpaintMaskEditor(
    source: Bitmap,
    mask: InpaintMask,
    onDismiss: () -> Unit,
    onDone: (InpaintMask) -> Unit,
) {
    require(mask.matchesSource(source.width, source.height))
    var edited by remember(source, mask) { mutableStateOf(mask.copy()) }
    var revision by remember { mutableIntStateOf(0) }
    var erase by remember { mutableStateOf(false) }
    var brushFraction by remember { mutableFloatStateOf(.025f) }
    var undo by remember { mutableStateOf(emptyList<InpaintMask>()) }
    var redo by remember { mutableStateOf(emptyList<InpaintMask>()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val mapping = remember(source, canvasSize) {
        canvasSize.takeIf { it.width > 0 && it.height > 0 }?.let {
            ImageDisplayMapping.fit(source.width, source.height, it.width.toFloat(), it.height.toFloat())
        }
    }
    val sourceImage = remember(source) { source.asImageBitmap() }
    val overlayBitmap = remember(source.width, source.height) {
        Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ALPHA_8)
    }
    val overlayBytes = remember(overlayBitmap) { ByteArray(overlayBitmap.rowBytes * overlayBitmap.height) }
    val overlayBuffer = remember(overlayBytes) { ByteBuffer.wrap(overlayBytes) }
    val renderedRevision = remember(edited, revision, overlayBitmap) {
        edited.copyPixelsTo(overlayBytes, overlayBitmap.rowBytes)
        overlayBuffer.rewind()
        overlayBitmap.copyPixelsFromBuffer(overlayBuffer)
        revision
    }
    val overlay = remember(overlayBitmap) { overlayBitmap.asImageBitmap() }
    DisposableEffect(overlayBitmap) { onDispose { overlayBitmap.recycle() } }
    val accent = MaterialTheme.colorScheme.primary.copy(alpha = .48f)

    fun checkpoint() {
        undo = (undo + edited.copy()).takeLast(MAX_UNDO)
        redo = emptyList()
    }
    fun refresh() { revision++ }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
                Text(stringResource(R.string.inpaint_title), style = MaterialTheme.typography.titleLarge)
                Canvas(
                    Modifier.weight(1f).fillMaxWidth().onSizeChanged { canvasSize = it }
                        .pointerInput(mapping, erase, brushFraction) {
                            val currentMapping = mapping ?: return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val first = currentMapping.toSource(down.position.x, down.position.y)
                                    ?: return@awaitEachGesture
                                down.consume()
                                checkpoint()
                                val radius = (minOf(source.width, source.height) * brushFraction).toInt().coerceAtLeast(1)
                                edited.fillCircle(first.x, first.y, radius, regenerate = !erase)
                                refresh()
                                var previous = first
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    currentMapping.toSource(change.position.x, change.position.y)?.let { point ->
                                        edited.drawLine(previous.x, previous.y, point.x, point.y, radius, regenerate = !erase)
                                        previous = point
                                        refresh()
                                        change.consume()
                                    }
                                } while (change.pressed)
                            }
                        },
                ) {
                    renderedRevision
                    mapping?.let { fitted ->
                        val destinationOffset = IntOffset(fitted.display.left.toInt(), fitted.display.top.toInt())
                        val destinationSize = IntSize(fitted.display.width.toInt(), fitted.display.height.toInt())
                        drawImage(sourceImage, dstOffset = destinationOffset, dstSize = destinationSize)
                        drawImage(overlay, dstOffset = destinationOffset, dstSize = destinationSize,
                            colorFilter = ColorFilter.tint(accent))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(!erase, { erase = false }, label = { Text(stringResource(R.string.inpaint_brush)) })
                    FilterChip(erase, { erase = true }, label = { Text(stringResource(R.string.inpaint_eraser)) })
                    TextButton(onClick = {
                        checkpoint()
                        edited.clear()
                        refresh()
                    }) { Text(stringResource(R.string.inpaint_clear)) }
                }
                Text(stringResource(R.string.inpaint_size))
                Slider(brushFraction, { brushFraction = it }, valueRange = .005f.. .12f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row {
                        TextButton(onClick = {
                            redo = (redo + edited.copy()).takeLast(MAX_UNDO)
                            edited = undo.last()
                            undo = undo.dropLast(1)
                            refresh()
                        }, enabled = undo.isNotEmpty()) { Text(stringResource(R.string.inpaint_undo)) }
                        TextButton(onClick = {
                            undo = (undo + edited.copy()).takeLast(MAX_UNDO)
                            edited = redo.last()
                            redo = redo.dropLast(1)
                            refresh()
                        }, enabled = redo.isNotEmpty()) { Text(stringResource(R.string.inpaint_redo)) }
                    }
                    Row {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                        Button(onClick = { onDone(edited.copy()) }) { Text(stringResource(R.string.inpaint_done)) }
                    }
                }
            }
        }
    }
}

private fun decodeMask(encoded: String, width: Int, height: Int): InpaintMask {
    val bytes = Base64.decode(encoded, Base64.DEFAULT)
    val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    try {
        require(bitmap.width == width && bitmap.height == height)
        val colors = IntArray(width * height)
        bitmap.getPixels(colors, 0, width, 0, 0, width, height)
        val pixels = ByteArray(colors.size) { index ->
            when (colors[index]) {
                android.graphics.Color.BLACK -> InpaintMask.BLACK.toByte()
                android.graphics.Color.WHITE -> InpaintMask.WHITE.toByte()
                else -> throw IllegalArgumentException("Mask must be opaque black/white")
            }
        }
        return InpaintMask.fromBlackWhitePixels(width, height, pixels)
    } finally { bitmap.recycle() }
}

private const val MAX_UNDO = 20
private const val MAX_SOURCE_PIXELS = 16_777_216L
