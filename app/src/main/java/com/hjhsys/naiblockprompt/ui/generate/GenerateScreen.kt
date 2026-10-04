package com.hjhsys.naiblockprompt.ui.generate

import android.util.Log
import android.os.Build
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import kotlin.math.atan2
import kotlin.math.PI
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.focus.onFocusChanged
import com.hjhsys.naiblockprompt.R
import com.hjhsys.naiblockprompt.BuildConfig
import com.hjhsys.naiblockprompt.domain.editor.*
import com.hjhsys.naiblockprompt.ui.library.CharacterImportModePicker
import com.hjhsys.naiblockprompt.domain.model.*
import com.hjhsys.naiblockprompt.domain.prompt.PromptProcessor
import com.hjhsys.naiblockprompt.domain.prompt.PromptTokenEstimator
import com.hjhsys.naiblockprompt.domain.prompt.TokenRange
import com.hjhsys.naiblockprompt.ui.MainViewModel
import com.hjhsys.naiblockprompt.ui.TagInsertReturnTarget
import com.hjhsys.naiblockprompt.ui.GenerationUiState
import com.hjhsys.naiblockprompt.ui.components.AppTitleBar
import com.hjhsys.naiblockprompt.ui.components.AppTitleMenuItem
import com.hjhsys.naiblockprompt.ui.components.NumericSlider
import com.hjhsys.naiblockprompt.ui.components.NumericSliderSpec
import com.hjhsys.naiblockprompt.ui.components.NumericValueEditor
import com.hjhsys.naiblockprompt.ui.components.HelpAffordance
import com.hjhsys.naiblockprompt.ui.components.quotaStatusText
import com.hjhsys.naiblockprompt.ui.components.ImageCardActions
import com.hjhsys.naiblockprompt.ui.components.ImageViewer
import com.hjhsys.naiblockprompt.ui.components.ImageCompareCandidate
import com.hjhsys.naiblockprompt.data.network.nai.NaiApiFailure
import com.hjhsys.naiblockprompt.domain.generation.MissingGenerationField
import com.hjhsys.naiblockprompt.domain.generation.NaiCatalogOption
import com.hjhsys.naiblockprompt.domain.generation.NaiGenerationCatalog
import com.hjhsys.naiblockprompt.domain.generation.CharacterPositioning
import com.hjhsys.naiblockprompt.domain.generation.VibeTransferRequestMapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File
import java.text.DateFormat
import java.util.Date
import android.net.Uri
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.hjhsys.naiblockprompt.domain.image.NaiImageMetadata
import com.hjhsys.naiblockprompt.domain.image.NaiPngMetadataParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.hjhsys.naiblockprompt.domain.autocomplete.PromptAutocomplete
import com.hjhsys.naiblockprompt.domain.autocomplete.PromptFragment
import com.hjhsys.naiblockprompt.domain.autocomplete.TagSuggestion
import com.hjhsys.naiblockprompt.ui.library.RestoreDialog
import com.hjhsys.naiblockprompt.ui.database.TagEditDialog

internal val LocalQuickEditExitSignal = staticCompositionLocalOf { 0 }

private class PromptCursorCenterHost(
    val requestCenter: (Float) -> Unit,
)

private class StickyPromptToolbarState(
    val blockId: String,
    val label: String,
    val showToolbar: Boolean,
    val quickEdit: Boolean,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val showFormatter: Boolean,
    val autocomplete: StickyAutocompleteState?,
    val cursorTopInWindow: Float?,
    val cursorBottomInWindow: Float?,
    val onUndo: () -> Unit,
    val onRedo: () -> Unit,
    val onToggleQuickEdit: () -> Unit,
    val onFormatMultiline: () -> Unit,
    val onFormatSingleLine: () -> Unit,
)

private class StickyAutocompleteState(
    val local: List<TagSuggestion>,
    val novelAi: List<TagSuggestion>,
    val danbooru: List<TagSuggestion>,
    val wildcards: List<TagSuggestion>,
    val loading: Boolean,
    val novelAiFailed: Boolean,
    val danbooruFailed: Boolean,
    val requestStartedAtNanos: Long,
    val rowScrollStates: AutocompleteRowScrollStates,
    val selectedSource: AutocompleteDisplaySource,
    val onSourceChange: (AutocompleteDisplaySource) -> Unit,
    val onSelect: (TagSuggestion) -> Unit,
)

private enum class AutocompleteDisplaySource(@param:StringRes val label: Int) {
    LOCAL(R.string.autocomplete_local_row),
    NOVEL_AI(R.string.autocomplete_nai_row),
    DANBOORU(R.string.autocomplete_danbooru_row),
    ;

    fun shifted(delta: Int): AutocompleteDisplaySource {
        val values = entries
        return values[(ordinal + delta).mod(values.size)]
    }
}

private class AutocompleteRowScrollStates(
    val wildcards: LazyListState,
    val local: LazyListState,
    val novelAi: LazyListState,
    val danbooru: LazyListState,
)

@Composable
private fun rememberAutocompleteRowScrollStates(): AutocompleteRowScrollStates {
    val wildcards = rememberLazyListState()
    val local = rememberLazyListState()
    val novelAi = rememberLazyListState()
    val danbooru = rememberLazyListState()
    return remember(wildcards, local, novelAi, danbooru) {
        AutocompleteRowScrollStates(wildcards, local, novelAi, danbooru)
    }
}

private class PromptToolbarHost(
    val viewportTopInWindow: Float,
    val viewportBottomInWindow: Float,
    val update: (String, StickyPromptToolbarState?) -> Unit,
)

private class QuickEditActionState(
    val blockId: String,
    val selectionTopInWindow: Float?,
    val selectionBottomInWindow: Float?,
    val canAdjustWeight: Boolean,
    val canSetWeight: Boolean,
    val canMutate: Boolean,
    val moveActive: Boolean,
    val multiSelect: Boolean,
    val canShowTagInfo: Boolean,
    val onDecrease: () -> Unit,
    val onIncrease: () -> Unit,
    val onToggleWeightSign: () -> Unit,
    val onFinishWeightAdjustment: () -> Unit,
    val onWeightPressChanged: (Boolean) -> Unit,
    val onToggleMultiSelect: () -> Unit,
    val onShowTagInfo: () -> Unit,
    val onToggleComment: () -> Unit,
    val onDelete: () -> Unit,
    val onToggleMove: () -> Unit,
    val onCopy: () -> Unit,
    val onCut: () -> Unit,
    val onSetWeight: () -> Unit,
    val onLineBreakBefore: () -> Unit,
    val onLineBreakAfter: () -> Unit,
)

private class QuickEditToolbarHost(val update: (String, QuickEditActionState?) -> Unit)

private val LocalPromptToolbarHost = staticCompositionLocalOf<PromptToolbarHost?> { null }
private val LocalQuickEditToolbarHost = staticCompositionLocalOf<QuickEditToolbarHost?> { null }
private val LocalQuickEditToolbarPlacement = staticCompositionLocalOf { QuickEditToolbarPlacement.BELOW_SELECTION }
private val LocalQuickEditLongPressMillis = staticCompositionLocalOf { QuickEditPressTiming.DEFAULT_LONG_PRESS_MILLIS }
private val LocalQuickEditRepeatIntervalMillis = staticCompositionLocalOf { QuickEditPressTiming.DEFAULT_REPEAT_INTERVAL_MILLIS }
private val LocalStickyPromptControlsEnabled = staticCompositionLocalOf { true }
private val LocalPromptEditorOverlayPlacement = staticCompositionLocalOf { PromptEditorOverlayPlacement.DYNAMIC }
private val LocalPromptListScrolling = staticCompositionLocalOf { false }
private val LocalPromptCursorCenterHost = staticCompositionLocalOf<PromptCursorCenterHost?> { null }
private val LocalTagInsertReturnTarget = staticCompositionLocalOf<TagInsertReturnTarget?> { null }

@Composable
private fun QuickEditActionToolbar(state: QuickEditActionState, modifier: Modifier = Modifier) {
    var showMore by remember(state.blockId) { mutableStateOf(false) }
    val commentDescription = stringResource(R.string.quick_edit_comment)
    val toggleWeightSignDescription = stringResource(R.string.quick_edit_toggle_weight_sign)
    val longPressMillis = LocalQuickEditLongPressMillis.current
    val repeatIntervalMillis = LocalQuickEditRepeatIntervalMillis.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        RepeatingIconButton(R.string.quick_edit_decrease, Icons.Default.Remove, state.canAdjustWeight, longPressMillis, repeatIntervalMillis, state.onDecrease, state.onFinishWeightAdjustment, state.onWeightPressChanged)
        RepeatingIconButton(R.string.quick_edit_increase, Icons.Default.Add, state.canAdjustWeight, longPressMillis, repeatIntervalMillis, state.onIncrease, state.onFinishWeightAdjustment, state.onWeightPressChanged)
        TextButton(
            onClick = state.onToggleWeightSign,
            enabled = state.canAdjustWeight,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(36.dp).semantics { contentDescription = toggleWeightSignDescription },
        ) { Text("±", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        TextButton(
            onClick = state.onToggleComment,
            enabled = state.canMutate,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(36.dp).semantics { contentDescription = "# · $commentDescription" },
        ) { Text("##", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black) }
        SmallIconButton(R.string.delete, Icons.Default.Delete, state.canMutate, state.onDelete)
        IconButton(
            onClick = state.onToggleMove,
            enabled = state.canMutate,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (state.moveActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                contentColor = if (state.moveActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.size(36.dp),
        ) { Icon(Icons.Default.OpenWith, stringResource(R.string.quick_edit_move), Modifier.size(20.dp)) }
        IconButton(
            onClick = state.onToggleMultiSelect,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (state.multiSelect) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                contentColor = if (state.multiSelect) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                if (state.multiSelect) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                stringResource(R.string.quick_edit_multi_select),
                Modifier.size(20.dp),
            )
        }
        Box {
            SmallIconButton(R.string.more_actions, Icons.Default.MoreVert, true) { showMore = true }
            DropdownMenu(showMore, { showMore = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.quick_edit_tag_info)) },
                    leadingIcon = { Icon(Icons.Default.Info, null) },
                    enabled = state.canShowTagInfo,
                    onClick = { showMore = false; state.onShowTagInfo() },
                )
                DropdownMenuItem(text = { Text(stringResource(R.string.quick_edit_copy)) }, onClick = { showMore = false; state.onCopy() })
                DropdownMenuItem(text = { Text(stringResource(R.string.cut)) }, enabled = state.canMutate, onClick = { showMore = false; state.onCut() })
                DropdownMenuItem(text = { Text(stringResource(R.string.quick_edit_set_weight)) }, enabled = state.canSetWeight, onClick = { showMore = false; state.onSetWeight() })
                DropdownMenuItem(text = { Text(stringResource(R.string.quick_edit_line_break_before)) }, enabled = state.canMutate, onClick = { showMore = false; state.onLineBreakBefore() })
                DropdownMenuItem(text = { Text(stringResource(R.string.quick_edit_line_break_after)) }, enabled = state.canMutate, onClick = { showMore = false; state.onLineBreakAfter() })
            }
        }
    }
}

internal fun shouldShowStickyPromptToolbar(
    active: Boolean,
    inlineToolbarTopInWindow: Float,
    viewportBottomInWindow: Float,
    thresholdPx: Float,
): Boolean = active &&
    inlineToolbarTopInWindow.isFinite() &&
    viewportBottomInWindow.isFinite() &&
    inlineToolbarTopInWindow >= viewportBottomInWindow - thresholdPx

internal fun effectivePromptViewportBottom(
    viewportTopInWindow: Float,
    viewportBottomInWindow: Float,
    imeBottomPx: Int,
    windowHeightPx: Int,
): Float {
    if (!viewportTopInWindow.isFinite() || !viewportBottomInWindow.isFinite()) return viewportBottomInWindow
    val imeTopInWindow = (windowHeightPx - imeBottomPx.coerceAtLeast(0)).toFloat()
    return minOf(viewportBottomInWindow, imeTopInWindow).coerceAtLeast(viewportTopInWindow)
}

internal fun effectivePromptViewportTop(
    measuredViewportTopInWindow: Float,
    titleBarBottomInWindow: Float,
    systemTopInsetPx: Int,
): Float = maxOf(
    systemTopInsetPx.coerceAtLeast(0).toFloat(),
    measuredViewportTopInWindow.takeIf { it.isFinite() } ?: Float.NEGATIVE_INFINITY,
    titleBarBottomInWindow.takeIf { it.isFinite() } ?: Float.NEGATIVE_INFINITY,
)

internal fun shouldShowStickyAutocomplete(
    editorTopInWindow: Float,
    editorBottomInWindow: Float,
    viewportTopInWindow: Float,
    viewportBottomInWindow: Float,
): Boolean = editorTopInWindow.isFinite() &&
    editorBottomInWindow.isFinite() &&
    viewportTopInWindow.isFinite() &&
    viewportBottomInWindow.isFinite() &&
    editorBottomInWindow > viewportTopInWindow &&
    editorTopInWindow < viewportBottomInWindow

internal fun shouldShowInlineAutocomplete(
    inlineTopInWindow: Float,
    estimatedHeightPx: Float,
    viewportTopInWindow: Float,
    viewportBottomInWindow: Float,
    marginPx: Float,
): Boolean = inlineTopInWindow.isFinite() &&
    estimatedHeightPx > 0f &&
    viewportTopInWindow.isFinite() &&
    viewportBottomInWindow.isFinite() &&
    inlineTopInWindow >= viewportTopInWindow &&
    inlineTopInWindow + estimatedHeightPx + marginPx <= viewportBottomInWindow

internal object EditorOverlayPlacementPolicy {
    fun verticalPosition(
        popupHeight: Int,
        visibleTop: Int,
        visibleBottom: Int,
        margin: Int,
        cursorTop: Int? = null,
        cursorBottom: Int? = null,
        preferBottom: Boolean = false,
        placement: PromptEditorOverlayPlacement = PromptEditorOverlayPlacement.DYNAMIC,
    ): Int {
        val top = visibleTop + margin
        val bottom = visibleBottom - margin
        val bottomAligned = (bottom - popupHeight).coerceAtLeast(top)
        if (placement == PromptEditorOverlayPlacement.TOP) return top
        if (placement == PromptEditorOverlayPlacement.BOTTOM) return bottomAligned
        val preferred = if (preferBottom) bottomAligned else top
        val alternate = if (preferBottom) top else bottomAligned
        if (cursorTop != null && cursorBottom != null) {
            fun overlapsCursor(y: Int): Boolean = y < cursorBottom && y + popupHeight > cursorTop
            if (!overlapsCursor(preferred)) return preferred
            if (!overlapsCursor(alternate)) return alternate
            val cursorCenter = (cursorTop + cursorBottom) / 2
            val topDistance = kotlin.math.abs(top + popupHeight / 2 - cursorCenter)
            val bottomDistance = kotlin.math.abs(bottomAligned + popupHeight / 2 - cursorCenter)
            return if (topDistance >= bottomDistance) top else bottomAligned
        }
        return preferred
    }
}

private class FixedWindowPopupPositionProvider(
    private val leftInWindow: Int,
    private val topInWindow: Int,
    private val margin: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        x = leftInWindow.coerceIn(
            margin,
            (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin),
        ),
        y = topInWindow.coerceIn(
            margin,
            (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin),
        ),
    )
}

@Composable
private fun StickyPromptToolbar(state: StickyPromptToolbarState, modifier: Modifier = Modifier) {
    var showFormatterMenu by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 8.dp,
        tonalElevation = 3.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                state.label,
                modifier = Modifier.weight(1f).padding(horizontal = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(state.onUndo, enabled = state.canUndo, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Undo, stringResource(R.string.undo_prompt_edit), Modifier.size(20.dp))
            }
            IconButton(state.onRedo, enabled = state.canRedo, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Redo, stringResource(R.string.redo_prompt_edit), Modifier.size(20.dp))
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
                FilterChip(
                    selected = state.quickEdit,
                    onClick = state.onToggleQuickEdit,
                    label = { Text(stringResource(R.string.quick_edit), fontSize = 11.sp, maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.TouchApp, null, Modifier.size(15.dp)) },
                )
            }
            if (state.showFormatter) Box {
                IconButton(onClick = { showFormatterMenu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions), Modifier.size(20.dp))
                }
                DropdownMenu(showFormatterMenu, { showFormatterMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.format_multiline)) },
                        onClick = { showFormatterMenu = false; state.onFormatMultiline() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.format_single_line)) },
                        onClick = { showFormatterMenu = false; state.onFormatSingleLine() },
                    )
                }
            }
        }
    }
}

@Composable
private fun StickyPromptEditorControls(state: StickyPromptToolbarState, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.autocomplete?.let { suggestions ->
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
            ) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    AutocompleteSuggestions(
                        local = suggestions.local,
                        novelAi = suggestions.novelAi,
                        danbooru = suggestions.danbooru,
                        wildcards = suggestions.wildcards,
                        loading = suggestions.loading,
                        novelAiFailed = suggestions.novelAiFailed,
                        danbooruFailed = suggestions.danbooruFailed,
                        requestStartedAtNanos = suggestions.requestStartedAtNanos,
                        rowScrollStates = suggestions.rowScrollStates,
                        selectedSource = suggestions.selectedSource,
                        onSourceChange = suggestions.onSourceChange,
                        onSelect = suggestions.onSelect,
                    )
                }
            }
        }
        if (state.showToolbar) StickyPromptToolbar(state)
    }
}

