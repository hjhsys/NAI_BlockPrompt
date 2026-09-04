package com.hjhsys.naiblockprompt.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.hjhsys.naiblockprompt.R
import com.hjhsys.naiblockprompt.data.library.*
import com.hjhsys.naiblockprompt.data.local.entity.SavedBlockEntity
import com.hjhsys.naiblockprompt.data.local.entity.SavedFolderEntity
import com.hjhsys.naiblockprompt.ui.MainViewModel
import com.hjhsys.naiblockprompt.ui.RestoreOptions
import com.hjhsys.naiblockprompt.ui.components.AppTitleBar
import com.hjhsys.naiblockprompt.ui.components.AppTitleMenuItem
import com.hjhsys.naiblockprompt.domain.editor.PromptOwner
import com.hjhsys.naiblockprompt.domain.model.SavedSetKind
import java.io.File
import android.net.Uri
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(viewModel: MainViewModel, onOpenSettings: () -> Unit, isActive: Boolean = true, onOpenResult: () -> Unit, onRestored: () -> Unit) {
    val history by viewModel.history.collectAsState()
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    val retainedUnfavorites = remember { mutableStateListOf<String>() }
    LaunchedEffect(isActive) {
        if (!isActive) {
            retainedUnfavorites.clear()
            viewModel.enforceHistoryLimit()
        }
    }
    var restore by remember { mutableStateOf<HistoryItem?>(null) }
    var original by remember { mutableStateOf<HistoryItem?>(null) }
    restore?.let { item -> RestoreDialog(item, { restore = null }) { options -> viewModel.restoreHistory(item, options); restore = null; onRestored() } }
    original?.takeIf { it.originalExists }?.let { item ->
        Dialog(onDismissRequest = { original = null }) {
            Surface(shape = MaterialTheme.shapes.large) {
                AsyncImage(historyImageModel(item.entity.imagePath), stringResource(R.string.generated_image), Modifier.fillMaxWidth().fillMaxHeight(.9f).clickable { original = null }, contentScale = ContentScale.Fit)
            }
        }
    }
    Scaffold(topBar = { AppTitleBar(
        R.string.history_title,
        directAction = AppTitleMenuItem(R.string.workspace_result, Icons.Default.Image, onClick = onOpenResult),
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
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(File(item.entity.thumbnailPath), stringResource(R.string.generated_image), Modifier.size(104.dp).clickable(enabled = item.originalExists) { original = item }, contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.entity.model ?: "-", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.entity.createdAt)), style = MaterialTheme.typography.bodySmall)
                        item.snapshot?.generation?.let { Text(stringResource(R.string.used_seed, it.usedSeed), style = MaterialTheme.typography.bodySmall) }
                        if (!item.originalExists) Text(stringResource(R.string.original_missing), color = MaterialTheme.colorScheme.error)
                        Row {
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
                            TextButton(onClick = { restore = item }) { Text(stringResource(R.string.load)) }
                            IconButton(onClick = { viewModel.deleteHistory(item.entity) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        }
    } }
}

private fun historyImageModel(reference: String): Any =
    if (reference.startsWith("content://")) Uri.parse(reference) else File(reference)

@Composable
private fun RestoreDialog(item: HistoryItem, dismiss: () -> Unit, confirm: (RestoreOptions) -> Unit) {
    var settings by rememberSaveable { mutableStateOf(true) }; var base by rememberSaveable { mutableStateOf(true) }
    var characters by rememberSaveable { mutableStateOf(true) }; var seed by rememberSaveable { mutableStateOf(true) }
    var inputImage by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.load_from_history)) }, text = {
        Column {
            Text(stringResource(R.string.restore_stash_notice))
            CheckRow(R.string.restore_settings, settings) { settings = it }; CheckRow(R.string.restore_base, base) { base = it }
            CheckRow(R.string.restore_characters, characters) { characters = it }; CheckRow(R.string.restore_seed, seed) { seed = it }
            if (item.snapshot?.session?.generationSettings?.imageInput != null) {
                val input = item.snapshot.session.generationSettings.imageInput
                val label = stringResource(when (input?.mode) {
                    com.hjhsys.naiblockprompt.domain.model.ImageInputMode.VIBE_TRANSFER -> R.string.restore_vibe_reference
                    com.hjhsys.naiblockprompt.domain.model.ImageInputMode.PRECISE_REFERENCE -> R.string.restore_precise_reference
                    else -> R.string.restore_image_to_image_input
                })
                if (item.inputImageExists) CheckRow(label, inputImage) { inputImage = it }
                else Text(stringResource(R.string.restore_input_image_missing_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }, confirmButton = { Button(onClick = { confirm(RestoreOptions(settings, base, characters, seed, inputImage)) }, enabled = item.snapshot != null) { Text(stringResource(R.string.load)) } }, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun CheckRow(label: Int, checked: Boolean, change: (Boolean) -> Unit) = CheckRow(stringResource(label), checked, change)

@Composable private fun CheckRow(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { change(!checked) }, verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, change); Text(label) }
}

@Composable
fun SavedScreen(viewModel: MainViewModel, onOpenSettings: () -> Unit, onWorkflowFinished: () -> Unit) {
    val blocks by viewModel.savedBlocks.collectAsState(); val folders by viewModel.savedFolders.collectAsState(); val presets by viewModel.presets.collectAsState(); val sets by viewModel.savedSets.collectAsState()
    val workflow by viewModel.savedWorkflow.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }; var search by rememberSaveable { mutableStateOf("") }
    var searchScope by rememberSaveable { mutableStateOf(SavedSearchScope.TITLE_ONLY) }
    var searchMenuExpanded by remember { mutableStateOf(false) }
    var workflowName by rememberSaveable(workflow) { mutableStateOf(when (val active = workflow) { is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SaveBlock -> active.block.name; is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SavePreset -> "Preset"; else -> "" }) }
    var workflowFolderId by rememberSaveable(workflow) { mutableStateOf<String?>(null) }
    var folderFilter by rememberSaveable { mutableStateOf("ALL") }
    var overwriteWorkflow by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(workflow) { if (workflow != null) tab = when (workflow) { is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SaveSet, is com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadSet -> 1; is com.hjhsys.naiblockprompt.ui.SavedWorkflow.SavePreset, com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadPreset -> 2; else -> 0 } }
    var folderDialog by rememberSaveable { mutableStateOf(false) }
    var movingBlock by remember { mutableStateOf<com.hjhsys.naiblockprompt.data.local.entity.SavedBlockEntity?>(null) }
    var movingPreset by remember { mutableStateOf<com.hjhsys.naiblockprompt.data.local.entity.PresetEntity?>(null) }
    var movingSet by remember { mutableStateOf<com.hjhsys.naiblockprompt.data.local.entity.SavedSetEntity?>(null) }
    var deletingFolder by remember { mutableStateOf<SavedFolderEntity?>(null) }
    if (folderDialog) NameDialog(R.string.new_folder, { folderDialog = false }) { viewModel.createFolder(it); folderDialog = false }
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
        if (workflow == null) PrimaryTabRow(selectedTabIndex = tab) { Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.saved_blocks)) }); Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.saved_sets)) }); Tab(tab == 2, { tab = 2 }, text = { Text(stringResource(R.string.presets)) }) }
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
        Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = { folderDialog = true }) { Icon(Icons.Default.CreateNewFolder, null); Text(stringResource(R.string.new_folder)) } }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            item { FilterChip(folderFilter == "ALL", { folderFilter = "ALL" }, { Text(stringResource(R.string.all_folders)) }) }
            item { FilterChip(folderFilter == "NONE", { folderFilter = "NONE" }, { Text(stringResource(R.string.no_folder)) }) }
            items(folders, key = { it.id }) { folder -> InputChip(folderFilter == folder.id, { folderFilter = folder.id }, { Text(folder.name) }, trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.delete), Modifier.size(16.dp).clickable { deletingFolder = folder }) }) }
        }
        if (tab == 0) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val filtered = blocks.filter { (search.isBlank() || it.name.contains(search, true) || (searchScope == SavedSearchScope.INCLUDE_CONTENT && it.content.contains(search, true))) && (folderFilter == "ALL" || (folderFilter == "NONE" && it.folderId == null) || it.folderId == folderFilter) }
                if (filtered.isEmpty()) item { Text(stringResource(R.string.saved_empty)) }
                items(filtered, key = { it.id }) { block -> ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(block.name, style = MaterialTheme.typography.titleMedium); Text(block.content.ifBlank { stringResource(R.string.empty_prompt) }, maxLines = 3, overflow = TextOverflow.Ellipsis); Row { if (workflow is com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadBlock) Button(onClick = { viewModel.finishBlockLoad(block); onWorkflowFinished() }) { Text(stringResource(R.string.load)) } else TextButton(onClick = { viewModel.addSavedBlockToBase(block) }) { Text(stringResource(R.string.add_to_base)) }; TextButton(onClick = { movingBlock = block }) { Text(folders.firstOrNull { it.id == block.folderId }?.name ?: stringResource(R.string.no_folder)) }; IconButton(onClick = { viewModel.deleteSavedBlock(block) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } } } } }
            }
        } else if (tab == 1) LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val kind = (workflow as? com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadSet)?.owner?.let { if (it is PromptOwner.Base) SavedSetKind.BASE else SavedSetKind.CHARACTER }
            val filtered = sets.filter { item -> (kind == null || item.set?.kind == kind) && (search.isBlank() || item.entity.name.contains(search, true) || (searchScope == SavedSearchScope.INCLUDE_CONTENT && (item.set?.prompts?.allBlocks()?.any { it.name.contains(search, true) || it.content.contains(search, true) } == true || item.set?.textRendering?.content?.contains(search, true) == true))) && (folderFilter == "ALL" || (folderFilter == "NONE" && item.entity.folderId == null) || item.entity.folderId == folderFilter) }
            if (filtered.isEmpty()) item { Text(stringResource(R.string.saved_empty)) }
            items(filtered, key = { it.entity.id }) { set -> ElevatedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(set.entity.name); Text(set.entity.kind, style = MaterialTheme.typography.bodySmall) }; if (workflow is com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadSet) Button(onClick = { viewModel.finishSetLoad(set); onWorkflowFinished() }) { Text(stringResource(R.string.load)) }; TextButton(onClick = { movingSet = set.entity }) { Text(folders.firstOrNull { it.id == set.entity.folderId }?.name ?: stringResource(R.string.no_folder)) }; IconButton(onClick = { viewModel.deleteSavedSet(set.entity) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } } } }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (presets.isEmpty()) item { Text(stringResource(R.string.saved_empty)) }
            items(presets.filter { item -> (search.isBlank() || item.entity.name.contains(search, true) || (searchScope == SavedSearchScope.INCLUDE_CONTENT && item.session?.containsPromptText(search) == true)) && (folderFilter == "ALL" || (folderFilter == "NONE" && item.entity.folderId == null) || item.entity.folderId == folderFilter) }, key = { it.entity.id }) { preset -> ElevatedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(preset.entity.name, style = MaterialTheme.typography.titleMedium); Text(preset.session?.generationSettings?.modelId ?: "-", style = MaterialTheme.typography.bodySmall) }; if (workflow == com.hjhsys.naiblockprompt.ui.SavedWorkflow.LoadPreset) Button(onClick = { viewModel.restorePreset(preset); viewModel.cancelSavedWorkflow(); onWorkflowFinished() }, enabled = preset.session != null) { Text(stringResource(R.string.load)) }; TextButton(onClick = { movingPreset = preset.entity }) { Text(folders.firstOrNull { it.id == preset.entity.folderId }?.name ?: stringResource(R.string.no_folder)) }; IconButton(onClick = { viewModel.deletePreset(preset.entity) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } } } }
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
