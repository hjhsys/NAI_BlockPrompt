package com.hjhsys.naiblockprompt.ui.database

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hjhsys.naiblockprompt.R
import com.hjhsys.naiblockprompt.domain.model.TagDictionaryFilter
import com.hjhsys.naiblockprompt.domain.model.TagDictionaryItem
import com.hjhsys.naiblockprompt.domain.model.AppTagCategory
import com.hjhsys.naiblockprompt.domain.model.TagDictionarySort
import com.hjhsys.naiblockprompt.domain.model.ExcludedTagItem
import com.hjhsys.naiblockprompt.domain.model.TagExclusionOrigin
import com.hjhsys.naiblockprompt.domain.model.TagExclusionFilter
import com.hjhsys.naiblockprompt.domain.tags.AiTranslationExportScope
import com.hjhsys.naiblockprompt.ui.MainViewModel
import com.hjhsys.naiblockprompt.ui.components.AppTitleBar
import com.hjhsys.naiblockprompt.ui.components.AppTitleMenuItem
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagDatabaseScreen(viewModel: MainViewModel, onOpenSettings: () -> Unit) {
    val tags by viewModel.dictionaryTags.collectAsStateWithLifecycle()
    val wildcards by viewModel.wildcards.collectAsStateWithLifecycle()
    val matchingCount by viewModel.dictionaryCount.collectAsStateWithLifecycle()
    val missingTranslationCount by viewModel.missingTranslationCount.collectAsStateWithLifecycle()
    val missingCategoryCount by viewModel.missingCategoryCount.collectAsStateWithLifecycle()
    val deferredCount by viewModel.deferredTranslationCount.collectAsStateWithLifecycle()
    val usedCategories by viewModel.usedTagCategories.collectAsStateWithLifecycle()
    val userCategories by viewModel.userTagCategories.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val importPreview by viewModel.translationImportPreview.collectAsStateWithLifecycle()
    val importFailed by viewModel.translationImportFailed.collectAsStateWithLifecycle()
    val excludedTags by viewModel.excludedTags.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val exportScope = rememberCoroutineScope()
    var exportContent by remember { mutableStateOf(byteArrayOf()) }
    var exportFileName by remember { mutableStateOf("nai_tags_translation_batch.zip") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val bytes = exportContent
            exportScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        require(bytes.isNotEmpty())
                        val output = context.contentResolver.openOutputStream(uri) ?: error("Cannot write translation export")
                        output.use { it.write(bytes) }
                    }
                    clipboard.setText(AnnotatedString(context.getString(R.string.translation_ai_clipboard_prompt)))
                    android.widget.Toast.makeText(context, R.string.translation_export_copied, android.widget.Toast.LENGTH_LONG).show()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    android.widget.Toast.makeText(context, R.string.translation_export_failed, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::previewTranslationFile)
    }
    var sharedExport by remember { mutableStateOf<MainViewModel.TransferFile?>(null) }
    val sharedExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val file = sharedExport ?: return@rememberLauncherForActivityResult
        uri?.let { context.contentResolver.openOutputStream(it)?.use { output -> output.write(file.bytes) } }
    }
    val sharedImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { context.contentResolver.openInputStream(it)?.use { input -> viewModel.importSharedTagDatabase(input.readBytes()) } }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(TagDictionaryFilter.ALL) }
    var category by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(TagDictionarySort.POPULAR) }
    var editing by remember { mutableStateOf<TagDictionaryItem?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var addingCategory by rememberSaveable { mutableStateOf(false) }
    var showExportOptions by rememberSaveable { mutableStateOf(false) }
    var exportAllBatches by rememberSaveable { mutableStateOf(false) }
    val sectionPagerState = rememberPagerState { 3 }
    val section = sectionPagerState.currentPage
    val sectionScope = rememberCoroutineScope()
    var aiSelectionMode by rememberSaveable { mutableStateOf(false) }
    var aiToolsExpanded by rememberSaveable { mutableStateOf(false) }
    val aiSelected = remember { mutableStateMapOf<String, TagDictionaryItem>() }
    var showAiExportScope by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.translationExport.collect { content ->
            exportContent = content.content
            exportFileName = content.fileName
            exportLauncher.launch(exportFileName)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.transferExport.collect { file ->
            if (file.name == "nai_user_tag_db.zip") { sharedExport = file; sharedExportLauncher.launch(file.name) }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.filterDictionary(TagDictionaryFilter.ALL)
        viewModel.filterDictionaryCategory("")
        viewModel.searchDictionary("")
    }
    LaunchedEffect(query) { viewModel.searchDictionary(query) }
    LaunchedEffect(filter) { viewModel.filterDictionary(filter) }
    LaunchedEffect(category) { viewModel.filterDictionaryCategory(category) }
    LaunchedEffect(sort) { viewModel.sortDictionary(sort) }
    editing?.let { item -> TagEditDialog(item, { editing = null }, { ko, aliases, category ->
        viewModel.saveTagDetails(item, ko, aliases, category); editing = null
    }, { viewModel.resetTagDetails(item); editing = null },
        onDelete = { viewModel.deleteUserOnlyTag(item); editing = null },
        categories = (AppTagCategory.entries.map { it.value } + userCategories).distinct(),
        generatedImages = history.map { it.entity.thumbnailPath to it.entity.imagePath },
        onChooseThumbnail = { viewModel.setTagThumbnail(item, it) },
        onUseGeneratedThumbnail = { viewModel.setTagThumbnailFromFile(item, it) },
        onRemoveThumbnail = { viewModel.removeTagThumbnail(item) },
        onExclude = { viewModel.excludeTag(item); editing = null },
    ) }
    if (adding) AddTagDialog({ adding = false }, (AppTagCategory.entries.map { it.value } + userCategories).distinct()) { canonical, ko, aliases, category ->
        viewModel.addUserTag(canonical, ko, aliases, category); adding = false
    }
    if (addingCategory) AddCategoryDialog({ addingCategory = false }) { viewModel.addTagCategory(it); addingCategory = false }
    if (showAiExportScope) AiTranslationExportScopeDialog(
        missingCount = missingTranslationCount,
        selectedCount = aiSelected.size,
        onDismiss = { showAiExportScope = false },
        onExport = { scope ->
            showAiExportScope = false
            viewModel.prepareTagsForAiExport(scope, aiSelected.values.toList())
        },
    )
    if (showExportOptions) TranslationExportOptionsDialog(
        onDismiss = { showExportOptions = false },
        onExport = { missingTranslation, missingCategory, includeDeferred ->
            showExportOptions = false
            if (exportAllBatches) viewModel.prepareAllTranslationExport(missingTranslation, missingCategory, includeDeferred)
            else viewModel.prepareTranslationExport(missingTranslation, missingCategory, includeDeferred)
        },
    )
    importPreview?.let { preview ->
        TranslationImportDialog(
            preview = preview,
            onDismiss = viewModel::dismissTranslationImport,
            onApply = viewModel::applyTranslationImport,
        )
    }
    if (importFailed) AlertDialog(
        onDismissRequest = viewModel::dismissTranslationImportFailure,
        title = { Text(stringResource(R.string.import_ai_result)) },
        text = { Text(stringResource(R.string.translation_file_error)) },
        confirmButton = { TextButton(onClick = viewModel::dismissTranslationImportFailure) { Text(stringResource(R.string.close)) } },
    )

    Scaffold(
        topBar = {
            Column {
            AppTitleBar(
                R.string.db_title,
                directAction = if (section == 0) AppTitleMenuItem(R.string.add, Icons.Default.Add, onClick = { showAddMenu = true }) else null,
                secondaryDirectAction = if (section == 0) AppTitleMenuItem(R.string.filter_favorites, if (filter == TagDictionaryFilter.FAVORITES) Icons.Default.Star else Icons.Default.StarBorder, onClick = {
                    filter = if (filter == TagDictionaryFilter.FAVORITES) TagDictionaryFilter.ALL else TagDictionaryFilter.FAVORITES
                }) else null,
                menuItems = if (section == 0) listOf(
                    AppTitleMenuItem(R.string.select_tags_for_ai, Icons.Default.Checklist, onClick = { aiSelectionMode = true }),
                    AppTitleMenuItem(R.string.export_shared_tag_db, Icons.Default.Share, onClick = viewModel::exportSharedTagDatabase),
                    AppTitleMenuItem(R.string.import_shared_tag_db, Icons.Default.Download, onClick = { sharedImportLauncher.launch(arrayOf("application/zip")) }),
                    AppTitleMenuItem(R.string.nav_settings, Icons.Default.Settings, onClick = onOpenSettings),
                ) else listOf(AppTitleMenuItem(R.string.nav_settings, Icons.Default.Settings, onClick = onOpenSettings)),
            )
            DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.add_user_tag)) }, leadingIcon = { Icon(Icons.Default.Label, null) }, onClick = { showAddMenu = false; adding = true })
                DropdownMenuItem(text = { Text(stringResource(R.string.add_category)) }, leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) }, onClick = { showAddMenu = false; addingCategory = true })
            }
            PrimaryTabRow(selectedTabIndex = section) {
                Tab(selected = section == 0, onClick = { sectionScope.launch { sectionPagerState.animateScrollToPage(0) } }, text = { Text(stringResource(R.string.tags_tab)) })
                Tab(selected = section == 1, onClick = { sectionScope.launch { sectionPagerState.animateScrollToPage(1) } }, text = { Text(stringResource(R.string.wildcards_tab)) })
                Tab(selected = section == 2, onClick = { sectionScope.launch { sectionPagerState.animateScrollToPage(2) } }, text = { Text(stringResource(R.string.excluded_tags_tab)) })
            }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = sectionPagerState,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { page ->
        if (page == 1) {
            WildcardScreen(wildcards, viewModel, Modifier.fillMaxSize())
        } else if (page == 2) {
            ExcludedTagsScreen(
                items = excludedTags,
                onFilter = viewModel::filterExcludedTags,
                onRestore = viewModel::restoreExcludedTags,
                onSetUserConfirmed = viewModel::setExcludedTagsUserConfirmed,
                showConfirmationHelp = settings.showExclusionConfirmationHelp,
                onShowConfirmationHelpChange = viewModel::setShowExclusionConfirmationHelp,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.search_tag_dictionary)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = if (query.isNotEmpty()) {{ IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, stringResource(R.string.clear_search)) } }} else null,
                    singleLine = true,
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryDropdown(category, usedCategories, compact = true) { category = it }
                    SortDropdown(sort, Modifier.weight(1f)) { sort = it }
                }
            }
            if (aiSelectionMode) item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.ai_translation_selected_count, aiSelected.size), style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { aiSelected.clear(); tags.forEach { aiSelected[it.id] = it } }) {
                                Text(stringResource(R.string.select_all_visible))
                            }
                            TextButton(onClick = { aiSelected.clear(); aiSelectionMode = false }) {
                                Text(stringResource(R.string.clear_selection))
                            }
                            FilledTonalButton(
                                enabled = aiSelected.isNotEmpty() || missingTranslationCount > 0,
                                onClick = { showAiExportScope = true },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.copy_for_ai), maxLines = 1)
                            }
                        }
                    }
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clickable { aiToolsExpanded = !aiToolsExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.ai_translation_tools), style = MaterialTheme.typography.titleMedium)
                                if (!aiToolsExpanded) Text(
                                    "${stringResource(R.string.missing_translation_count, missingTranslationCount)} · ${stringResource(R.string.missing_category_count, missingCategoryCount)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                if (aiToolsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = stringResource(if (aiToolsExpanded) R.string.collapse else R.string.expand),
                            )
                        }
                        if (aiToolsExpanded) {
                        if (deferredCount > 0) Text(stringResource(R.string.translation_deferred_count, deferredCount), style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.missing_translation_count, missingTranslationCount), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.missing_category_count, missingCategoryCount), style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { exportAllBatches = false; showExportOptions = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.UploadFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.export_for_ai), maxLines = 1)
                            }
                            OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.import_ai_result), maxLines = 1)
                            }
                        }
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.tag_dictionary_count, matchingCount, tags.size), style = MaterialTheme.typography.bodySmall) }
            if (tags.isEmpty()) item { Text(stringResource(R.string.tag_dictionary_empty)) }
            items(tags, key = { it.id }) { tag ->
                val selectedForAi = tag.id in aiSelected
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().combinedClickable(
                        onClick = {
                            if (aiSelectionMode) {
                                if (selectedForAi) aiSelected.remove(tag.id) else aiSelected[tag.id] = tag
                            } else editing = tag
                        },
                        onLongClick = {
                            aiSelectionMode = true
                            aiSelected[tag.id] = tag
                        },
                    ),
                    colors = CardDefaults.elevatedCardColors(containerColor = if (selectedForAi) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (aiSelectionMode) Checkbox(
                            checked = selectedForAi,
                            onCheckedChange = { checked -> if (checked) aiSelected[tag.id] = tag else aiSelected.remove(tag.id) },
                        )
                        Box(Modifier.size(52.dp).padding(end = 8.dp), contentAlignment = Alignment.Center) {
                            if (tag.thumbnailPath != null) {
                                AsyncImage(
                                    model = File(tag.thumbnailPath),
                                    contentDescription = stringResource(R.string.tag_thumbnail),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.small) {
                                    Icon(Icons.Default.Image, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                                }
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(tag.canonicalTag.replace('_', ' '), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            tag.korean?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                            val visibleCategory = (tag.appCategory ?: tag.danbooruCategory)?.let { categoryLabel(it) }
                            val detail = listOfNotNull(visibleCategory, tag.englishAliases?.takeIf { query.isNotBlank() }?.take(80)).joinToString(" · ")
                            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val metrics = listOfNotNull(
                                tag.danbooruPostCount?.let { stringResource(R.string.tag_metric_public_uses, compactCount(it)) },
                                tag.naiCount?.let { stringResource(R.string.tag_metric_nai_count, compactCount(it)) },
                                tag.useCount.takeIf { it > 0 }?.let { stringResource(R.string.tag_metric_app_uses, it) },
                            ).joinToString(" · ")
                            if (metrics.isNotBlank()) Text(metrics, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            tag.lastUsedAt?.let { Text(stringResource(R.string.tag_metric_last_used, shortDate(it)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            IconButton(onClick = { viewModel.setTagFavorite(tag, !tag.favorite) }) {
                                Icon(if (tag.favorite) Icons.Default.Star else Icons.Default.StarBorder, stringResource(if (tag.favorite) R.string.remove_favorite else R.string.add_favorite), tint = if (tag.favorite) MaterialTheme.colorScheme.tertiary else LocalContentColor.current)
                            }
                            tag.danbooruPostCount?.let { Text(compactCount(it), style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
            item {
                TextButton(onClick = { exportAllBatches = true; showExportOptions = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.developer_export_all_batches), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        }
        }
    }
}

@Composable
private fun ExcludedTagsScreen(
    items: List<ExcludedTagItem>,
    onFilter: (TagExclusionFilter) -> Unit,
    onRestore: (Collection<ExcludedTagItem>) -> Unit,
    onSetUserConfirmed: (Collection<ExcludedTagItem>, Boolean) -> Unit,
    showConfirmationHelp: Boolean,
    onShowConfirmationHelpChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf(TagExclusionFilter.ALL) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    val selected = remember { mutableStateMapOf<String, ExcludedTagItem>() }
    var pendingRestore by remember { mutableStateOf<List<ExcludedTagItem>?>(null) }
    var pendingConfirmationHelp by remember { mutableStateOf<List<ExcludedTagItem>?>(null) }
    var hideConfirmationHelp by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(filter) {
        selected.clear()
        onFilter(filter)
    }
    val selectedItems = selected.values.toList()
    val pendingAiItems = selectedItems.filter { it.origin == TagExclusionOrigin.AI && !it.userConfirmed }
    val confirmedAiItems = selectedItems.filter { it.origin == TagExclusionOrigin.AI && it.userConfirmed }
    pendingRestore?.let { restoreItems ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.restore_excluded_tag_title)) },
            text = {
                Text(
                    if (restoreItems.size == 1) stringResource(R.string.restore_excluded_tag_message, restoreItems.single().canonicalTag)
                    else stringResource(R.string.restore_excluded_tags_message, restoreItems.size),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRestore(restoreItems)
                    pendingRestore = null
                    selected.clear()
                    selectionMode = false
                }) {
                    Text(stringResource(R.string.restore_excluded_tag_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    pendingConfirmationHelp?.let { confirmItems ->
        AlertDialog(
            onDismissRequest = { pendingConfirmationHelp = null; hideConfirmationHelp = false },
            title = { Text(stringResource(R.string.confirm_excluded_tag_help_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.confirm_excluded_tag_help_message))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = hideConfirmationHelp, onCheckedChange = { hideConfirmationHelp = it })
                        Text(stringResource(R.string.do_not_show_again))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetUserConfirmed(confirmItems, true)
                    if (hideConfirmationHelp) onShowConfirmationHelpChange(false)
                    pendingConfirmationHelp = null
                    hideConfirmationHelp = false
                    selected.clear()
                    selectionMode = false
                }) { Text(stringResource(R.string.confirm_excluded_tag)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmationHelp = null; hideConfirmationHelp = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    Column(modifier.padding(horizontal = 12.dp)) {
        LazyRow(
            contentPadding = PaddingValues(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { FilterChip(selected = filter == TagExclusionFilter.ALL, onClick = { filter = TagExclusionFilter.ALL }, label = { Text(stringResource(R.string.excluded_filter_all)) }) }
            item { FilterChip(selected = filter == TagExclusionFilter.NEEDS_REVIEW, onClick = { filter = TagExclusionFilter.NEEDS_REVIEW }, label = { Text(stringResource(R.string.excluded_filter_needs_review)) }) }
            item { FilterChip(selected = filter == TagExclusionFilter.USER_CONFIRMED, onClick = { filter = TagExclusionFilter.USER_CONFIRMED }, label = { Text(stringResource(R.string.excluded_filter_user_confirmed)) }) }
            item { FilterChip(selected = filter == TagExclusionFilter.AI_SUGGESTED, onClick = { filter = TagExclusionFilter.AI_SUGGESTED }, label = { Text(stringResource(R.string.excluded_filter_ai_suggested)) }) }
            item { FilterChip(selected = filter == TagExclusionFilter.USER_DIRECT, onClick = { filter = TagExclusionFilter.USER_DIRECT }, label = { Text(stringResource(R.string.excluded_filter_user_direct)) }) }
        }
        if (!selectionMode) {
            TextButton(onClick = { selectionMode = true }, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.Checklist, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.excluded_select_multiple))
            }
        } else {
            ElevatedCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.excluded_selected_count, selected.size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { items.forEach { selected[it.canonicalTag] = it } }) {
                            Text(stringResource(R.string.select_all_visible))
                        }
                        IconButton(onClick = { selected.clear(); selectionMode = false }) {
                            Icon(Icons.Default.Close, stringResource(R.string.cancel))
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilledTonalButton(
                                enabled = pendingAiItems.isNotEmpty(),
                                onClick = {
                                    if (showConfirmationHelp) pendingConfirmationHelp = pendingAiItems
                                    else {
                                        onSetUserConfirmed(pendingAiItems, true)
                                        selected.clear()
                                        selectionMode = false
                                    }
                                },
                            ) { Text(stringResource(R.string.excluded_batch_confirm, pendingAiItems.size)) }
                        }
                        item {
                            OutlinedButton(
                                enabled = confirmedAiItems.isNotEmpty(),
                                onClick = {
                                    onSetUserConfirmed(confirmedAiItems, false)
                                    selected.clear()
                                    selectionMode = false
                                },
                            ) { Text(stringResource(R.string.excluded_batch_unconfirm, confirmedAiItems.size)) }
                        }
                        item {
                            OutlinedButton(
                                enabled = selectedItems.isNotEmpty(),
                                onClick = { pendingRestore = selectedItems },
                            ) { Text(stringResource(R.string.excluded_batch_restore, selectedItems.size)) }
                        }
                    }
                }
            }
        }
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.excluded_tags_empty)) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(items, key = { it.canonicalTag }) { item ->
                    ElevatedCard(
                        Modifier.fillMaxWidth().then(
                            if (selectionMode) Modifier.clickable {
                                if (selected.containsKey(item.canonicalTag)) selected.remove(item.canonicalTag)
                                else selected[item.canonicalTag] = item
                            } else Modifier,
                        ),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (selectionMode) {
                                Checkbox(
                                    checked = selected.containsKey(item.canonicalTag),
                                    onCheckedChange = { checked ->
                                        if (checked) selected[item.canonicalTag] = item else selected.remove(item.canonicalTag)
                                    },
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(item.canonicalTag.replace('_', ' '), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(
                                        R.string.excluded_origin_reason,
                                        stringResource(
                                            if (item.origin == TagExclusionOrigin.AI && item.userConfirmed) R.string.excluded_origin_ai_user
                                            else if (item.origin == TagExclusionOrigin.AI) R.string.excluded_origin_ai
                                            else R.string.excluded_origin_user,
                                        ),
                                        item.reasonCode,
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                item.reasonText?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                if (item.origin == TagExclusionOrigin.AI && item.userConfirmed) {
                                    Text(stringResource(R.string.excluded_user_confirmed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (!selectionMode) Column {
                                if (item.origin == TagExclusionOrigin.AI) {
                                    IconToggleButton(
                                        checked = item.userConfirmed,
                                        onCheckedChange = { checked ->
                                            if (checked && showConfirmationHelp) pendingConfirmationHelp = listOf(item)
                                            else onSetUserConfirmed(listOf(item), checked)
                                        },
                                    ) {
                                        Icon(
                                            if (item.userConfirmed) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                                            stringResource(
                                                if (item.userConfirmed) R.string.unconfirm_excluded_tag
                                                else R.string.confirm_excluded_tag,
                                            ),
                                            tint = if (item.userConfirmed) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                        )
                                    }
                                }
                                IconButton(onClick = { pendingRestore = listOf(item) }) {
                                    Icon(Icons.Default.Restore, stringResource(R.string.restore_excluded_tag))
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
private fun WildcardScreen(
    items: List<com.hjhsys.naiblockprompt.data.local.entity.WildcardEntity>,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<com.hjhsys.naiblockprompt.data.local.entity.WildcardEntity?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<com.hjhsys.naiblockprompt.data.local.entity.WildcardEntity?>(null) }
    var folderFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var folderMenu by remember { mutableStateOf(false) }
    val folders = items.mapNotNull { it.folder }.distinct().sorted()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val displayName = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }.orEmpty().substringBeforeLast('.').ifBlank { "wildcard" }
        val values = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (values.isNotBlank()) viewModel.saveWildcard(null, displayName, values, folderFilter)
    }
    if (creating || editing != null) {
        WildcardEditDialog(editing, viewModel, folders, onDismiss = { creating = false; editing = null }) { id, name, values, folder ->
            viewModel.saveWildcard(id, name, values, folder)
            creating = false
            editing = null
        }
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_wildcard_title)) },
            text = { Text(stringResource(R.string.delete_wildcard_message, item.name)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteWildcard(item); deleting = null }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    LazyColumn(modifier, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { creating = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.add_wildcard))
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("text/plain")) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.import_txt))
                }
            }
        }
        item {
            Box {
                OutlinedButton(onClick = { folderMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(folderFilter ?: stringResource(R.string.all_folders)); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(folderMenu, { folderMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.all_folders)) }, onClick = { folderFilter = null; folderMenu = false })
                    folders.forEach { folder -> DropdownMenuItem(text = { Text(folder) }, onClick = { folderFilter = folder; folderMenu = false }) }
                }
            }
        }
        if (items.isEmpty()) item { Text(stringResource(R.string.wildcard_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(items.filter { folderFilter == null || it.folder == folderFilter }, key = { it.id }) { item ->
            ElevatedCard(Modifier.fillMaxWidth().clickable { editing = item }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("__${item.name}__", style = MaterialTheme.typography.titleMedium)
                        item.folder?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                        Text(stringResource(R.string.wildcard_value_count, item.valuesText.lines().count { it.isNotBlank() }), style = MaterialTheme.typography.bodySmall)
                        Text(item.valuesText.replace("\n", " · "), maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { deleting = item }) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.delete)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WildcardEditDialog(
    item: com.hjhsys.naiblockprompt.data.local.entity.WildcardEntity?,
    viewModel: MainViewModel,
    folders: List<String>,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, String?) -> Unit,
) {
    var name by rememberSaveable(item?.id) { mutableStateOf(item?.name.orEmpty()) }
    var values by rememberSaveable(item?.id) { mutableStateOf(item?.valuesText.orEmpty()) }
    var folder by rememberSaveable(item?.id) { mutableStateOf(item?.folder.orEmpty()) }
    var showTags by rememberSaveable { mutableStateOf(false) }
    if (showTags) WildcardTagPicker(viewModel, { showTags = false }) { selected ->
        values = (values.lineSequence().filter(String::isNotBlank) + selected.asSequence()).distinct().joinToString("\n")
        showTags = false
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(if (item == null) R.string.add_wildcard else R.string.edit_wildcard)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.cancel)) }
                    },
                    actions = {
                        TextButton(
                            onClick = { onSave(item?.id, name, values, folder.ifBlank { null }) },
                            enabled = name.isNotBlank() && values.lineSequence().any { it.isNotBlank() },
                        ) { Text(stringResource(R.string.save)) }
                    },
                )
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        name,
                        { name = it.removePrefix("__").removeSuffix("__") },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.wildcard_name)) },
                        prefix = { Text("__") },
                        suffix = { Text("__") },
                        supportingText = { Text(stringResource(R.string.wildcard_name_hint)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        folder,
                        { folder = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.folder)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        values,
                        { values = it },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        label = { Text(stringResource(R.string.wildcard_values)) },
                        placeholder = { Text(stringResource(R.string.wildcard_values_hint)) },
                        supportingText = { Text(stringResource(R.string.wildcard_value_count, values.lineSequence().count { it.isNotBlank() })) },
                        trailingIcon = {
                            FilledTonalIconButton(onClick = { showTags = true }) {
                                Icon(Icons.Default.Storage, stringResource(R.string.select_wildcard_tags))
                            }
                        },
                    )
                    OutlinedButton(onClick = { showTags = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Storage, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.select_wildcard_tags))
                    }
                    Button(
                        onClick = { onSave(item?.id, name, values, folder.ifBlank { null }) },
                        enabled = name.isNotBlank() && values.lineSequence().any { it.isNotBlank() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}

@Composable
private fun WildcardTagPicker(viewModel: MainViewModel, onDismiss: () -> Unit, onDone: (List<String>) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            TagPickerScreen(viewModel, onDismiss, onInserted = {}, onSelectTags = onDone)
        }
    }
}

@Composable
private fun TagEditDialog(
    item: TagDictionaryItem,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    categories: List<String>,
    generatedImages: List<Pair<String, String>>,
    onChooseThumbnail: (android.net.Uri) -> Unit,
    onUseGeneratedThumbnail: (String) -> Unit,
    onRemoveThumbnail: () -> Unit,
    onExclude: () -> Unit,
) {
    var korean by rememberSaveable(item.id) { mutableStateOf(item.korean.orEmpty()) }
    var aliases by rememberSaveable(item.id) { mutableStateOf(item.koreanAliases.orEmpty()) }
    var category by rememberSaveable(item.id) { mutableStateOf(item.appCategory.orEmpty()) }
    var showGeneratedImages by rememberSaveable(item.id) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(item.id) { mutableStateOf(false) }
    var previewModel by remember(item.id) { mutableStateOf<Any?>(item.thumbnailPath?.let(::File)) }
    var advanced by rememberSaveable(item.id) { mutableStateOf(false) }
    val canDelete = item.userCreated && !item.novelAiSource && !item.danbooruSource && !item.bundled
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { previewModel = it; onChooseThumbnail(it) } }
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
                            previewModel = if (original.startsWith("content://")) android.net.Uri.parse(original) else File(original)
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
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_tag_title)) },
        text = { Text(stringResource(R.string.delete_tag_message, item.canonicalTag)) },
        confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
    )
    AlertDialog(onDismissRequest = onDismiss, title = { Text(item.canonicalTag) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (previewModel != null) AsyncImage(previewModel, stringResource(R.string.tag_thumbnail), Modifier.fillMaxWidth().height(140.dp), contentScale = ContentScale.Crop)
            else Surface(Modifier.fillMaxWidth().height(140.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium) {
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
            OutlinedTextField(korean, { korean = it }, label = { Text(stringResource(R.string.korean_translation)) }, singleLine = true)
            OutlinedTextField(aliases, { aliases = it }, label = { Text(stringResource(R.string.korean_aliases)) })
            CategoryDropdown(category, categories) { category = it }
            TextButton(onClick = { advanced = !advanced }) {
                Text(stringResource(R.string.advanced_information))
                Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            if (advanced) {
                Text(stringResource(R.string.tag_sources, sourceBadges(item)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.danbooruPostCount?.let { Text(stringResource(R.string.tag_metric_public_uses, compactCount(it)), style = MaterialTheme.typography.bodySmall) }
                item.naiCount?.let { Text(stringResource(R.string.tag_metric_nai_count, compactCount(it)), style = MaterialTheme.typography.bodySmall) }
                Text(stringResource(R.string.tag_metric_app_uses, item.useCount), style = MaterialTheme.typography.bodySmall)
                item.lastUsedAt?.let { Text(stringResource(R.string.tag_metric_last_used, shortDate(it)), style = MaterialTheme.typography.bodySmall) }
                item.englishAliases?.let { Text(stringResource(R.string.english_aliases, it), style = MaterialTheme.typography.bodySmall) }
                if (!canDelete) Text(stringResource(R.string.delete_tag_protected), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onReset) { Text(stringResource(R.string.restore_base_translation)) }
            TextButton(onClick = onExclude) {
                Icon(Icons.Default.VisibilityOff, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.exclude_tag_from_search))
            }
            if (canDelete) {
                TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete_tag), color = MaterialTheme.colorScheme.error) }
            }
        }
    }, confirmButton = { TextButton(onClick = { onSave(korean, aliases, category) }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

private fun sourceBadges(item: TagDictionaryItem): String = buildList {
    if (item.novelAiSource) add("N")
    if (item.danbooruSource) add("D")
    if (item.userCreated) add("C")
    if (item.bundled) add("B")
}.joinToString(" · ").ifBlank { "—" }

@Composable
fun TagPickerScreen(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onInserted: () -> Unit,
    onSelectTags: ((List<String>) -> Unit)? = null,
) {
    val tags by viewModel.dictionaryTags.collectAsStateWithLifecycle()
    val usedCategories by viewModel.usedTagCategories.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(TagDictionarySort.POPULAR) }
    val selected = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        viewModel.filterDictionary(TagDictionaryFilter.ALL)
        viewModel.filterDictionaryCategory("")
        viewModel.searchDictionary("")
    }
    LaunchedEffect(query) { viewModel.searchDictionary(query) }
    LaunchedEffect(category) { viewModel.filterDictionaryCategory(category) }
    LaunchedEffect(sort) { viewModel.sortDictionary(sort) }
    DisposableEffect(viewModel) {
        onDispose { viewModel.searchDictionary("") }
    }

    Scaffold(
        topBar = { AppTitleBar(R.string.tag_picker_title, onBack = onDismiss) },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (selected.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(selected, key = { it }) { tag ->
                            InputChip(selected = true, onClick = { selected.remove(tag) }, label = { Text(tag.replace('_', ' '), maxLines = 1) })
                        }
                    }
                    Button(
                        enabled = selected.isNotEmpty(),
                        onClick = {
                            if (onSelectTags != null) onSelectTags(selected.toList())
                            else { viewModel.insertDictionaryTags(selected.toList()); onInserted() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.insert_selected_tags, selected.size)) }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                placeholder = { Text(stringResource(R.string.search_tag_dictionary)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CategoryDropdown(category, usedCategories, compact = true) { category = it }
                SortDropdown(sort, Modifier.weight(1f)) { sort = it }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(tags, key = { it.id }) { tag ->
                    val isSelected = tag.canonicalTag in selected
                    Surface(
                        onClick = {
                            if (isSelected) selected.remove(tag.canonicalTag) else selected.add(tag.canonicalTag)
                        },
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(isSelected, onCheckedChange = null, modifier = Modifier.size(36.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tag.canonicalTag.replace('_', ' '), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                tag.korean?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                            }
                            tag.danbooruPostCount?.let { Text(compactCount(it), style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTagDialog(onDismiss: () -> Unit, categories: List<String>, onSave: (String, String, String, String) -> Unit) {
    var canonical by rememberSaveable { mutableStateOf("") }
    var korean by rememberSaveable { mutableStateOf("") }
    var aliases by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.add_user_tag)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(canonical, { canonical = it }, label = { Text(stringResource(R.string.canonical_tag)) }, singleLine = true)
            OutlinedTextField(korean, { korean = it }, label = { Text(stringResource(R.string.korean_translation)) }, singleLine = true)
            OutlinedTextField(aliases, { aliases = it }, label = { Text(stringResource(R.string.korean_aliases)) })
            CategoryDropdown(category, categories) { category = it }
        }
    }, confirmButton = { TextButton(enabled = canonical.isNotBlank(), onClick = { onSave(canonical, korean, aliases, category) }) { Text(stringResource(R.string.add)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

private fun compactCount(value: Long): String = when {
    value >= 1_000_000 -> "${value / 100_000 / 10.0}M"
    value >= 1_000 -> "${value / 100 / 10.0}K"
    else -> value.toString()
}

private fun compactCount(value: Double): String = compactCount(value.toLong())
private fun shortDate(epochMillis: Long): String = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(epochMillis))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selected: String,
    categories: List<String>,
    compact: Boolean = false,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val values = listOf("") + categories
    val emptyLabel = stringResource(if (compact) R.string.category_all else R.string.category_unspecified)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = if (selected.isEmpty()) emptyLabel else categoryLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = if (compact) null else {{ Text(stringResource(R.string.app_category)) }},
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            textStyle = if (compact) MaterialTheme.typography.bodySmall else LocalTextStyle.current,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).then(if (compact) Modifier.width(112.dp) else Modifier.fillMaxWidth()),
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 280.dp)) {
            values.distinct().forEach { value ->
                DropdownMenuItem(text = { Text(if (value.isEmpty()) emptyLabel else categoryLabel(value)) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortDropdown(selected: TagDictionarySort, modifier: Modifier = Modifier, onSelect: (TagDictionarySort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }, modifier) {
        OutlinedTextField(
            value = stringResource(selected.label), onValueChange = {}, readOnly = true, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Sort, null, Modifier.size(18.dp)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            TagDictionarySort.entries.forEach { value -> DropdownMenuItem(text = { Text(stringResource(value.label)) }, onClick = { onSelect(value); expanded = false }) }
        }
    }
}

private val TagDictionarySort.label: Int get() = when (this) {
    TagDictionarySort.POPULAR -> R.string.sort_popular
    TagDictionarySort.APP_USAGE -> R.string.sort_app_usage
    TagDictionarySort.RECENT -> R.string.sort_recent
    TagDictionarySort.NAME -> R.string.sort_name
}

@Composable
private fun AddCategoryDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.add_category)) }, text = {
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.category_name)) }, singleLine = true)
    }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name) }) { Text(stringResource(R.string.add)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun TranslationImportDialog(
    preview: com.hjhsys.naiblockprompt.domain.tags.TagTranslationImportPreview,
    onDismiss: () -> Unit,
    onApply: (Boolean, Set<String>, Boolean) -> Unit,
) {
    var overwrite by remember(preview) { mutableStateOf(false) }
    var deferReviewed by remember(preview) { mutableStateOf(true) }
    var selectedDeleteIds by remember(preview) { mutableStateOf(emptySet<String>()) }
    var confirmDeletion by remember(preview) { mutableStateOf(false) }
    if (confirmDeletion) {
        AlertDialog(
            onDismissRequest = { confirmDeletion = false },
            title = { Text(stringResource(R.string.translation_delete_title, selectedDeleteIds.size)) },
            text = { Text(stringResource(R.string.translation_delete_confirm)) },
            confirmButton = { TextButton(onClick = { onApply(overwrite, selectedDeleteIds, deferReviewed) }) { Text(stringResource(R.string.apply_import)) } },
            dismissButton = { TextButton(onClick = { confirmDeletion = false }) { Text(stringResource(R.string.cancel)) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.translation_import_preview)) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.translation_import_valid, preview.validRows.size - preview.reviewCount - preview.unchangedCount))
                Text(stringResource(R.string.translation_import_unchanged, preview.unchangedCount))
                Text(stringResource(R.string.translation_import_excluded, preview.excludedCandidates.size))
                Text(stringResource(R.string.translation_import_invalid, preview.invalidLines))
                Text(stringResource(R.string.translation_import_unknown, preview.unknownTags.size))
                Text(stringResource(R.string.translation_import_review, preview.reviewCount))
                if (preview.newCategories.isNotEmpty()) {
                    Text(stringResource(R.string.translation_import_new_categories, preview.newCategories.joinToString(", ")))
                }
                preview.excludedCandidates.forEach { candidate ->
                    Text(
                        "${candidate.row.tag} · ${candidate.row.exclusionReasonCode}: ${candidate.row.exclusionReasonText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(overwrite, { overwrite = it })
                    Text(stringResource(R.string.overwrite_existing_user_translation))
                }
                if (preview.reviewCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(deferReviewed, { deferReviewed = it })
                        Text(stringResource(R.string.translation_defer_reviewed))
                    }
                    HorizontalDivider()
                    Text(stringResource(R.string.translation_delete_candidates), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.translation_delete_hint), style = MaterialTheme.typography.bodySmall)
                    preview.validRows.filter { it.row.needsReview }.forEach { candidate ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = candidate.tagId in selectedDeleteIds,
                                enabled = candidate.canDelete,
                                onCheckedChange = { checked -> selectedDeleteIds = if (checked) selectedDeleteIds + candidate.tagId else selectedDeleteIds - candidate.tagId },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(candidate.row.tag, style = MaterialTheme.typography.bodyMedium)
                                Text(stringResource(if (!candidate.canDelete) R.string.translation_delete_protected else if (candidate.row.isTypo) R.string.translation_typo_flagged else R.string.translation_review_only), style = MaterialTheme.typography.labelSmall)
                                candidate.row.typoReason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = preview.validRows.isNotEmpty() || preview.excludedCandidates.isNotEmpty(), onClick = {
                if (selectedDeleteIds.isNotEmpty()) confirmDeletion = true else onApply(overwrite, emptySet(), deferReviewed)
            }) {
                Text(stringResource(R.string.apply_import))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AiTranslationExportScopeDialog(
    missingCount: Int,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onExport: (AiTranslationExportScope) -> Unit,
) {
    var scope by rememberSaveable { mutableStateOf(AiTranslationExportScope.MISSING_AND_SELECTED) }
    val options = listOf(
        AiTranslationExportScope.MISSING_ONLY to R.string.ai_export_scope_missing_only,
        AiTranslationExportScope.SELECTED_ONLY to R.string.ai_export_scope_selected_only,
        AiTranslationExportScope.MISSING_AND_SELECTED to R.string.ai_export_scope_missing_and_selected,
    )
    val canExport = when (scope) {
        AiTranslationExportScope.MISSING_ONLY -> missingCount > 0
        AiTranslationExportScope.SELECTED_ONLY -> selectedCount > 0
        AiTranslationExportScope.MISSING_AND_SELECTED -> missingCount > 0 || selectedCount > 0
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_export_scope_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.ai_export_scope_summary, missingCount, selectedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                options.forEach { (option, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { scope = option },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = scope == option, onClick = { scope = option })
                        Text(stringResource(label))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canExport, onClick = { onExport(scope) }) {
                Text(stringResource(R.string.copy_for_ai))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TranslationExportOptionsDialog(
    onDismiss: () -> Unit,
    onExport: (Boolean, Boolean, Boolean) -> Unit,
) {
    var missingTranslation by rememberSaveable { mutableStateOf(true) }
    var missingCategory by rememberSaveable { mutableStateOf(true) }
    var includeDeferred by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.translation_export_options)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.translation_export_options_hint), style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { missingTranslation = !missingTranslation }) {
                    Checkbox(missingTranslation, { missingTranslation = it })
                    Text(stringResource(R.string.export_missing_translation))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { missingCategory = !missingCategory }) {
                    Checkbox(missingCategory, { missingCategory = it })
                    Text(stringResource(R.string.export_missing_category))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { includeDeferred = !includeDeferred }) {
                    Checkbox(includeDeferred, { includeDeferred = it })
                    Text(stringResource(R.string.translation_include_deferred))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = missingTranslation || missingCategory || includeDeferred, onClick = { onExport(missingTranslation, missingCategory, includeDeferred) }) {
                Text(stringResource(R.string.export_file))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun categoryLabel(value: String): String = when (value) {
    "" -> ""
    "clothes" -> stringResource(R.string.category_clothes)
    "pose" -> stringResource(R.string.category_pose)
    "hair" -> stringResource(R.string.category_hair)
    "body" -> stringResource(R.string.category_body)
    "expression" -> stringResource(R.string.category_expression)
    "accessory" -> stringResource(R.string.category_accessory)
    "background" -> stringResource(R.string.category_background)
    "composition" -> stringResource(R.string.category_composition)
    "lighting" -> stringResource(R.string.category_lighting)
    "effect" -> stringResource(R.string.category_effect)
    "other" -> stringResource(R.string.category_other)
    "general" -> stringResource(R.string.category_danbooru_general)
    "artist" -> stringResource(R.string.category_danbooru_artist)
    "copyright" -> stringResource(R.string.category_danbooru_copyright)
    "character" -> stringResource(R.string.category_danbooru_character)
    "meta" -> stringResource(R.string.category_danbooru_meta)
    else -> value
}