@Composable
fun GenerateScreen(
    session: Session?,
    appSettings: AppSettings,
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenGenerationSettings: () -> Unit,
    onOpenTagDatabase: () -> Unit,
    headerTokenSummary: String?,
) {
    if (session == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.loading_session))
            }
        }
        return
    }

    val firstBlockName = stringResource(R.string.default_block_name, 1)
    val context = LocalContext.current
    val overlayDensity = LocalDensity.current
    val windowConfiguration = LocalConfiguration.current
    val windowHeightPx = remember(context, windowConfiguration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds.height()
        } else {
            context.resources.displayMetrics.heightPixels
        }
    }
    val systemTopInsetPx = WindowInsets.statusBars.getTop(overlayDensity)
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val duplicateWarning by viewModel.duplicateWarning.collectAsStateWithLifecycle()
    val lastSentPrompt by viewModel.lastSentPrompt.collectAsStateWithLifecycle()
    var showCharacterTypeDialog by rememberSaveable { mutableStateOf(false) }
    var showLastSentPrompt by rememberSaveable { mutableStateOf(false) }
    var currentPromptPreview by remember { mutableStateOf<GeneratedPromptSnapshot?>(null) }
    var showDefaultBlockScale by rememberSaveable { mutableStateOf(false) }
    var defaultBlockScaleInput by remember(session.id) { mutableStateOf(session.defaultBlockScale) }
    val hasStash by viewModel.hasStash.collectAsStateWithLifecycle()
    val loadUiRevision by viewModel.loadUiRevision.collectAsStateWithLifecycle()
    val tagInsertReturnTarget by viewModel.tagInsertReturnTarget.collectAsStateWithLifecycle()
    val subscriptionStatus by viewModel.subscriptionStatus.collectAsStateWithLifecycle()
    val wildcardItems by viewModel.wildcards.collectAsStateWithLifecycle()
    val modelSummary = compactModelName(session.generationSettings.modelId)
    val seedSummary = stringResource(if (session.generationSettings.seedMode == SeedMode.RANDOM) R.string.seed_status_random else R.string.seed_status_fixed)
    val wildcardValues = remember(wildcardItems) {
        wildcardItems.associate { it.name to it.valuesText.lines().filter(String::isNotBlank) }
    }
    fun sectionTokenRange(pair: PromptPair, textRendering: TextRenderingState): TokenRange =
        PromptTokenEstimator.estimate(
            if (appSettings.useTextRendering) PromptProcessor.appendTextRendering(
                PromptProcessor.joinEnabledBlocks(pair.positiveBlocks, appSettings.normalizeWeightClosings),
                textRendering,
            ) else PromptProcessor.joinEnabledBlocks(pair.positiveBlocks, appSettings.normalizeWeightClosings),
            wildcardValues,
        )
    val supportsT5Estimate = session.generationSettings.modelId?.contains("diffusion-4") == true
    var activeTagTarget by remember { mutableStateOf<TagEditorTarget?>(null) }
    var cursorCenterReturnTarget by remember { mutableStateOf<TagInsertReturnTarget?>(null) }
    var colorTarget by remember { mutableStateOf<TagEditorTarget?>(null) }
    var showColorHelper by rememberSaveable { mutableStateOf(false) }
    val restoredPromptScrollPosition = remember(viewModel) { viewModel.generatePromptScrollPosition() }
    val promptListState = rememberLazyListState(
        initialFirstVisibleItemIndex = restoredPromptScrollPosition.itemIndex,
        initialFirstVisibleItemScrollOffset = restoredPromptScrollPosition.scrollOffset,
    )
    LaunchedEffect(promptListState, viewModel) {
        snapshotFlow {
            promptListState.firstVisibleItemIndex to promptListState.firstVisibleItemScrollOffset
        }.collect { (itemIndex, scrollOffset) ->
            viewModel.updateGeneratePromptScrollPosition(itemIndex, scrollOffset)
        }
    }
    LaunchedEffect(loadUiRevision) {
        if (loadUiRevision > 0) promptListState.scrollToItem(0)
    }
    LaunchedEffect(tagInsertReturnTarget?.revision) {
        val target = tagInsertReturnTarget ?: return@LaunchedEffect
        val scaleOffset = if (session.blockScaleEnabled) 1 else 0
        val sectionIndex = when (val owner = target.owner) {
            PromptOwner.Base -> scaleOffset
            is PromptOwner.Character -> {
                val characterIndex = session.characters.sortedBy { it.order }.indexOfFirst { it.id == owner.id }
                if (characterIndex < 0) scaleOffset else scaleOffset + 1 + characterIndex
            }
        }
        promptListState.scrollToItem(sectionIndex.coerceAtLeast(0))
        cursorCenterReturnTarget = target
        activeTagTarget = TagEditorTarget(target.owner, target.polarity, target.blockId, target.cursor)
        viewModel.consumeTagInsertReturnTarget(target.revision)
    }
    var shortcutArea by remember { mutableStateOf(IntSize.Zero) }
    var promptViewportTopInWindow by remember { mutableFloatStateOf(Float.NEGATIVE_INFINITY) }
    var promptViewportBottomInWindow by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
    var titleBarBottomInWindow by remember { mutableFloatStateOf(Float.NEGATIVE_INFINITY) }
    val effectivePromptViewportTopInWindow = effectivePromptViewportTop(
        measuredViewportTopInWindow = promptViewportTopInWindow,
        titleBarBottomInWindow = titleBarBottomInWindow,
        systemTopInsetPx = systemTopInsetPx,
    )
    val imeBottomPx = WindowInsets.ime.getBottom(overlayDensity)
    val effectivePromptViewportBottomInWindow = effectivePromptViewportBottom(
        viewportTopInWindow = effectivePromptViewportTopInWindow,
        viewportBottomInWindow = promptViewportBottomInWindow,
        imeBottomPx = imeBottomPx,
        windowHeightPx = windowHeightPx,
    )
    val promptScrollScope = rememberCoroutineScope()
    val promptCursorCenterHost = remember(
        promptListState,
        effectivePromptViewportTopInWindow,
        effectivePromptViewportBottomInWindow,
    ) {
        PromptCursorCenterHost { cursorCenterInWindow ->
            val viewportCenter = (effectivePromptViewportTopInWindow + effectivePromptViewportBottomInWindow) / 2f
            if (cursorCenterInWindow.isFinite() && viewportCenter.isFinite()) {
                promptScrollScope.launch {
                    promptListState.scrollBy(cursorCenterInWindow - viewportCenter)
                }
            }
        }
    }
    var stickyPromptToolbar by remember { mutableStateOf<StickyPromptToolbarState?>(null) }
    var bottomQuickEditToolbar by remember { mutableStateOf<QuickEditActionState?>(null) }
    var editorOverlayStackHeight by remember { mutableIntStateOf(0) }
    var databasePosition by rememberSaveable { mutableStateOf(floatArrayOf(-1f, -1f)) }
    var colorPosition by rememberSaveable { mutableStateOf(floatArrayOf(-1f, -1f)) }
    if (duplicateWarning) AlertDialog(
        onDismissRequest = viewModel::cancelDuplicateGeneration,
        title = { Text(stringResource(R.string.duplicate_generation_title)) },
        text = { Text(stringResource(R.string.duplicate_generation_message)) },
        confirmButton = { Button(onClick = viewModel::confirmDuplicateGeneration) { Text(stringResource(R.string.generate_anyway)) } },
        dismissButton = { TextButton(onClick = viewModel::cancelDuplicateGeneration) { Text(stringResource(R.string.cancel)) } },
    )
    if (showCharacterTypeDialog) {
        CharacterTypeDialog(
            onDismiss = { showCharacterTypeDialog = false },
            onSelect = { type ->
                viewModel.addCharacter(type, firstBlockName)
                showCharacterTypeDialog = false
            },
        )
    }
    if (showLastSentPrompt && lastSentPrompt != null) {
        ProcessedPromptDialog(lastSentPrompt!!) { showLastSentPrompt = false }
    }
    currentPromptPreview?.let { preview ->
        ProcessedPromptDialog(
            prompt = preview,
            title = stringResource(R.string.current_prompt_preview_title),
        ) { currentPromptPreview = null }
    }
    if (showDefaultBlockScale) AlertDialog(
        onDismissRequest = { showDefaultBlockScale = false },
        title = { Text(stringResource(R.string.default_block_scale)) },
        text = {
            OutlinedTextField(
                value = defaultBlockScaleInput,
                onValueChange = { defaultBlockScaleInput = it },
                label = { Text(stringResource(R.string.default_block_scale)) },
                supportingText = { Text(stringResource(R.string.default_block_scale_hint)) },
                isError = defaultBlockScaleInput.toBigDecimalOrNull() == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(
                enabled = defaultBlockScaleInput.toBigDecimalOrNull() != null,
                onClick = {
                    viewModel.updateBlockScale(true, defaultBlockScaleInput)
                    showDefaultBlockScale = false
                },
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = { showDefaultBlockScale = false }) { Text(stringResource(R.string.cancel)) } },
    )
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Box(
                Modifier.onGloballyPositioned { coordinates ->
                    titleBarBottomInWindow = coordinates.positionInWindow().y + coordinates.size.height
                },
            ) {
                AppTitleBar(
                    R.string.generate_title,
                    subtitle = stringResource(R.string.generate_status_summary, modelSummary, seedSummary),
                    onSubtitleClick = viewModel::toggleSeedMode,
                    trailingOverline = headerTokenSummary,
                    trailingSubtitle = quotaStatusText(subscriptionStatus),
                    menuItems = listOf(
                        AppTitleMenuItem(R.string.current_prompt_preview, Icons.Default.Description, onClick = {
                            val preview = viewModel.previewCurrentPrompt()
                            if (preview != null) currentPromptPreview = preview
                            else android.widget.Toast.makeText(context, R.string.current_prompt_preview_unavailable, android.widget.Toast.LENGTH_SHORT).show()
                        }),
                        AppTitleMenuItem(R.string.load_preset, Icons.Default.FolderOpen, onClick = viewModel::beginPresetLoad),
                        AppTitleMenuItem(R.string.save_preset, Icons.Default.Save, onClick = viewModel::beginPresetSave),
                        AppTitleMenuItem(R.string.previous_work, Icons.Default.Restore, enabled = hasStash, onClick = viewModel::swapStash),
                        AppTitleMenuItem(R.string.nav_settings, Icons.Default.Settings, onClick = onOpenSettings),
                    ),
                )
            }
        },
    ) { innerPadding -> Box(
        Modifier.fillMaxSize().padding(innerPadding)
            .onSizeChanged { shortcutArea = it }
            .onGloballyPositioned { coordinates ->
                promptViewportTopInWindow = coordinates.positionInWindow().y
                promptViewportBottomInWindow = promptViewportTopInWindow + coordinates.size.height
            },
    ) {
      CompositionLocalProvider(
          LocalStickyPromptControlsEnabled provides appSettings.showStickyPromptControls,
          LocalPromptEditorOverlayPlacement provides appSettings.promptEditorOverlayPlacement,
          LocalPromptListScrolling provides promptListState.isScrollInProgress,
          LocalPromptCursorCenterHost provides promptCursorCenterHost,
          LocalTagInsertReturnTarget provides cursorCenterReturnTarget,
          LocalQuickEditToolbarPlacement provides appSettings.quickEditToolbarPlacement,
          LocalQuickEditLongPressMillis provides appSettings.quickEditLongPressMillis,
          LocalQuickEditRepeatIntervalMillis provides appSettings.quickEditRepeatIntervalMillis,
          LocalQuickEditToolbarHost provides QuickEditToolbarHost { blockId, state ->
              if (state != null) bottomQuickEditToolbar = state
              else if (bottomQuickEditToolbar?.blockId == blockId) bottomQuickEditToolbar = null
          },
          LocalPromptToolbarHost provides PromptToolbarHost(effectivePromptViewportTopInWindow, effectivePromptViewportBottomInWindow) { blockId, state ->
              if (state != null) stickyPromptToolbar = state
              else if (stickyPromptToolbar?.blockId == blockId) stickyPromptToolbar = null
          },
      ) {
      LazyColumn(
        state = promptListState,
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures {
                activeTagTarget = null
                viewModel.clearAutocomplete()
                viewModel.cancelTagInsert()
            }
        },
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 12.dp,
            end = 12.dp,
            bottom = 76.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (session.blockScaleEnabled) item {
            FilledTonalButton(
                onClick = {
                    defaultBlockScaleInput = session.defaultBlockScale
                    showDefaultBlockScale = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Tune, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.base_scale_value, session.defaultBlockScale))
            }
        }
        item {
            PromptSectionCard(
                title = stringResource(R.string.base_prompt),
                owner = PromptOwner.Base,
                pair = session.base.prompts,
                selectedPolarity = session.base.selectedPolarity,
                textRendering = session.base.textRendering,
                collapsed = session.base.collapsed,
                showFormatter = appSettings.showFormatterActions,
                quickEditWeightStep = appSettings.quickEditWeightStep,
                blockScaleEnabled = session.blockScaleEnabled,
                defaultBlockScale = session.defaultBlockScale,
                showTextRendering = appSettings.useTextRendering,
                tokenRange = if (supportsT5Estimate && appSettings.showTokenEstimates) sectionTokenRange(session.base.prompts, session.base.textRendering) else null,
                viewModel = viewModel,
                onCollapsedChange = viewModel::setBaseCollapsed,
                onOpenTagDictionary = { owner, polarity, blockId, cursor ->
                    viewModel.beginTagInsert(owner, polarity, blockId, cursor, exactOffset = true)
                    onOpenTagDatabase()
                },
                onTagEditorState = { owner, polarity, blockId, focused, cursor ->
                    activeTagTarget = if (focused) TagEditorTarget(owner, polarity, blockId, cursor)
                    else activeTagTarget?.takeUnless { it.blockId == blockId }
                },
            )
        }
        itemsIndexed(session.characters.sortedBy { it.order }, key = { _, item -> item.id }) { index, character ->
            CharacterSectionCard(
                index = index,
                character = character,
                count = session.characters.size,
                alternateColors = appSettings.alternateCharacterColors,
                showFormatter = appSettings.showFormatterActions,
                quickEditWeightStep = appSettings.quickEditWeightStep,
                blockScaleEnabled = session.blockScaleEnabled,
                defaultBlockScale = session.defaultBlockScale,
                showTextRendering = appSettings.useTextRendering,
                tokenRange = if (supportsT5Estimate && appSettings.showTokenEstimates) sectionTokenRange(character.prompts, character.textRendering) else null,
                viewModel = viewModel,
                onOpenTagDictionary = { owner, polarity, blockId, cursor ->
                    viewModel.beginTagInsert(owner, polarity, blockId, cursor, exactOffset = true)
                    onOpenTagDatabase()
                },
            ) { owner, polarity, blockId, focused, cursor ->
                activeTagTarget = if (focused) TagEditorTarget(owner, polarity, blockId, cursor)
                else activeTagTarget?.takeUnless { it.blockId == blockId }
            }
        }
        item {
            FilledTonalButton(
                onClick = { showCharacterTypeDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_character))
            }
        }
        if (session.characters.any { it.enabled }) item {
            CharacterPositioningButton(session, viewModel)
        }
        item {
            Text(stringResource(R.string.not_official_notice), style = MaterialTheme.typography.bodySmall)
        }
        if (BuildConfig.DEBUG && lastSentPrompt != null) item {
            OutlinedButton(
                onClick = { showLastSentPrompt = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Code, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.prompt_preview_debug_full))
            }
        }
      }
      }
      PromptScrollIndicator(promptListState, Modifier.align(Alignment.CenterEnd))
      activeTagTarget?.let { target ->
          DraggablePromptShortcut(
              area = shortcutArea, position = databasePosition, defaultOffset = 72,
              onPositionChange = { databasePosition = it },
              onClick = {
                  viewModel.beginTagInsert(target.owner, target.polarity, target.blockId, target.cursor)
                  onOpenTagDatabase()
              },
          ) { Icon(Icons.Default.Storage, stringResource(R.string.open_tag_dictionary)) }
      }
      val colorShortcutVisible = when (appSettings.colorHelperMode) {
          ColorHelperMode.ALWAYS -> true
          ColorHelperMode.WHILE_EDITING -> activeTagTarget != null
          ColorHelperMode.OFF -> false
      }
      if (colorShortcutVisible) {
          DraggablePromptShortcut(
              area = shortcutArea, position = colorPosition, defaultOffset = 132,
              onPositionChange = { colorPosition = it },
              onClick = { colorTarget = activeTagTarget; showColorHelper = true },
          ) { Icon(Icons.Default.Palette, stringResource(R.string.color_helper)) }
      }
      if (showColorHelper) {
          ColorHelperSheet(
              settings = appSettings,
              canInsert = colorTarget != null,
              onDismiss = { showColorHelper = false },
              onFavorite = viewModel::toggleFavoriteColor,
              onUsed = viewModel::recordRecentColor,
              onInsert = { hex ->
                  colorTarget?.let { viewModel.insertColor(it.owner, it.polarity, it.blockId, it.cursor, hex) }
                  showColorHelper = false
              },
          )
      }
      val transientBarsVisible = !LocalGeneratePagerScrolling.current && !promptListState.isScrollInProgress
      if (transientBarsVisible) {
          val quickEditActions = bottomQuickEditToolbar
              ?.takeIf { appSettings.quickEditToolbarPlacement == QuickEditToolbarPlacement.BOTTOM }
          val promptToolbar = stickyPromptToolbar?.takeIf { appSettings.showStickyPromptControls }
          if (quickEditActions != null || promptToolbar != null) {
              val overlayMarginPx = with(overlayDensity) { 6.dp.roundToPx() }
              val overlayViewportTop = effectivePromptViewportTopInWindow.toInt()
              val overlayViewportBottom = effectivePromptViewportBottomInWindow.takeIf(Float::isFinite)?.toInt()
                  ?: shortcutArea.height
              val targetTop = quickEditActions?.selectionTopInWindow ?: promptToolbar?.cursorTopInWindow
              val targetBottom = quickEditActions?.selectionBottomInWindow ?: promptToolbar?.cursorBottomInWindow
              val overlayY = EditorOverlayPlacementPolicy.verticalPosition(
                  popupHeight = editorOverlayStackHeight,
                  visibleTop = overlayViewportTop,
                  visibleBottom = overlayViewportBottom,
                  margin = overlayMarginPx,
                  cursorTop = targetTop?.toInt(),
                  cursorBottom = targetBottom?.toInt(),
                  preferBottom = imeBottomPx <= 0,
                  placement = appSettings.promptEditorOverlayPlacement,
              )
              val overlayOffsetY = (overlayY - promptViewportTopInWindow.toInt()).coerceAtLeast(0)
              Column(
                  modifier = Modifier
                      .align(Alignment.TopCenter)
                      .offset { IntOffset(0, overlayOffsetY) }
                      .padding(
                          start = 12.dp,
                          end = 12.dp,
                      )
                      .onSizeChanged { editorOverlayStackHeight = it.height },
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                  quickEditActions?.let { actions ->
                      Surface(
                          shape = MaterialTheme.shapes.large,
                          color = MaterialTheme.colorScheme.surfaceContainerHigh,
                          tonalElevation = 4.dp,
                          shadowElevation = 7.dp,
                      ) { QuickEditActionToolbar(actions, Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) }
                  }
                  promptToolbar?.let { toolbar ->
                      StickyPromptEditorControls(state = toolbar)
                  }
              }
          }
      }
    } }
}

@Composable
private fun DraggablePromptShortcut(
    area: IntSize,
    position: FloatArray,
    defaultOffset: Int,
    onPositionChange: (FloatArray) -> Unit,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val buttonSize = with(density) { 48.dp.toPx() }
    val maxX = (area.width - buttonSize).coerceAtLeast(0f)
    val maxY = (area.height - buttonSize).coerceAtLeast(0f)
    val defaultX = (maxX - with(density) { 14.dp.toPx() }).coerceIn(0f, maxX)
    val defaultY = (maxY / 2f + with(density) { defaultOffset.dp.toPx() }).coerceIn(0f, maxY)
    val x = if (position[0] < 0f) defaultX else position[0] * maxX
    val y = if (position[1] < 0f) defaultY else position[1] * maxY
    val latestOrigin by rememberUpdatedState(Offset(x, y))
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt()) }
            .size(48.dp)
            .pointerInput(maxX, maxY) {
                var dragged = Offset.Zero
                detectDragGestures(
                    onDragStart = { dragged = latestOrigin },
                    onDrag = { change, amount ->
                        change.consume()
                        dragged = Offset((dragged.x + amount.x).coerceIn(0f, maxX), (dragged.y + amount.y).coerceIn(0f, maxY))
                        onPositionChange(floatArrayOf(if (maxX > 0) dragged.x / maxX else 0f, if (maxY > 0) dragged.y / maxY else 0f))
                    },
                )
            },
        content = content,
    )
}

@Composable
private fun ColorHelperSheet(
    settings: AppSettings,
    canInsert: Boolean,
    onDismiss: () -> Unit,
    onFavorite: (String) -> Unit,
    onUsed: (String) -> Unit,
    onInsert: (String) -> Unit,
) {
    var hue by rememberSaveable { mutableFloatStateOf(330f) }
    var saturation by rememberSaveable { mutableFloatStateOf(0.35f) }
    var value by rememberSaveable { mutableFloatStateOf(0.9f) }
    var hexInput by rememberSaveable { mutableStateOf("#E8A9C3") }
    var invalidHex by rememberSaveable { mutableStateOf(false) }
    var showFavoriteList by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    fun currentHex(): String = String.format("#%06X", android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)) and 0xFFFFFF)
    fun syncHex() { hexInput = currentHex(); invalidHex = false }
    fun applyHex(raw: String) {
        hexInput = raw
        val normalized = raw.removePrefix("#").let { if (it.length == 3) it.map { c -> "$c$c" }.joinToString("") else it }
        val rgb = normalized.takeIf { it.length == 6 && it.all { character -> character.digitToIntOrNull(16) != null } }?.toIntOrNull(16)
        if (rgb == null) invalidHex = true else {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(0xFF000000.toInt() or rgb, hsv)
            hue = hsv[0]; saturation = hsv[1]; value = hsv[2]; hexInput = "#${normalized.uppercase()}"; invalidHex = false
        }
    }
    if (showFavoriteList) FavoriteColorDialog(
        colors = settings.favoriteColors,
        usage = settings.favoriteColorUsage,
        addedAt = settings.favoriteColorAddedAt,
        onSelect = { applyHex(it); showFavoriteList = false },
        onDismiss = { showFavoriteList = false },
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = true)) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
          Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.color_picker), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { showFavoriteList = true }) {
                    Icon(Icons.Default.Star, stringResource(R.string.color_favorites), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ColorWheelSvPicker(hue, saturation, value) { h, s, v -> hue = h; saturation = s; value = v; syncHex() }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.size(52.dp), shape = MaterialTheme.shapes.medium, color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {}
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = ::applyHex,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.color_hex)) },
                    isError = invalidHex,
                    supportingText = if (invalidHex) {{ Text(stringResource(R.string.invalid_hex_color)) }} else null,
                    trailingIcon = {
                        IconButton(onClick = { onInsert(currentHex()) }, enabled = canInsert && !invalidHex) {
                            Icon(Icons.Default.KeyboardReturn, stringResource(R.string.insert_color))
                        }
                    },
                    singleLine = true,
                )
            }
            ColorSlider(R.string.color_hue, hue, 0f..360f, "${hue.toInt()}°") { hue = it; syncHex() }
            ColorSlider(R.string.color_saturation, saturation, 0f..1f, "${(saturation * 100).toInt()}%") { saturation = it; syncHex() }
            ColorSlider(R.string.color_value, value, 0f..1f, "${(value * 100).toInt()}%") { value = it; syncHex() }
            ColorChipRow(R.string.color_recent, settings.recentColors, ::applyHex)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    FilledTonalIconButton(onClick = { onFavorite(currentHex()) }) {
                        Icon(if (currentHex() in settings.favoriteColors) Icons.Default.Star else Icons.Default.StarBorder, stringResource(R.string.favorite_color))
                    }
                    Text(stringResource(if (currentHex() in settings.favoriteColors) R.string.remove_favorite_color else R.string.favorite_color), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                  FilledTonalIconButton(onClick = {
                    val hex = currentHex(); clipboard.setText(AnnotatedString(hex)); onUsed(hex)
                    android.widget.Toast.makeText(context, context.getString(R.string.color_copied, hex), android.widget.Toast.LENGTH_SHORT).show()
                  }) { Icon(Icons.Default.ContentCopy, stringResource(R.string.copy_color)) }
                  Text(stringResource(R.string.copy_color), style = MaterialTheme.typography.labelSmall)
                }
            }
          }
        }
    }
}

