package com.hjhsys.naiblockprompt.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import android.widget.Toast
import coil3.compose.AsyncImage
import com.hjhsys.naiblockprompt.R
import com.hjhsys.naiblockprompt.data.library.*
import com.hjhsys.naiblockprompt.data.local.entity.SavedBlockEntity
import com.hjhsys.naiblockprompt.data.local.entity.SavedFolderEntity
import com.hjhsys.naiblockprompt.data.local.entity.SavedFolderKind
import com.hjhsys.naiblockprompt.ui.MainViewModel
import com.hjhsys.naiblockprompt.ui.RestoreOptions
import com.hjhsys.naiblockprompt.ui.components.AppTitleBar
import com.hjhsys.naiblockprompt.ui.components.AppTitleMenuItem
import com.hjhsys.naiblockprompt.ui.components.quotaStatusText
import com.hjhsys.naiblockprompt.ui.generate.compactModelName
import com.hjhsys.naiblockprompt.ui.components.ImageCardActions
import com.hjhsys.naiblockprompt.ui.components.ImageViewer
import com.hjhsys.naiblockprompt.ui.components.ImageCompareCandidate
import com.hjhsys.naiblockprompt.ui.components.FolderChoice
import com.hjhsys.naiblockprompt.ui.components.FolderSelectionDialog
import com.hjhsys.naiblockprompt.domain.editor.BaseSetImportSelection
import com.hjhsys.naiblockprompt.domain.editor.PromptOwner
import com.hjhsys.naiblockprompt.domain.editor.PromptCherryPick
import com.hjhsys.naiblockprompt.domain.editor.PromptCherryPickDraft
import com.hjhsys.naiblockprompt.domain.editor.CharacterImportMode
import com.hjhsys.naiblockprompt.ui.generate.PromptCherryPickDialog
import com.hjhsys.naiblockprompt.ui.generate.ProcessedPromptDialog
import com.hjhsys.naiblockprompt.ui.generate.PromptCompareCandidate
import com.hjhsys.naiblockprompt.domain.generation.SeedSelection
import com.hjhsys.naiblockprompt.domain.model.SavedSetKind
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(viewModel: MainViewModel, onOpenSettings: () -> Unit, isActive: Boolean = true, onRestored: () -> Unit, headerTokenSummary: String? = null) {
    var showGallery by rememberSaveable { mutableStateOf(false) }
    if (showGallery) {
        GeneratedGalleryScreen(
            viewModel = viewModel,
            onBack = { showGallery = false },
            onImported = { showGallery = false; onRestored() },
        )
        return
    }
    val history by viewModel.history.collectAsState()
    val subscriptionStatus by viewModel.subscriptionStatus.collectAsState()
    val session by viewModel.session.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val modelSummary = compactModelName(session?.generationSettings?.modelId)
    val seedSummary = session?.let { stringResource(if (it.generationSettings.seedMode == com.hjhsys.naiblockprompt.domain.model.SeedMode.RANDOM) R.string.seed_status_random else R.string.seed_status_fixed) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    val allHistoryListState = rememberLazyListState()
    val favoriteHistoryListState = rememberLazyListState()
    LaunchedEffect(isActive) {
        if (!isActive) {
            viewModel.enforceHistoryLimit()
        }
    }
    var restore by remember { mutableStateOf<HistoryItem?>(null) }
    var cherryPick by remember { mutableStateOf<PromptCherryPickDraft?>(null) }
    var promptToView by remember { mutableStateOf<HistoryItem?>(null) }
    var viewerReference by remember { mutableStateOf<String?>(null) }
    var initialImageComparison by remember { mutableStateOf<ImageCompareCandidate?>(null) }
    var initialPromptComparison by remember { mutableStateOf<PromptCompareCandidate?>(null) }
    var actionImage by remember { mutableStateOf<HistoryItem?>(null) }
    var deleteCandidate by remember { mutableStateOf<HistoryItem?>(null) }
    var deleteOriginal by remember { mutableStateOf(false) }
    var deleteFailed by remember { mutableStateOf(false) }
    actionImage?.let { item -> com.hjhsys.naiblockprompt.ui.components.ImageActionsDialog(item.entity.imagePath, viewModel) { actionImage = null } }
    restore?.let { item ->
        RestoreDialog(
            item = item,
            initialOptions = RestoreOptions.from(settings.promptImportPreferences),
            dismiss = { restore = null },
        ) { options -> viewModel.restoreHistory(item, options); restore = null; onRestored() }
    }
    cherryPick?.let { draft -> session?.let { current ->
        PromptCherryPickDialog(current, draft, dismiss = { cherryPick = null }) { blocks, selected, destination ->
            viewModel.appendCherryPickedSelection(blocks, selected, destination)
        }
    } }
    promptToView?.let { selectedItem ->
        selectedItem.snapshot?.generation?.let { prompt ->
            ProcessedPromptDialog(
                prompt = prompt,
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
                    viewerReference = selectedItem.entity.imagePath
                    promptToView = null
                },
                comparisonCandidates = history.mapNotNull { item ->
                    val candidatePrompt = item.snapshot?.generation ?: return@mapNotNull null
                    if (item.entity.id == selectedItem.entity.id) return@mapNotNull null
                    PromptCompareCandidate(
                        id = item.entity.id,
                        prompt = candidatePrompt,
                        imageReference = item.entity.imagePath,
                        imageAvailable = item.originalExists,
                        thumbnailPath = item.entity.thumbnailPath,
                        dateLabel = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.entity.createdAt)),
                        modelLabel = item.entity.model,
                        promptPreview = (listOf(candidatePrompt.basePositive) + candidatePrompt.characterPositive).filter(String::isNotBlank).joinToString(", "),
                    )
                },
            ) {
                promptToView = null
                initialPromptComparison = null
            }
        } ?: selectedItem.snapshot?.session?.let { SessionPromptDialog(it) { promptToView = null } }
    }
    viewerReference?.let { reference ->
        ImageViewer(
            reference = reference,
            initialComparison = initialImageComparison,
            onComparePrompts = { candidate ->
                val baseItem = history.firstOrNull { it.entity.imagePath == reference }
                val candidatePrompt = candidate.prompt
                if (baseItem != null && candidatePrompt != null) {
                    initialPromptComparison = PromptCompareCandidate(
                        id = candidate.id,
                        prompt = candidatePrompt,
                        imageReference = candidate.reference,
                        imageAvailable = true,
                        thumbnailPath = candidate.thumbnailReference,
                        dateLabel = candidate.dateLabel,
                        modelLabel = candidate.modelLabel,
                        promptPreview = candidate.promptPreview,
                    )
                    initialImageComparison = null
                    promptToView = baseItem
                    viewerReference = null
                }
            },
            comparisonCandidates = history.filter { item ->
                item.originalExists && item.entity.imagePath != reference
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
        ) {
            viewerReference = null
            initialImageComparison = null
        }
    }
    deleteCandidate?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.delete_history_title)) },
            text = { CheckRow(R.string.delete_history_original, deleteOriginal) { deleteOriginal = it } },
            confirmButton = { TextButton(onClick = {
                viewModel.deleteHistory(item.entity, deleteOriginal) { result ->
                    if (result == HistoryDeleteResult.OriginalDeleteFailed) deleteFailed = true
                }
                deleteCandidate = null
            }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (deleteFailed) AlertDialog(
        onDismissRequest = { deleteFailed = false },
        title = { Text(stringResource(R.string.delete_history_image_failed_title)) },
        text = { Text(stringResource(R.string.delete_history_image_failed)) },
        confirmButton = { TextButton(onClick = { deleteFailed = false }) { Text(stringResource(R.string.confirm)) } },
    )
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = { AppTitleBar(
        R.string.history_title,
        menuItems = listOf(AppTitleMenuItem(R.string.nav_settings, Icons.Default.Settings, onClick = onOpenSettings)),
        subtitle = seedSummary?.let { stringResource(R.string.generate_status_summary, modelSummary, it) },
        onSubtitleClick = if (session == null) null else viewModel::toggleSeedMode,
        trailingOverline = headerTokenSummary,
        trailingSubtitle = quotaStatusText(subscriptionStatus),
    ) }) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = !favoritesOnly,
                    onClick = { favoritesOnly = false },
                    label = { Text(stringResource(R.string.history_all)) },
                    modifier = Modifier.height(32.dp),
                )
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { favoritesOnly = true },
                    label = { Text(stringResource(R.string.history_favorites)) },
                    leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.height(32.dp),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showGallery = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Folder, stringResource(R.string.gallery_open), modifier = Modifier.size(20.dp))
                }
            }
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            state = if (favoritesOnly) favoriteHistoryListState else allHistoryListState,
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        val visibleHistory = if (favoritesOnly) {
            history.filter { it.entity.favorite }
        } else {
            val recordIds = HistoryRetentionPolicy.records(history.map { it.entity }, settings.historyLimit)
                .mapTo(hashSetOf()) { it.id }
            history.filter { it.entity.id in recordIds }
        }
        if (visibleHistory.isEmpty()) item { Text(stringResource(if (favoritesOnly) R.string.history_favorites_empty else R.string.history_empty)) }
        items(visibleHistory, key = { it.entity.id }) { item ->
            val actualSeed = item.snapshot?.generation?.usedSeed?.takeIf(SeedSelection::isValid)
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(File(item.entity.thumbnailPath), stringResource(R.string.generated_image), Modifier.size(104.dp).clickable(enabled = item.originalExists) {
                            viewModel.selectImageSeed(actualSeed)
                            initialImageComparison = null
                            viewerReference = item.entity.imagePath
                        }, contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.entity.model ?: "-", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                IconButton(onClick = {
                                    val favorite = !item.entity.favorite
                                    viewModel.setHistoryFavorite(item.entity, favorite)
                                }) {
                                    Icon(
                                        if (item.entity.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        stringResource(if (item.entity.favorite) R.string.remove_favorite else R.string.add_favorite),
                                        tint = if (item.entity.favorite) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                    )
                                }
                            }
                            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.entity.createdAt)), style = MaterialTheme.typography.bodySmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                item.snapshot?.generation?.let {
                                    Text(stringResource(R.string.used_seed, it.usedSeed), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { deleteOriginal = false; deleteCandidate = item }) {
                                    Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (!item.originalExists) Text(stringResource(R.string.original_missing), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    ImageCardActions(
                        imageActionsEnabled = item.originalExists,
                        informationEnabled = true,
                        onImageActions = {
                            viewModel.selectImageSeed(actualSeed)
                            actionImage = item
                        },
                        onImportInformation = { restore = item },
                        seedEnabled = actualSeed != null,
                        onApplySeed = { actualSeed?.let(viewModel::applyHistorySeed) },
                        promptSelectionEnabled = item.snapshot?.session != null,
                        onImportPromptSelection = item.snapshot?.session?.let { source ->
                            { cherryPick = PromptCherryPick.fromSession(source) }
                        },
                        promptViewEnabled = item.snapshot != null,
                        onViewPrompt = item.snapshot?.let { { initialPromptComparison = null; promptToView = item } },
                    )
                }
            }
        }
    } } }
}

