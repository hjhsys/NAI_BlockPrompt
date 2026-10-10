package com.hjhsys.naiblockprompt.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import coil3.compose.AsyncImage
import com.hjhsys.naiblockprompt.R
import com.hjhsys.naiblockprompt.data.gallery.GalleryFilterState
import com.hjhsys.naiblockprompt.data.gallery.GalleryPeriod
import com.hjhsys.naiblockprompt.data.gallery.GalleryTagMatch
import com.hjhsys.naiblockprompt.data.local.entity.GalleryDateBounds
import com.hjhsys.naiblockprompt.data.local.entity.GalleryImageEntity
import com.hjhsys.naiblockprompt.domain.autocomplete.TagSuggestion
import com.hjhsys.naiblockprompt.domain.editor.CharacterImportMode
import com.hjhsys.naiblockprompt.domain.editor.PromptCherryPick
import com.hjhsys.naiblockprompt.domain.editor.PromptCherryPickDraft
import com.hjhsys.naiblockprompt.domain.generation.SeedSelection
import com.hjhsys.naiblockprompt.domain.image.NaiImageMetadata
import com.hjhsys.naiblockprompt.domain.model.GeneratedPromptSnapshot
import com.hjhsys.naiblockprompt.domain.model.PromptImportPreferences
import com.hjhsys.naiblockprompt.ui.GalleryScanUiState
import com.hjhsys.naiblockprompt.ui.MainViewModel
import com.hjhsys.naiblockprompt.ui.components.ImageActionsDialog
import com.hjhsys.naiblockprompt.ui.components.ImageCardActions
import com.hjhsys.naiblockprompt.ui.components.ImageCompareCandidate
import com.hjhsys.naiblockprompt.ui.components.ImageViewer
import com.hjhsys.naiblockprompt.ui.components.imageReferenceModel
import com.hjhsys.naiblockprompt.ui.database.TagPickerScreen
import com.hjhsys.naiblockprompt.ui.generate.ProcessedPromptDialog
import com.hjhsys.naiblockprompt.ui.generate.PromptCompareCandidate
import com.hjhsys.naiblockprompt.ui.generate.PromptCherryPickDialog
import com.hjhsys.naiblockprompt.ui.generate.LocalGeneratePagerSwipeLock
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GeneratedGalleryScreen(viewModel: MainViewModel, onBack: () -> Unit, onImported: () -> Unit) {
    val images by viewModel.galleryImages.collectAsState()
    val filter by viewModel.galleryFilter.collectAsState()
    val models by viewModel.galleryModels.collectAsState()
    val samplers by viewModel.gallerySamplers.collectAsState()
    val bounds by viewModel.galleryDateBounds.collectAsState()
    val scanState by viewModel.galleryScanState.collectAsState()
    val autocomplete by viewModel.autocomplete.collectAsState()
    val selected = remember { mutableStateListOf<String>() }
    var detail by remember { mutableStateOf<GalleryImageEntity?>(null) }
    var tagPickerOpen by remember { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var deleteItems by remember { mutableStateOf<List<GalleryImageEntity>?>(null) }
    var deleteFailureCount by remember { mutableIntStateOf(0) }
    val galleryListState = rememberLazyListState()

    val selectionMode = selected.isNotEmpty()
    BackHandler {
        when {
            tagPickerOpen -> tagPickerOpen = false
            detail != null -> detail = null
            selectionMode -> selected.clear()
            else -> onBack()
        }
    }
    LaunchedEffect(Unit) { viewModel.scanGallery() }
    DisposableEffect(Unit) { onDispose(viewModel::clearAutocomplete) }

    if (tagPickerOpen) {
        TagPickerScreen(
            viewModel = viewModel,
            onDismiss = { tagPickerOpen = false },
            onInserted = { tagPickerOpen = false },
            maxSelection = 1,
            onSelectTags = { tags -> tags.firstOrNull()?.let(viewModel::chooseGalleryTag); tagPickerOpen = false },
        )
        return
    }
    detail?.let { item ->
        GalleryImageDetail(
            item = item,
            metadata = viewModel.galleryMetadata(item),
            comparisonItems = images.filter { it.uri != item.uri },
            viewModel = viewModel,
            onBack = { detail = null },
            onUseOrImport = { detail = null; onImported() },
            onDelete = { deleteItems = listOf(item) },
        )
        deleteItems?.let { candidates ->
            DeleteGalleryDialog(candidates.size, onDismiss = { deleteItems = null }) {
                viewModel.deleteGalleryImages(candidates) { deleted ->
                    deleteFailureCount = candidates.size - deleted
                    if (deleted > 0) detail = null
                }
                deleteItems = null
            }
        }
        if (deleteFailureCount > 0) AlertDialog(
            onDismissRequest = { deleteFailureCount = 0 },
            title = { Text(stringResource(R.string.gallery_delete_failed_title)) },
            text = { Text(stringResource(R.string.gallery_delete_some_failed, deleteFailureCount)) },
            confirmButton = { TextButton(onClick = { deleteFailureCount = 0 }) { Text(stringResource(R.string.confirm)) } },
        )
        return
    }

    deleteItems?.let { candidates ->
        DeleteGalleryDialog(candidates.size, onDismiss = { deleteItems = null }) {
            viewModel.deleteGalleryImages(candidates) { deleted ->
                deleteFailureCount = candidates.size - deleted
                if (deleted > 0) selected.removeAll(candidates.map { it.uri }.toSet())
            }
            deleteItems = null
        }
    }
    if (deleteFailureCount > 0) AlertDialog(
        onDismissRequest = { deleteFailureCount = 0 },
        title = { Text(stringResource(R.string.gallery_delete_failed_title)) },
        text = { Text(stringResource(R.string.gallery_delete_some_failed, deleteFailureCount)) },
        confirmButton = { TextButton(onClick = { deleteFailureCount = 0 }) { Text(stringResource(R.string.confirm)) } },
    )
    if (datePickerOpen) GalleryDatePicker(filter, bounds, onDismiss = { datePickerOpen = false }) { start, end ->
        viewModel.setGalleryPeriod(GalleryPeriod.CUSTOM, start, end)
        datePickerOpen = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) stringResource(R.string.gallery_selected_count, selected.size) else stringResource(R.string.gallery_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (selectionMode) selected.clear() else onBack() }) {
                        Icon(if (selectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (selectionMode) {
                        TextButton(onClick = {
                            if (selected.size == images.size) selected.clear() else {
                                selected.clear(); selected.addAll(images.map { it.uri })
                            }
                        }) { Text(stringResource(R.string.gallery_select_results)) }
                        IconButton(onClick = { deleteItems = images.filter { it.uri in selected } }) { Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
                    } else {
                        if (scanState is GalleryScanUiState.Scanning) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        IconButton(onClick = viewModel::scanGallery, enabled = scanState !is GalleryScanUiState.Scanning) { Icon(Icons.Default.Refresh, stringResource(R.string.gallery_refresh)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            GallerySearchPanel(
                filter = filter,
                models = models,
                samplers = samplers,
                bounds = bounds,
                localSuggestions = if (autocomplete.blockId == "__gallery_search__") autocomplete.local else emptyList(),
                novelAiSuggestions = if (autocomplete.blockId == "__gallery_search__") autocomplete.novelAi else emptyList(),
                danbooruSuggestions = if (autocomplete.blockId == "__gallery_search__") autocomplete.danbooru else emptyList(),
                autocompleteLoading = autocomplete.blockId == "__gallery_search__" && autocomplete.loading,
                novelAiFailed = autocomplete.blockId == "__gallery_search__" && autocomplete.novelAiFailed,
                danbooruFailed = autocomplete.blockId == "__gallery_search__" && autocomplete.danbooruFailed,
                onTag = viewModel::requestGalleryTagAutocomplete,
                onTagMatch = viewModel::setGalleryTagMatch,
                onSuggestion = { suggestion ->
                    viewModel.recordAutocompleteUse(suggestion)
                    viewModel.chooseGalleryTag(suggestion.tag)
                },
                onTagPicker = { tagPickerOpen = true },
                onModel = viewModel::setGalleryModel,
                onSampler = viewModel::setGallerySampler,
                onSteps = viewModel::setGallerySteps,
                onScale = viewModel::setGalleryScale,
                onRescale = viewModel::setGalleryGuidanceRescale,
                onClearGeneration = viewModel::clearGalleryGenerationFilters,
                onPeriod = { period -> if (period == GalleryPeriod.CUSTOM) datePickerOpen = true else viewModel.setGalleryPeriod(period) },
                onCustomDates = { datePickerOpen = true },
                onCustomRange = { start, end -> viewModel.setGalleryPeriod(GalleryPeriod.CUSTOM, start, end) },
            )
            when {
                scanState is GalleryScanUiState.Failed -> Text(stringResource(R.string.gallery_scan_failed), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                images.isEmpty() && scanState is GalleryScanUiState.Scanning -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                images.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.gallery_empty)) }
                else -> GalleryGrid(
                    images = images,
                    listState = galleryListState,
                    selected = selected,
                    onOpen = { detail = it },
                    onToggle = { item -> if (item.uri in selected) selected.remove(item.uri) else selected.add(item.uri) },
                    onSelectDay = { dayItems ->
                        val ids = dayItems.map { it.uri }
                        if (ids.all { it in selected }) selected.removeAll(ids.toSet()) else ids.filterNot { it in selected }.forEach(selected::add)
                    },
                )
            }
        }
    }
}

@Composable
private fun GallerySearchPanel(
    filter: GalleryFilterState,
    models: List<String>,
    samplers: List<String>,
    bounds: GalleryDateBounds,
    localSuggestions: List<TagSuggestion>,
    novelAiSuggestions: List<TagSuggestion>,
    danbooruSuggestions: List<TagSuggestion>,
    autocompleteLoading: Boolean,
    novelAiFailed: Boolean,
    danbooruFailed: Boolean,
    onTag: (String) -> Unit,
    onTagMatch: (GalleryTagMatch) -> Unit,
    onSuggestion: (TagSuggestion) -> Unit,
    onTagPicker: () -> Unit,
    onModel: (String) -> Unit,
    onSampler: (String) -> Unit,
    onSteps: (String) -> Unit,
    onScale: (String) -> Unit,
    onRescale: (String) -> Unit,
    onClearGeneration: () -> Unit,
    onPeriod: (GalleryPeriod) -> Unit,
    onCustomDates: () -> Unit,
    onCustomRange: (Long, Long) -> Unit,
) {
    var detailedSearchEnabled by rememberSaveable { mutableStateOf(false) }
    var detailedSearchExpanded by rememberSaveable { mutableStateOf(false) }
    var dateSliderActive by rememberSaveable { mutableStateOf(false) }
    var autocompleteSourceName by rememberSaveable { mutableStateOf(GalleryAutocompleteSource.LOCAL.name) }
    val autocompleteSource = runCatching { GalleryAutocompleteSource.valueOf(autocompleteSourceName) }
        .getOrDefault(GalleryAutocompleteSource.LOCAL)
    fun selectAutocompleteSource(source: GalleryAutocompleteSource) { autocompleteSourceName = source.name }
    Column(
        Modifier
            .padding(horizontal = 10.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ) { dateSliderActive = false },
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        OutlinedTextField(
            value = filter.tag,
            onValueChange = onTag,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.gallery_tag_search), style = MaterialTheme.typography.bodyMedium) },
            textStyle = MaterialTheme.typography.bodyMedium,
            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(22.dp)) },
            trailingIcon = {
                Row {
                    if (filter.tag.isNotEmpty()) IconButton(onClick = { onTag("") }) { Icon(Icons.Default.Clear, null, Modifier.size(22.dp)) }
                    IconButton(onClick = onTagPicker) { Icon(Icons.Default.Storage, stringResource(R.string.search_tag_dictionary), Modifier.size(22.dp)) }
                }
            },
        )
        if (filter.tag.length >= 2) GalleryAutocompleteRow(
            source = autocompleteSource,
            local = localSuggestions,
            novelAi = novelAiSuggestions,
            danbooru = danbooruSuggestions,
            loading = autocompleteLoading,
            novelAiFailed = novelAiFailed,
            danbooruFailed = danbooruFailed,
            onSourceChange = ::selectAutocompleteSource,
            onSuggestion = onSuggestion,
        )
        Row(Modifier.fillMaxWidth().heightIn(min = 38.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).clickable {
                    onTagMatch(if (filter.tagMatch == GalleryTagMatch.EXACT) GalleryTagMatch.PARTIAL else GalleryTagMatch.EXACT)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = filter.tagMatch == GalleryTagMatch.EXACT,
                    onCheckedChange = { checked -> onTagMatch(if (checked) GalleryTagMatch.EXACT else GalleryTagMatch.PARTIAL) },
                    modifier = Modifier.size(36.dp),
                )
                Text(stringResource(R.string.gallery_tag_match_exact_checkbox), style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier.clickable {
                    if (detailedSearchEnabled) {
                        detailedSearchExpanded = !detailedSearchExpanded
                        if (!detailedSearchExpanded) dateSliderActive = false
                    }
                    else {
                        detailedSearchEnabled = true
                        detailedSearchExpanded = true
                    }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = detailedSearchEnabled,
                    onCheckedChange = {
                        detailedSearchEnabled = it
                        detailedSearchExpanded = it
                        if (!it) dateSliderActive = false
                    },
                    modifier = Modifier.size(36.dp),
                )
                Text(stringResource(R.string.gallery_detailed_search), style = MaterialTheme.typography.bodySmall)
                if (detailedSearchEnabled) Icon(
                    if (detailedSearchExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    Modifier.size(18.dp),
                )
            }
        }
        if (detailedSearchEnabled && detailedSearchExpanded) ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(34.dp).clickable {
                        detailedSearchExpanded = false
                        dateSliderActive = false
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Tune, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.gallery_search_conditions), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClearGeneration, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(stringResource(R.string.reset)) }
                    Icon(Icons.Default.ExpandLess, stringResource(R.string.collapse), Modifier.size(20.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterDropdown(stringResource(R.string.model_id), filter.model, models, Modifier.weight(1f), onModel)
                    FilterDropdown(stringResource(R.string.sampler), filter.sampler, samplers, Modifier.weight(1f), onSampler)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberFilter(R.string.steps, filter.steps, Modifier.weight(1f), onSteps)
                    NumberFilter(R.string.gallery_prompt_guidance_short, filter.scale, Modifier.weight(1f), onScale)
                    NumberFilter(R.string.gallery_guidance_rescale_advanced, filter.guidanceRescale, Modifier.weight(1f), onRescale)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    item { PeriodChip(R.string.gallery_period_month, filter.period == GalleryPeriod.MONTH) { onPeriod(GalleryPeriod.MONTH) } }
                    item { PeriodChip(R.string.gallery_period_year, filter.period == GalleryPeriod.YEAR) { onPeriod(GalleryPeriod.YEAR) } }
                    item { PeriodChip(R.string.gallery_period_all, filter.period == GalleryPeriod.ALL) { onPeriod(GalleryPeriod.ALL) } }
                }
                if (bounds.oldest != null && bounds.newest != null) {
                    val (selectedStart, selectedEnd) = effectiveRange(filter, bounds)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(
                            onClick = { dateSliderActive = false; onCustomDates() },
                            modifier = Modifier.weight(1f).height(34.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        ) { Text(shortDate(selectedStart), style = MaterialTheme.typography.bodySmall) }
                        Text("~")
                        OutlinedButton(
                            onClick = { dateSliderActive = false; onCustomDates() },
                            modifier = Modifier.weight(1f).height(34.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        ) { Text(shortDate(selectedEnd), style = MaterialTheme.typography.bodySmall) }
                    }
                    GalleryRangeBar(
                        filter = filter,
                        bounds = bounds,
                        active = dateSliderActive,
                        onActiveChange = { dateSliderActive = it },
                        onRangeChange = onCustomRange,
                    )
                }
            }
        }
    }
}

