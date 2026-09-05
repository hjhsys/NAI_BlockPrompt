package com.hjhsys.naiblockprompt.ui.database

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
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
    val totalCount by viewModel.tagCount.collectAsStateWithLifecycle()
    val missingTranslationCount by viewModel.missingTranslationCount.collectAsStateWithLifecycle()
    val missingCategoryCount by viewModel.missingCategoryCount.collectAsStateWithLifecycle()
    val usedCategories by viewModel.usedTagCategories.collectAsStateWithLifecycle()
    val userCategories by viewModel.userTagCategories.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val importPreview by viewModel.translationImportPreview.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var exportContent by remember { mutableStateOf(byteArrayOf()) }
    var exportFileName by remember { mutableStateOf("nai_tags_translation_batch.zip") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.use { output -> output.write(exportContent) } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> viewModel.previewTranslationImport(reader.readText()) } }
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
    ) }
    if (adding) AddTagDialog({ adding = false }, (AppTagCategory.entries.map { it.value } + userCategories).distinct()) { canonical, ko, aliases, category ->
        viewModel.addUserTag(canonical, ko, aliases, category); adding = false
    }
    if (addingCategory) AddCategoryDialog({ addingCategory = false }) { viewModel.addTagCategory(it); addingCategory = false }
    if (showExportOptions) TranslationExportOptionsDialog(
        onDismiss = { showExportOptions = false },
        onExport = { missingTranslation, missingCategory ->
            showExportOptions = false
            if (exportAllBatches) viewModel.prepareAllTranslationExport(missingTranslation, missingCategory)
            else viewModel.prepareTranslationExport(missingTranslation, missingCategory)
        },
    )
    importPreview?.let { preview ->
        TranslationImportDialog(
            preview = preview,
            onDismiss = viewModel::dismissTranslationImport,
            onApply = viewModel::applyTranslationImport,
        )
    }

    Scaffold(
        topBar = {
            AppTitleBar(
                R.string.db_title,
                directAction = AppTitleMenuItem(R.string.add, Icons.Default.Add, onClick = { showAddMenu = true }),
                secondaryDirectAction = AppTitleMenuItem(R.string.filter_favorites, if (filter == TagDictionaryFilter.FAVORITES) Icons.Default.Star else Icons.Default.StarBorder, onClick = {
                    filter = if (filter == TagDictionaryFilter.FAVORITES) TagDictionaryFilter.ALL else TagDictionaryFilter.FAVORITES
                }),
                menuItems = listOf(
                    AppTitleMenuItem(R.string.export_shared_tag_db, Icons.Default.Share, onClick = viewModel::exportSharedTagDatabase),
                    AppTitleMenuItem(R.string.import_shared_tag_db, Icons.Default.Download, onClick = { sharedImportLauncher.launch(arrayOf("application/zip")) }),
                    AppTitleMenuItem(R.string.nav_settings, Icons.Default.Settings, onClick = onOpenSettings),
                ),
            )
            DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.add_user_tag)) }, leadingIcon = { Icon(Icons.Default.Label, null) }, onClick = { showAddMenu = false; adding = true })
                DropdownMenuItem(text = { Text(stringResource(R.string.add_category)) }, leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) }, onClick = { showAddMenu = false; addingCategory = true })
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.ai_translation_tools), style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.missing_translation_count, missingTranslationCount), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.missing_category_count, missingCategoryCount), style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { exportAllBatches = false; showExportOptions = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.UploadFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.export_for_ai), maxLines = 1)
                            }
                            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "application/x-ndjson", "text/plain")) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.import_ai_result), maxLines = 1)
                            }
                        }
                    }
                }
            }
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
            item { Text(if (totalCount == 0) stringResource(R.string.tag_db_importing) else stringResource(R.string.tag_dictionary_count, totalCount, tags.size), style = MaterialTheme.typography.bodySmall) }
            if (tags.isEmpty()) item { Text(stringResource(R.string.tag_dictionary_empty)) }
            items(tags, key = { it.id }) { tag ->
                ElevatedCard(Modifier.fillMaxWidth().combinedClickable(
                    onClick = { editing = tag },
                    onLongClick = { editing = tag },
                )) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
) {
    var korean by rememberSaveable(item.id) { mutableStateOf(item.korean.orEmpty()) }
    var aliases by rememberSaveable(item.id) { mutableStateOf(item.koreanAliases.orEmpty()) }
    var category by rememberSaveable(item.id) { mutableStateOf(item.appCategory.orEmpty()) }
    var showGeneratedImages by rememberSaveable(item.id) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(item.id) { mutableStateOf(false) }
    val canDelete = item.userCreated && !item.novelAiSource && !item.danbooruSource && !item.bundled
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(onChooseThumbnail) }
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
            Text(stringResource(R.string.tag_sources, sourceBadges(item)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            item.thumbnailPath?.let { AsyncImage(File(it), stringResource(R.string.tag_thumbnail), Modifier.fillMaxWidth().height(120.dp), contentScale = ContentScale.Crop) }
            item.danbooruPostCount?.let { Text(stringResource(R.string.tag_metric_public_uses, compactCount(it)), style = MaterialTheme.typography.bodySmall) }
            item.naiCount?.let { Text(stringResource(R.string.tag_metric_nai_count, compactCount(it)), style = MaterialTheme.typography.bodySmall) }
            Text(stringResource(R.string.tag_metric_app_uses, item.useCount), style = MaterialTheme.typography.bodySmall)
            item.lastUsedAt?.let { Text(stringResource(R.string.tag_metric_last_used, shortDate(it)), style = MaterialTheme.typography.bodySmall) }
            OutlinedTextField(korean, { korean = it }, label = { Text(stringResource(R.string.korean_translation)) }, singleLine = true)
            OutlinedTextField(aliases, { aliases = it }, label = { Text(stringResource(R.string.korean_aliases)) })
            CategoryDropdown(category, categories) { category = it }
            Row {
                TextButton(onClick = { imagePicker.launch("image/*") }) { Text(stringResource(R.string.choose_image_file)) }
                if (generatedImages.isNotEmpty()) TextButton(onClick = { showGeneratedImages = true }) { Text(stringResource(R.string.choose_generated_image)) }
            }
            item.englishAliases?.let { Text(stringResource(R.string.english_aliases, it), style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = onReset) { Text(stringResource(R.string.restore_base_translation)) }
            if (canDelete) {
                TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete_tag), color = MaterialTheme.colorScheme.error) }
            } else {
                Text(stringResource(R.string.delete_tag_protected), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        onClick = { viewModel.insertDictionaryTags(selected.toList()); onInserted() },
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
    onApply: (Boolean) -> Unit,
) {
    var overwrite by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.translation_import_preview)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.translation_import_valid, preview.validRows.size - preview.reviewCount))
                Text(stringResource(R.string.translation_import_invalid, preview.invalidLines))
                Text(stringResource(R.string.translation_import_unknown, preview.unknownTags.size))
                Text(stringResource(R.string.translation_import_review, preview.reviewCount))
                if (preview.newCategories.isNotEmpty()) {
                    Text(stringResource(R.string.translation_import_new_categories, preview.newCategories.joinToString(", ")))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(overwrite, { overwrite = it })
                    Text(stringResource(R.string.overwrite_existing_user_translation))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = preview.validRows.isNotEmpty(), onClick = { onApply(overwrite) }) {
                Text(stringResource(R.string.apply_import))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TranslationExportOptionsDialog(
    onDismiss: () -> Unit,
    onExport: (Boolean, Boolean) -> Unit,
) {
    var missingTranslation by rememberSaveable { mutableStateOf(true) }
    var missingCategory by rememberSaveable { mutableStateOf(true) }
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
            }
        },
        confirmButton = {
            TextButton(enabled = missingTranslation || missingCategory, onClick = { onExport(missingTranslation, missingCategory) }) {
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