@Composable
private fun SessionPromptDialog(session: com.hjhsys.naiblockprompt.domain.model.Session, dismiss: () -> Unit) {
    fun com.hjhsys.naiblockprompt.domain.model.PromptPair.text(positive: Boolean): String =
        (if (positive) positiveBlocks else negativeBlocks).joinToString("\n") { it.content }.ifEmpty { "—" }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.prompt_view_title)) },
        text = { SelectionContainer { Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PromptViewSection(stringResource(R.string.restore_base_positive), session.base.prompts.text(true))
            PromptViewSection(stringResource(R.string.restore_base_negative), session.base.prompts.text(false))
            session.characters.sortedBy { it.order }.forEachIndexed { index, character ->
                PromptViewSection("${stringResource(R.string.character_prompt, index + 1)} · ${stringResource(R.string.positive)}", character.prompts.text(true))
                PromptViewSection("${stringResource(R.string.character_prompt, index + 1)} · ${stringResource(R.string.negative)}", character.prompts.text(false))
            }
        } } },
        confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.confirm)) } },
    )
}

@Composable
private fun PromptViewSection(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small) {
            Text(value, Modifier.fillMaxWidth().padding(10.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun RestoreDialog(item: HistoryItem, initialOptions: RestoreOptions, dismiss: () -> Unit, confirm: (RestoreOptions) -> Unit) {
    var settings by rememberSaveable(item.entity.id, initialOptions) { mutableStateOf(initialOptions.settings) }
    var basePositive by rememberSaveable(item.entity.id, initialOptions) { mutableStateOf(initialOptions.basePositive) }
    var baseNegative by rememberSaveable(item.entity.id, initialOptions) { mutableStateOf(initialOptions.baseNegative) }
    var characters by rememberSaveable(item.entity.id, initialOptions) { mutableStateOf(initialOptions.characters) }
    var seed by rememberSaveable(item.entity.id, initialOptions) { mutableStateOf(initialOptions.seed) }
    var inputImage by rememberSaveable(item.entity.id, initialOptions) { mutableStateOf(initialOptions.inputImage) }
    var wildcardOriginal by rememberSaveable(item.entity.id, initialOptions) { mutableStateOf(initialOptions.wildcardOriginal) }
    var characterMode by rememberSaveable(item.entity.id, initialOptions) { mutableStateOf(initialOptions.characterMode) }
    val hasWildcard = item.snapshot?.session?.let { snapshot ->
        (snapshot.base.prompts.positiveBlocks + snapshot.base.prompts.negativeBlocks + snapshot.characters.flatMap { it.prompts.positiveBlocks + it.prompts.negativeBlocks }).any { "__" in it.content }
    } == true
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.load_from_history)) }, text = {
        Column {
            Text(stringResource(R.string.restore_stash_notice))
            CheckRow(R.string.restore_settings, settings) { settings = it }
            Text(stringResource(R.string.restore_base), style = MaterialTheme.typography.titleSmall)
            Column(Modifier.padding(start = 16.dp)) {
                CheckRow(R.string.restore_base_positive, basePositive) { basePositive = it }
                CheckRow(R.string.restore_base_negative, baseNegative) { baseNegative = it }
            }
            CheckRow(R.string.restore_characters, characters) { characters = it }
            if (characters) CharacterImportModePicker(characterMode) { characterMode = it }
            CheckRow(R.string.restore_seed, seed) { seed = it }
            if (hasWildcard) CheckRow(R.string.restore_wildcard_original, wildcardOriginal) { wildcardOriginal = it }
            if (item.snapshot?.session?.generationSettings?.imageInput != null) {
                val input = item.snapshot.session.generationSettings.imageInput
                val label = stringResource(when (input?.mode) {
                    com.hjhsys.naiblockprompt.domain.model.ImageInputMode.VIBE_TRANSFER -> R.string.restore_vibe_reference
                    com.hjhsys.naiblockprompt.domain.model.ImageInputMode.INPAINT -> R.string.inpaint_restore
                    com.hjhsys.naiblockprompt.domain.model.ImageInputMode.PRECISE_REFERENCE -> R.string.restore_precise_reference
                    else -> R.string.restore_image_to_image_input
                })
                if (item.inputImageExists) CheckRow(label, inputImage) { inputImage = it }
                else Text(stringResource(R.string.restore_input_image_missing_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }, confirmButton = { Button(onClick = { confirm(RestoreOptions(
        settings = settings,
        basePositive = basePositive,
        baseNegative = baseNegative,
        characters = characters,
        seed = seed,
        inputImage = inputImage,
        wildcardOriginal = wildcardOriginal,
        characterMode = characterMode,
    )) }, enabled = item.snapshot != null) { Text(stringResource(R.string.load)) } }, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
internal fun CharacterImportModePicker(mode: CharacterImportMode, onChange: (CharacterImportMode) -> Unit) {
    Column(Modifier.padding(start = 16.dp)) {
        Row(Modifier.fillMaxWidth().clickable { onChange(CharacterImportMode.REPLACE) }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(mode == CharacterImportMode.REPLACE, { onChange(CharacterImportMode.REPLACE) })
            Text(stringResource(R.string.character_import_replace))
        }
        Row(Modifier.fillMaxWidth().clickable { onChange(CharacterImportMode.APPEND) }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(mode == CharacterImportMode.APPEND, { onChange(CharacterImportMode.APPEND) })
            Text(stringResource(R.string.character_import_append))
        }
    }
}

@Composable private fun CheckRow(label: Int, checked: Boolean, change: (Boolean) -> Unit) = CheckRow(stringResource(label), checked, change)

@Composable private fun CheckRow(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { change(!checked) }, verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, change); Text(label) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedScreen(viewModel: MainViewModel, onOpenSettings: () -> Unit, onWorkflowFinished: () -> Unit) {
    val blocks by viewModel.savedBlocks.collectAsState(); val folders by viewModel.savedFolders.collectAsState(); val presets by viewModel.presets.collectAsState(); val sets by viewModel.savedSets.collectAsState()
    val history by viewModel.history.collectAsState()
    val workflow by viewModel.savedWorkflow.collectAsState()
    val pagerState = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val tab = pagerState.currentPage
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val resources = LocalResources.current
    var search by rememberSaveable { mutableStateOf("") }
    var searchScope by rememberSaveable { mutableStateOf(SavedSearchScope.TITLE_ONLY) }
    var searchMenuExpanded by remember { mutableStateOf(false) }
    var showLibraryHelp by remember { mutableStateOf(false) }
    var workflowName by rememberSaveable(workflow) { mutableStateOf(when (val active = workflow) { is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SaveBlock -> active.block.name; is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SavePreset -> "Preset"; else -> "" }) }
    var folderFilter by rememberSaveable { mutableStateOf("ALL") }
    var overwriteWorkflow by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(workflow) { if (workflow != null) pagerState.scrollToPage(when (workflow) { is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SaveSet, is com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadSet -> 1; is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SavePreset, com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadPreset -> 2; else -> 0 }) }
    var folderDialog by rememberSaveable { mutableStateOf(false) }
    var folderSelector by rememberSaveable { mutableStateOf(false) }
    var movingBlock by remember { mutableStateOf<com.hjhsys.naiblockprompt.data.local.entity.SavedBlockEntity?>(null) }
    var movingPreset by remember { mutableStateOf<com.hjhsys.naiblockprompt.data.local.entity.PresetEntity?>(null) }
    var movingSet by remember { mutableStateOf<com.hjhsys.naiblockprompt.data.local.entity.SavedSetEntity?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var bulkMove by remember { mutableStateOf(false) }
    var baseSetToLoad by remember { mutableStateOf<SavedSetItem?>(null) }
    var loadBasePositive by rememberSaveable { mutableStateOf(true) }
    var loadBaseNegative by rememberSaveable { mutableStateOf(true) }
    var thumbnailTarget by remember { mutableStateOf<LibraryThumbnailTarget?>(null) }
    if (showLibraryHelp) AlertDialog(
        onDismissRequest = { showLibraryHelp = false },
        title = { Text(stringResource(R.string.library_types_help_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LibraryTypeHelpSection(R.string.saved_blocks, R.string.library_block_help)
                LibraryTypeHelpSection(R.string.saved_sets, R.string.library_set_help)
                LibraryTypeHelpSection(R.string.presets, R.string.library_preset_help)
            }
        },
        confirmButton = { TextButton(onClick = { showLibraryHelp = false }) { Text(stringResource(R.string.confirm)) } },
    )
    thumbnailTarget?.let { target ->
        LibraryThumbnailDialog(
            target = target,
            generatedImages = history.filter { it.originalExists }.map { it.entity.thumbnailPath to it.entity.imagePath },
            onDismiss = { thumbnailTarget = null },
            onChooseThumbnail = { viewModel.setLibraryThumbnail(target.kind, target.id, target.thumbnailPath, it) },
            onUseGeneratedThumbnail = { viewModel.setLibraryThumbnailFromFile(target.kind, target.id, target.thumbnailPath, it) },
            onRemoveThumbnail = { viewModel.removeLibraryThumbnail(target.kind, target.id, target.thumbnailPath) },
        )
    }
    baseSetToLoad?.let { item ->
        AlertDialog(
            onDismissRequest = { baseSetToLoad = null },
            title = { Text(stringResource(R.string.load_set)) },
            text = {
                Column {
                    CheckRow(R.string.positive, loadBasePositive) { loadBasePositive = it }
                    CheckRow(R.string.negative, loadBaseNegative) { loadBaseNegative = it }
                }
            },
            confirmButton = {
                Button(
                    enabled = loadBasePositive || loadBaseNegative,
                    onClick = {
                        viewModel.finishSetLoad(
                            item,
                            BaseSetImportSelection(loadBasePositive, loadBaseNegative),
                        )
                        baseSetToLoad = null
                        onWorkflowFinished()
                    },
                ) { Text(stringResource(R.string.load)) }
            },
            dismissButton = {
                TextButton(onClick = { baseSetToLoad = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    val folderKind = when (workflow) {
        is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SaveBlock, is com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadBlock -> SavedFolderKind.BLOCK
        is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SaveSet, is com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadSet -> SavedFolderKind.SET
        is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SavePreset, com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadPreset -> SavedFolderKind.PRESET
        else -> when (tab) { 0 -> SavedFolderKind.BLOCK; 1 -> SavedFolderKind.SET; else -> SavedFolderKind.PRESET }
    }
    val visibleFolders = folders.filter { it.kind == folderKind.name }
    val isSaveWorkflow = workflow is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SaveBlock ||
        workflow is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SaveSet ||
        workflow is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SavePreset
    val selectedSaveFolderId = folderFilter.takeUnless { it == "ALL" || it == "NONE" }
    LaunchedEffect(folderKind, workflow) {
        folderFilter = if (isSaveWorkflow) "NONE" else "ALL"
        selectionMode = false
        selectedIds = emptySet()
    }
    if (folderDialog) NameDialog(R.string.new_folder, { folderDialog = false }) { viewModel.createFolder(it, folderKind); folderDialog = false }
    val assignmentRootLabel = stringResource(when (folderKind) {
        SavedFolderKind.BLOCK -> R.string.library_root_blocks
        SavedFolderKind.SET -> R.string.library_root_sets
        SavedFolderKind.PRESET -> R.string.library_root_presets
        SavedFolderKind.WILDCARD -> R.string.wildcards_tab
    })
    val folderItemCounts = when (folderKind) {
        SavedFolderKind.BLOCK -> blocks.mapNotNull { it.folderId }
        SavedFolderKind.SET -> sets.mapNotNull { it.entity.folderId }
        SavedFolderKind.PRESET -> presets.mapNotNull { it.entity.folderId }
        SavedFolderKind.WILDCARD -> emptyList()
    }.groupingBy { it }.eachCount()
    val assignmentFolders = visibleFolders.map { FolderChoice(it.id, it.name, folderItemCounts[it.id] ?: 0) }
    if (folderSelector) {
        FolderSelectionDialog(
            rootLabel = assignmentRootLabel,
            folders = assignmentFolders,
            selectedId = folderFilter.takeUnless { it == "ALL" || it == "NONE" },
            onSelect = { folderFilter = it ?: "NONE"; folderSelector = false },
            onCreateFolder = { name -> viewModel.createFolder(name, folderKind) { folder -> folderFilter = folder.id; folderSelector = false } },
            onDismiss = { folderSelector = false },
            showAll = !isSaveWorkflow,
            allSelected = folderFilter == "ALL",
            onSelectAll = { folderFilter = "ALL"; folderSelector = false },
            onDeleteFolder = { choice ->
                visibleFolders.firstOrNull { it.id == choice.id }?.let(viewModel::deleteFolder)
                if (folderFilter == choice.id) folderFilter = if (isSaveWorkflow) "NONE" else "ALL"
            },
            onRenameFolder = { choice, name -> visibleFolders.firstOrNull { it.id == choice.id }?.let { viewModel.renameFolder(it, name) } },
            noFolderSelected = folderFilter == "NONE",
        )
    }
    if (overwriteWorkflow) AlertDialog(onDismissRequest = { overwriteWorkflow = false }, title = { Text(stringResource(R.string.overwrite_title)) }, text = { Text(stringResource(R.string.overwrite_message, workflowName.trim())) }, confirmButton = { Button(onClick = { overwriteWorkflow = false; when (workflow) { is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SavePreset -> viewModel.finishPresetSave(workflowName, selectedSaveFolderId); is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SaveSet -> viewModel.finishSetSave(workflowName, selectedSaveFolderId); else -> viewModel.finishBlockSave(workflowName, selectedSaveFolderId) }; onWorkflowFinished() }) { Text(stringResource(R.string.overwrite)) } }, dismissButton = { TextButton(onClick = { overwriteWorkflow = false }) { Text(stringResource(R.string.cancel)) } })
    movingBlock?.let { block ->
        FolderSelectionDialog(
            rootLabel = assignmentRootLabel,
            folders = assignmentFolders,
            selectedId = block.folderId,
            onSelect = { viewModel.moveSavedBlock(block, it); movingBlock = null },
            onCreateFolder = { name -> viewModel.createFolder(name, folderKind) { folder -> viewModel.moveSavedBlock(block, folder.id); movingBlock = null } },
            onDismiss = { movingBlock = null },
            onDeleteFolder = { choice -> visibleFolders.firstOrNull { it.id == choice.id }?.let(viewModel::deleteFolder) },
            onRenameFolder = { choice, name -> visibleFolders.firstOrNull { it.id == choice.id }?.let { viewModel.renameFolder(it, name) } },
        )
    }
    movingPreset?.let { preset ->
        FolderSelectionDialog(
            rootLabel = assignmentRootLabel,
            folders = assignmentFolders,
            selectedId = preset.folderId,
            onSelect = { viewModel.movePreset(preset, it); movingPreset = null },
            onCreateFolder = { name -> viewModel.createFolder(name, folderKind) { folder -> viewModel.movePreset(preset, folder.id); movingPreset = null } },
            onDismiss = { movingPreset = null },
            onDeleteFolder = { choice -> visibleFolders.firstOrNull { it.id == choice.id }?.let(viewModel::deleteFolder) },
            onRenameFolder = { choice, name -> visibleFolders.firstOrNull { it.id == choice.id }?.let { viewModel.renameFolder(it, name) } },
        )
    }
    movingSet?.let { set ->
        FolderSelectionDialog(
            rootLabel = assignmentRootLabel,
            folders = assignmentFolders,
            selectedId = set.folderId,
            onSelect = { viewModel.moveSavedSet(set, it); movingSet = null },
            onCreateFolder = { name -> viewModel.createFolder(name, folderKind) { folder -> viewModel.moveSavedSet(set, folder.id); movingSet = null } },
            onDismiss = { movingSet = null },
            onDeleteFolder = { choice -> visibleFolders.firstOrNull { it.id == choice.id }?.let(viewModel::deleteFolder) },
            onRenameFolder = { choice, name -> visibleFolders.firstOrNull { it.id == choice.id }?.let { viewModel.renameFolder(it, name) } },
        )
    }
    if (bulkMove) {
        fun moveSelected(folderId: String?) {
            when (tab) {
                0 -> viewModel.moveSavedBlocks(blocks.filter { it.id in selectedIds }, folderId)
                1 -> viewModel.moveSavedSets(sets.map { it.entity }.filter { it.id in selectedIds }, folderId)
                else -> viewModel.movePresets(presets.map { it.entity }.filter { it.id in selectedIds }, folderId)
            }
            bulkMove = false
            selectionMode = false
            selectedIds = emptySet()
        }
        FolderSelectionDialog(
            rootLabel = assignmentRootLabel,
            folders = assignmentFolders,
            selectedId = null,
            onSelect = ::moveSelected,
            onCreateFolder = { name -> viewModel.createFolder(name, folderKind) { folder -> moveSelected(folder.id) } },
            onDismiss = { bulkMove = false },
            onDeleteFolder = { choice -> visibleFolders.firstOrNull { it.id == choice.id }?.let(viewModel::deleteFolder) },
            onRenameFolder = { choice, name -> visibleFolders.firstOrNull { it.id == choice.id }?.let { viewModel.renameFolder(it, name) } },
        )
    }
    val submitSave = {
        when (val active = workflow) {
            is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SaveBlock -> {
                if (blocks.any { it.name.equals(workflowName.trim(), true) }) overwriteWorkflow = true
                else { viewModel.finishBlockSave(workflowName, selectedSaveFolderId); onWorkflowFinished() }
            }
            is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SavePreset -> {
                if (presets.any { it.entity.name.equals(workflowName.trim(), true) }) overwriteWorkflow = true
                else { viewModel.finishPresetSave(workflowName, selectedSaveFolderId); onWorkflowFinished() }
            }
            is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SaveSet -> {
                if (sets.any { it.entity.name.equals(workflowName.trim(), true) && it.entity.kind == active.set.kind.name }) overwriteWorkflow = true
                else { viewModel.finishSetSave(workflowName, selectedSaveFolderId); onWorkflowFinished() }
            }
            else -> Unit
        }
    }
    Scaffold(
        topBar = {
            if (workflow == null) AppTitleBar(
                R.string.saved_title,
                menuItems = listOf(
                    AppTitleMenuItem(R.string.nav_settings, Icons.Default.Settings, onClick = onOpenSettings),
                    AppTitleMenuItem(R.string.library_types_help_title, Icons.Default.HelpOutline, onClick = { showLibraryHelp = true }),
                ),
            )
            else if (isSaveWorkflow) AppTitleBar(
                when (workflow) {
                    is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SaveBlock -> R.string.save_block_mode
                    is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SavePreset -> R.string.save_preset_mode
                    else -> R.string.save_set
                },
                directAction = AppTitleMenuItem(R.string.cancel, Icons.Default.Close, onClick = { viewModel.cancelSavedWorkflow(); onWorkflowFinished() }, showLabel = true),
            )
        },
    ) { screenPadding ->
    Column(
        Modifier.fillMaxSize().padding(screenPadding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        workflow?.takeUnless { isSaveWorkflow }?.let {
            ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(when (it) { is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SaveBlock -> R.string.save_block_mode; is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SavePreset -> R.string.save_preset_mode; is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SaveSet -> R.string.save_set; is com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadSet -> R.string.load_set; com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadPreset -> R.string.load_preset; else -> R.string.load_block_mode }), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); TextButton(onClick = { viewModel.cancelSavedWorkflow(); onWorkflowFinished() }) { Text(stringResource(R.string.cancel)) } }
                Text(stringResource(R.string.choose_block_to_load), style = MaterialTheme.typography.bodySmall)
            } }
        }
        if (workflow == null) PrimaryTabRow(selectedTabIndex = tab) { Tab(tab == 0, { scope.launch { pagerState.animateScrollToPage(0) } }, text = { Text(stringResource(R.string.saved_blocks)) }); Tab(tab == 1, { scope.launch { pagerState.animateScrollToPage(1) } }, text = { Text(stringResource(R.string.saved_sets)) }); Tab(tab == 2, { scope.launch { pagerState.animateScrollToPage(2) } }, text = { Text(stringResource(R.string.presets)) }) }
        OutlinedTextField(
            search,
            { search = it },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.search)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                Box {
                    IconButton(onClick = { searchMenuExpanded = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.search_options)) }
                    DropdownMenu(searchMenuExpanded, { searchMenuExpanded = false }) {
                        SavedSearchScope.entries.forEach { scope ->
                            DropdownMenuItem(
                                text = { Text(stringResource(scope.label)) },
                                leadingIcon = { if (searchScope == scope) Icon(Icons.Default.Check, null) },
                                onClick = { searchScope = scope; searchMenuExpanded = false },
                            )
                        }
                    }
                }
            },
            supportingText = { Text(stringResource(searchScope.label)) },
            singleLine = true,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { folderSelector = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.FolderOpen, null)
                Spacer(Modifier.width(6.dp))
                Text(when (folderFilter) { "ALL" -> assignmentRootLabel; "NONE" -> stringResource(R.string.no_folder); else -> visibleFolders.firstOrNull { it.id == folderFilter }?.name ?: assignmentRootLabel }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, null)
            }
            IconButton(onClick = { folderDialog = true }) { Icon(Icons.Default.CreateNewFolder, stringResource(R.string.new_folder)) }
            if (workflow == null) IconButton(onClick = { selectionMode = !selectionMode; selectedIds = emptySet() }) { Icon(if (selectionMode) Icons.Default.Close else Icons.Default.Checklist, stringResource(if (selectionMode) R.string.cancel else R.string.select_items)) }
        }
        if (isSaveWorkflow) {
            OutlinedTextField(
                workflowName,
                { workflowName = it },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (workflowName.isNotBlank()) submitSave() }),
            )
            Button(onClick = submitSave, enabled = workflowName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
        }
        if (selectionMode && workflow == null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.selected_count, selectedIds.size), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Button(onClick = { bulkMove = true }, enabled = selectedIds.isNotEmpty()) { Icon(Icons.Default.DriveFileMove, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.move_selected)) }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f), userScrollEnabled = workflow == null) { page ->
        if (page == 0) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val filtered = blocks.filter { (search.isBlank() || it.name.contains(search, true) || (searchScope == SavedSearchScope.INCLUDE_CONTENT && it.content.contains(search, true))) && (folderFilter == "ALL" || (folderFilter == "NONE" && it.folderId == null) || it.folderId == folderFilter) }
                if (filtered.isEmpty()) item { Text(stringResource(R.string.saved_empty)) }
                items(filtered, key = { it.id }) { block ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (selectionMode) Checkbox(block.id in selectedIds, { checked -> selectedIds = if (checked) selectedIds + block.id else selectedIds - block.id })
                                LibraryItemThumbnail(block.thumbnailPath) { thumbnailTarget = LibraryThumbnailTarget(SavedFolderKind.BLOCK, block.id, block.name, block.thumbnailPath) }
                                Column(Modifier.weight(1f)) {
                                    Text(block.name, style = MaterialTheme.typography.titleMedium)
                                    Text(block.content.ifBlank { stringResource(R.string.empty_prompt) }, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Row {
                                if (workflow is com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadBlock) Button(onClick = { viewModel.finishBlockLoad(block); onWorkflowFinished() }) { Text(stringResource(R.string.load)) }
                                else TextButton(onClick = { clipboard.setText(AnnotatedString(block.content)); Toast.makeText(context, resources.getString(R.string.copied), Toast.LENGTH_SHORT).show() }) { Text(stringResource(R.string.copy)) }
                                TextButton(onClick = { movingBlock = block }) { Text(visibleFolders.firstOrNull { it.id == block.folderId }?.name ?: stringResource(R.string.no_folder)) }
                                IconButton(onClick = { viewModel.deleteSavedBlock(block) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                            }
                        }
                    }
                }
            }
        } else if (page == 1) LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val activeLoad = workflow as? com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadSet
            val kind = activeLoad?.owner?.let { if (it is PromptOwner.Base) SavedSetKind.BASE else SavedSetKind.CHARACTER }
            val filtered = sets.filter { item -> (kind == null || item.set?.kind == kind) && (search.isBlank() || item.entity.name.contains(search, true) || (searchScope == SavedSearchScope.INCLUDE_CONTENT && (item.set?.prompts?.allBlocks()?.any { it.name.contains(search, true) || it.content.contains(search, true) } == true || item.set?.textRendering?.content?.contains(search, true) == true))) && (folderFilter == "ALL" || (folderFilter == "NONE" && item.entity.folderId == null) || item.entity.folderId == folderFilter) }
            if (filtered.isEmpty()) item { Text(stringResource(R.string.saved_empty)) }
            items(filtered, key = { it.entity.id }) { set ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (selectionMode) Checkbox(set.entity.id in selectedIds, { checked -> selectedIds = if (checked) selectedIds + set.entity.id else selectedIds - set.entity.id })
                            LibraryItemThumbnail(set.entity.thumbnailPath) { thumbnailTarget = LibraryThumbnailTarget(SavedFolderKind.SET, set.entity.id, set.entity.name, set.entity.thumbnailPath) }
                            Column(Modifier.weight(1f)) { Text(set.entity.name); Text(set.entity.kind, style = MaterialTheme.typography.bodySmall) }
                        }
                        Row {
                            if (activeLoad != null) Button(onClick = {
                                if (activeLoad.owner is PromptOwner.Base) {
                                    loadBasePositive = true
                                    loadBaseNegative = true
                                    baseSetToLoad = set
                                } else {
                                    viewModel.finishSetLoad(set)
                                    onWorkflowFinished()
                                }
                            }) { Text(stringResource(R.string.load)) }
                            TextButton(onClick = { movingSet = set.entity }) { Text(visibleFolders.firstOrNull { it.id == set.entity.folderId }?.name ?: stringResource(R.string.no_folder)) }
                            IconButton(onClick = { viewModel.deleteSavedSet(set.entity) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        } else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (presets.isEmpty()) item { Text(stringResource(R.string.saved_empty)) }
            items(presets.filter { item -> (search.isBlank() || item.entity.name.contains(search, true) || (searchScope == SavedSearchScope.INCLUDE_CONTENT && item.session?.containsPromptText(search) == true)) && (folderFilter == "ALL" || (folderFilter == "NONE" && item.entity.folderId == null) || item.entity.folderId == folderFilter) }, key = { it.entity.id }) { preset ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (selectionMode) Checkbox(preset.entity.id in selectedIds, { checked -> selectedIds = if (checked) selectedIds + preset.entity.id else selectedIds - preset.entity.id })
                            LibraryItemThumbnail(preset.entity.thumbnailPath) { thumbnailTarget = LibraryThumbnailTarget(SavedFolderKind.PRESET, preset.entity.id, preset.entity.name, preset.entity.thumbnailPath) }
                            Column(Modifier.weight(1f)) { Text(preset.entity.name, style = MaterialTheme.typography.titleMedium); Text(preset.session?.generationSettings?.modelId ?: "-", style = MaterialTheme.typography.bodySmall) }
                        }
                        Row {
                            if (workflow == com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadPreset) Button(onClick = { viewModel.restorePreset(preset); viewModel.cancelSavedWorkflow(); onWorkflowFinished() }, enabled = preset.session != null) { Text(stringResource(R.string.load)) }
                            TextButton(onClick = { movingPreset = preset.entity }) { Text(visibleFolders.firstOrNull { it.id == preset.entity.folderId }?.name ?: stringResource(R.string.no_folder)) }
                            IconButton(onClick = { viewModel.deletePreset(preset.entity) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        }
        }
    } }
}

@Composable
private fun LibraryTypeHelpSection(@StringRes title: Int, @StringRes body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private enum class SavedSearchScope(@param:StringRes val label: Int) {
    TITLE_ONLY(R.string.search_titles_only),
    INCLUDE_CONTENT(R.string.search_include_content),
}

private data class LibraryThumbnailTarget(
    val kind: SavedFolderKind,
    val id: String,
    val name: String,
    val thumbnailPath: String?,
)

@Composable
private fun LibraryItemThumbnail(path: String?, onClick: () -> Unit) {
    val image = path?.let(::File)?.takeIf(File::isFile)
    Surface(
        modifier = Modifier.size(72.dp).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        if (image != null) {
            AsyncImage(image, stringResource(R.string.library_preview_image), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AddPhotoAlternate, stringResource(R.string.set_preview_image), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun LibraryThumbnailDialog(
    target: LibraryThumbnailTarget,
    generatedImages: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onChooseThumbnail: (android.net.Uri) -> Unit,
    onUseGeneratedThumbnail: (String) -> Unit,
    onRemoveThumbnail: () -> Unit,
) {
    var previewModel by remember(target.id, target.thumbnailPath) { mutableStateOf<Any?>(target.thumbnailPath?.let(::File)?.takeIf(File::isFile)) }
    var showGeneratedImages by rememberSaveable(target.id) { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            previewModel = it
            onChooseThumbnail(it)
        }
    }
    if (showGeneratedImages) AlertDialog(
        onDismissRequest = { showGeneratedImages = false },
        title = { Text(stringResource(R.string.choose_generated_image)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(generatedImages) { (thumbnail, original) ->
                    AsyncImage(
                        model = File(thumbnail),
                        contentDescription = stringResource(R.string.generated_image),
                        modifier = Modifier.fillMaxWidth().height(150.dp).clickable {
                            previewModel = File(thumbnail)
                            onUseGeneratedThumbnail(original)
                            showGeneratedImages = false
                        },
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { showGeneratedImages = false }) { Text(stringResource(R.string.cancel)) } },
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preview_image_for, target.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (previewModel != null) AsyncImage(previewModel, stringResource(R.string.library_preview_image), Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Crop)
                else Surface(Modifier.fillMaxWidth().height(180.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Image, stringResource(R.string.no_preview_image), Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.image_source_file))
                    }
                    if (generatedImages.isNotEmpty()) FilledTonalButton(onClick = { showGeneratedImages = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.History, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.image_source_history))
                    }
                }
                if (previewModel != null) TextButton(onClick = { previewModel = null; onRemoveThumbnail() }) { Text(stringResource(R.string.remove_image)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

private fun com.hjhsys.naiblockprompt.domain.model.PromptPair.allBlocks() = positiveBlocks + negativeBlocks

private fun com.hjhsys.naiblockprompt.domain.model.Session.containsPromptText(query: String): Boolean {
    val baseMatches = base.prompts.allBlocks().any { it.name.contains(query, true) || it.content.contains(query, true) } ||
        base.textRendering.content.contains(query, true)
    return baseMatches || characters.any { character ->
        character.prompts.allBlocks().any { it.name.contains(query, true) || it.content.contains(query, true) } ||
            character.textRendering.content.contains(query, true)
    }
}

@Composable
fun NameDialog(title: Int, dismiss: () -> Unit, initialName: String = "", existingNames: List<String> = emptyList(), confirm: (String) -> Unit) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var confirmOverwrite by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val submit = { if (existingNames.any { it.equals(name.trim(), true) }) confirmOverwrite = true else confirm(name) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(title)) }, text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focusRequester), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); if (name.isNotBlank()) submit() })) }, confirmButton = { Button(onClick = submit, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
    if (confirmOverwrite) AlertDialog(onDismissRequest = { confirmOverwrite = false }, title = { Text(stringResource(R.string.overwrite_title)) }, text = { Text(stringResource(R.string.overwrite_message, name.trim())) }, confirmButton = { Button(onClick = { confirmOverwrite = false; confirm(name) }) { Text(stringResource(R.string.overwrite)) } }, dismissButton = { TextButton(onClick = { confirmOverwrite = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun SaveBlockDialog(
    initialName: String,
    blocks: List<SavedBlockEntity>,
    folders: List<SavedFolderEntity>,
    dismiss: () -> Unit,
    confirm: (String, String?) -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var folderId by rememberSaveable { mutableStateOf<String?>(null) }
    var overwrite by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val save = { if (blocks.any { it.name.equals(name.trim(), true) }) overwrite = true else confirm(name, folderId) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.save_block)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.save_location), style = MaterialTheme.typography.labelLarge)
            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                item { FilterChip(folderId == null, { folderId = null }, { Text(stringResource(R.string.no_folder)) }) }
                items(folders, key = { it.id }) { folder -> FilterChip(folderId == folder.id, { folderId = folder.id }, { Text(folder.name, maxLines = 1) }) }
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().focusRequester(focusRequester), label = { Text(stringResource(R.string.name)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) save() }))
        }
    }, confirmButton = { Button(onClick = save, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
    if (overwrite) AlertDialog(onDismissRequest = { overwrite = false }, title = { Text(stringResource(R.string.overwrite_title)) }, text = { Text(stringResource(R.string.overwrite_message, name.trim())) }, confirmButton = { Button(onClick = { overwrite = false; confirm(name, folderId) }) { Text(stringResource(R.string.overwrite)) } }, dismissButton = { TextButton(onClick = { overwrite = false }) { Text(stringResource(R.string.cancel)) } })
}