@Composable
private fun ColorWheelSvPicker(hue: Float, saturation: Float, value: Float, onChange: (Float, Float, Float) -> Unit) {
    var measured by remember { mutableStateOf(IntSize.Zero) }
    val markerColor = MaterialTheme.colorScheme.onSurface
    fun update(offset: Offset) {
        if (measured.width == 0 || measured.height == 0) return
        val side = minOf(measured.width, measured.height).toFloat()
        val origin = Offset((measured.width - side) / 2f, (measured.height - side) / 2f)
        val center = origin + Offset(side / 2f, side / 2f)
        val dx = offset.x - center.x; val dy = offset.y - center.y
        val radius = kotlin.math.sqrt(dx * dx + dy * dy)
        if (radius > side * 0.36f) {
            val angle = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f
            onChange(angle, saturation, value)
        } else {
            val square = side * 0.58f; val left = origin.x + (side - square) / 2f; val top = origin.y + (side - square) / 2f
            onChange(hue, ((offset.x - left) / square).coerceIn(0f, 1f), (1f - (offset.y - top) / square).coerceIn(0f, 1f))
        }
    }
    Canvas(
        Modifier.sizeIn(maxWidth = 240.dp, maxHeight = 240.dp).aspectRatio(1f).onSizeChanged { measured = it }
            .pointerInput(hue, saturation, value) { detectDragGestures(onDragStart = ::update) { change, _ -> update(change.position) } },
    ) {
        val side = minOf(size.width, size.height)
        drawCircle(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)), radius = side * 0.455f, center = center, style = Stroke(width = side * 0.09f))
        val square = side * 0.58f; val topLeft = Offset(center.x - square / 2f, center.y - square / 2f)
        val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor), topLeft.x, topLeft.x + square), topLeft, androidx.compose.ui.geometry.Size(square, square))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), topLeft.y, topLeft.y + square), topLeft, androidx.compose.ui.geometry.Size(square, square))
        drawCircle(markerColor, 7.dp.toPx(), Offset(topLeft.x + square * saturation, topLeft.y + square * (1f - value)), style = Stroke(2.dp.toPx()))
    }
}

@Composable private fun ColorSlider(@StringRes label: Int, value: Float, range: ClosedFloatingPointRange<Float>, suffix: String, onChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row { Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text(suffix, style = MaterialTheme.typography.bodySmall) }
        Slider(value, onChange, Modifier.height(28.dp), valueRange = range)
    }
}

private enum class FavoriteColorSort { USAGE, NEWEST, OLDEST, ASCENDING, DESCENDING }

@Composable
private fun FavoriteColorDialog(colors: List<String>, usage: Map<String, Int>, addedAt: Map<String, Long>, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var sort by rememberSaveable { mutableStateOf(FavoriteColorSort.USAGE) }
    var sortMenu by remember { mutableStateOf(false) }
    val sorted = when (sort) {
        FavoriteColorSort.ASCENDING -> colors.sorted()
        FavoriteColorSort.DESCENDING -> colors.sortedDescending()
        FavoriteColorSort.USAGE -> colors.sortedWith(compareByDescending<String> { usage[it] ?: 0 }.thenBy { it })
        FavoriteColorSort.NEWEST -> colors.sortedWith(compareByDescending<String> { addedAt[it] ?: Long.MIN_VALUE }.thenBy { it })
        FavoriteColorSort.OLDEST -> colors.sortedWith(compareBy<String> { addedAt[it] ?: Long.MAX_VALUE }.thenBy { it })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_favorites)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { sortMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Sort, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.color_sort_label, stringResource(sort.labelResource())), modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        FavoriteColorSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.labelResource())) },
                                trailingIcon = if (option == sort) {{ Icon(Icons.Default.Check, contentDescription = null) }} else null,
                                onClick = { sort = option; sortMenu = false },
                            )
                        }
                    }
                }
                if (sorted.isEmpty()) Text(stringResource(R.string.no_favorite_colors), color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(sorted) { hex ->
                        val parsed = runCatching { android.graphics.Color.parseColor(hex) }.getOrDefault(android.graphics.Color.TRANSPARENT)
                        Surface(onClick = { onSelect(hex) }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(Modifier.size(28.dp), shape = androidx.compose.foundation.shape.CircleShape, color = Color(parsed), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {}
                                Spacer(Modifier.width(10.dp)); Text(hex, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(stringResource(R.string.color_generation_uses, usage[hex] ?: 0), style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        addedAt[hex]?.let { DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it)) } ?: stringResource(R.string.date_unknown),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@StringRes
private fun FavoriteColorSort.labelResource(): Int = when (this) {
    FavoriteColorSort.ASCENDING -> R.string.sort_hex_ascending
    FavoriteColorSort.DESCENDING -> R.string.sort_hex_descending
    FavoriteColorSort.USAGE -> R.string.sort_color_usage
    FavoriteColorSort.NEWEST -> R.string.sort_added_newest
    FavoriteColorSort.OLDEST -> R.string.sort_added_oldest
}

@Composable private fun ColorChipRow(@StringRes label: Int, colors: List<String>, onSelect: (String) -> Unit) {
    if (colors.isEmpty()) return
    Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(colors) { hex ->
            val parsed = runCatching { android.graphics.Color.parseColor(hex) }.getOrDefault(android.graphics.Color.TRANSPARENT)
            Surface(onClick = { onSelect(hex) }, modifier = Modifier.size(34.dp), shape = androidx.compose.foundation.shape.CircleShape, color = Color(parsed), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {}
        }
    }
}

@Composable
private fun PromptScrollIndicator(state: androidx.compose.foundation.lazy.LazyListState, modifier: Modifier = Modifier) {
    val layout = state.layoutInfo
    val total = layout.totalItemsCount
    val visible = layout.visibleItemsInfo.size
    if (total <= visible || total == 0) return
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        if (state.isScrollInProgress) 0.75f else 0.18f,
        label = "prompt-scroll-indicator",
    )
    BoxWithConstraints(modifier.fillMaxHeight().width(7.dp).padding(vertical = 4.dp)) {
        val fraction = (visible.toFloat() / total).coerceIn(0.06f, 1f)
        val progress = (state.firstVisibleItemIndex.toFloat() / (total - visible).coerceAtLeast(1)).coerceIn(0f, 1f)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (maxHeight * (1f - fraction)) * progress)
                .fillMaxWidth()
                .fillMaxHeight(fraction)
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
        )
    }
}

@Composable
private fun CharacterPositioningButton(session: Session, viewModel: MainViewModel) {
    val enabledCharacters = session.characters.filter { it.enabled }
    var open by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.ControlCamera, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(
                    R.string.character_positioning_status,
                    stringResource(if (enabledCharacters.all { it.position != null }) R.string.position_custom else R.string.position_ai_choice),
                ),
            )
        }
        HelpAffordance(R.string.character_positioning, R.string.help_character_positioning_body)
    }
    if (open) CharacterPositioningDialog(session, viewModel) { open = false }
}

@Composable
private fun CharacterPositioningDialog(session: Session, viewModel: MainViewModel, onDismiss: () -> Unit) {
    val characters = session.characters.filter { it.enabled }.sortedBy { it.order }
    var selectedId by rememberSaveable { mutableStateOf(characters.first().id) }
    val custom = characters.all { it.position != null }
    val continuous = CharacterPositioning.supportsContinuousCoordinates(session.generationSettings.modelId)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.character_positioning), style = MaterialTheme.typography.titleLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !custom,
                        onClick = { viewModel.setCharacterPositioningEnabled(false); onDismiss() },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text(stringResource(R.string.position_ai_choice)) }
                    SegmentedButton(
                        selected = custom,
                        onClick = { viewModel.setCharacterPositioningEnabled(true) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text(stringResource(R.string.position_custom)) }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(characters, key = { _, item -> item.id }) { index, character ->
                        FilterChip(
                            selected = selectedId == character.id,
                            onClick = { selectedId = character.id },
                            label = { Text("${index + 1}") },
                        )
                    }
                }
                if (custom) {
                    val gridColor = MaterialTheme.colorScheme.outlineVariant
                    BoxWithConstraints(
                        Modifier.fillMaxWidth().aspectRatio(session.generationSettings.width.toFloat() / session.generationSettings.height)
                            .pointerInput(selectedId, continuous) {
                                detectTapGestures { offset ->
                                    viewModel.setCharacterPosition(
                                        selectedId,
                                        CharacterPositioning.normalize(
                                            session.generationSettings.modelId,
                                            offset.x / size.width,
                                            offset.y / size.height,
                                        ),
                                    )
                                }
                            },
                    ) {
                        Canvas(Modifier.matchParentSize()) {
                            drawRect(gridColor.copy(alpha = 0.18f))
                            if (!continuous) for (i in 1..4) {
                                val x = size.width * i / 5f
                                val y = size.height * i / 5f
                                drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height))
                                drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y))
                            }
                        }
                        characters.forEachIndexed { index, character ->
                            val position = character.position ?: CharacterPosition(0.5f, 0.5f)
                            val latestPosition by rememberUpdatedState(position)
                            val canvasWidthPx = constraints.maxWidth.toFloat()
                            val canvasHeightPx = constraints.maxHeight.toFloat()
                            val dragModifier = if (selectedId == character.id) {
                                Modifier.pointerInput(character.id, continuous, canvasWidthPx, canvasHeightPx) {
                                    var dragOrigin = latestPosition
                                    var dragDelta = Offset.Zero
                                    detectDragGestures(
                                        onDragStart = {
                                            dragOrigin = latestPosition
                                            dragDelta = Offset.Zero
                                        },
                                    ) { change, amount ->
                                        change.consume()
                                        dragDelta += amount
                                        viewModel.setCharacterPosition(
                                            character.id,
                                            CharacterPositioning.moveByPixels(
                                                session.generationSettings.modelId,
                                                dragOrigin,
                                                dragDelta.x,
                                                dragDelta.y,
                                                canvasWidthPx,
                                                canvasHeightPx,
                                            ),
                                        )
                                    }
                                }
                            } else Modifier
                            Surface(
                                modifier = Modifier
                                    .offset(maxWidth * position.normalizedX - 18.dp, maxHeight * position.normalizedY - 18.dp)
                                    .size(36.dp)
                                    .then(dragModifier),
                                shape = MaterialTheme.shapes.extraLarge,
                                color = if (selectedId == character.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                onClick = { selectedId = character.id },
                            ) { Box(contentAlignment = Alignment.Center) { Text("${index + 1}") } }
                        }
                    }
                    Text(
                        stringResource(if (continuous) R.string.position_v5_hint else R.string.position_v45_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.done)) }
            }
        }
    }
}

private data class TagEditorTarget(val owner: PromptOwner, val polarity: PromptPolarity, val blockId: String, val cursor: Int)

internal data class PromptCompareCandidate(
    val id: String,
    val prompt: GeneratedPromptSnapshot,
    val imageReference: String,
    val imageAvailable: Boolean,
    val thumbnailPath: String,
    val dateLabel: String,
    val modelLabel: String?,
    val promptPreview: String,
)

@Composable
fun GenerationSettingsScreen(session: Session?, viewModel: MainViewModel, onBack: () -> Unit, onOpenSettings: () -> Unit, headerTokenSummary: String? = null) {
    val subscriptionStatus by viewModel.subscriptionStatus.collectAsStateWithLifecycle()
    val modelSummary = compactModelName(session?.generationSettings?.modelId)
    val seedSummary = session?.let { stringResource(if (it.generationSettings.seedMode == SeedMode.RANDOM) R.string.seed_status_random else R.string.seed_status_fixed) }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTitleBar(
                R.string.generation_settings,
                menuItems = listOf(AppTitleMenuItem(R.string.nav_settings, Icons.Default.Settings, onClick = onOpenSettings)),
                onBack = onBack,
                bottomContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            seedSummary?.let {
                                Text(
                                    stringResource(R.string.generate_status_summary, modelSummary, it),
                                    Modifier.weight(1f).clickable(onClick = viewModel::toggleSeedMode),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            Text(
                                quotaStatusText(subscriptionStatus),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        headerTokenSummary?.let {
                            Text(
                                it,
                                Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                maxLines = 1,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (session == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { GenerationSettingsCard(session, viewModel, onMetadataImported = onBack) }
                item { Text(stringResource(R.string.generation_settings_swipe_hint), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun GenerationCard(
    state: GenerationUiState,
    record: com.hjhsys.naiblockprompt.data.generation.GenerationRecord?,
    viewModel: MainViewModel,
    onImportInformation: (() -> Unit)? = null,
    onCherryPick: (() -> Unit)? = null,
    onExportInpaintDiagnostics: (() -> Unit)? = null,
    debugPrompt: GeneratedPromptSnapshot? = null,
    comparisonCandidates: List<ImageCompareCandidate> = emptyList(),
    promptComparisonCandidates: List<PromptCompareCandidate> = emptyList(),
) {
    var showActions by remember { mutableStateOf(false) }
    var showOriginal by rememberSaveable { mutableStateOf(false) }
    var showDebugPrompt by rememberSaveable { mutableStateOf(false) }
    var initialImageComparison by remember { mutableStateOf<ImageCompareCandidate?>(null) }
    var initialPromptComparison by remember { mutableStateOf<PromptCompareCandidate?>(null) }
    val originalAvailable = record?.let { com.hjhsys.naiblockprompt.ui.components.rememberOriginalImageAvailable(it.imagePath) } == true
    if (showActions && record != null) com.hjhsys.naiblockprompt.ui.components.ImageActionsDialog(record.imagePath, viewModel) { showActions = false }
    if (showOriginal && record != null) {
        ImageViewer(
            reference = record.imagePath,
            comparisonCandidates = comparisonCandidates,
            initialComparison = initialImageComparison,
            onComparePrompts = { candidate ->
                val comparedPrompt = candidate.prompt ?: return@ImageViewer
                initialPromptComparison = PromptCompareCandidate(
                    id = candidate.id,
                    prompt = comparedPrompt,
                    imageReference = candidate.reference,
                    imageAvailable = true,
                    thumbnailPath = candidate.thumbnailReference,
                    dateLabel = candidate.dateLabel,
                    modelLabel = candidate.modelLabel,
                    promptPreview = candidate.promptPreview,
                )
                initialImageComparison = null
                showOriginal = false
                showDebugPrompt = true
            },
        ) {
            showOriginal = false
            initialImageComparison = null
        }
    }
    if (showDebugPrompt && debugPrompt != null) ProcessedPromptDialog(
        prompt = debugPrompt,
        comparisonCandidates = promptComparisonCandidates,
        initialComparison = initialPromptComparison,
        onCompareImages = { candidate ->
            initialImageComparison = ImageCompareCandidate(
                id = candidate.id,
                reference = candidate.imageReference,
                thumbnailReference = candidate.thumbnailPath,
                dateLabel = candidate.dateLabel,
                modelLabel = candidate.modelLabel,
                promptPreview = candidate.promptPreview,
                prompt = candidate.prompt,
            )
            initialPromptComparison = null
            showDebugPrompt = false
            showOriginal = true
        },
    ) {
        showDebugPrompt = false
        initialPromptComparison = null
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (record != null) {
                AsyncImage(
                    model = imageModel(record.imagePath),
                    contentDescription = stringResource(R.string.generated_image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).clickable {
                        viewModel.selectImageSeed(record.seed)
                        initialImageComparison = null
                        showOriginal = true
                    },
                )
                Text(stringResource(R.string.used_seed, record.seed), style = MaterialTheme.typography.bodySmall)
                ImageCardActions(
                    imageActionsEnabled = originalAvailable,
                    informationEnabled = onImportInformation != null,
                    onImageActions = { viewModel.selectImageSeed(record.seed); showActions = true },
                    onImportInformation = { onImportInformation?.invoke() },
                    seedEnabled = true,
                    onApplySeed = { viewModel.applyHistorySeed(record.seed) },
                    promptSelectionEnabled = onCherryPick != null,
                    onImportPromptSelection = onCherryPick,
                    promptViewEnabled = debugPrompt != null,
                    onViewPrompt = if (debugPrompt != null) {{ initialPromptComparison = null; showDebugPrompt = true }} else null,
                )
                if (!originalAvailable) {
                    Text(stringResource(R.string.original_missing), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (BuildConfig.DEBUG && onExportInpaintDiagnostics != null) {
                    OutlinedButton(
                        onClick = onExportInpaintDiagnostics,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.export_inpaint_diagnostics))
                    }
                }
            }
            if (state is GenerationUiState.Loading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.generating))
                }
            }
            GenerationStatus(state)
            if (state is GenerationUiState.Failed) {
                OutlinedButton(onClick = viewModel::retry, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.retry_same_request))
                }
            }
        }
    }
}

@Composable
fun ResultScreen(viewModel: MainViewModel, onOpenSettings: () -> Unit, onRestored: () -> Unit, headerTokenSummary: String? = null) {
    val state by viewModel.generationState.collectAsStateWithLifecycle()
    val currentResult by viewModel.currentResult.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val subscriptionStatus by viewModel.subscriptionStatus.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val modelSummary = compactModelName(session?.generationSettings?.modelId)
    val seedSummary = session?.let { stringResource(if (it.generationSettings.seedMode == SeedMode.RANDOM) R.string.seed_status_random else R.string.seed_status_fixed) }
    val resultHistory = currentResult?.let { result -> history.firstOrNull { it.entity.imagePath == result.imagePath } }
    val context = LocalContext.current
    var diagnosticExport by remember { mutableStateOf<MainViewModel.TransferFile?>(null) }
    val diagnosticExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val file = diagnosticExport ?: return@rememberLauncherForActivityResult
        uri?.let { context.contentResolver.openOutputStream(it)?.use { output -> output.write(file.bytes) } }
    }
    if (BuildConfig.DEBUG) {
        LaunchedEffect(viewModel.transferExport) {
            viewModel.transferExport.collect { file ->
                if (file.name == "nai_inpaint_diagnostics.zip") {
                    diagnosticExport = file
                    diagnosticExportLauncher.launch(file.name)
                }
            }
        }
    }
    var restore by remember { mutableStateOf<com.hjhsys.naiblockprompt.data.library.HistoryItem?>(null) }
    var cherryPick by remember { mutableStateOf<PromptCherryPickDraft?>(null) }
    restore?.let { item ->
        RestoreDialog(item, dismiss = { restore = null }) { options ->
            viewModel.restoreHistory(item, options)
            restore = null
            onRestored()
        }
    }
    cherryPick?.let { draft ->
        session?.let { current ->
            PromptCherryPickDialog(current, draft, dismiss = { cherryPick = null }) { blocks, selected, destination ->
                viewModel.appendCherryPickedSelection(blocks, selected, destination)
            }
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTitleBar(
                R.string.workspace_result,
                menuItems = listOf(AppTitleMenuItem(R.string.nav_settings, Icons.Default.Settings, onClick = onOpenSettings)),
                subtitle = seedSummary?.let { stringResource(R.string.generate_status_summary, modelSummary, it) },
                onSubtitleClick = if (session == null) null else viewModel::toggleSeedMode,
                trailingOverline = headerTokenSummary,
                trailingSubtitle = quotaStatusText(subscriptionStatus),
            )
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            GenerationCard(
                state,
                currentResult,
                viewModel,
                onImportInformation = resultHistory?.let { item -> { restore = item } },
                onCherryPick = resultHistory?.snapshot?.session?.let { source -> { cherryPick = PromptCherryPick.fromSession(source) } },
                onExportInpaintDiagnostics = if (BuildConfig.DEBUG) {
                    { viewModel.exportLatestInpaintDiagnostics() }
                } else null,
                debugPrompt = resultHistory?.snapshot?.generation,
                comparisonCandidates = history.filter { item ->
                    item.originalExists && item.entity.imagePath != currentResult?.imagePath
                }.map { item ->
                    ImageCompareCandidate(
                        id = item.entity.id,
                        reference = item.entity.imagePath,
                        thumbnailReference = item.entity.thumbnailPath,
                        dateLabel = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.entity.createdAt)),
                        modelLabel = item.entity.model,
                        promptPreview = item.snapshot?.generation?.let { generation ->
                            (listOf(generation.basePositive) + generation.characterPositive)
                                .filter(String::isNotBlank)
                                .joinToString(", ")
                        } ?: item.snapshot?.session?.base?.prompts?.positiveBlocks
                            ?.filter { it.enabled && it.content.isNotBlank() }
                            ?.joinToString(", ") { it.content }
                            .orEmpty(),
                        prompt = item.snapshot?.generation,
                    )
                },
                promptComparisonCandidates = history.mapNotNull { item ->
                    val prompt = item.snapshot?.generation ?: return@mapNotNull null
                    if (item.entity.imagePath == currentResult?.imagePath) return@mapNotNull null
                    PromptCompareCandidate(
                        id = item.entity.id,
                        prompt = prompt,
                        imageReference = item.entity.imagePath,
                        imageAvailable = item.originalExists,
                        thumbnailPath = item.entity.thumbnailPath,
                        dateLabel = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.entity.createdAt)),
                        modelLabel = item.entity.model,
                        promptPreview = (listOf(prompt.basePositive) + prompt.characterPositive).filter(String::isNotBlank).joinToString(", "),
                    )
                },
            )
        }
    }
}