private enum class GalleryAutocompleteSource { LOCAL, NOVEL_AI, DANBOORU;
    fun shifted(delta: Int): GalleryAutocompleteSource {
        val values = entries
        return values[(ordinal + delta).mod(values.size)]
    }
}

@Composable
private fun GalleryAutocompleteRow(
    source: GalleryAutocompleteSource,
    local: List<TagSuggestion>,
    novelAi: List<TagSuggestion>,
    danbooru: List<TagSuggestion>,
    loading: Boolean,
    novelAiFailed: Boolean,
    danbooruFailed: Boolean,
    onSourceChange: (GalleryAutocompleteSource) -> Unit,
    onSuggestion: (TagSuggestion) -> Unit,
) {
    val suggestions = when (source) {
        GalleryAutocompleteSource.LOCAL -> local
        GalleryAutocompleteSource.NOVEL_AI -> novelAi
        GalleryAutocompleteSource.DANBOORU -> danbooru
    }
    val failed = when (source) {
        GalleryAutocompleteSource.LOCAL -> false
        GalleryAutocompleteSource.NOVEL_AI -> novelAiFailed
        GalleryAutocompleteSource.DANBOORU -> danbooruFailed
    }
    var verticalDrag by remember { mutableFloatStateOf(0f) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(58.dp)
                    .fillMaxHeight()
                    .clickable { onSourceChange(source.shifted(1)) }
                    .pointerInput(source) {
                        detectVerticalDragGestures(
                            onDragStart = { verticalDrag = 0f },
                            onVerticalDrag = { change, amount -> change.consume(); verticalDrag += amount },
                            onDragEnd = {
                                when {
                                    verticalDrag > 12.dp.toPx() -> onSourceChange(source.shifted(-1))
                                    verticalDrag < -12.dp.toPx() -> onSourceChange(source.shifted(1))
                                }
                                verticalDrag = 0f
                            },
                            onDragCancel = { verticalDrag = 0f },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) { Text(source.shortLabel, style = MaterialTheme.typography.labelMedium) }
            VerticalDivider(Modifier.height(28.dp))
            when {
                loading && source != GalleryAutocompleteSource.LOCAL && suggestions.isEmpty() ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { CircularProgressIndicator(Modifier.padding(start = 14.dp).size(20.dp), strokeWidth = 2.dp) }
                failed -> Text(stringResource(R.string.autocomplete_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp))
                suggestions.isEmpty() -> Text(stringResource(R.string.autocomplete_no_suggestions), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
                else -> LazyRow(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(suggestions.take(16), key = { "${it.source}-${it.tag}" }) { suggestion ->
                        AssistChip(
                            onClick = { onSuggestion(suggestion) },
                            label = { Text(suggestion.tag.replace('_', ' '), maxLines = 1) },
                            modifier = Modifier.height(34.dp),
                        )
                    }
                }
            }
        }
    }
}

private val GalleryAutocompleteSource.shortLabel: String get() = when (this) {
    GalleryAutocompleteSource.LOCAL -> "LOCAL"
    GalleryAutocompleteSource.NOVEL_AI -> "NAI"
    GalleryAutocompleteSource.DANBOORU -> "DAN"
}

@Composable
private fun FilterDropdown(label: String, value: String, options: List<String>, modifier: Modifier, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(value.ifBlank { stringResource(R.string.gallery_filter_any) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.gallery_filter_any)) }, onClick = { onSelect(""); open = false })
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); open = false }) }
        }
    }
}

