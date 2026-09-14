package com.hjhsys.naiblockprompt.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hjhsys.naiblockprompt.R
import com.hjhsys.naiblockprompt.data.library.*
import com.hjhsys.naiblockprompt.data.local.entity.SavedBlockEntity
import com.hjhsys.naiblockprompt.data.local.entity.SavedFolderEntity
import com.hjhsys.naiblockprompt.ui.MainViewModel
import com.hjhsys.naiblockprompt.ui.RestoreOptions
import com.hjhsys.naiblockprompt.ui.components.AppTitleBar
import com.hjhsys.naiblockprompt.ui.components.AppTitleMenuItem
import com.hjhsys.naiblockprompt.ui.components.quotaStatusText
import com.hjhsys.naiblockprompt.ui.generate.compactModelName
import com.hjhsys.naiblockprompt.ui.components.ImageCardActions
import com.hjhsys.naiblockprompt.ui.components.ImageViewer
import com.hjhsys.naiblockprompt.domain.editor.BaseSetImportSelection
import com.hjhsys.naiblockprompt.domain.editor.PromptOwner
import com.hjhsys.naiblockprompt.domain.editor.PromptCherryPick
import com.hjhsys.naiblockprompt.domain.editor.PromptCherryPickDraft
import com.hjhsys.naiblockprompt.ui.generate.PromptCherryPickDialog
import com.hjhsys.naiblockprompt.domain.generation.SeedSelection
import com.hjhsys.naiblockprompt.domain.model.SavedSetKind
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(viewModel: MainViewModel, onOpenSettings: () -> Unit, isActive: Boolean = true, onRestored: () -> Unit, headerTokenSummary: String? = null) {
    val history by viewModel.history.collectAsState()
    val subscriptionStatus by viewModel.subscriptionStatus.collectAsState()
    val session by viewModel.session.collectAsState()
    val modelSummary = compactModelName(session?.generationSettings?.modelId)
    val seedSummary = session?.let { stringResource(if (it.generationSettings.seedMode == com.hjhsys.naiblockprompt.domain.model.SeedMode.RANDOM) R.string.seed_status_random else R.string.seed_status_fixed) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    val retainedUnfavorites = remember { mutableStateListOf<String>() }
    LaunchedEffect(isActive) {
        if (!isActive) {
            retainedUnfavorites.clear()
            viewModel.enforceHistoryLimit()
        }
    }
    var restore by remember { mutableStateOf<HistoryItem?>(null) }
    var cherryPick by remember { mutableStateOf<PromptCherryPickDraft?>(null) }
    var viewerReference by remember { mutableStateOf<String?>(null) }
    var actionImage by remember { mutableStateOf<HistoryItem?>(null) }
    actionImage?.let { item -> com.hjhsys.naiblockprompt.ui.components.ImageActionsDialog(item.entity.imagePath, viewModel) { actionImage = null } }
    restore?.let { item -> RestoreDialog(item, { restore = null }) { options -> viewModel.restoreHistory(item, options); restore = null; onRestored() } }
    cherryPick?.let { draft -> session?.let { current ->
        PromptCherryPickDialog(current, draft, dismiss = { cherryPick = null }) { blocks, selected, destination ->
            viewModel.appendCherryPickedSelection(blocks, selected, destination)
        }
    } }
    viewerReference?.let { reference -> ImageViewer(reference) { viewerReference = null } }
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = { AppTitleBar(
        R.string.history_title,
        menuItems = listOf(AppTitleMenuItem(R.string.nav_settings, Icons.Default.Settings, onClick = onOpenSettings)),
        subtitle = seedSummary?.let { stringResource(R.string.generate_status_summary, modelSummary, it) },
        onSubtitleClick = if (session == null) null else viewModel::toggleSeedMode,
        trailingOverline = headerTokenSummary,
        trailingSubtitle = quotaStatusText(subscriptionStatus),
    ) }) { padding ->
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !favoritesOnly, onClick = { favoritesOnly = false }, label = { Text(stringResource(R.string.history_all)) })
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { favoritesOnly = true },
                    label = { Text(stringResource(R.string.history_favorites)) },
                    leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        val visibleHistory = if (favoritesOnly) history.filter { it.entity.favorite || it.entity.id in retainedUnfavorites } else history
        if (visibleHistory.isEmpty()) item { Text(stringResource(if (favoritesOnly) R.string.history_favorites_empty else R.string.history_empty)) }
        items(visibleHistory, key = { it.entity.id }) { item ->
            val actualSeed = item.snapshot?.generation?.usedSeed?.takeIf(SeedSelection::isValid)
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(File(item.entity.thumbnailPath), stringResource(R.string.generated_image), Modifier.size(104.dp).clickable(enabled = item.originalExists) {
                            viewModel.selectImageSeed(actualSeed)
                            viewerReference = item.entity.imagePath
                        }, contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.entity.model ?: "-", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                IconButton(onClick = {
                                    val favorite = !item.entity.favorite
                                    if (!favorite && favoritesOnly) retainedUnfavorites.add(item.entity.id) else retainedUnfavorites.remove(item.entity.id)
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
                                IconButton(onClick = { viewModel.deleteHistory(item.entity) }) {
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
                    )
                }
            }
        }
    } }
}