@Composable
internal fun ProcessedPromptDialog(
    prompt: GeneratedPromptSnapshot,
    comparisonCandidates: List<PromptCompareCandidate> = emptyList(),
    title: String? = null,
    initialComparison: PromptCompareCandidate? = null,
    onCompareImages: ((PromptCompareCandidate) -> Unit)? = null,
    dismiss: () -> Unit,
) {
    var showComparePicker by remember(prompt) { mutableStateOf(false) }
    var compareTarget by remember(prompt, initialComparison?.id) { mutableStateOf(initialComparison) }
    if (showComparePicker) {
        PromptComparePicker(
            candidates = comparisonCandidates,
            dismiss = { showComparePicker = false },
            select = { compareTarget = it; showComparePicker = false },
        )
        return
    }
    compareTarget?.let { target ->
        PromptComparisonDialog(
            first = prompt,
            secondCandidate = target,
            onCompareImages = onCompareImages?.takeIf { target.imageAvailable }?.let { switch -> { switch(target) } },
            dismiss = {
                if (initialComparison != null) dismiss()
                else compareTarget = null
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title ?: stringResource(R.string.prompt_view_title)) },
        text = {
            SelectionContainer {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val generationDetails = listOf(
                        stringResource(R.string.model_id) to prompt.modelId,
                        stringResource(R.string.sampler) to prompt.samplerId,
                        stringResource(R.string.resolution) to "${prompt.width} × ${prompt.height}",
                        stringResource(R.string.steps) to prompt.steps.toString(),
                        stringResource(R.string.prompt_guidance) to prompt.scale.toString(),
                        stringResource(R.string.guidance_rescale) to (prompt.guidanceRescale?.toString() ?: "—"),
                        stringResource(R.string.noise_schedule) to prompt.noiseSchedule,
                        stringResource(R.string.seed) to prompt.usedSeed.toString(),
                    ).joinToString("\n") { (label, value) -> "$label: $value" }
                    DebugPromptSection(stringResource(R.string.generation_details), generationDetails)
                    DebugPromptSection(stringResource(R.string.restore_base_positive), prompt.basePositive)
                    DebugPromptSection(stringResource(R.string.restore_base_negative), prompt.baseNegative)
                    prompt.characterPositive.forEachIndexed { index, value ->
                        DebugPromptSection("${stringResource(R.string.character_prompt, index + 1)} · ${stringResource(R.string.positive)}", value)
                    }
                    prompt.characterNegative.forEachIndexed { index, value ->
                        DebugPromptSection("${stringResource(R.string.character_prompt, index + 1)} · ${stringResource(R.string.negative)}", value)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.confirm)) } },
        dismissButton = {
            if (comparisonCandidates.isNotEmpty()) {
                TextButton(onClick = { showComparePicker = true }) {
                    Icon(Icons.Default.CompareArrows, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.compare_prompts))
                }
            }
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PromptComparePicker(
    candidates: List<PromptCompareCandidate>,
    dismiss: () -> Unit,
    select: (PromptCompareCandidate) -> Unit,
) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = { TopAppBar(title = { Text(stringResource(R.string.choose_prompt_to_compare)) }, navigationIcon = { IconButton(onClick = dismiss) { Icon(Icons.Default.Close, stringResource(R.string.close)) } }) },
            ) { padding ->
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(candidates, key = { it.id }) { candidate ->
                        ElevatedCard(Modifier.fillMaxWidth().clickable { select(candidate) }) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = File(candidate.thumbnailPath),
                                    contentDescription = stringResource(R.string.generated_image),
                                    modifier = Modifier.size(92.dp),
                                    contentScale = ContentScale.Crop,
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(candidate.modelLabel ?: candidate.prompt.modelId, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(candidate.dateLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(candidate.promptPreview.ifBlank { stringResource(R.string.empty_prompt) }, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PromptComparisonDialog(
    first: GeneratedPromptSnapshot,
    secondCandidate: PromptCompareCandidate,
    onCompareImages: (() -> Unit)? = null,
    dismiss: () -> Unit,
) {
    var showSecond by remember(first, secondCandidate.id) { mutableStateOf(false) }
    val firstFields = processedPromptFields(first)
    val secondFields = processedPromptFields(secondCandidate.prompt)
    val firstMap = firstFields.toMap()
    val secondMap = secondFields.toMap()
    val labels = (firstFields.map { it.first } + secondFields.map { it.first }).distinct()
    val changedCount = labels.count { firstMap[it].orEmpty() != secondMap[it].orEmpty() }
    val visible = if (showSecond) secondMap else firstMap
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.prompt_comparison_title)) },
                        actions = {
                            onCompareImages?.let { switch ->
                                IconButton(onClick = switch) {
                                    Icon(Icons.Default.Image, stringResource(R.string.switch_to_image_comparison))
                                }
                            }
                            IconButton(onClick = dismiss) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
                        },
                    )
                },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!showSecond, { showSecond = false }, label = { Text("A") }, modifier = Modifier.weight(1f))
                        FilterChip(showSecond, { showSecond = true }, label = { Text("B") }, modifier = Modifier.weight(1f))
                    }
                    Text(stringResource(R.string.prompt_comparison_changed_count, changedCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(labels, key = { it }) { label ->
                            DebugPromptSection(
                                title = label,
                                value = visible[label].orEmpty(),
                                highlighted = firstMap[label].orEmpty() != secondMap[label].orEmpty(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun processedPromptFields(prompt: GeneratedPromptSnapshot): List<Pair<String, String>> {
    val basePositiveLabel = stringResource(R.string.restore_base_positive)
    val baseNegativeLabel = stringResource(R.string.restore_base_negative)
    val positiveLabel = stringResource(R.string.positive)
    val negativeLabel = stringResource(R.string.negative)
    val characterLabels = (0 until maxOf(prompt.characterPositive.size, prompt.characterNegative.size)).map { index ->
        stringResource(R.string.character_prompt, index + 1)
    }
    val enabled = stringResource(R.string.on)
    val disabled = stringResource(R.string.off)
    fun flag(value: Boolean) = if (value) enabled else disabled
    return buildList {
        add(stringResource(R.string.model_id) to prompt.modelId)
        add(stringResource(R.string.sampler) to prompt.samplerId)
        add(stringResource(R.string.resolution) to "${prompt.width} × ${prompt.height}")
        add(stringResource(R.string.steps) to prompt.steps.toString())
        add(stringResource(R.string.prompt_guidance) to prompt.scale.toString())
        add(stringResource(R.string.guidance_rescale) to (prompt.guidanceRescale?.toString() ?: "—"))
        add(stringResource(R.string.noise_schedule) to prompt.noiseSchedule)
        add(stringResource(R.string.seed) to prompt.usedSeed.toString())
        add(stringResource(R.string.generation_setting_sm) to flag(prompt.sm))
        add(stringResource(R.string.generation_setting_sm_dynamic) to flag(prompt.smDynamic))
        add(stringResource(R.string.generation_setting_dynamic_thresholding) to flag(prompt.dynamicThresholding))
        add(stringResource(R.string.generation_setting_character_coordinates) to flag(prompt.useCoordinates))
        add(stringResource(R.string.generation_setting_character_order) to flag(prompt.useOrder))
        add(
            stringResource(R.string.generation_setting_character_positions) to
                prompt.characterPositions.joinToString(" · ") { position ->
                    position?.let { "${it.normalizedX}, ${it.normalizedY}" } ?: "AI"
                }.ifBlank { "—" },
        )
        prompt.imageInput?.let { input ->
            add(stringResource(R.string.generation_setting_image_source) to input.uri)
            add(stringResource(R.string.generation_setting_image_input_mode) to input.mode.name)
            add(stringResource(R.string.image_strength) to input.strength.toString())
            add(stringResource(R.string.image_noise) to input.noise.toString())
            add(stringResource(R.string.information_extracted) to input.informationExtracted.toString())
            add(stringResource(R.string.reference_fidelity) to input.fidelity.toString())
            add(stringResource(R.string.generation_setting_reference_type) to input.preciseType.name)
            add(stringResource(R.string.generation_setting_inpaint_mask) to flag(!input.maskPngBase64.isNullOrBlank()))
        }
        add(basePositiveLabel to prompt.basePositive)
        add(baseNegativeLabel to prompt.baseNegative)
        val characterCount = maxOf(prompt.characterPositive.size, prompt.characterNegative.size)
        repeat(characterCount) { index ->
            add("${characterLabels[index]} · $positiveLabel" to prompt.characterPositive.getOrNull(index).orEmpty())
            add("${characterLabels[index]} · $negativeLabel" to prompt.characterNegative.getOrNull(index).orEmpty())
        }
    }
}

@Composable
private fun DebugPromptSection(title: String, value: String, highlighted: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Surface(color = if (highlighted) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .55f) else MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small) {
            Text(value.ifEmpty { "—" }, Modifier.fillMaxWidth().padding(10.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PromptCherryPickDialog(
    current: Session,
    initialDraft: PromptCherryPickDraft,
    dismiss: () -> Unit,
    apply: (List<PromptBlock>, Set<String>, CherryPickDestination) -> Unit,
) {
    var draft by remember(initialDraft) { mutableStateOf(initialDraft) }
    data class SourceField(val key: String, val label: String, val owner: PromptOwner, val polarity: PromptPolarity, val blocks: List<PromptBlock>, val type: CharacterType)
    val sourceFields = buildList {
        if (draft.base.positiveBlocks.any { it.content.isNotBlank() }) add(SourceField("base-p", stringResource(R.string.base_prompt) + " · " + stringResource(R.string.positive), PromptOwner.Base, PromptPolarity.POSITIVE, draft.base.positiveBlocks, CharacterType.OTHER))
        if (draft.base.negativeBlocks.any { it.content.isNotBlank() }) add(SourceField("base-n", stringResource(R.string.base_prompt) + " · " + stringResource(R.string.negative), PromptOwner.Base, PromptPolarity.NEGATIVE, draft.base.negativeBlocks, CharacterType.OTHER))
        draft.characters.forEachIndexed { index, character ->
            if (character.prompts.positiveBlocks.any { it.content.isNotBlank() }) add(SourceField("${character.id}-p", stringResource(R.string.character_prompt, index + 1) + " · " + stringResource(R.string.positive), PromptOwner.Character(character.id), PromptPolarity.POSITIVE, character.prompts.positiveBlocks, character.type))
            if (character.prompts.negativeBlocks.any { it.content.isNotBlank() }) add(SourceField("${character.id}-n", stringResource(R.string.character_prompt, index + 1) + " · " + stringResource(R.string.negative), PromptOwner.Character(character.id), PromptPolarity.NEGATIVE, character.prompts.negativeBlocks, character.type))
        }
    }
    var sourceIndex by rememberSaveable(initialDraft) { mutableIntStateOf(0) }
    val sourcePage = sourceIndex.coerceIn(0, sourceFields.lastIndex.coerceAtLeast(0))
    val source = sourceFields.getOrNull(sourcePage)
    var selected by remember(source?.key) { mutableStateOf(emptySet<String>()) }
    var destination by remember(source?.key) {
        mutableStateOf(
            source?.let { PromptCherryPick.defaultDestination(current, draft, it.owner, it.polarity) }
                ?: CherryPickDestination.Base(PromptPolarity.POSITIVE),
        )
    }
    var importedNotice by remember { mutableStateOf(false) }
    var cherryPickViewportTop by remember { mutableFloatStateOf(Float.NaN) }
    val cursors = remember(initialDraft) { mutableStateMapOf<String, Int>() }
    val splitName = stringResource(R.string.imported_block_name)
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.cherry_pick_title)) },
                        actions = { TextButton(onClick = dismiss) { Text(stringResource(R.string.finish)) } },
                    )
                },
                bottomBar = {
                    Surface(shadowElevation = 8.dp) {
                        Button(
                            onClick = { source?.let { apply(it.blocks, selected, destination); selected = emptySet(); importedNotice = true } },
                            enabled = source != null && selected.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp).heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.cherry_pick_apply)) }
                    }
                },
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .onGloballyPositioned { cherryPickViewportTop = it.positionInWindow().y },
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item { Text(stringResource(R.string.cherry_pick_source), style = MaterialTheme.typography.titleMedium) }
                    item {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            sourceFields.groupBy { it.owner }.values.forEach { fields ->
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(0.dp),
                                    maxItemsInEachRow = 2,
                                ) {
                                    fields.forEach { field ->
                                        val index = sourceFields.indexOf(field)
                                        CompactPromptSelectorChip(
                                            selected = sourcePage == index,
                                            onClick = { sourceIndex = index },
                                            label = field.label,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (sourceFields.isNotEmpty()) item {
                        Text(
                            "${source?.label.orEmpty()}  ·  ${sourcePage + 1} / ${sourceFields.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    source?.let { field ->
                        item(key = field.key) {
                            val swipeThreshold = with(LocalDensity.current) { 48.dp.toPx() }
                            Column(
                                Modifier.fillMaxWidth().pointerInput(sourceFields.size, sourcePage) {
                                    var totalDrag = 0f
                                    detectHorizontalDragGestures(
                                        onDragStart = { totalDrag = 0f },
                                        onDragCancel = { totalDrag = 0f },
                                        onDragEnd = {
                                            sourceIndex = when {
                                                totalDrag < -swipeThreshold -> (sourcePage + 1).coerceAtMost(sourceFields.lastIndex)
                                                totalDrag > swipeThreshold -> (sourcePage - 1).coerceAtLeast(0)
                                                else -> sourcePage
                                            }
                                            totalDrag = 0f
                                        },
                                    ) { change, amount ->
                                        change.consume()
                                        totalDrag += amount
                                    }
                                },
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val orderedBlocks = field.blocks.sortedBy { it.order }
                                orderedBlocks.forEachIndexed { index, block ->
                                    key(block.id) {
                                        val cursor = cursors[block.id]?.coerceIn(0, block.content.length) ?: block.content.length
                                        var blockTopInWindow by remember(block.id) { mutableFloatStateOf(Float.NaN) }
                                        var blockHeightPx by remember(block.id) { mutableIntStateOf(0) }
                                        var actionsHeightPx by remember(block.id) { mutableIntStateOf(0) }
                                        val stickyMarginPx = with(LocalDensity.current) { 4.dp.toPx() }
                                        val actionsOffset = if (cherryPickViewportTop.isFinite() && blockTopInWindow.isFinite()) {
                                            (cherryPickViewportTop + stickyMarginPx - blockTopInWindow)
                                                .coerceIn(0f, (blockHeightPx - actionsHeightPx).coerceAtLeast(0).toFloat())
                                                .toInt()
                                        } else 0
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .onGloballyPositioned {
                                                    blockTopInWindow = it.positionInWindow().y
                                                    blockHeightPx = it.size.height
                                                },
                                        ) {
                                            Row(Modifier.fillMaxWidth().padding(end = 48.dp), verticalAlignment = Alignment.Top) {
                                                Checkbox(block.id in selected, { checked -> selected = if (checked) selected + block.id else selected - block.id })
                                                OutlinedTextField(
                                                    value = TextFieldValue(block.content, TextRange(cursor)),
                                                    onValueChange = { value ->
                                                        cursors[block.id] = value.selection.end
                                                        if (value.text != block.content) draft = PromptCherryPick.updateBlockContent(draft, field.owner, field.polarity, block.id, value.text)
                                                    },
                                                    label = { Text(block.name) },
                                                    minLines = 3,
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                            Column(
                                                Modifier
                                                    .align(Alignment.TopEnd)
                                                    .offset { IntOffset(0, actionsOffset) }
                                                    .onSizeChanged { actionsHeightPx = it.height },
                                            ) {
                                                IconButton(
                                                    onClick = { draft = PromptCherryPick.splitBlock(draft, field.owner, field.polarity, block.id, cursor, splitName) },
                                                    enabled = PromptProcessor.splitAtTopLevelBoundary(block.content, cursor) != null,
                                                ) { Icon(Icons.Default.CallSplit, stringResource(R.string.cherry_pick_split)) }
                                                IconButton(
                                                    onClick = {
                                                        val previous = orderedBlocks[index - 1]
                                                        draft = PromptCherryPick.mergeBlockWithPrevious(draft, field.owner, field.polarity, block.id)
                                                        if (block.id in selected) selected = selected - block.id + previous.id
                                                    },
                                                    enabled = index > 0,
                                                ) { Icon(Icons.Default.MergeType, stringResource(R.string.merge_with_previous_block)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                    item { Text(stringResource(R.string.cherry_pick_destination), style = MaterialTheme.typography.titleMedium) }
                    item {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), maxItemsInEachRow = 2) {
                                CompactPromptSelectorChip(
                                    destination == CherryPickDestination.Base(PromptPolarity.POSITIVE),
                                    { destination = CherryPickDestination.Base(PromptPolarity.POSITIVE) },
                                    stringResource(R.string.base_prompt) + " · " + stringResource(R.string.positive),
                                )
                                CompactPromptSelectorChip(
                                    destination == CherryPickDestination.Base(PromptPolarity.NEGATIVE),
                                    { destination = CherryPickDestination.Base(PromptPolarity.NEGATIVE) },
                                    stringResource(R.string.base_prompt) + " · " + stringResource(R.string.negative),
                                )
                            }
                            current.characters.sortedBy { it.order }.forEachIndexed { index, character ->
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), maxItemsInEachRow = 2) {
                                    CompactPromptSelectorChip(
                                        destination == CherryPickDestination.Character(character.id, PromptPolarity.POSITIVE),
                                        { destination = CherryPickDestination.Character(character.id, PromptPolarity.POSITIVE) },
                                        stringResource(R.string.character_prompt, index + 1) + " · " + stringResource(R.string.positive_short),
                                    )
                                    CompactPromptSelectorChip(
                                        destination == CherryPickDestination.Character(character.id, PromptPolarity.NEGATIVE),
                                        { destination = CherryPickDestination.Character(character.id, PromptPolarity.NEGATIVE) },
                                        stringResource(R.string.character_prompt, index + 1) + " · " + stringResource(R.string.negative_short),
                                    )
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), maxItemsInEachRow = 2) {
                                CompactPromptSelectorChip(
                                    (destination as? CherryPickDestination.NewCharacter)?.polarity == PromptPolarity.POSITIVE,
                                    { destination = CherryPickDestination.NewCharacter(source?.type ?: CharacterType.OTHER, PromptPolarity.POSITIVE) },
                                    stringResource(R.string.cherry_pick_new_character) + " · " + stringResource(R.string.positive_short),
                                )
                                CompactPromptSelectorChip(
                                    (destination as? CherryPickDestination.NewCharacter)?.polarity == PromptPolarity.NEGATIVE,
                                    { destination = CherryPickDestination.NewCharacter(source?.type ?: CharacterType.OTHER, PromptPolarity.NEGATIVE) },
                                    stringResource(R.string.cherry_pick_new_character) + " · " + stringResource(R.string.negative_short),
                                )
                            }
                        }
                    }
                    if (selected.isEmpty()) item { Text(stringResource(R.string.cherry_pick_nothing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (importedNotice) item { Text(stringResource(R.string.cherry_pick_imported), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
private fun CompactPromptSelectorChip(selected: Boolean, onClick: () -> Unit, label: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
        modifier = Modifier.height(36.dp),
    )
}

@Composable
private fun GenerationStatus(state: GenerationUiState) {
    if (state is GenerationUiState.Failed && state.error is NaiApiFailure.Api) {
        Text(
            stringResource(R.string.generation_error_api_status, state.error.statusCode),
            color = MaterialTheme.colorScheme.error,
        )
        state.error.reason?.let { reason ->
            SelectionContainer { Text(reason, style = MaterialTheme.typography.bodySmall) }
        }
        return
    }
    val message = when (state) {
        GenerationUiState.Idle, is GenerationUiState.Success, GenerationUiState.Loading -> null
        GenerationUiState.MissingToken -> R.string.generation_error_token
        is GenerationUiState.Invalid -> when (state.field) {
            MissingGenerationField.MODEL -> R.string.generation_error_model
            MissingGenerationField.UNSUPPORTED_MODEL -> R.string.generation_error_unsupported_model
            MissingGenerationField.SAMPLER -> R.string.generation_error_sampler
            MissingGenerationField.STEPS -> R.string.generation_error_steps
            MissingGenerationField.SCALE -> R.string.generation_error_scale
            MissingGenerationField.FIXED_SEED -> R.string.generation_error_seed
        }
        is GenerationUiState.Failed -> when (state.error) {
            NaiApiFailure.Authentication -> R.string.generation_error_auth
            NaiApiFailure.PaymentRequired -> R.string.generation_error_payment
            NaiApiFailure.RateLimited -> R.string.generation_error_rate
            is NaiApiFailure.Network -> R.string.generation_error_network
            is NaiApiFailure.Api -> R.string.generation_error_api
            is NaiApiFailure.InvalidResponse -> R.string.generation_error_response
            is NaiApiFailure.Storage -> R.string.generation_error_storage
        }
    }
    if (message != null) Text(stringResource(message), color = MaterialTheme.colorScheme.error)
    if (state is GenerationUiState.Failed && state.error is NaiApiFailure.Network) {
        state.error.reason?.let { reason ->
            SelectionContainer { Text(reason, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun imageModel(reference: String): Any =
    if (reference.startsWith("content://")) Uri.parse(reference) else File(reference)

@Composable
private fun CharacterTypeDialog(
    onDismiss: () -> Unit,
    onSelect: (CharacterType) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_character_type)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.choose_character_type_hint), style = MaterialTheme.typography.bodySmall)
                CharacterType.entries.forEach { type ->
                    OutlinedButton(
                        onClick = { onSelect(type) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(type.labelResource))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CharacterSectionCard(
    index: Int,
    character: CharacterPrompt,
    count: Int,
    alternateColors: Boolean,
    showFormatter: Boolean,
    quickEditWeightStep: String,
    blockScaleEnabled: Boolean,
    defaultBlockScale: String,
    showTextRendering: Boolean,
    tokenRange: TokenRange?,
    viewModel: MainViewModel,
    onOpenTagDictionary: (PromptOwner, PromptPolarity, String, Int) -> Unit,
    onTagEditorState: (PromptOwner, PromptPolarity, String, Boolean, Int) -> Unit,
) {
    val enabledDescription = stringResource(R.string.character_generation_enabled)
    var showMore by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(character.id) { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_character_title)) },
            text = { Text(stringResource(R.string.delete_character_message, index + 1)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.removeCharacter(character.id) }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val characterContainerColor = when {
        !alternateColors -> MaterialTheme.colorScheme.surfaceContainerHigh
        !darkTheme && index % 2 == 0 -> MaterialTheme.colorScheme.surface
        !darkTheme -> MaterialTheme.colorScheme.surfaceContainerHigh
        index % 2 == 0 -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = characterContainerColor)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clickable { viewModel.setCharacterCollapsed(character.id, !character.collapsed) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(stringResource(R.string.character_prompt, index + 1), style = MaterialTheme.typography.titleLarge)
                        tokenRange?.let { SectionTokenEstimate(it) }
                    }
                    Icon(
                        if (character.collapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                        contentDescription = stringResource(if (character.collapsed) R.string.expand else R.string.collapse),
                    )
                }
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
                    Switch(
                        checked = character.enabled,
                        onCheckedChange = { viewModel.setCharacterEnabled(character.id, it) },
                        modifier = Modifier.scale(.78f).semantics { contentDescription = enabledDescription },
                    )
                }
                SmallIconButton(R.string.move_up, Icons.Default.ArrowUpward, index > 0) {
                    viewModel.moveCharacter(character.id, MoveDirection.UP)
                }
                SmallIconButton(R.string.move_down, Icons.Default.ArrowDownward, index < count - 1) {
                    viewModel.moveCharacter(character.id, MoveDirection.DOWN)
                }
                Box {
                    SmallIconButton(R.string.more_actions, Icons.Default.MoreVert, true) { showMore = true }
                    DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.load_set)) },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                            onClick = { showMore = false; viewModel.beginSetLoad(PromptOwner.Character(character.id)) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.save_set)) },
                            leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                            onClick = { showMore = false; viewModel.beginSetSave(PromptOwner.Character(character.id)) },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { showMore = false; confirmDelete = true },
                        )
                    }
                }
            }
            if (!character.collapsed) {
                PromptSectionContent(
                    ownerLabel = stringResource(R.string.character_prompt, index + 1),
                    owner = PromptOwner.Character(character.id),
                    pair = character.prompts,
                    selectedPolarity = character.selectedPolarity,
                    textRendering = character.textRendering,
                    showFormatter = showFormatter,
                    quickEditWeightStep = quickEditWeightStep,
                    blockScaleEnabled = blockScaleEnabled,
                    defaultBlockScale = defaultBlockScale,
                    showTextRendering = showTextRendering,
                    viewModel = viewModel,
                    onOpenTagDictionary = onOpenTagDictionary,
                    onTagEditorState = onTagEditorState,
                    blockContainerColor = characterContainerColor,
                )
            }
        }
    }
}

@Composable
private fun PromptSectionCard(
    title: String,
    owner: PromptOwner,
    pair: PromptPair,
    selectedPolarity: PromptPolarity,
    textRendering: TextRenderingState,
    collapsed: Boolean,
    showFormatter: Boolean,
    quickEditWeightStep: String,
    blockScaleEnabled: Boolean,
    defaultBlockScale: String,
    showTextRendering: Boolean,
    tokenRange: TokenRange?,
    viewModel: MainViewModel,
    onCollapsedChange: (Boolean) -> Unit,
    onOpenTagDictionary: (PromptOwner, PromptPolarity, String, Int) -> Unit,
    onTagEditorState: (PromptOwner, PromptPolarity, String, Boolean, Int) -> Unit,
) {
    val containerColor = if (owner == PromptOwner.Base) {
        if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF240C38)
        else lerp(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.secondaryContainer, 0.58f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = containerColor)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { onCollapsedChange(!collapsed) }) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    tokenRange?.let { SectionTokenEstimate(it) }
                }
                SmallIconButton(
                    if (collapsed) R.string.expand else R.string.collapse,
                    if (collapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                    true,
                ) { onCollapsedChange(!collapsed) }
                SmallIconButton(R.string.load_set, Icons.Default.FolderOpen, true) { viewModel.beginSetLoad(owner) }
                SmallIconButton(R.string.save_set, Icons.Default.Save, true) { viewModel.beginSetSave(owner) }
            }
            val promptBlockColor = if (owner == PromptOwner.Base) {
                lerp(containerColor, Color.Black, if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.16f else 0.055f)
            } else containerColor
            if (!collapsed) PromptSectionContent(title, owner, pair, selectedPolarity, textRendering, showFormatter, quickEditWeightStep, blockScaleEnabled, defaultBlockScale, showTextRendering, viewModel, onOpenTagDictionary, onTagEditorState, promptBlockColor)
        }
    }
}

@Composable
private fun PromptSectionContent(
    ownerLabel: String,
    owner: PromptOwner,
    pair: PromptPair,
    selectedPolarity: PromptPolarity,
    textRendering: TextRenderingState,
    showFormatter: Boolean,
    quickEditWeightStep: String,
    blockScaleEnabled: Boolean,
    defaultBlockScale: String,
    showTextRendering: Boolean,
    viewModel: MainViewModel,
    onOpenTagDictionary: (PromptOwner, PromptPolarity, String, Int) -> Unit,
    onTagEditorState: (PromptOwner, PromptPolarity, String, Boolean, Int) -> Unit,
    blockContainerColor: Color,
) {
    val blockClipboard by viewModel.blockClipboard.collectAsStateWithLifecycle()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PromptPolarity.entries.forEach { polarity ->
            FilterChip(
                selected = selectedPolarity == polarity,
                onClick = { viewModel.selectPolarity(owner, polarity) },
                label = { Text(stringResource(polarity.labelResource)) },
                leadingIcon = if (selectedPolarity == polarity) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
    val blocks = when (selectedPolarity) {
        PromptPolarity.POSITIVE -> pair.positiveBlocks
        PromptPolarity.NEGATIVE -> pair.negativeBlocks
    }.sortedBy { it.order }
    val newBlockName = stringResource(R.string.default_block_name, blocks.size + 1)
    blocks.forEachIndexed { index, block ->
        PromptBlockCard(
            ownerLabel = ownerLabel,
            owner = owner,
            polarity = selectedPolarity,
            block = block,
            previousBlock = blocks.getOrNull(index - 1),
            newBlockName = newBlockName,
            containerColor = blockContainerColor,
            canMoveUp = index > 0,
            canMoveDown = index < blocks.lastIndex,
            showFormatter = showFormatter,
            quickEditWeightStep = quickEditWeightStep,
            blockScaleEnabled = blockScaleEnabled,
            defaultBlockScale = defaultBlockScale,
            onUpdate = { transform -> viewModel.updateBlock(owner, selectedPolarity, block.id, transform) },
            onEnabledChange = { viewModel.setBlockEnabled(owner, selectedPolarity, block.id, it) },
            onCollapsedChange = { viewModel.setBlockCollapsed(owner, selectedPolarity, block.id, it) },
            onLockedChange = { viewModel.setBlockLocked(owner, selectedPolarity, block.id, it) },
            onMove = { viewModel.moveBlock(owner, selectedPolarity, block.id, it) },
            onDelete = { viewModel.removeBlock(owner, selectedPolarity, block.id) },
            onLoad = { viewModel.beginBlockLoad(owner, selectedPolarity, block.id) },
            onSave = { viewModel.beginBlockSave(block) },
            onCopy = { viewModel.copyBlockForPaste(block) },
            onCut = { viewModel.cutBlockForPaste(owner, selectedPolarity, block.id) },
            onFormat = { formatter, selectionStart, selectionEnd ->
                viewModel.formatBlock(owner, selectedPolarity, block.id, formatter, selectionStart, selectionEnd)
            },
            viewModel = viewModel,
            onOpenTagDictionary = { cursor -> onOpenTagDictionary(owner, selectedPolarity, block.id, cursor) },
            onEditorState = { focused, cursor -> onTagEditorState(owner, selectedPolarity, block.id, focused, cursor) },
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { viewModel.addBlock(owner, selectedPolarity, newBlockName) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.add_block))
        }
        OutlinedButton(onClick = { viewModel.pasteBlock(owner, selectedPolarity) }, enabled = blockClipboard != null, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.ContentPaste, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.paste_block))
        }
    }
    if (showTextRendering && selectedPolarity == PromptPolarity.POSITIVE) {
        TextRenderingSlot(
            state = textRendering,
            onEnabledChange = { enabled -> viewModel.updateTextRendering(owner) { it.copy(enabled = enabled) } },
            onDescriptionChange = { description -> viewModel.updateTextRendering(owner) { it.copy(description = description) } },
            onContentChange = { content -> viewModel.updateTextRendering(owner) { it.copy(content = content) } },
        )
    }
    if (owner == PromptOwner.Base) {
        Text(stringResource(R.string.comment_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTokenEstimate(range: TokenRange) {
    Text(
        text = if (range.hasRange) stringResource(R.string.section_estimated_tokens_range, range.minimum, range.maximum)
        else stringResource(R.string.section_estimated_tokens_single, range.minimum),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TextRenderingSlot(
    state: TextRenderingState,
    onEnabledChange: (Boolean) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
) {
    OutlinedCard {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.text_rendering),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                HelpAffordance(R.string.text_rendering, R.string.help_text_rendering_body, Modifier.size(40.dp))
                Text(stringResource(if (state.enabled) R.string.on else R.string.off), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(4.dp))
                Switch(
                    checked = state.enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.heightIn(max = 40.dp),
                )
            }
            if (state.enabled) {
                OutlinedTextField(
                    value = state.description,
                    onValueChange = onDescriptionChange,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.text_rendering_description)) },
                    placeholder = { Text(stringResource(R.string.text_rendering_description_hint)) },
                )
                Text(stringResource(R.string.text_rendering_prefix), style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = state.content,
                    onValueChange = onContentChange,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.text_rendering_hint)) },
                )
                Text(
                    stringResource(R.string.text_rendering_separator_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PromptBlockCard(
    ownerLabel: String,
    owner: PromptOwner,
    polarity: PromptPolarity,
    block: PromptBlock,
    previousBlock: PromptBlock?,
    newBlockName: String,
    containerColor: Color,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    showFormatter: Boolean,
    quickEditWeightStep: String,
    blockScaleEnabled: Boolean,
    defaultBlockScale: String,
    onUpdate: ((PromptBlock) -> PromptBlock) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onCollapsedChange: (Boolean) -> Unit,
    onLockedChange: (Boolean) -> Unit,
    onMove: (MoveDirection) -> Unit,
    onDelete: () -> Unit,
    onLoad: () -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onFormat: (BlockFormatter, Int, Int) -> Unit,
    viewModel: MainViewModel,
    onOpenTagDictionary: (Int) -> Unit,
    onEditorState: (Boolean, Int) -> Unit,
) {
    val validation = remember(block.content) { PromptProcessor.validateWeights(block.content) }
    val randomizerValidation = remember(block.content) { PromptProcessor.validateRandomizers(block.content) }
    var confirmDelete by rememberSaveable(block.id) { mutableStateOf(false) }
    var confirmClear by rememberSaveable(block.id) { mutableStateOf(false) }
    var renameBlock by rememberSaveable(block.id) { mutableStateOf(false) }
    var renameValue by rememberSaveable(block.id) { mutableStateOf(block.name) }
    var showMore by remember { mutableStateOf(false) }
    var editorFocused by remember { mutableStateOf(false) }
    // Quick Edit is transient editor UI. Removing a block card by collapsing its Base or
    // Character parent must dispose it rather than restore a stale popup/drag state.
    var quickEdit by remember(block.id) { mutableStateOf(false) }
    var quickSelections by remember(block.id) { mutableStateOf<List<QuickEditSelection>>(emptyList()) }
    var quickMultiSelect by remember(block.id) { mutableStateOf(false) }
    var quickTagDetails by remember(block.id) { mutableStateOf<TagDictionaryItem?>(null) }
    var quickPasteOffset by remember(block.id) { mutableStateOf<Int?>(null) }
    var quickPasteText by remember(block.id) { mutableStateOf<String?>(null) }
    var quickSelectionTopInWindow by remember(block.id) { mutableStateOf<Float?>(null) }
    var quickSelectionBottomInWindow by remember(block.id) { mutableStateOf<Float?>(null) }
    var showWeightInput by remember { mutableStateOf(false) }
    var weightInput by remember { mutableStateOf("") }
    var moveTargets by remember { mutableStateOf<List<QuickEditDropTarget>>(emptyList()) }
    var moveTargetIndex by remember { mutableStateOf(0) }
    var weightAdjustmentKey by remember(block.id) { mutableStateOf<String?>(null) }
    var showBlockScale by remember { mutableStateOf(false) }
    var customScaleInput by remember(block.id) { mutableStateOf(block.customScale) }
    var scaleModeInput by remember(block.id) { mutableStateOf(block.scaleMode) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val quickEditScope = rememberCoroutineScope()
    val autocomplete by viewModel.autocomplete.collectAsStateWithLifecycle()
    val autocompleteScrollStates = rememberAutocompleteRowScrollStates()
    var autocompleteSourceName by rememberSaveable(block.id) {
        mutableStateOf(AutocompleteDisplaySource.LOCAL.name)
    }
    val selectedAutocompleteSource = runCatching {
        AutocompleteDisplaySource.valueOf(autocompleteSourceName)
    }.getOrDefault(AutocompleteDisplaySource.LOCAL)
    fun selectAutocompleteSource(source: AutocompleteDisplaySource) {
        autocompleteSourceName = source.name
    }
    var rememberedAutocompleteRequest by rememberSaveable(block.id) { mutableStateOf(0L) }
    LaunchedEffect(autocomplete.blockId, autocomplete.requestStartedAtNanos) {
        if (autocomplete.blockId == block.id && autocomplete.requestStartedAtNanos > 0L) {
            if (rememberedAutocompleteRequest > 0L &&
                rememberedAutocompleteRequest != autocomplete.requestStartedAtNanos
            ) {
                autocompleteScrollStates.wildcards.scrollToItem(0)
                autocompleteScrollStates.local.scrollToItem(0)
                autocompleteScrollStates.novelAi.scrollToItem(0)
                autocompleteScrollStates.danbooru.scrollToItem(0)
            }
            rememberedAutocompleteRequest = autocomplete.requestStartedAtNanos
        }
    }
    val undoableBlocks by viewModel.undoablePromptBlocks.collectAsStateWithLifecycle()
    val redoableBlocks by viewModel.redoablePromptBlocks.collectAsStateWithLifecycle()
    val undoKey = remember(owner, polarity, block.id) { PromptUndoKey(owner, polarity, block.id) }
    val toolbarHost = LocalPromptToolbarHost.current
    val quickEditToolbarHost = LocalQuickEditToolbarHost.current
    val quickEditToolbarPlacement = LocalQuickEditToolbarPlacement.current
    val stickyPromptControlsEnabled = LocalStickyPromptControlsEnabled.current
    val promptListScrolling = LocalPromptListScrolling.current
    val promptCursorCenterHost = LocalPromptCursorCenterHost.current
    val tagInsertReturnTarget = LocalTagInsertReturnTarget.current
    val pagerScrolling = LocalGeneratePagerScrolling.current
    val setPagerSwipeLocked = LocalGeneratePagerSwipeLock.current
    val density = LocalDensity.current
    var inlineToolbarTopInWindow by remember(block.id) { mutableFloatStateOf(Float.NaN) }
    var inlineToolbarBottomInWindow by remember(block.id) { mutableFloatStateOf(Float.NaN) }
    var inlineToolbarLeftInWindow by remember(block.id) { mutableFloatStateOf(Float.NaN) }
    var inlineToolbarWidthPx by remember(block.id) { mutableIntStateOf(0) }
    var editorTopInWindow by remember(block.id) { mutableFloatStateOf(Float.NaN) }
    var editorBottomInWindow by remember(block.id) { mutableFloatStateOf(Float.NaN) }
    var editorTextLayout by remember(block.id) { mutableStateOf<TextLayoutResult?>(null) }
    var cursorCenterRequestRevision by remember(block.id) { mutableIntStateOf(0) }
    var editorValue by remember(block.id) {
        mutableStateOf(TextFieldValue(block.content, TextRange(block.content.length)))
    }
    var lastEditorSelection by remember(block.id) { mutableStateOf(editorValue.selection) }
    var observedBlockContent by remember(block.id) { mutableStateOf(block.content) }
    var pendingQuickContent by remember(block.id) { mutableStateOf<String?>(null) }
    val quickEditExitSignal = LocalQuickEditExitSignal.current
    LaunchedEffect(quickEditExitSignal) {
        if (quickEditExitSignal > 0) {
            quickEdit = false
            quickSelections = emptyList()
            quickMultiSelect = false
            quickTagDetails = null
            quickPasteOffset = null
            quickPasteText = null
            moveTargets = emptyList()
            showWeightInput = false
        }
    }
    LaunchedEffect(block.collapsed) {
        if (block.collapsed) {
            quickEdit = false
            quickSelections = emptyList()
            quickMultiSelect = false
            quickTagDetails = null
            quickPasteOffset = null
            quickPasteText = null
            moveTargets = emptyList()
        }
    }
    LaunchedEffect(block.content) {
        if (block.content != observedBlockContent) {
            observedBlockContent = block.content
            // A quick-edit mutation updates editorValue before the Session flow returns it.
            // Preserve the freshly resolved semantic selection for that local echo only.
            if (pendingQuickContent == block.content) {
                pendingQuickContent = null
            } else if (editorValue.text != block.content) {
                editorValue = synchronizePromptEditorValue(editorValue, block.content).also {
                    lastEditorSelection = it.selection
                }
                quickSelections = emptyList()
                quickMultiSelect = false
                quickTagDetails = null
                quickPasteOffset = null
                quickPasteText = null
                moveTargets = emptyList()
            }
        }
    }
    fun finishWeightAdjustment() {
        if (weightAdjustmentKey != null) {
            viewModel.finishWeightAdjustment(owner, polarity, block.id, editorValue.text, editorValue.selection.start, editorValue.selection.end)
            weightAdjustmentKey = null
        }
    }
    fun applyQuick(result: QuickEditResult, weightAdjustment: Boolean = false) {
        val changed = result as? QuickEditResult.Changed ?: return
        if (changed.text == editorValue.text) return
        if (!weightAdjustment) finishWeightAdjustment()
        val previous = editorValue
        val selectionOffsets = changed.preferredSelectionOffsets.ifEmpty {
            changed.preferredSelectionOffset?.let(::listOf).orEmpty()
        }
        val resolvedSelections = selectionOffsets.mapNotNull { PromptQuickEdit.selectionAt(changed.text, it) }.distinctBy { it.range }
        // Publish the text and its replacement semantic selection together. Remember the
        // pending value so the subsequent Session echo cannot briefly clear UI state.
        pendingQuickContent = changed.text
        editorValue = TextFieldValue(changed.text, TextRange(changed.text.length.coerceAtMost(previous.selection.end)))
        quickSelections = resolvedSelections
        quickPasteOffset = null
        quickPasteText = null
        moveTargets = emptyList()
        val coalesceKey = if (weightAdjustment) weightAdjustmentKey ?: "${block.id}:${System.nanoTime()}".also { weightAdjustmentKey = it } else null
        viewModel.updateBlockContent(owner, polarity, block.id, changed.text, previous.selection.start, previous.selection.end, if (weightAdjustment) PromptEditKind.WEIGHT_ADJUSTMENT else PromptEditKind.DISCRETE, coalesceKey)
    }
    fun performUndo() {
        finishWeightAdjustment()
        viewModel.undoBlockContent(owner, polarity, block.id, editorValue.selection.start, editorValue.selection.end)?.let { restored ->
            editorValue = TextFieldValue(restored.content, TextRange(restored.selectionStart, restored.selectionEnd))
            lastEditorSelection = editorValue.selection
        }
    }
    fun performRedo() {
        finishWeightAdjustment()
        viewModel.redoBlockContent(owner, polarity, block.id, editorValue.selection.start, editorValue.selection.end)?.let { restored ->
            editorValue = TextFieldValue(restored.content, TextRange(restored.selectionStart, restored.selectionEnd))
            lastEditorSelection = editorValue.selection
        }
    }
    fun toggleQuickEdit() {
        finishWeightAdjustment()
        quickEdit = !quickEdit
        quickSelections = emptyList()
        quickMultiSelect = false
        quickTagDetails = null
        quickPasteOffset = null
        quickPasteText = null
        moveTargets = emptyList()
        if (quickEdit) {
            keyboardController?.hide()
            focusManager.clearFocus()
            viewModel.clearAutocomplete()
        }
    }
    fun formatMultiline() {
        finishWeightAdjustment()
        onFormat(BlockFormatter.MULTILINE, editorValue.selection.start, editorValue.selection.end)
    }
    fun formatSingleLine() {
        finishWeightAdjustment()
        onFormat(BlockFormatter.SINGLE_LINE, editorValue.selection.start, editorValue.selection.end)
    }
    fun selectAutocompleteSuggestion(suggestion: TagSuggestion) {
        val fragment = autocomplete.fragment ?: return
        if (!isCurrentAutocompleteFragment(editorValue, fragment)) {
            viewModel.clearAutocomplete()
            return
        }
        val replacement = PromptAutocomplete.replace(editorValue.text, fragment, suggestion.tag)
        val previous = editorValue
        editorValue = TextFieldValue(replacement.text, TextRange(replacement.cursor))
        lastEditorSelection = editorValue.selection
        viewModel.updateBlockContent(
            owner,
            polarity,
            block.id,
            replacement.text,
            previous.selection.start,
            previous.selection.end,
            PromptEditKind.DISCRETE,
        )
        viewModel.recordAutocompleteUse(suggestion)
        viewModel.clearAutocomplete()
    }
    val canUndo = !block.locked && undoKey in undoableBlocks
    val canRedo = !block.locked && undoKey in redoableBlocks
    val editorCursorRect = editorTextLayout?.let { layout ->
        layout.getCursorRect(editorValue.selection.end.coerceIn(0, layout.layoutInput.text.length))
    }
    val editorTextTopPadding = with(density) { 16.dp.roundToPx() }
    val cursorTopInAnchor = editorCursorRect?.top?.toInt()?.plus(editorTextTopPadding)
    val cursorBottomInAnchor = editorCursorRect?.bottom?.toInt()?.plus(editorTextTopPadding)
    val cursorTopInWindow = cursorTopInAnchor?.takeIf { editorTopInWindow.isFinite() }
        ?.let { editorTopInWindow + it }
    val cursorBottomInWindow = cursorBottomInAnchor?.takeIf { editorTopInWindow.isFinite() }
        ?.let { editorTopInWindow + it }
    val latestCursorCenterInWindow by rememberUpdatedState(
        if (cursorTopInWindow != null && cursorBottomInWindow != null) {
            (cursorTopInWindow + cursorBottomInWindow) / 2f
        } else null,
    )
    val latestPromptCursorCenterHost by rememberUpdatedState(promptCursorCenterHost)
    LaunchedEffect(tagInsertReturnTarget?.revision) {
        val target = tagInsertReturnTarget ?: return@LaunchedEffect
        if (target.owner == owner && target.polarity == polarity && target.blockId == block.id) {
            val cursor = target.cursor.coerceIn(0, editorValue.text.length)
            editorValue = editorValue.copy(selection = TextRange(cursor))
            lastEditorSelection = editorValue.selection
            cursorCenterRequestRevision++
        }
    }
    LaunchedEffect(cursorCenterRequestRevision) {
        if (cursorCenterRequestRevision <= 0) return@LaunchedEffect
        delay(320)
        latestCursorCenterInWindow?.let { latestPromptCursorCenterHost?.requestCenter(it) }
    }
    val stickyToolbarLabel = "$ownerLabel · ${block.name}"
    val editorVisibleInViewport = toolbarHost?.let { host ->
        shouldShowStickyAutocomplete(
            editorTopInWindow = editorTopInWindow,
            editorBottomInWindow = editorBottomInWindow,
            viewportTopInWindow = host.viewportTopInWindow,
            viewportBottomInWindow = host.viewportBottomInWindow,
        )
    } == true
    val hasAutocompleteContent = autocomplete.loading ||
        autocomplete.local.isNotEmpty() || autocomplete.novelAi.isNotEmpty() ||
        autocomplete.danbooru.isNotEmpty() || autocomplete.wildcards.isNotEmpty() ||
        autocomplete.novelAiFailed || autocomplete.danbooruFailed
    val estimatedAutocompleteHeightPx = with(density) {
        44.dp.toPx()
    }
    val inlineAutocompleteFits = toolbarHost?.let { host ->
        shouldShowInlineAutocomplete(
            inlineTopInWindow = inlineToolbarBottomInWindow,
            estimatedHeightPx = estimatedAutocompleteHeightPx,
            viewportTopInWindow = host.viewportTopInWindow,
            viewportBottomInWindow = host.viewportBottomInWindow,
            marginPx = with(density) { 4.dp.toPx() },
        )
    } == true
    val showInlineAutocomplete = editorFocused && !quickEdit && !pagerScrolling && !promptListScrolling &&
        editorVisibleInViewport && autocomplete.blockId == block.id && hasAutocompleteContent && inlineAutocompleteFits &&
        inlineToolbarLeftInWindow.isFinite() && inlineToolbarWidthPx > 0
    val stickyAutocomplete = remember(
        autocomplete,
        block.id,
        quickEdit,
        editorVisibleInViewport,
        autocompleteScrollStates,
        selectedAutocompleteSource,
    ) {
        if (!quickEdit && autocomplete.blockId == block.id && editorVisibleInViewport) {
            StickyAutocompleteState(
                local = autocomplete.local,
                novelAi = autocomplete.novelAi,
                danbooru = autocomplete.danbooru,
                wildcards = autocomplete.wildcards,
                loading = autocomplete.loading,
                novelAiFailed = autocomplete.novelAiFailed,
                danbooruFailed = autocomplete.danbooruFailed,
                requestStartedAtNanos = autocomplete.requestStartedAtNanos,
                rowScrollStates = autocompleteScrollStates,
                selectedSource = selectedAutocompleteSource,
                onSourceChange = ::selectAutocompleteSource,
                onSelect = ::selectAutocompleteSuggestion,
            )
        } else null
    }
    val stickyThresholdPx = with(density) { 4.dp.toPx() }
    val showStickyToolbar = stickyPromptControlsEnabled && !pagerScrolling && !promptListScrolling && editorVisibleInViewport && toolbarHost?.let { host ->
        shouldShowStickyPromptToolbar(
            active = editorFocused || quickEdit,
            inlineToolbarTopInWindow = inlineToolbarTopInWindow,
            viewportBottomInWindow = host.viewportBottomInWindow,
            thresholdPx = stickyThresholdPx,
        )
    } == true
    val showFloatingAutocomplete = editorFocused && !quickEdit && !pagerScrolling && !promptListScrolling &&
        editorVisibleInViewport && autocomplete.blockId == block.id && hasAutocompleteContent && !showInlineAutocomplete
    val showPromptOverlay = showStickyToolbar || showFloatingAutocomplete
    val stickyToolbarState = remember(
        block.id,
        stickyToolbarLabel,
        showStickyToolbar,
        quickEdit,
        canUndo,
        canRedo,
        showFormatter,
        stickyAutocomplete,
        showFloatingAutocomplete,
        cursorTopInWindow,
        cursorBottomInWindow,
    ) {
        StickyPromptToolbarState(
            blockId = block.id,
            label = stickyToolbarLabel,
            showToolbar = showStickyToolbar,
            quickEdit = quickEdit,
            canUndo = canUndo,
            canRedo = canRedo,
            showFormatter = showFormatter,
            autocomplete = stickyAutocomplete.takeIf { showFloatingAutocomplete },
            cursorTopInWindow = cursorTopInWindow,
            cursorBottomInWindow = cursorBottomInWindow,
            onUndo = ::performUndo,
            onRedo = ::performRedo,
            onToggleQuickEdit = ::toggleQuickEdit,
            onFormatMultiline = ::formatMultiline,
            onFormatSingleLine = ::formatSingleLine,
        )
    }
    LaunchedEffect(toolbarHost, showPromptOverlay, stickyToolbarState) {
        toolbarHost?.update(block.id, stickyToolbarState.takeIf { showPromptOverlay })
    }
    DisposableEffect(toolbarHost, block.id) {
        onDispose { toolbarHost?.update(block.id, null) }
    }
    BackHandler(enabled = editorFocused) {
        keyboardController?.hide()
        focusManager.clearFocus()
    }
    quickTagDetails?.let { item ->
        QuickEditTagDetailsDialog(item, viewModel) { quickTagDetails = null }
    }
    if (showBlockScale) AlertDialog(
        onDismissRequest = { showBlockScale = false },
        title = { Text(stringResource(R.string.block_scale)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().clickable { scaleModeInput = BlockScaleMode.DEFAULT }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(scaleModeInput == BlockScaleMode.DEFAULT, { scaleModeInput = BlockScaleMode.DEFAULT })
                    Text(stringResource(R.string.block_scale_default_value, defaultBlockScale))
                }
                Row(Modifier.fillMaxWidth().clickable { scaleModeInput = BlockScaleMode.CUSTOM }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(scaleModeInput == BlockScaleMode.CUSTOM, { scaleModeInput = BlockScaleMode.CUSTOM })
                    Text(stringResource(R.string.block_scale_custom))
                }
                OutlinedTextField(customScaleInput, { customScaleInput = it }, label = { Text(stringResource(R.string.block_scale_custom)) }, singleLine = true)
            }
        },
        confirmButton = { TextButton(
            enabled = customScaleInput.toBigDecimalOrNull() != null,
            onClick = { onUpdate { it.copy(scaleMode = scaleModeInput, customScale = customScaleInput) }; showBlockScale = false },
        ) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = { showBlockScale = false }) { Text(stringResource(R.string.cancel)) } },
    )
    if (showWeightInput) {
        AlertDialog(
            onDismissRequest = { showWeightInput = false },
            title = { Text(stringResource(R.string.quick_edit_set_weight)) },
            text = { OutlinedTextField(weightInput, { weightInput = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)) },
            confirmButton = { TextButton(onClick = {
                quickSelections.singleOrNull()?.let { selection -> weightInput.toBigDecimalOrNull()?.let { applyQuick(PromptQuickEdit.setWeight(editorValue.text, selection, it)) } }
                showWeightInput = false
            }, enabled = weightInput.toBigDecimalOrNull()?.compareTo(java.math.BigDecimal.ZERO)?.let { it != 0 } == true) { Text(stringResource(R.string.confirm)) } },
            dismissButton = { TextButton(onClick = { showWeightInput = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_block_title)) },
            text = { Text(stringResource(R.string.delete_block_message, block.name)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_block_content_title)) },
            text = { Text(stringResource(R.string.clear_block_content_message, block.name)) },
            confirmButton = { TextButton(onClick = {
                confirmClear = false
                quickEdit = false
                quickSelections = emptyList()
                quickPasteOffset = null
                quickPasteText = null
                moveTargets = emptyList()
                viewModel.clearBlockContent(owner, polarity, block.id, editorValue.selection.start, editorValue.selection.end)
            }) { Text(stringResource(R.string.clear_block_content), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (renameBlock) {
        AlertDialog(
            onDismissRequest = { renameBlock = false },
            title = { Text(stringResource(R.string.rename_block)) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text(stringResource(R.string.block_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameValue.isNotBlank(),
                    onClick = { onUpdate { it.copy(name = renameValue.trim()) }; renameBlock = false },
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { renameBlock = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = .72f),
        ),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
                        Checkbox(checked = block.enabled, onCheckedChange = onEnabledChange, modifier = Modifier.scale(.82f))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(block.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (blockScaleEnabled) {
                            val customScale = block.customScale.toBigDecimalOrNull()
                            val baseScale = defaultBlockScale.toBigDecimalOrNull()
                            val customMatchesBase = customScale != null && baseScale != null && customScale.compareTo(baseScale) == 0
                            val scaleLabel = when {
                                block.scaleMode == BlockScaleMode.DEFAULT -> stringResource(R.string.block_scale_uses_base, defaultBlockScale)
                                customMatchesBase -> stringResource(R.string.block_scale_custom_matches_base, block.customScale)
                                else -> stringResource(R.string.block_scale_uses_custom, block.customScale)
                            }
                            Text(
                                scaleLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (block.scaleMode == BlockScaleMode.DEFAULT) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    SmallIconButton(
                        if (block.collapsed) R.string.expand else R.string.collapse,
                        if (block.collapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                        true,
                    ) { onCollapsedChange(!block.collapsed) }
                }
                SmallIconButton(R.string.move_up, Icons.Default.ArrowUpward, canMoveUp && !block.locked) { onMove(MoveDirection.UP) }
                SmallIconButton(R.string.move_down, Icons.Default.ArrowDownward, canMoveDown && !block.locked) { onMove(MoveDirection.DOWN) }
                Box {
                    SmallIconButton(R.string.more_actions, Icons.Default.MoreVert, true) { showMore = true }
                    DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename_block)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            enabled = !block.locked,
                            onClick = { showMore = false; renameValue = block.name; renameBlock = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.load_saved_block)) },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                            enabled = !block.locked,
                            onClick = { showMore = false; onLoad() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.save_block)) },
                            leadingIcon = { Icon(Icons.Default.BookmarkAdd, contentDescription = null) },
                            onClick = { showMore = false; onSave() },
                        )
                        if (blockScaleEnabled) DropdownMenuItem(
                            text = { Text(if (block.scaleMode == BlockScaleMode.DEFAULT) stringResource(R.string.block_scale_default_value, defaultBlockScale) else stringResource(R.string.block_scale_custom_value, block.customScale)) },
                            leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                            onClick = {
                                showMore = false
                                customScaleInput = block.customScale
                                scaleModeInput = block.scaleMode
                                showBlockScale = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_block)) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = { showMore = false; clipboard.setText(AnnotatedString(block.content)); onCopy() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.cut_block)) },
                            leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                            enabled = !block.locked,
                            onClick = { showMore = false; clipboard.setText(AnnotatedString(block.content)); onCut() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.split_block_at_cursor)) },
                            leadingIcon = { Icon(Icons.Default.CallSplit, contentDescription = null) },
                            enabled = !block.locked && !quickEdit,
                            onClick = {
                                showMore = false
                                val selection = lastEditorSelection
                                when {
                                    !selection.collapsed -> android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.split_block_selection_error),
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                    PromptProcessor.splitAtTopLevelBoundary(editorValue.text, selection.end) == null ->
                                        android.widget.Toast.makeText(
                                            context,
                                            context.getString(R.string.split_block_boundary_error),
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    else -> viewModel.splitBlockAtCursor(
                                        owner,
                                        polarity,
                                        block.id,
                                        selection.end,
                                        newBlockName,
                                    )
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.merge_with_previous_block)) },
                            leadingIcon = { Icon(Icons.Default.MergeType, contentDescription = null) },
                            enabled = previousBlock != null && !block.locked && previousBlock.locked.not(),
                            onClick = {
                                showMore = false
                                if (previousBlock?.enabled != block.enabled) {
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.merge_block_enabled_mismatch),
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    viewModel.mergeBlockWithPrevious(owner, polarity, block.id)
                                }
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.clear_block_content)) },
                            leadingIcon = { Icon(Icons.Default.Backspace, contentDescription = null) },
                            enabled = !block.locked && block.content.isNotEmpty(),
                            onClick = { showMore = false; confirmClear = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            enabled = !block.locked,
                            onClick = { showMore = false; confirmDelete = true },
                        )
                    }
                }
            }
            if (block.collapsed) {
                Text(
                    block.content.replace(Regex("\\s+"), " ").trim().ifBlank { stringResource(R.string.empty_prompt) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            } else {
                val highColor = MaterialTheme.colorScheme.error
                val lowColor = MaterialTheme.colorScheme.primary
                val neutralWeightColor = MaterialTheme.colorScheme.onSurfaceVariant
                val commentColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
                    Color(0xFF81C784)
                } else {
                    Color(0xFF2E7D32)
                }
                val randomizerColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
                    Color(0xFFFFD54F)
                } else {
                    Color(0xFF9A6700)
                }
                val activeSelection = quickSelections.lastOrNull()
                val quickStep = quickEditWeightStep.toBigDecimalOrNull() ?: java.math.BigDecimal("0.1")
                val canQuickWeight = !block.locked && PromptQuickEdit.canAdjustWeight(editorValue.text, quickSelections)
                val canSetQuickWeight = !block.locked && PromptQuickEdit.canAdjustWeight(quickSelections)
                val selectedCanonicalTag = PromptQuickEdit.selectedCanonicalTag(editorValue.text, quickSelections)
                val quickActionState = activeSelection?.let {
                    QuickEditActionState(
                        blockId = block.id,
                        selectionTopInWindow = quickSelectionTopInWindow,
                        selectionBottomInWindow = quickSelectionBottomInWindow,
                        canAdjustWeight = canQuickWeight,
                        canSetWeight = canSetQuickWeight,
                        canMutate = !block.locked,
                        moveActive = moveTargets.isNotEmpty(),
                        multiSelect = quickMultiSelect,
                        canShowTagInfo = selectedCanonicalTag != null,
                        onDecrease = { applyQuick(PromptQuickEdit.adjustWeight(editorValue.text, quickSelections, quickStep.negate()), weightAdjustment = true) },
                        onIncrease = { applyQuick(PromptQuickEdit.adjustWeight(editorValue.text, quickSelections, quickStep), weightAdjustment = true) },
                        onToggleWeightSign = { applyQuick(PromptQuickEdit.toggleWeightSign(editorValue.text, quickSelections)) },
                        onFinishWeightAdjustment = ::finishWeightAdjustment,
                        onWeightPressChanged = setPagerSwipeLocked,
                        onToggleMultiSelect = {
                            quickMultiSelect = !quickMultiSelect
                            if (!quickMultiSelect) quickSelections = quickSelections.takeLast(1)
                        },
                        onShowTagInfo = {
                            selectedCanonicalTag?.let { canonical ->
                                quickEditScope.launch {
                                    val item = viewModel.tagDetails(canonical)
                                    if (item != null) quickTagDetails = item
                                    else android.widget.Toast.makeText(context, R.string.quick_edit_tag_not_found, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onToggleComment = { applyQuick(PromptQuickEdit.toggleComments(editorValue.text, quickSelections)) },
                        onDelete = { applyQuick(PromptQuickEdit.deleteMany(editorValue.text, quickSelections)) },
                        onToggleMove = {
                            if (moveTargets.isEmpty()) {
                                moveTargets = PromptQuickEdit.dropTargets(editorValue.text, quickSelections)
                                moveTargetIndex = 0
                            } else moveTargets = emptyList()
                        },
                        onCopy = { clipboard.setText(AnnotatedString(PromptQuickEdit.selectedText(editorValue.text, quickSelections))) },
                        onCut = {
                            clipboard.setText(AnnotatedString(PromptQuickEdit.selectedText(editorValue.text, quickSelections)))
                            applyQuick(PromptQuickEdit.deleteMany(editorValue.text, quickSelections))
                        },
                        onSetWeight = {
                            weightInput = activeSelection.weightValue?.let(PromptQuickEdit::format) ?: "1.0"
                            showWeightInput = true
                        },
                        onLineBreakBefore = { applyQuick(PromptQuickEdit.insertLineBreak(editorValue.text, quickSelections, before = true)) },
                        onLineBreakAfter = { applyQuick(PromptQuickEdit.insertLineBreak(editorValue.text, quickSelections, before = false)) },
                    )
                }
                SideEffect {
                    quickEditToolbarHost?.update(
                        block.id,
                        quickActionState.takeIf { quickEdit && quickEditToolbarPlacement == QuickEditToolbarPlacement.BOTTOM },
                    )
                }
                DisposableEffect(quickEditToolbarHost, block.id) {
                    onDispose { quickEditToolbarHost?.update(block.id, null) }
                }
                if (quickEdit) {
                    QuickEditPromptSurface(
                        text = editorValue.text,
                        selections = quickSelections,
                        pasteOffset = quickPasteOffset,
                        insertionOffset = moveTargets.getOrNull(moveTargetIndex)?.offset,
                        highColor = highColor,
                        lowColor = lowColor,
                        neutralWeightColor = neutralWeightColor,
                        commentColor = commentColor,
                        randomizerColor = randomizerColor,
                        onTapOffset = { offset, insertionIntent ->
                            finishWeightAdjustment()
                            val candidate = if (insertionIntent) null else PromptQuickEdit.selectionAt(editorValue.text, offset)
                            if (candidate != null) {
                                quickSelections = quickEditSelectionsAfterTap(quickSelections, candidate, quickMultiSelect)
                                quickPasteOffset = null
                                quickPasteText = null
                            } else {
                                quickSelections = emptyList()
                                val insertionOffset = offset.coerceIn(0, editorValue.text.length)
                                if (quickPasteOffset == insertionOffset) {
                                    quickPasteOffset = null
                                    quickPasteText = null
                                } else {
                                    quickPasteOffset = insertionOffset
                                    quickPasteText = clipboard.getText()?.text?.takeIf { it.isNotBlank() }
                                }
                            }
                            moveTargets = emptyList()
                            viewModel.clearAutocomplete()
                            viewModel.cancelTagInsert()
                        },
                        moveTargets = moveTargets,
                        activeMoveTarget = moveTargetIndex,
                        onMoveTarget = { moveTargetIndex = it },
                        onDrop = { target -> applyQuick(PromptQuickEdit.moveMany(editorValue.text, quickSelections, target)) },
                        dragPreview = PromptQuickEdit.selectedText(editorValue.text, quickSelections),
                        showSelectionToolbar = quickEditToolbarPlacement == QuickEditToolbarPlacement.BELOW_SELECTION,
                        onSelectionBoundsInWindow = { top, bottom ->
                            quickSelectionTopInWindow = top
                            quickSelectionBottomInWindow = bottom
                        },
                        pasteToolbar = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { quickPasteOffset?.let(onOpenTagDictionary) },
                                    enabled = !block.locked && quickPasteOffset != null,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(stringResource(R.string.quick_edit_choose_tags), style = MaterialTheme.typography.labelMedium)
                                }
                                TextButton(
                                    onClick = {
                                        val offset = quickPasteOffset
                                        val value = quickPasteText
                                        if (offset != null && value != null) applyQuick(PromptQuickEdit.pasteAt(editorValue.text, offset, value))
                                    },
                                    enabled = !block.locked && !quickPasteText.isNullOrBlank(),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(stringResource(R.string.quick_edit_paste), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        },
                        toolbar = {
                            quickActionState?.let { QuickEditActionToolbar(it) }
                        },
                    )
                } else Box {
                    OutlinedTextField(
                        value = editorValue,
                        onValueChange = { value ->
                            val previous = editorValue
                            editorValue = value
                            lastEditorSelection = value.selection
                            if (previous.text == value.text && previous.selection != value.selection) {
                                cursorCenterRequestRevision++
                            }
                            if (editorFocused) onEditorState(true, value.selection.end)
                            viewModel.updateBlockContent(
                                owner,
                                polarity,
                                block.id,
                                value.text,
                                previous.selection.start,
                                previous.selection.end,
                                PromptEditKind.TYPING,
                            )
                            viewModel.requestAutocomplete(block.id, autocompleteFragmentForEditor(value))
                        },
                        enabled = !block.locked,
                        label = { Text(stringResource(R.string.prompt_content)) },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                editorTopInWindow = coordinates.positionInWindow().y
                                editorBottomInWindow = editorTopInWindow + coordinates.size.height
                            }
                            .onFocusChanged {
                                val gainedFocus = it.isFocused && !editorFocused
                                editorFocused = it.isFocused
                                onEditorState(it.isFocused, editorValue.selection.end)
                                if (gainedFocus) cursorCenterRequestRevision++
                            },
                        visualTransformation = remember(highColor, lowColor, neutralWeightColor, commentColor, randomizerColor) {
                            PromptVisualTransformation(highColor, lowColor, neutralWeightColor, commentColor, randomizerColor)
                        },
                    )
                    Text(
                        text = editorValue.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Transparent,
                        onTextLayout = { editorTextLayout = it },
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .clearAndSetSemantics { },
                    )
                }
                if (validation.hasUnclosedWeight) {
                    Text(
                        stringResource(R.string.weight_warning, validation.delimiterCount),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (randomizerValidation.hasUnclosedRandomizer) {
                    Text(
                        stringResource(R.string.randomizer_warning, randomizerValidation.delimiterCount),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
                        val position = coordinates.positionInWindow()
                        inlineToolbarTopInWindow = position.y
                        inlineToolbarBottomInWindow = inlineToolbarTopInWindow + coordinates.size.height
                        inlineToolbarLeftInWindow = position.x
                        inlineToolbarWidthPx = coordinates.size.width
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    SmallIconButton(
                        if (block.locked) R.string.unlock_block else R.string.lock_block,
                        if (block.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                        true,
                    ) { onLockedChange(!block.locked) }
                    SmallIconButton(
                        R.string.undo_prompt_edit,
                        Icons.Default.Undo,
                        canUndo,
                    ) { performUndo() }
                    SmallIconButton(
                        R.string.redo_prompt_edit,
                        Icons.Default.Redo,
                        canRedo,
                    ) { performRedo() }
                    FilterChip(
                        selected = quickEdit,
                        onClick = ::toggleQuickEdit,
                        label = { Text(stringResource(R.string.quick_edit), style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.TouchApp, null, Modifier.size(16.dp)) },
                    )
                    Spacer(Modifier.weight(1f))
                    if (showFormatter) {
                        TextButton(
                            onClick = ::formatMultiline,
                            enabled = !block.locked,
                            contentPadding = PaddingValues(horizontal = 7.dp),
                        ) { Text(stringResource(R.string.format_multiline), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                        TextButton(
                            onClick = ::formatSingleLine,
                            enabled = !block.locked,
                            contentPadding = PaddingValues(horizontal = 7.dp),
                        ) { Text(stringResource(R.string.format_single_line), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                    }
                }
                if (showInlineAutocomplete) {
                    val popupMargin = with(density) { 4.dp.roundToPx() }
                    Popup(
                        popupPositionProvider = remember(
                            inlineToolbarLeftInWindow,
                            inlineToolbarBottomInWindow,
                            popupMargin,
                        ) {
                            FixedWindowPopupPositionProvider(
                                leftInWindow = inlineToolbarLeftInWindow.toInt(),
                                topInWindow = inlineToolbarBottomInWindow.toInt(),
                                margin = popupMargin,
                            )
                        },
                        properties = PopupProperties(focusable = false, clippingEnabled = true),
                    ) {
                        Surface(
                            modifier = Modifier.width(with(density) { inlineToolbarWidthPx.toDp() }).heightIn(max = 260.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 2.dp,
                            shadowElevation = 3.dp,
                        ) {
                            Column(
                                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                            ) {
                                AutocompleteSuggestions(
                                    local = autocomplete.local,
                                    novelAi = autocomplete.novelAi,
                                    danbooru = autocomplete.danbooru,
                                    wildcards = autocomplete.wildcards,
                                    loading = autocomplete.loading,
                                    novelAiFailed = autocomplete.novelAiFailed,
                                    danbooruFailed = autocomplete.danbooruFailed,
                                    requestStartedAtNanos = autocomplete.requestStartedAtNanos,
                                    rowScrollStates = autocompleteScrollStates,
                                    selectedSource = selectedAutocompleteSource,
                                    onSourceChange = ::selectAutocompleteSource,
                                    onSelect = ::selectAutocompleteSuggestion,
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
private fun QuickEditTagDetailsDialog(
    item: TagDictionaryItem,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    val userCategories by viewModel.userTagCategories.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    TagEditDialog(
        item = item,
        onDismiss = onDismiss,
        onSave = { korean, aliases, category ->
            viewModel.saveTagDetails(item, korean, aliases, category)
            onDismiss()
        },
        onReset = {
            viewModel.resetTagDetails(item)
            onDismiss()
        },
        onDelete = {
            viewModel.deleteUserOnlyTag(item)
            onDismiss()
        },
        categories = (AppTagCategory.entries.map { it.value } + userCategories).distinct(),
        generatedImages = history.map { it.entity.thumbnailPath to it.entity.imagePath },
        onChooseThumbnail = { viewModel.setTagThumbnail(item, it) },
        onUseGeneratedThumbnail = { viewModel.setTagThumbnailFromFile(item, it) },
        onRemoveThumbnail = { viewModel.removeTagThumbnail(item) },
        onExclude = {
            viewModel.excludeTag(item)
            onDismiss()
        },
    )
}

@Composable
private fun QuickEditPromptSurface(
    text: String,
    selections: List<QuickEditSelection>,
    pasteOffset: Int?,
    insertionOffset: Int?,
    highColor: Color,
    lowColor: Color,
    neutralWeightColor: Color,
    commentColor: Color,
    randomizerColor: Color,
    onTapOffset: (Int, Boolean) -> Unit,
    moveTargets: List<QuickEditDropTarget>,
    activeMoveTarget: Int,
    onMoveTarget: (Int) -> Unit,
    onDrop: (QuickEditDropTarget) -> Unit,
    dragPreview: String,
    showSelectionToolbar: Boolean,
    onSelectionBoundsInWindow: (Float?, Float?) -> Unit,
    pasteToolbar: @Composable () -> Unit,
    toolbar: @Composable () -> Unit,
) {
    // Keep the previous layout until Text publishes the replacement one. Resetting this
    // on every text mutation makes the anchored popup disappear for one composition.
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var textContentTopInWindow by remember { mutableFloatStateOf(Float.NaN) }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    var validDrag by remember { mutableStateOf(false) }
    var dragTargetIndex by remember(moveTargets) { mutableStateOf(if (moveTargets.isEmpty()) 0 else activeMoveTarget.coerceIn(moveTargets.indices)) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val base = remember(text, highColor, lowColor, neutralWeightColor, commentColor, randomizerColor) {
        PromptVisualTransformation(highColor, lowColor, neutralWeightColor, commentColor, randomizerColor).filter(AnnotatedString(text)).text
    }
    val selectedBackground = if (moveTargets.isNotEmpty()) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = .78f)
    } else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .58f)
    val insertionColor = MaterialTheme.colorScheme.primary
    val annotated = remember(base, selections, selectedBackground) {
        buildAnnotatedString {
            append(base)
            selections.forEach { selection ->
                val range = selection.range
                if (range.start in 0..length && range.endExclusive in 0..length) {
                    addStyle(SpanStyle(background = selectedBackground, fontWeight = FontWeight.SemiBold), range.start, range.endExclusive)
                }
            }
        }
    }
    LaunchedEffect(layout, selections, textContentTopInWindow) {
        val result = layout
        val layoutLength = result?.layoutInput?.text?.length ?: 0
        val validSelections = selections.filter {
            it.range.start in 0 until layoutLength &&
                it.range.endExclusive in 1..layoutLength &&
                it.range.start < it.range.endExclusive
        }
        if (result == null || !textContentTopInWindow.isFinite() || validSelections.isEmpty()) {
            onSelectionBoundsInWindow(null, null)
        } else {
            val top = validSelections.minOf { result.getBoundingBox(it.range.start).top }
            val bottom = validSelections.maxOf { result.getBoundingBox(it.range.endExclusive - 1).bottom }
            onSelectionBoundsInWindow(textContentTopInWindow + top, textContentTopInWindow + bottom)
        }
    }
    DisposableEffect(Unit) {
        onDispose { onSelectionBoundsInWindow(null, null) }
    }
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Text(
                text = annotated,
                minLines = 3,
                style = MaterialTheme.typography.bodyLarge,
                onTextLayout = { layout = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        textContentTopInWindow = coordinates.positionInWindow().y + with(density) { 14.dp.toPx() }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .drawBehind {
                        val result = layout
                        val layoutTextLength = result?.layoutInput?.text?.length ?: 0
                        val offset = (if (moveTargets.isNotEmpty()) insertionOffset else pasteOffset)
                            ?.coerceIn(0, layoutTextLength)
                        if (result != null && offset != null) {
                            val rect = result.getCursorRect(offset)
                            drawLine(insertionColor, Offset(rect.left, rect.top), Offset(rect.left, rect.bottom), strokeWidth = 2.dp.toPx())
                        }
                    }
                    .pointerInput(text, moveTargets) {
                        if (moveTargets.isEmpty()) detectTapGestures { position -> layout?.let { result ->
                            if (position.y < 0f || position.y > result.size.height || position.x < 0f) {
                                onTapOffset(text.length, true)
                            }
                            else {
                                val line = result.getLineForVerticalPosition(position.y)
                                val outsideText = position.x > result.getLineRight(line) + 8.dp.toPx()
                                val offset = if (outsideText) result.getLineEnd(line, visibleEnd = true) else result.getOffsetForPosition(position)
                                onTapOffset(offset.coerceIn(0, text.length), outsideText)
                            }
                        } }
                    }
                    .pointerInput(text, moveTargets, selections) {
                        if (moveTargets.isNotEmpty() && selections.isNotEmpty()) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    val offset = layout?.getOffsetForPosition(start)
                                    validDrag = offset != null && selections.any { it.range.contains(offset) }
                                    if (validDrag) { dragPosition = start; dragTargetIndex = activeMoveTarget.coerceIn(moveTargets.indices) }
                                },
                                onDragCancel = { validDrag = false; dragPosition = null },
                                onDragEnd = {
                                    if (validDrag) moveTargets.getOrNull(dragTargetIndex)?.let(onDrop)
                                    validDrag = false; dragPosition = null
                                },
                            ) { change, amount ->
                                if (validDrag) {
                                    change.consume()
                                    val next = (dragPosition ?: change.position) + amount
                                    dragPosition = next
                                    val result = layout ?: return@detectDragGestures
                                    val line = result.getLineForVerticalPosition(next.y.coerceIn(0f, result.size.height.toFloat()))
                                    val lineEnd = result.getLineEnd(line, visibleEnd = true)
                                    val inRightBlank = next.x >= result.getLineRight(line)
                                    val nearest = moveTargets.indices.minByOrNull { index ->
                                        val targetOffset = moveTargets[index].offset.coerceIn(0, result.layoutInput.text.length)
                                        if (inRightBlank) kotlin.math.abs(targetOffset - lineEnd).toFloat()
                                        else {
                                            val cursor = result.getCursorRect(targetOffset)
                                            val dx = cursor.left - next.x
                                            val dy = (cursor.top + cursor.bottom) / 2f - next.y
                                            dx * dx + dy * dy
                                        }
                                    }
                                    if (nearest != null) { dragTargetIndex = nearest; onMoveTarget(nearest) }
                                }
                            }
                        }
                    },
            )
            val result = layout
            val selected = selections.lastOrNull()
            if (showSelectionToolbar && result != null && selected != null && dragPosition == null) {
                val layoutTextLength = result.layoutInput.text.length
                quickEditBoundingBoxIndex(layoutTextLength, selected.range.start)?.let { anchorIndex ->
                    val startRect = result.getBoundingBox(anchorIndex)
                    val insetX = with(density) { 16.dp.toPx() }
                    val insetY = with(density) { 14.dp.toPx() }
                    Popup(
                        popupPositionProvider = QuickEditPopupPositionProvider(startRect.translate(insetX, insetY), with(density) { 14.dp.roundToPx() }),
                        properties = androidx.compose.ui.window.PopupProperties(focusable = false, clippingEnabled = false),
                    ) {
                        Surface(shape = MaterialTheme.shapes.small, tonalElevation = 5.dp, shadowElevation = 6.dp) { toolbar() }
                    }
                }
            }
            if (result != null && selected == null && pasteOffset != null && dragPosition == null) {
                val cursor = result.getCursorRect(pasteOffset.coerceIn(0, result.layoutInput.text.length))
                val insetX = with(density) { 16.dp.toPx() }
                val insetY = with(density) { 14.dp.toPx() }
                Popup(
                    popupPositionProvider = QuickEditPopupPositionProvider(cursor.translate(insetX, insetY), with(density) { 6.dp.roundToPx() }),
                    properties = androidx.compose.ui.window.PopupProperties(focusable = false, clippingEnabled = false),
                ) {
                    Surface(shape = MaterialTheme.shapes.small, tonalElevation = 5.dp, shadowElevation = 6.dp) { pasteToolbar() }
                }
            }
            dragPosition?.takeIf { validDrag }?.let { local ->
                Popup(alignment = Alignment.TopStart, offset = IntOffset((local.x + 12).toInt(), (local.y - 28).toInt())) {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer, shadowElevation = 8.dp) {
                        Text(dragPreview, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

internal fun quickEditBoundingBoxIndex(textLength: Int, requestedOffset: Int): Int? =
    if (textLength <= 0) null else requestedOffset.coerceIn(0, textLength - 1)

internal fun quickEditSelectionsAfterTap(
    current: List<QuickEditSelection>,
    candidate: QuickEditSelection,
    multiSelect: Boolean,
): List<QuickEditSelection> = if (multiSelect) {
    PromptQuickEdit.toggleSelection(current, candidate)
} else if (current.singleOrNull() == candidate) {
    emptyList()
} else {
    listOf(candidate)
}

private class QuickEditPopupPositionProvider(
    private val selectionRect: Rect,
    private val margin: Int,
) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val center = anchorBounds.left + selectionRect.center.x.toInt()
        val x = (center - popupContentSize.width / 2).coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
        val below = anchorBounds.top + selectionRect.bottom.toInt() + margin
        val y = below.coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin))
        return IntOffset(x, y)
    }
}

@Composable
private fun AutocompleteSuggestions(
    local: List<TagSuggestion>,
    novelAi: List<TagSuggestion>,
    danbooru: List<TagSuggestion>,
    wildcards: List<TagSuggestion>,
    loading: Boolean,
    novelAiFailed: Boolean,
    danbooruFailed: Boolean,
    requestStartedAtNanos: Long,
    rowScrollStates: AutocompleteRowScrollStates,
    selectedSource: AutocompleteDisplaySource,
    onSourceChange: (AutocompleteDisplaySource) -> Unit,
    onSelect: (TagSuggestion) -> Unit,
) {
    LaunchedEffect(requestStartedAtNanos, local) {
        if (BuildConfig.DEBUG && requestStartedAtNanos > 0L && local.isNotEmpty()) {
            Log.d(
                "AutocompletePerf",
                "inputToUiRenderMs=${(System.nanoTime() - requestStartedAtNanos) / 1_000_000.0} localCount=${local.size}",
            )
        }
    }
    val localSuggestions = remember(wildcards, local) {
        (wildcards + local).distinctBy { it.tag }
    }
    val suggestions = when (selectedSource) {
        AutocompleteDisplaySource.LOCAL -> localSuggestions
        AutocompleteDisplaySource.NOVEL_AI -> novelAi
        AutocompleteDisplaySource.DANBOORU -> danbooru
    }
    val rowState = when (selectedSource) {
        AutocompleteDisplaySource.LOCAL -> rowScrollStates.local
        AutocompleteDisplaySource.NOVEL_AI -> rowScrollStates.novelAi
        AutocompleteDisplaySource.DANBOORU -> rowScrollStates.danbooru
    }
    val failed = when (selectedSource) {
        AutocompleteDisplaySource.LOCAL -> false
        AutocompleteDisplaySource.NOVEL_AI -> novelAiFailed
        AutocompleteDisplaySource.DANBOORU -> danbooruFailed
    }
    SuggestionRow(
        source = selectedSource,
        suggestions = suggestions,
        rowState = rowState,
        loading = loading && selectedSource != AutocompleteDisplaySource.LOCAL,
        failed = failed,
        onSourceChange = onSourceChange,
        onSelect = onSelect,
    )
}

@Composable
private fun SuggestionRow(
    source: AutocompleteDisplaySource,
    suggestions: List<TagSuggestion>,
    rowState: LazyListState,
    loading: Boolean,
    failed: Boolean,
    onSourceChange: (AutocompleteDisplaySource) -> Unit,
    onSelect: (TagSuggestion) -> Unit,
) {
    val setPagerSwipeLocked = LocalGeneratePagerSwipeLock.current
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 18.dp.toPx() }
    val sourceDialHeightPx = with(density) { 32.dp.toPx() }
    val sourceDialScope = rememberCoroutineScope()
    var sourceDrag by remember { mutableFloatStateOf(0f) }
    var sourceSettling by remember { mutableStateOf(false) }
    fun settleSourceDial(direction: Int) {
        if (sourceSettling) return
        sourceSettling = true
        val step = direction.coerceIn(-1, 1)
        val target = -step * sourceDialHeightPx
        sourceDialScope.launch {
            animate(
                initialValue = sourceDrag,
                targetValue = target,
                animationSpec = tween(130),
            ) { value, _ -> sourceDrag = value }
            if (step != 0) onSourceChange(source.shifted(step))
            sourceDrag = 0f
            sourceSettling = false
        }
    }
    DisposableEffect(setPagerSwipeLocked) {
        onDispose { setPagerSwipeLocked(false) }
    }
    // Consume only the horizontal remainder that the suggestion row could not scroll.
    // Without this, an edge swipe is handed to the parent generation pager.
    val pagerEdgeGuard = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                Offset(available.x, 0f)

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(available.x, 0f)
        }
    }
    Row(Modifier.heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(32.dp)
                .clipToBounds()
                .clickable(enabled = !sourceSettling) { settleSourceDial(1) }
                .pointerInput(source, swipeThresholdPx, sourceSettling) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            if (!sourceSettling) sourceDrag = 0f
                        },
                        onVerticalDrag = { change, amount ->
                            if (!sourceSettling) {
                                change.consume()
                                sourceDrag = (sourceDrag + amount).coerceIn(-sourceDialHeightPx, sourceDialHeightPx)
                            }
                        },
                        onDragEnd = {
                            if (!sourceSettling) when {
                                sourceDrag <= -swipeThresholdPx -> settleSourceDial(1)
                                sourceDrag >= swipeThresholdPx -> settleSourceDial(-1)
                                else -> settleSourceDial(0)
                            }
                        },
                        onDragCancel = {
                            if (!sourceSettling) settleSourceDial(0)
                        },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            listOf(-1, 0, 1).forEach { relativeStep ->
                val labelSource = source.shifted(relativeStep)
                val translation = sourceDrag + relativeStep * sourceDialHeightPx
                Text(
                    text = stringResource(labelSource.label),
                    fontSize = 9.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.graphicsLayer {
                        translationY = translation
                        alpha = (1f - .45f * kotlin.math.abs(translation / sourceDialHeightPx)).coerceIn(.45f, 1f)
                    },
                )
            }
        }
        VerticalDivider(Modifier.height(22.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.width(6.dp))
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
            when {
                suggestions.isNotEmpty() -> LazyRow(
                    state = rowState,
                    modifier = Modifier
                        .nestedScroll(pagerEdgeGuard)
                        .pointerInput(setPagerSwipeLocked) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                setPagerSwipeLocked(true)
                                try {
                                    waitForUpOrCancellation()
                                } finally {
                                    setPagerSwipeLocked(false)
                                }
                            }
                        },
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(suggestions, key = { "${it.source}:${it.tag}" }) { suggestion ->
                        SuggestionChip(
                            onClick = { onSelect(suggestion) },
                            label = { Text(suggestion.tag.replace('_', ' '), fontSize = 12.sp, maxLines = 1) },
                        )
                    }
                }
                loading -> LinearProgressIndicator(Modifier.weight(1f).height(2.dp))
                else -> Text(
                    stringResource(if (failed) R.string.autocomplete_source_unavailable else R.string.autocomplete_no_suggestions),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun GenerationSettingsCard(session: Session, viewModel: MainViewModel, onMetadataImported: () -> Unit) {
    val settings = session.generationSettings
    var cherryDraft by remember { mutableStateOf<PromptCherryPickDraft?>(null) }
    var activeSlider by rememberSaveable { mutableStateOf<Int?>(null) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    var pendingImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            pendingImageUri = it.toString()
        }
    }
    cherryDraft?.let { draft ->
        PromptCherryPickDialog(session, draft, dismiss = { cherryDraft = null }) { blocks, selected, destination ->
            viewModel.appendCherryPickedSelection(blocks, selected, destination)
        }
    }
    pendingImageUri?.let { uri ->
        val metadata by produceState<com.hjhsys.naiblockprompt.domain.image.NaiImageMetadata?>(initialValue = null, uri) {
            value = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use { NaiPngMetadataParser.parse(it.readBytes()) } }.getOrNull()
            }
        }
        var includePrompt by rememberSaveable(uri) { mutableStateOf(true) }
        var includeNegative by rememberSaveable(uri) { mutableStateOf(true) }
        var includeCharacters by rememberSaveable(uri) { mutableStateOf(true) }
        var characterImportMode by rememberSaveable(uri) { mutableStateOf(CharacterImportMode.REPLACE) }
        var includeSettings by rememberSaveable(uri) { mutableStateOf(true) }
        var includeSeed by rememberSaveable(uri) { mutableStateOf(true) }
        var showMetadataPreview by rememberSaveable(uri) { mutableStateOf(false) }
        val importedBlockName = stringResource(R.string.imported_block_name)
        if (showMetadataPreview && metadata != null) {
            ExternalImageMetadataPreviewDialog(
                metadata = metadata!!,
                onImport = {
                    viewModel.importImageMetadata(metadata!!, importedBlockName, includePrompt, includeNegative, includeCharacters, includeSettings, includeSeed, characterImportMode)
                    pendingImageUri = null
                    onMetadataImported()
                },
                onCherryPick = {
                    cherryDraft = PromptCherryPick.fromMetadata(metadata!!, importedBlockName)
                    pendingImageUri = null
                },
                dismiss = { showMetadataPreview = false },
            )
        } else AlertDialog(
            onDismissRequest = { pendingImageUri = null },
            title = { Text(stringResource(R.string.choose_image_use)) },
            text = {
                Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AsyncImage(model = Uri.parse(uri), contentDescription = stringResource(R.string.import_image), modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp), contentScale = ContentScale.Fit)
                    com.hjhsys.naiblockprompt.ui.components.ImageActionChoices(uri, viewModel) { pendingImageUri = null }
                    if (settings.modelId?.startsWith("nai-diffusion-4-5-") != true) {
                        Text(stringResource(R.string.precise_reference_v45_only), style = MaterialTheme.typography.bodySmall)
                    }
                    if (metadata != null) {
                        HorizontalDivider()
                        Text(stringResource(R.string.nai_metadata_found), style = MaterialTheme.typography.titleSmall)
                        if (metadata?.usedExternalImageGuidance == true) {
                            Text(
                                stringResource(R.string.external_reference_not_restored_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Text(stringResource(R.string.restore_base), style = MaterialTheme.typography.titleSmall)
                            Column(Modifier.padding(start = 16.dp)) {
                                MetadataChoice(R.string.restore_base_positive, includePrompt) { includePrompt = it }
                                MetadataChoice(R.string.restore_base_negative, includeNegative) { includeNegative = it }
                            }
                            MetadataChoice(R.string.import_characters, includeCharacters) { includeCharacters = it }
                            if (includeCharacters) CharacterImportModePicker(characterImportMode) { characterImportMode = it }
                            MetadataChoice(R.string.import_settings, includeSettings) { includeSettings = it }
                            MetadataChoice(R.string.import_seed, includeSeed) { includeSeed = it }
                        }
                        OutlinedButton(
                            onClick = { showMetadataPreview = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.preview_image_metadata))
                        }
                        Button(onClick = {
                            viewModel.importImageMetadata(metadata!!, importedBlockName, includePrompt, includeNegative, includeCharacters, includeSettings, includeSeed, characterImportMode)
                            pendingImageUri = null
                            onMetadataImported()
                        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_metadata)) }
                        OutlinedButton(
                            onClick = {
                                cherryDraft = PromptCherryPick.fromMetadata(metadata!!, importedBlockName)
                                pendingImageUri = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.cherry_pick_prompts)) }
                    } else {
                        Text(stringResource(R.string.nai_metadata_not_found), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pendingImageUri = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    LaunchedEffect(settings.steps, settings.scale, settings.guidanceRescale) {
        if (settings.steps == null || settings.scale == null || settings.guidanceRescale == null) {
            viewModel.updateGenerationSettings {
                it.copy(steps = it.steps ?: 28, scale = it.scale ?: 5f, guidanceRescale = it.guidanceRescale ?: 0.4f)
            }
        }
    }
    Column(
        Modifier.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
        ) { activeSlider = null },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElevatedCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.model_id), style = MaterialTheme.typography.titleMedium)
                CatalogDropdown(R.string.model_id, settings.modelId, NaiGenerationCatalog.models) { value ->
                    viewModel.updateGenerationSettings { it.copy(modelId = value) }
                }
                OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/png", "image/jpeg", "image/webp")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AddPhotoAlternate, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.import_image))
                }
                settings.imageInput?.let { input ->
                    AsyncImage(
                        model = Uri.parse(input.uri),
                        contentDescription = stringResource(R.string.import_image),
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(input.mode.labelResource), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.updateGenerationSettings { it.copy(imageInput = null) } }) {
                            Icon(Icons.Default.Close, stringResource(R.string.remove_imported_image))
                        }
                    }
                    if (input.mode == ImageInputMode.INPAINT) {
                        var editMask by remember { mutableStateOf(false) }
                        OutlinedButton(onClick = { editMask = true }) { Text(stringResource(R.string.inpaint_edit)) }
                        if (editMask) com.hjhsys.naiblockprompt.ui.components.InpaintEditor(input.uri, input, { editMask = false }) { updated, generate ->
                            viewModel.useImageInput(updated)
                            editMask = false
                            if (generate) viewModel.generate()
                        }
                    }
                    if (input.mode == ImageInputMode.VIBE_TRANSFER) {
                        val unsupported = settings.modelId?.let { !VibeTransferRequestMapper.isSupported(it) } == true
                        Text(
                            stringResource(if (unsupported) R.string.vibe_v5_unused_status else R.string.vibe_encoding_cost_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (unsupported) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SliderSettingRow(R.string.image_strength, input.strength, IMAGE_INPUT_SLIDER, activeKey = activeSlider, onActiveKeyChange = { activeSlider = it }) { value ->
                        viewModel.updateGenerationSettings { current -> current.copy(imageInput = input.copy(strength = value)) }
                    }
                    if (input.mode == ImageInputMode.IMAGE_TO_IMAGE) {
                        SliderSettingRow(R.string.image_noise, input.noise, IMAGE_INPUT_SLIDER, activeKey = activeSlider, onActiveKeyChange = { activeSlider = it }) { value ->
                            viewModel.updateGenerationSettings { current -> current.copy(imageInput = input.copy(noise = value)) }
                        }
                    } else {
                        SliderSettingRow(R.string.information_extracted, input.informationExtracted, IMAGE_INPUT_SLIDER, activeKey = activeSlider, onActiveKeyChange = { activeSlider = it }) { value ->
                            viewModel.updateGenerationSettings { current -> current.copy(imageInput = input.copy(informationExtracted = value)) }
                        }
                    }
                    if (input.mode == ImageInputMode.PRECISE_REFERENCE) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            PreciseReferenceType.entries.forEach { type ->
                                FilterChip(
                                    selected = input.preciseType == type,
                                    onClick = { viewModel.updateGenerationSettings { it.copy(imageInput = input.copy(preciseType = type)) } },
                                    label = { Text(stringResource(type.labelResource), maxLines = 1) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        SliderSettingRow(R.string.reference_fidelity, input.fidelity, IMAGE_INPUT_SLIDER, activeKey = activeSlider, onActiveKeyChange = { activeSlider = it }) { value ->
                            viewModel.updateGenerationSettings { current -> current.copy(imageInput = input.copy(fidelity = value)) }
                        }
                    }
                }
            }
        }
        ElevatedCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.image_settings), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.resolution_value, settings.width, settings.height), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ResolutionPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = settings.width == preset.width && settings.height == preset.height,
                            onClick = { viewModel.updateGenerationSettings { it.copy(width = preset.width, height = preset.height) } },
                            label = { Text(stringResource(preset.label)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberSettingField(R.string.width, settings.width.toString(), Modifier.weight(1f)) { value ->
                        value.toIntOrNull()?.takeIf { it > 0 }?.let { width -> viewModel.updateGenerationSettings { it.copy(width = width) } }
                    }
                    IconButton(onClick = { viewModel.updateGenerationSettings { it.copy(width = it.height, height = it.width) } }) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = stringResource(R.string.swap_dimensions))
                    }
                    NumberSettingField(R.string.height, settings.height.toString(), Modifier.weight(1f)) { value ->
                        value.toIntOrNull()?.takeIf { it > 0 }?.let { height -> viewModel.updateGenerationSettings { it.copy(height = height) } }
                    }
                }
            }
        }
        ElevatedCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.ai_settings), style = MaterialTheme.typography.titleMedium)
                SliderSettingRow(R.string.steps, (settings.steps ?: 28).toFloat(), STEPS_SLIDER, activeKey = activeSlider, onActiveKeyChange = { activeSlider = it }) { value ->
                    viewModel.updateGenerationSettings { it.copy(steps = value.toInt()) }
                }
                SliderSettingRow(R.string.prompt_guidance, settings.scale ?: 5f, GUIDANCE_SLIDER, activeKey = activeSlider, onActiveKeyChange = { activeSlider = it }) { value ->
                    viewModel.updateGenerationSettings { it.copy(scale = value) }
                }
                Text(stringResource(R.string.seed), style = MaterialTheme.typography.labelLarge)
                FilledTonalButton(
                    onClick = viewModel::toggleSeedMode,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        if (settings.seedMode == SeedMode.RANDOM) Icons.Default.Casino else Icons.Default.Lock,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (settings.seedMode == SeedMode.RANDOM) R.string.seed_toggle_random_to_fixed else R.string.seed_toggle_fixed_to_random))
                }
                if (settings.seedMode == SeedMode.FIXED) {
                    NumberSettingField(R.string.seed, settings.seed?.toString().orEmpty(), Modifier.fillMaxWidth()) { value ->
                        viewModel.updateGenerationSettings { it.copy(seed = value.toLongOrNull()) }
                    }
                }
                CatalogDropdown(
                    label = R.string.sampler,
                    selectedId = settings.samplerId,
                    options = NaiGenerationCatalog.samplers,
                ) { value -> viewModel.updateGenerationSettings { it.copy(samplerId = value) } }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { advancedExpanded = !advancedExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.advanced_settings), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Icon(if (advancedExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight, contentDescription = null)
                }
                if (advancedExpanded) {
                    if (NaiGenerationCatalog.supportsSelectableNoiseSchedule(settings.modelId)) {
                        CatalogDropdown(
                            label = R.string.noise_schedule,
                            selectedId = settings.noiseSchedule,
                            options = NaiGenerationCatalog.noiseSchedules,
                        ) { value ->
                            viewModel.updateGenerationSettings { it.copy(noiseSchedule = value) }
                        }
                    }
                    SliderSettingRow(
                        R.string.guidance_rescale,
                        settings.guidanceRescale ?: 0.4f,
                        GUIDANCE_RESCALE_SLIDER,
                        helpBody = R.string.help_guidance_rescale_body,
                        activeKey = activeSlider,
                        onActiveKeyChange = { activeSlider = it },
                    ) { value ->
                        viewModel.updateGenerationSettings { it.copy(guidanceRescale = value) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExternalImageMetadataPreviewDialog(
    metadata: NaiImageMetadata,
    onImport: () -> Unit,
    onCherryPick: () -> Unit,
    dismiss: () -> Unit,
) {
    val unavailable = stringResource(R.string.metadata_value_unavailable)
    val resolvedModel = NaiGenerationCatalog.resolveModelId(metadata.model)
    val modelLabel = resolvedModel?.let { modelId ->
        NaiGenerationCatalog.models.firstOrNull { it.apiId == modelId }?.displayName?.let { "$it ($modelId)" }
    } ?: metadata.model ?: unavailable
    val resolution = if (metadata.width != null && metadata.height != null) {
        "${metadata.width} × ${metadata.height}"
    } else unavailable
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.import_metadata_preview_title)) },
        text = {
            SelectionContainer {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val generationDetails = listOf(
                        stringResource(R.string.model_id) to modelLabel,
                        stringResource(R.string.sampler) to (metadata.sampler ?: unavailable),
                        stringResource(R.string.resolution) to resolution,
                        stringResource(R.string.steps) to (metadata.steps?.toString() ?: unavailable),
                        stringResource(R.string.prompt_guidance) to (metadata.scale?.toString() ?: unavailable),
                        stringResource(R.string.seed) to (metadata.seed?.toString() ?: unavailable),
                    ).joinToString("\n") { (label, value) -> "$label: $value" }
                    DebugPromptSection(stringResource(R.string.generation_details), generationDetails)
                    DebugPromptSection(stringResource(R.string.restore_base_positive), metadata.prompt.orEmpty())
                    DebugPromptSection(stringResource(R.string.restore_base_negative), metadata.negativePrompt.orEmpty())
                    metadata.characterPrompts.forEachIndexed { index, value ->
                        DebugPromptSection("${stringResource(R.string.character_prompt, index + 1)} · ${stringResource(R.string.positive)}", value)
                    }
                    metadata.characterNegativePrompts.forEachIndexed { index, value ->
                        DebugPromptSection("${stringResource(R.string.character_prompt, index + 1)} · ${stringResource(R.string.negative)}", value)
                    }
                    Button(
                        onClick = onImport,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.import_metadata)) }
                    OutlinedButton(
                        onClick = onCherryPick,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.cherry_pick_prompts)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.confirm)) } },
    )
}