@Composable
private fun NumberFilter(label: Int, value: String, modifier: Modifier, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = modifier.height(52.dp),
        singleLine = true,
        label = { Text(stringResource(label), maxLines = 1) },
        placeholder = { Text(stringResource(R.string.gallery_filter_any)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun PeriodChip(label: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringResource(label), maxLines = 1) }, modifier = Modifier.height(30.dp))
}

@Composable
private fun GalleryRangeBar(
    filter: GalleryFilterState,
    bounds: GalleryDateBounds,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    onRangeChange: (Long, Long) -> Unit,
) {
    val oldest = bounds.oldest ?: return
    val newest = bounds.newest ?: return
    val (selectedStart, selectedEnd) = effectiveRange(filter, bounds)
    val span = (newest - oldest).coerceAtLeast(1L)
    val startFraction = ((selectedStart - oldest).toFloat() / span).coerceIn(0f, 1f)
    val endFraction = ((selectedEnd - oldest).toFloat() / span).coerceIn(startFraction, 1f)
    var sliderRange by remember(oldest, newest) { mutableStateOf(startFraction..endFraction) }
    LaunchedEffect(startFraction, endFraction) { sliderRange = startFraction..endFraction }
    val horizontalEdgeGuard = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                Offset(available.x, 0f)

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(available.x, 0f)
        }
    }
    val setPagerSwipeLocked = LocalGeneratePagerSwipeLock.current
    LaunchedEffect(active, setPagerSwipeLocked) { setPagerSwipeLocked(active) }
    DisposableEffect(setPagerSwipeLocked) { onDispose { setPagerSwipeLocked(false) } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        RangeSlider(
            value = sliderRange,
            onValueChange = { sliderRange = it },
            onValueChangeFinished = {
                val start = oldest + (span * sliderRange.start).toLong()
                val end = oldest + (span * sliderRange.endInclusive).toLong()
                onRangeChange(start, end.coerceAtLeast(start))
            },
            valueRange = 0f..1f,
            enabled = active,
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .nestedScroll(horizontalEdgeGuard)
                .then(
                    if (active) Modifier
                    else Modifier.pointerInput(selectedStart, selectedEnd, setPagerSwipeLocked) {
                        detectTapGestures(
                            onPress = {
                                setPagerSwipeLocked(true)
                                if (!tryAwaitRelease()) setPagerSwipeLocked(false)
                            },
                            onTap = {
                                // Freeze relative presets (1 month / 1 year / all) into a
                                // custom range before enabling direct manipulation.
                                onRangeChange(selectedStart, selectedEnd)
                                onActiveChange(true)
                            },
                        )
                    },
                ),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(shortDate(oldest), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(shortDate(newest), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryDatePicker(filter: GalleryFilterState, bounds: GalleryDateBounds, onDismiss: () -> Unit, onConfirm: (Long, Long) -> Unit) {
    val (start, end) = effectiveRange(filter, bounds)
    val state = rememberDateRangePickerState(initialSelectedStartDateMillis = start, initialSelectedEndDateMillis = end)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = {
            val selectedStart = state.selectedStartDateMillis
            val selectedEnd = state.selectedEndDateMillis
            if (selectedStart != null && selectedEnd != null) onConfirm(selectedStart, selectedEnd + DAY_MILLIS - 1)
        }) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DateRangePicker(state = state, title = null, headline = null) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryGrid(
    images: List<GalleryImageEntity>,
    listState: LazyListState,
    selected: List<String>,
    onOpen: (GalleryImageEntity) -> Unit,
    onToggle: (GalleryImageEntity) -> Unit,
    onSelectDay: (List<GalleryImageEntity>) -> Unit,
) {
    val groups = remember(images) { images.groupBy { dayKey(it.createdAt) }.toList() }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        groups.forEach { (_, dayImages) ->
            item(key = "day-${dayKey(dayImages.first().createdAt)}") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(dayLabel(dayImages.first().createdAt), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (selected.isNotEmpty()) TextButton(onClick = { onSelectDay(dayImages) }) {
                        val allSelected = dayImages.all { it.uri in selected }
                        Icon(if (allSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(3.dp)); Text(stringResource(R.string.gallery_select_date))
                    }
                }
            }
            items(dayImages.chunked(3), key = { row -> row.joinToString { it.uri } }) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { item ->
                        val isSelected = item.uri in selected
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .combinedClickable(onClick = { if (selected.isNotEmpty()) onToggle(item) else onOpen(item) }, onLongClick = { onToggle(item) }),
                        ) {
                            AsyncImage(imageReferenceModel(item.uri), item.displayName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            if (isSelected) {
                                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = .28f)))
                                Icon(Icons.Default.CheckCircle, null, Modifier.align(Alignment.TopEnd).padding(5.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            item.model?.let { model ->
                                Text(model, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = .78f)).padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryImageDetail(
    item: GalleryImageEntity,
    metadata: NaiImageMetadata,
    comparisonItems: List<GalleryImageEntity>,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onUseOrImport: () -> Unit,
    onDelete: () -> Unit,
) {
    val session by viewModel.session.collectAsState()
    val appSettings by viewModel.settings.collectAsState()
    var viewer by remember { mutableStateOf(false) }
    var actions by remember { mutableStateOf(false) }
    var promptInfo by remember { mutableStateOf(false) }
    var importOptions by remember { mutableStateOf(false) }
    var cherryDraft by remember { mutableStateOf<PromptCherryPickDraft?>(null) }
    var cherryPickImported by remember { mutableStateOf(false) }
    var initialImageComparison by remember { mutableStateOf<ImageCompareCandidate?>(null) }
    var initialPromptComparison by remember { mutableStateOf<PromptCompareCandidate?>(null) }
    val importedBlockName = stringResource(R.string.imported_block_name)
    val hasPromptData = metadata.hasPromptData()
    val comparisonMetadata = remember(item.uri, comparisonItems) {
        comparisonItems.mapNotNull { candidate ->
            val candidateMetadata = viewModel.galleryMetadata(candidate)
            if (!candidateMetadata.hasPromptData()) null else candidate to candidateMetadata
        }
    }
    val imageComparisonCandidates = comparisonMetadata.map { (candidate, candidateMetadata) ->
        ImageCompareCandidate(
            id = candidate.uri,
            reference = candidate.uri,
            thumbnailReference = candidate.uri,
            dateLabel = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(candidate.createdAt)),
            modelLabel = candidate.model,
            promptPreview = candidateMetadata.promptPreview(),
            prompt = candidateMetadata.toGeneratedPrompt(),
        )
    }
    val promptComparisonCandidates = comparisonMetadata.map { (candidate, candidateMetadata) ->
        PromptCompareCandidate(
            id = candidate.uri,
            prompt = candidateMetadata.toGeneratedPrompt(),
            imageReference = candidate.uri,
            imageAvailable = true,
            thumbnailPath = candidate.uri,
            dateLabel = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(candidate.createdAt)),
            modelLabel = candidate.model,
            promptPreview = candidateMetadata.promptPreview(),
        )
    }
    if (viewer) ImageViewer(
        reference = item.uri,
        comparisonCandidates = imageComparisonCandidates,
        initialComparison = initialImageComparison,
        startFullscreen = true,
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
            viewer = false
            promptInfo = true
        },
        onDismiss = {
            viewer = false
            initialImageComparison = null
        },
    )
    if (actions) ImageActionsDialog(item.uri, viewModel, onApplied = onUseOrImport) { actions = false }
    if (promptInfo) ProcessedPromptDialog(
        prompt = metadata.toGeneratedPrompt(),
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
            promptInfo = false
            viewer = true
        },
        dismiss = {
            promptInfo = false
            initialPromptComparison = null
        },
    )
    cherryDraft?.let { draft ->
        session?.let { current ->
            PromptCherryPickDialog(
                current = current,
                initialDraft = draft,
                dismiss = {
                    cherryDraft = null
                    if (cherryPickImported) {
                        cherryPickImported = false
                        onUseOrImport()
                    }
                },
            ) { blocks, selected, destination ->
                viewModel.appendCherryPickedSelection(blocks, selected, destination)
                cherryPickImported = true
            }
        }
    }
    if (importOptions) GalleryMetadataImportDialog(
        metadata = metadata,
        initialPreferences = appSettings.promptImportPreferences,
        onDismiss = { importOptions = false },
    ) { includePrompt, includeNegative, includeCharacters, includeSettings, includeSeed, characterMode ->
        viewModel.importImageMetadata(
            metadata = metadata,
            blockName = importedBlockName,
            includePrompt = includePrompt,
            includeNegative = includeNegative,
            includeCharacters = includeCharacters,
            includeSettings = includeSettings,
            includeSeed = includeSeed,
            characterMode = characterMode,
        )
        importOptions = false
        onUseOrImport()
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                AsyncImage(imageReferenceModel(item.uri), item.displayName, Modifier.fillMaxWidth().heightIn(max = 540.dp).clickable {
                    initialImageComparison = null
                    viewer = true
                }, contentScale = ContentScale.Fit)
            }
            item {
                Text(buildList {
                    metadata.model?.let { add(it) }
                    if (metadata.width != null && metadata.height != null) add("${metadata.width} × ${metadata.height}")
                    metadata.seed?.let { add("Seed $it") }
                }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            item {
                ImageCardActions(
                    imageActionsEnabled = true,
                    informationEnabled = hasPromptData,
                    onImageActions = { actions = true },
                    onImportInformation = { importOptions = true },
                    seedEnabled = metadata.seed?.let(SeedSelection::isValid) == true,
                    onApplySeed = { metadata.seed?.let(viewModel::applyHistorySeed) },
                    promptSelectionEnabled = hasPromptData && session != null,
                    onImportPromptSelection = {
                        cherryPickImported = false
                        cherryDraft = PromptCherryPick.fromMetadata(metadata, importedBlockName)
                    },
                    promptViewEnabled = hasPromptData,
                    onViewPrompt = {
                        initialPromptComparison = null
                        promptInfo = true
                    },
                )
            }
            item { OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().height(42.dp), contentPadding = PaddingValues(horizontal = 10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.Delete, null, Modifier.size(19.dp)); Spacer(Modifier.width(5.dp)); Text(stringResource(R.string.delete)) } }
        }
    }
}

@Composable
private fun GalleryMetadataImportDialog(
    metadata: NaiImageMetadata,
    initialPreferences: PromptImportPreferences,
    onDismiss: () -> Unit,
    onImport: (Boolean, Boolean, Boolean, Boolean, Boolean, CharacterImportMode) -> Unit,
) {
    var includePrompt by rememberSaveable(initialPreferences) { mutableStateOf(initialPreferences.basePositive) }
    var includeNegative by rememberSaveable(initialPreferences) { mutableStateOf(initialPreferences.baseNegative) }
    var includeCharacters by rememberSaveable(initialPreferences) { mutableStateOf(initialPreferences.characters) }
    var includeSettings by rememberSaveable(initialPreferences) { mutableStateOf(initialPreferences.settings) }
    var includeSeed by rememberSaveable(initialPreferences) { mutableStateOf(initialPreferences.seed) }
    var characterMode by rememberSaveable(initialPreferences) {
        mutableStateOf(if (initialPreferences.appendCharacters) CharacterImportMode.APPEND else CharacterImportMode.REPLACE)
    }
    val anythingSelected = includePrompt || includeNegative || includeCharacters || includeSettings || includeSeed
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_metadata)) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (metadata.usedExternalImageGuidance) {
                    Text(
                        stringResource(R.string.external_reference_not_restored_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(stringResource(R.string.restore_base), style = MaterialTheme.typography.titleSmall)
                Column(Modifier.padding(start = 14.dp)) {
                    GalleryMetadataChoice(R.string.restore_base_positive, includePrompt) { includePrompt = it }
                    GalleryMetadataChoice(R.string.restore_base_negative, includeNegative) { includeNegative = it }
                }
                GalleryMetadataChoice(R.string.import_characters, includeCharacters) { includeCharacters = it }
                if (includeCharacters) CharacterImportModePicker(characterMode) { characterMode = it }
                GalleryMetadataChoice(R.string.import_settings, includeSettings) { includeSettings = it }
                GalleryMetadataChoice(R.string.import_seed, includeSeed) { includeSeed = it }
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(includePrompt, includeNegative, includeCharacters, includeSettings, includeSeed, characterMode) },
                enabled = anythingSelected,
            ) { Text(stringResource(R.string.import_metadata)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun GalleryMetadataChoice(label: Int, checked: Boolean, onChecked: (Boolean) -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
        Row(
            Modifier.fillMaxWidth().height(38.dp).clickable { onChecked(!checked) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = onChecked, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(label))
        }
    }
}

@Composable
private fun DeleteGalleryDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gallery_delete_count_title, count)) },
        text = { Text(stringResource(R.string.gallery_delete_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun NaiImageMetadata.toGeneratedPrompt() = GeneratedPromptSnapshot(
    basePositive = prompt.orEmpty(), baseNegative = negativePrompt.orEmpty(), characterPositive = characterPrompts,
    characterNegative = characterNegativePrompts, usedSeed = seed?.takeIf(SeedSelection::isValid) ?: 0L,
    modelId = model.orEmpty(), samplerId = sampler.orEmpty(), width = width ?: 0, height = height ?: 0,
    steps = steps ?: 0, scale = scale ?: 0f, guidanceRescale = guidanceRescale, noiseSchedule = noiseSchedule.orEmpty(),
    characterPositions = characterPositions,
)

private fun NaiImageMetadata.hasPromptData(): Boolean = prompt?.isNotBlank() == true ||
    negativePrompt?.isNotBlank() == true ||
    characterPrompts.any(String::isNotBlank) ||
    characterNegativePrompts.any(String::isNotBlank)

private fun NaiImageMetadata.promptPreview(): String =
    (listOfNotNull(prompt) + characterPrompts).filter(String::isNotBlank).joinToString(", ")

private fun effectiveRange(filter: GalleryFilterState, bounds: GalleryDateBounds): Pair<Long, Long> {
    val oldest = bounds.oldest ?: 0L
    val newest = bounds.newest ?: System.currentTimeMillis()
    return when (filter.period) {
        GalleryPeriod.ALL -> oldest to newest
        GalleryPeriod.CUSTOM -> filter.customStartAt.coerceAtLeast(oldest) to filter.customEndAt.coerceAtMost(newest)
        GalleryPeriod.MONTH -> Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.timeInMillis.coerceAtLeast(oldest) to newest
        GalleryPeriod.YEAR -> Calendar.getInstance().apply { add(Calendar.YEAR, -1) }.timeInMillis.coerceAtLeast(oldest) to newest
    }
}
private fun shortDate(timestamp: Long): String = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(timestamp))
private fun dayKey(timestamp: Long): String = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date(timestamp))
@Composable private fun dayLabel(timestamp: Long): String {
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    if (sameDay(target, today)) return stringResource(R.string.gallery_today)
    today.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay(target, today)) return stringResource(R.string.gallery_yesterday)
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
}
private fun sameDay(a: Calendar, b: Calendar) = a.get(Calendar.ERA) == b.get(Calendar.ERA) && a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