@Composable
internal fun RestoreDialog(item: HistoryItem, dismiss: () -> Unit, confirm: (RestoreOptions) -> Unit) {
    var settings by rememberSaveable { mutableStateOf(true) }
    var basePositive by rememberSaveable { mutableStateOf(true) }
    var baseNegative by rememberSaveable { mutableStateOf(true) }
    var characters by rememberSaveable { mutableStateOf(true) }; var seed by rememberSaveable { mutableStateOf(true) }
    var inputImage by rememberSaveable { mutableStateOf(false) }
    var wildcardOriginal by rememberSaveable { mutableStateOf(true) }
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
            CheckRow(R.string.restore_characters, characters) { characters = it }; CheckRow(R.string.restore_seed, seed) { seed = it }
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
    )) }, enabled = item.snapshot != null) { Text(stringResource(R.string.load)) } }, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun CheckRow(label: Int, checked: Boolean, change: (Boolean) -> Unit) = CheckRow(stringResource(label), checked, change)

@Composable private fun CheckRow(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { change(!checked) }, verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, change); Text(label) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedScreen(viewModel: MainViewModel, onOpenSettings: () -> Unit, onWorkflowFinished: () -> Unit) {
    val blocks by viewModel.savedBlocks.collectAsState(); val folders by viewModel.savedFolders.collectAsState(); val presets by viewModel.presets.collectAsState(); val sets by viewModel.savedSets.collectAsState()
    val workflow by viewModel.savedWorkflow.collectAsState()
    val pagerState = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val tab = pagerState.currentPage
    var search by rememberSaveable { mutableStateOf("") }
    var searchScope by rememberSaveable { mutableStateOf(SavedSearchScope.TITLE_ONLY) }
    var searchMenuExpanded by remember { mutableStateOf(false) }
    var workflowName by rememberSaveable(workflow) { mutableStateOf(when (val active = workflow) { is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SaveBlock -> active.block.name; is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SavePreset -> "Preset"; else -> "" }) }
    var workflowFolderId by rememberSaveable(workflow) { mutableStateOf<String?>(null) }
    var folderFilter by rememberSaveable { mutableStateOf("ALL") }
    var overwriteWorkflow by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(workflow) { if (workflow != null) pagerState.scrollToPage(when (workflow) { is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SaveSet, is com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadSet -> 1; is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SavePreset, com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadPreset -> 2; else -> 0 }) }
    var folderDialog by rememberSaveable { mutableStateOf(false) }
    var folderSelector by rememberSaveable { mutableStateOf(false) }
    var movingBlock by remember { mutableStateOf<com.hjhsys.naiblockprompt.data.local.entity.SavedBlockEntity?>(null) }
    var movingPreset by remember { mutableStateOf<com.hjhsys.naiblockprompt.data.local.entity.PresetEntity?>(null) }
    var movingSet by remember { mutableStateOf<com.hjhsys.naiblockprompt.data.local.entity.SavedSetEntity?>(null) }
    var deletingFolder by remember { mutableStateOf<SavedFolderEntity?>(null) }
    var baseSetToLoad by remember { mutableStateOf<SavedSetItem?>(null) }
    var loadBasePositive by rememberSaveable { mutableStateOf(true) }
    var loadBaseNegative by rememberSaveable { mutableStateOf(true) }
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
    if (folderDialog) NameDialog(R.string.new_folder, { folderDialog = false }) { viewModel.createFolder(it); folderDialog = false }
    if (folderSelector) AlertDialog(
        onDismissRequest = { folderSelector = false },
        title = { Text(stringResource(R.string.select_folder_filter)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.all_folders)) },
                        leadingContent = { Icon(Icons.Default.Home, null) },
                        trailingContent = { if (folderFilter == "ALL") Icon(Icons.Default.Check, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { folderFilter = "ALL"; folderSelector = false },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(when (tab) { 0 -> R.string.library_root_blocks; 1 -> R.string.library_root_sets; else -> R.string.library_root_presets })) },
                        leadingContent = { Icon(Icons.Default.Folder, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { folderFilter = "ALL"; folderSelector = false },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.no_folder)) },
                        leadingContent = { Icon(Icons.Default.SubdirectoryArrowRight, null) },
                        trailingContent = { if (folderFilter == "NONE") Icon(Icons.Default.Check, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.padding(start = 24.dp).clickable { folderFilter = "NONE"; folderSelector = false },
                    )
                }
                items(folders, key = { it.id }) { folder ->
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            leadingContent = { Icon(Icons.Default.SubdirectoryArrowRight, null) },
                            trailingContent = { if (folderFilter == folder.id) Icon(Icons.Default.Check, null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.weight(1f).clickable { folderFilter = folder.id; folderSelector = false },
                        )
                        IconButton(onClick = { folderSelector = false; deletingFolder = folder }) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.delete)) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { folderSelector = false }) { Text(stringResource(R.string.cancel)) } },
    )
    if (overwriteWorkflow) AlertDialog(onDismissRequest = { overwriteWorkflow = false }, title = { Text(stringResource(R.string.overwrite_title)) }, text = { Text(stringResource(R.string.overwrite_message, workflowName.trim())) }, confirmButton = { Button(onClick = { overwriteWorkflow = false; when (workflow) { is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SavePreset -> viewModel.finishPresetSave(workflowName, workflowFolderId); is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SaveSet -> viewModel.finishSetSave(workflowName, workflowFolderId); else -> viewModel.finishBlockSave(workflowName, workflowFolderId) }; onWorkflowFinished() }) { Text(stringResource(R.string.overwrite)) } }, dismissButton = { TextButton(onClick = { overwriteWorkflow = false }) { Text(stringResource(R.string.cancel)) } })
    deletingFolder?.let { folder -> AlertDialog(onDismissRequest = { deletingFolder = null }, title = { Text(stringResource(R.string.delete_folder_title)) }, text = { Text(stringResource(R.string.delete_folder_message, folder.name)) }, confirmButton = { TextButton(onClick = { viewModel.deleteFolder(folder); if (folderFilter == folder.id) folderFilter = "ALL"; deletingFolder = null }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text(stringResource(R.string.cancel)) } }) }
    movingBlock?.let { block -> AlertDialog(onDismissRequest = { movingBlock = null }, title = { Text(stringResource(R.string.choose_folder)) }, text = { Column { TextButton(onClick = { viewModel.moveSavedBlock(block, null); movingBlock = null }) { Text(stringResource(R.string.no_folder)) }; folders.forEach { folder -> TextButton(onClick = { viewModel.moveSavedBlock(block, folder.id); movingBlock = null }) { Text(folder.name) } } } }, confirmButton = {}) }
    movingPreset?.let { preset -> AlertDialog(onDismissRequest = { movingPreset = null }, title = { Text(stringResource(R.string.choose_folder)) }, text = { Column { TextButton(onClick = { viewModel.movePreset(preset, null); movingPreset = null }) { Text(stringResource(R.string.no_folder)) }; folders.forEach { folder -> TextButton(onClick = { viewModel.movePreset(preset, folder.id); movingPreset = null }) { Text(folder.name) } } } }, confirmButton = {}) }
    movingSet?.let { set -> AlertDialog(onDismissRequest = { movingSet = null }, title = { Text(stringResource(R.string.choose_folder)) }, text = { Column { TextButton(onClick = { viewModel.moveSavedSet(set, null); movingSet = null }) { Text(stringResource(R.string.no_folder)) }; folders.forEach { folder -> TextButton(onClick = { viewModel.moveSavedSet(set, folder.id); movingSet = null }) { Text(folder.name) } } } }, confirmButton = {}) }
    Scaffold(
        topBar = {
            if (workflow == null) AppTitleBar(R.string.saved_title, menuItems = listOf(AppTitleMenuItem(R.string.nav_settings, Icons.Default.Settings, onClick = onOpenSettings)))
        },
    ) { screenPadding ->
    Column(
        Modifier.fillMaxSize().padding(screenPadding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        workflow?.let {
            ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(when (it) { is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SaveBlock -> R.string.save_block_mode; is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SavePreset -> R.string.save_preset_mode; is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SaveSet -> R.string.save_set; is com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadSet -> R.string.load_set; com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadPreset -> R.string.load_preset; else -> R.string.load_block_mode }), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); TextButton(onClick = { viewModel.cancelSavedWorkflow(); onWorkflowFinished() }) { Text(stringResource(R.string.cancel)) } }
                if (it is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SaveBlock) {
                    Text(stringResource(R.string.save_location), style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { item { FilterChip(workflowFolderId == null, { workflowFolderId = null }, { Text(stringResource(R.string.no_folder)) }) }; items(folders, key = { folder -> folder.id }) { folder -> FilterChip(workflowFolderId == folder.id, { workflowFolderId = folder.id }, { Text(folder.name) }) } }
                    OutlinedTextField(workflowName, { workflowName = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.name)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done))
                    Button(onClick = { if (blocks.any { block -> block.name.equals(workflowName.trim(), true) }) overwriteWorkflow = true else { viewModel.finishBlockSave(workflowName, workflowFolderId); onWorkflowFinished() } }, enabled = workflowName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
                } else if (it is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SavePreset || it is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SaveSet) {
                    Text(stringResource(R.string.save_location), style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { item { FilterChip(workflowFolderId == null, { workflowFolderId = null }, { Text(stringResource(R.string.no_folder)) }) }; items(folders, key = { folder -> folder.id }) { folder -> FilterChip(workflowFolderId == folder.id, { workflowFolderId = folder.id }, { Text(folder.name) }) } }
                    OutlinedTextField(workflowName, { workflowName = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.name)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done))
                    Button(onClick = { val exists = if (it is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SavePreset) presets.any { p -> p.entity.name.equals(workflowName.trim(), true) } else sets.any { s -> s.entity.name.equals(workflowName.trim(), true) && s.entity.kind == (it as com.hjhsys.naiblockprompt.ui.SavedWorkflow.SaveSet).set.kind.name }; if (exists) overwriteWorkflow = true else { if (it is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SavePreset) viewModel.finishPresetSave(workflowName, workflowFolderId) else viewModel.finishSetSave(workflowName, workflowFolderId); onWorkflowFinished() } }, enabled = workflowName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
                } else Text(stringResource(R.string.choose_block_to_load), style = MaterialTheme.typography.bodySmall)
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
                Text(when (folderFilter) { "ALL" -> stringResource(R.string.all_folders); "NONE" -> stringResource(R.string.no_folder); else -> folders.firstOrNull { it.id == folderFilter }?.name ?: stringResource(R.string.all_folders) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, null)
            }
            IconButton(onClick = { folderDialog = true }) { Icon(Icons.Default.CreateNewFolder, stringResource(R.string.new_folder)) }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f), userScrollEnabled = workflow == null) { page ->
        if (page == 0) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val filtered = blocks.filter { (search.isBlank() || it.name.contains(search, true) || (searchScope == SavedSearchScope.INCLUDE_CONTENT && it.content.contains(search, true))) && (folderFilter == "ALL" || (folderFilter == "NONE" && it.folderId == null) || it.folderId == folderFilter) }
                if (filtered.isEmpty()) item { Text(stringResource(R.string.saved_empty)) }
                items(filtered, key = { it.id }) { block -> ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(block.name, style = MaterialTheme.typography.titleMedium); Text(block.content.ifBlank { stringResource(R.string.empty_prompt) }, maxLines = 3, overflow = TextOverflow.Ellipsis); Row { if (workflow is com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadBlock) Button(onClick = { viewModel.finishBlockLoad(block); onWorkflowFinished() }) { Text(stringResource(R.string.load)) } else TextButton(onClick = { viewModel.addSavedBlockToBase(block) }) { Text(stringResource(R.string.add_to_base)) }; TextButton(onClick = { movingBlock = block }) { Text(folders.firstOrNull { it.id == block.folderId }?.name ?: stringResource(R.string.no_folder)) }; IconButton(onClick = { viewModel.deleteSavedBlock(block) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } } } } }
            }
        } else if (page == 1) LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val activeLoad = workflow as? com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadSet
            val kind = activeLoad?.owner?.let { if (it is PromptOwner.Base) SavedSetKind.BASE else SavedSetKind.CHARACTER }
            val filtered = sets.filter { item -> (kind == null || item.set?.kind == kind) && (search.isBlank() || item.entity.name.contains(search, true) || (searchScope == SavedSearchScope.INCLUDE_CONTENT && (item.set?.prompts?.allBlocks()?.any { it.name.contains(search, true) || it.content.contains(search, true) } == true || item.set?.textRendering?.content?.contains(search, true) == true))) && (folderFilter == "ALL" || (folderFilter == "NONE" && item.entity.folderId == null) || item.entity.folderId == folderFilter) }
            if (filtered.isEmpty()) item { Text(stringResource(R.string.saved_empty)) }
            items(filtered, key = { it.entity.id }) { set -> ElevatedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(set.entity.name); Text(set.entity.kind, style = MaterialTheme.typography.bodySmall) }; if (activeLoad != null) Button(onClick = {
                if (activeLoad.owner is PromptOwner.Base) {
                    loadBasePositive = true
                    loadBaseNegative = true
                    baseSetToLoad = set
                } else {
                    viewModel.finishSetLoad(set)
                    onWorkflowFinished()
                }
            }) { Text(stringResource(R.string.load)) }; TextButton(onClick = { movingSet = set.entity }) { Text(folders.firstOrNull { it.id == set.entity.folderId }?.name ?: stringResource(R.string.no_folder)) }; IconButton(onClick = { viewModel.deleteSavedSet(set.entity) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } } } }
        } else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (presets.isEmpty()) item { Text(stringResource(R.string.saved_empty)) }
            items(presets.filter { item -> (search.isBlank() || item.entity.name.contains(search, true) || (searchScope == SavedSearchScope.INCLUDE_CONTENT && item.session?.containsPromptText(search) == true)) && (folderFilter == "ALL" || (folderFilter == "NONE" && item.entity.folderId == null) || item.entity.folderId == folderFilter) }, key = { it.entity.id }) { preset -> ElevatedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(preset.entity.name, style = MaterialTheme.typography.titleMedium); Text(preset.session?.generationSettings?.modelId ?: "-", style = MaterialTheme.typography.bodySmall) }; if (workflow == com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadPreset) Button(onClick = { viewModel.restorePreset(preset); viewModel.cancelSavedWorkflow(); onWorkflowFinished() }, enabled = preset.session != null) { Text(stringResource(R.string.load)) }; TextButton(onClick = { movingPreset = preset.entity }) { Text(folders.firstOrNull { it.id == preset.entity.folderId }?.name ?: stringResource(R.string.no_folder)) }; IconButton(onClick = { viewModel.deletePreset(preset.entity) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } } } }
        }
        }
    } }
}

private enum class SavedSearchScope(@param:StringRes val label: Int) {
    TITLE_ONLY(R.string.search_titles_only),
    INCLUDE_CONTENT(R.string.search_include_content),
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