@Composable
private fun MetadataChoice(@StringRes label: Int, checked: Boolean, onChecked: (Boolean) -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
    Row(Modifier.fillMaxWidth().height(38.dp).clickable { onChecked(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChecked, modifier = Modifier.scale(.82f))
        Text(stringResource(label))
    }
    }
}

@Composable
internal fun generateButtonLabel(state: GenerationUiState, input: ImageInputState?, inProgress: Boolean = state is GenerationUiState.Loading): String {
    if (inProgress) return stringResource(R.string.generating)
    val extra = if (input?.mode == ImageInputMode.PRECISE_REFERENCE) 5 else 0
    return if (extra > 0) stringResource(R.string.generate_one_image_with_extra_anlas, extra)
    else if (input?.mode == ImageInputMode.VIBE_TRANSFER) stringResource(R.string.generate_one_image_vibe_encoding)
    else stringResource(R.string.generate_one_image)
}

private enum class ResolutionPreset(@param:StringRes val label: Int, val width: Int, val height: Int) {
    PORTRAIT(R.string.resolution_portrait, 832, 1216),
    SQUARE(R.string.resolution_square, 1024, 1024),
    LANDSCAPE(R.string.resolution_landscape, 1216, 832),
}

@Composable
private fun SliderSettingRow(
    @StringRes label: Int,
    value: Float,
    spec: NumericSliderSpec,
    @StringRes helpBody: Int? = null,
    activeKey: Int?,
    onActiveKeyChange: (Int?) -> Unit,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            helpBody?.let { HelpAffordance(title = label, body = it, modifier = Modifier.size(40.dp)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumericValueEditor(
                value = value.toDouble(),
                spec = spec,
                label = stringResource(label),
                onValueChange = { onValueChange(it.toFloat()) },
            )
            val active = activeKey == label
            NumericSlider(
                value = value.toDouble(),
                spec = spec,
                onValueChange = { onValueChange(it.toFloat()) },
                enabled = active,
                modifier = Modifier.weight(1f).then(
                    if (active) Modifier else Modifier.clickable { onActiveKeyChange(label) },
                ),
            )
        }
    }
}

private val STEPS_SLIDER = NumericSliderSpec(min = 1.0, max = 50.0, step = 1.0, displayDecimals = 0)
private val GUIDANCE_SLIDER = NumericSliderSpec(min = 0.0, max = 10.0, step = 0.1, displayDecimals = 1)
private val GUIDANCE_RESCALE_SLIDER = NumericSliderSpec(min = 0.0, max = 1.0, step = 0.05, displayDecimals = 2)
private val IMAGE_INPUT_SLIDER = NumericSliderSpec(min = 0.0, max = 1.0, step = 0.05, displayDecimals = 2)

@Composable
private fun TextSettingField(@StringRes label: Int, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value, onValueChange, label = { Text(stringResource(label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogDropdown(
    @StringRes label: Int,
    selectedId: String?,
    options: List<NaiCatalogOption>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.apiId == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.displayName.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(label)) },
            placeholder = { Text(stringResource(R.string.select_option)) },
            supportingText = selected?.let { { Text(it.apiId) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.displayName)
                            Text(option.apiId, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = { onSelect(option.apiId); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun NumberSettingField(
    @StringRes label: Int,
    value: String,
    modifier: Modifier,
    decimal: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun SmallIconButton(@StringRes label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = stringResource(label), modifier = Modifier.size(26.dp))
    }
}

internal fun synchronizePromptEditorValue(current: TextFieldValue, text: String): TextFieldValue {
    val maximum = text.length
    val selection = TextRange(
        current.selection.start.coerceIn(0, maximum),
        current.selection.end.coerceIn(0, maximum),
    )
    val composition = current.composition?.takeIf { range ->
        range.start in 0..maximum && range.end in 0..maximum
    }
    if (current.text == text && current.selection == selection && current.composition == composition) return current
    return TextFieldValue(
        text = text,
        selection = selection,
        // An IME composition belongs to the old text and cannot safely survive an external replacement.
        composition = if (current.text == text) composition else null,
    )
}

@Composable
private fun RepeatingIconButton(
    @StringRes label: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    longPressMillis: Int,
    repeatIntervalMillis: Int,
    onStep: () -> Unit,
    onRelease: () -> Unit,
    onPressChanged: (Boolean) -> Unit,
) {
    val description = stringResource(label)
    val currentStep by rememberUpdatedState(onStep)
    val currentRelease by rememberUpdatedState(onRelease)
    val currentPressChanged by rememberUpdatedState(onPressChanged)
    Surface(
        color = Color.Transparent,
        shape = androidx.compose.foundation.shape.CircleShape,
        modifier = Modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .semantics {
                contentDescription = description
                if (enabled) onClick { currentStep(); currentRelease(); true }
            }
            .pointerInput(enabled, longPressMillis, repeatIntervalMillis) {
                if (enabled) detectTapGestures(
                    onPress = {
                        currentPressChanged(true)
                        try {
                            currentStep()
                            coroutineScope {
                                val repeatJob = launch {
                                    delay(longPressMillis.toLong())
                                    while (true) {
                                        currentStep()
                                        delay(repeatIntervalMillis.toLong())
                                    }
                                }
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    repeatJob.cancel()
                                    currentRelease()
                                }
                            }
                        } finally {
                            currentPressChanged(false)
                        }
                    },
                    )
            },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
        }
    }
}

internal fun autocompleteFragmentForEditor(value: TextFieldValue): PromptFragment? =
    if (value.selection.collapsed) PromptAutocomplete.currentFragment(value.text, value.selection.end) else null

internal fun isCurrentAutocompleteFragment(value: TextFieldValue, fragment: PromptFragment): Boolean =
    autocompleteFragmentForEditor(value) == fragment

internal class PromptVisualTransformation(
    private val highColor: Color,
    private val lowColor: Color,
    private val neutralColor: Color,
    private val commentColor: Color,
    private val randomizerColor: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text)
        PromptProcessor.weightSpans(text.text).forEach { span ->
            val difference = kotlin.math.abs(span.weight - 1f)
            val strength = (difference / if (span.weight < 1f) 0.7f else 1f).coerceIn(0f, 1f)
            val baseColor = when {
                span.weight > 1f -> highColor
                span.weight < 1f -> lowColor
                else -> neutralColor
            }
            if (span.start >= 0 && span.endExclusive in span.start..text.length) {
                builder.addStyle(
                    SpanStyle(
                        color = baseColor.copy(alpha = 0.72f + 0.28f * strength),
                        background = baseColor.copy(alpha = 0.08f + 0.10f * strength),
                    ),
                    span.start,
                    span.endExclusive,
                )
            }
        }
        PromptProcessor.randomizerSpans(text.text).forEach { span ->
            if (span.start >= 0 && span.endExclusive in span.start..text.length) {
                builder.addStyle(SpanStyle(color = randomizerColor), span.start, span.endExclusive)
            }
        }
        // Apply comments last so they take precedence over an enclosing weight span.
        PromptProcessor.commentSpans(text.text).forEach { span ->
            if (span.start >= 0 && span.endExclusive in span.start..text.length) {
                builder.addStyle(SpanStyle(color = commentColor), span.start, span.endExclusive)
            }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

private val PromptPolarity.labelResource: Int get() = if (this == PromptPolarity.POSITIVE) R.string.positive else R.string.negative
private val CharacterType.labelResource: Int get() = when (this) {
    CharacterType.GIRL -> R.string.character_girl
    CharacterType.BOY -> R.string.character_boy
    CharacterType.OTHER -> R.string.character_other
}

private val ImageInputMode.labelResource: Int get() = when (this) {
    ImageInputMode.INPAINT -> R.string.inpaint_title
    ImageInputMode.IMAGE_TO_IMAGE -> R.string.image_to_image
    ImageInputMode.VIBE_TRANSFER -> R.string.vibe_transfer
    ImageInputMode.PRECISE_REFERENCE -> R.string.precise_reference
}

private val PreciseReferenceType.labelResource: Int get() = when (this) {
    PreciseReferenceType.CHARACTER_AND_STYLE -> R.string.reference_character_and_style
    PreciseReferenceType.CHARACTER -> R.string.reference_character
    PreciseReferenceType.STYLE -> R.string.reference_style
}

internal fun compactModelName(modelId: String?): String = when (modelId) {
    "nai-diffusion-5-full" -> "V5F"
    "nai-diffusion-5-curated" -> "V5C"
    "nai-diffusion-4-5-full" -> "V4.5F"
    "nai-diffusion-4-5-curated" -> "V4.5C"
    "nai-diffusion-4-full" -> "V4F"
    "nai-diffusion-4-curated-preview" -> "V4C"
    "nai-diffusion-3" -> "V3"
    "nai-diffusion-furry-3" -> "Furry V3"
    else -> "—"
}

internal fun estimateSessionTokenRange(
    session: Session,
    appSettings: AppSettings,
    wildcardValues: Map<String, List<String>>,
): TokenRange {
    val prompt = buildList {
        fun positive(pair: PromptPair, textRendering: TextRenderingState): String {
            val blocks = PromptProcessor.joinEnabledBlocks(pair.positiveBlocks, appSettings.normalizeWeightClosings)
            return if (appSettings.useTextRendering) PromptProcessor.appendTextRendering(blocks, textRendering) else blocks
        }
        add(positive(session.base.prompts, session.base.textRendering))
        session.characters.sortedBy { it.order }.forEach { add(positive(it.prompts, it.textRendering)) }
    }.joinToString(", ")
    return PromptTokenEstimator.estimate(prompt, wildcardValues)
}
