package com.hjhsys.naiblockprompt.ui.generate

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hjhsys.naiblockprompt.R
import com.hjhsys.naiblockprompt.domain.editor.*
import com.hjhsys.naiblockprompt.domain.model.*
import com.hjhsys.naiblockprompt.domain.prompt.PromptProcessor
import com.hjhsys.naiblockprompt.ui.MainViewModel
import com.hjhsys.naiblockprompt.ui.GenerationUiState
import com.hjhsys.naiblockprompt.ui.components.AppTitleBar
import com.hjhsys.naiblockprompt.data.network.nai.NaiApiFailure
import com.hjhsys.naiblockprompt.domain.generation.MissingGenerationField
import com.hjhsys.naiblockprompt.domain.generation.NaiCatalogOption
import com.hjhsys.naiblockprompt.domain.generation.NaiGenerationCatalog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File
import androidx.compose.ui.window.Dialog

@Composable
fun GenerateScreen(
    session: Session?,
    appSettings: AppSettings,
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
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
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val duplicateWarning by viewModel.duplicateWarning.collectAsStateWithLifecycle()
    var workspace by rememberSaveable { mutableStateOf(GenerateWorkspace.EDITOR) }
    var showCharacterTypeDialog by rememberSaveable { mutableStateOf(false) }
    val hasStash by viewModel.hasStash.collectAsStateWithLifecycle()
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
    Scaffold(
        topBar = {
            AppTitleBar(R.string.generate_title, onOpenSettings = onOpenSettings)
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        GenerateWorkspace.entries.forEachIndexed { index, value ->
                            SegmentedButton(
                                selected = workspace == value,
                                onClick = { workspace = value },
                                shape = SegmentedButtonDefaults.itemShape(index, GenerateWorkspace.entries.size),
                                label = { Text(stringResource(if (value == GenerateWorkspace.EDITOR) R.string.workspace_editor else R.string.workspace_result)) },
                                icon = { Icon(if (value == GenerateWorkspace.EDITOR) Icons.Default.Edit else Icons.Default.Image, contentDescription = null) },
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = { viewModel.generate(); workspace = GenerateWorkspace.RESULT },
                        enabled = generationState !is GenerationUiState.Loading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        if (generationState is GenerationUiState.Loading) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(if (generationState is GenerationUiState.Loading) R.string.generating else R.string.generate_one_image))
                    }
                }
            }
        },
    ) { innerPadding -> LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = viewModel::beginPresetSave) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.save_preset))
                }
                TextButton(onClick = viewModel::swapStash, enabled = hasStash) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.previous_work_short))
                }
            }
        }
        if (workspace == GenerateWorkspace.EDITOR) {
        item {
            PromptSectionCard(
                title = stringResource(R.string.base_prompt),
                owner = PromptOwner.Base,
                pair = session.base.prompts,
                selectedPolarity = session.base.selectedPolarity,
                textRendering = session.base.textRendering,
                showFormatter = appSettings.showFormatterActions,
                viewModel = viewModel,
            )
        }
        itemsIndexed(session.characters.sortedBy { it.order }, key = { _, item -> item.id }) { index, character ->
            CharacterSectionCard(index, character, session.characters.size, appSettings.showFormatterActions, viewModel)
        }
        item {
            OutlinedButton(
                onClick = { showCharacterTypeDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_character))
            }
        }
        item { GenerationSettingsCard(session.generationSettings, viewModel) }
        item { PromptPreviewCard(session, appSettings.normalizeWeightClosings) }
        } else {
            item { GenerationCard(generationState, viewModel) }
        }
        item {
            Text(stringResource(R.string.not_official_notice), style = MaterialTheme.typography.bodySmall)
        }
    } }
}

private enum class GenerateWorkspace { EDITOR, RESULT }

@Composable
private fun GenerationCard(state: GenerationUiState, viewModel: MainViewModel) {
    var showOriginal by rememberSaveable { mutableStateOf(false) }
    val success = state as? GenerationUiState.Success
    if (showOriginal && success != null) {
        Dialog(onDismissRequest = { showOriginal = false }) {
            Surface(shape = MaterialTheme.shapes.large) {
                AsyncImage(
                    model = File(success.record.imagePath),
                    contentDescription = stringResource(R.string.generated_image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).clickable { showOriginal = false },
                )
            }
        }
    }
    ElevatedCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.latest_generation), style = MaterialTheme.typography.titleLarge)
            if (success != null) {
                AsyncImage(
                    model = File(success.record.imagePath),
                    contentDescription = stringResource(R.string.generated_image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).clickable { showOriginal = true },
                )
                Text(stringResource(R.string.used_seed, success.record.seed), style = MaterialTheme.typography.bodySmall)
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
        }
    }
    if (message != null) Text(stringResource(message), color = MaterialTheme.colorScheme.error)
    if (state is GenerationUiState.Failed && state.error is NaiApiFailure.Network) {
        state.error.reason?.let { reason ->
            SelectionContainer { Text(reason, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

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
    showFormatter: Boolean,
    viewModel: MainViewModel,
) {
    ElevatedCard {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.character_prompt, index + 1),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                SmallIconButton(R.string.load_set, Icons.Default.FolderOpen, true) { viewModel.beginSetLoad(PromptOwner.Character(character.id)) }
                SmallIconButton(R.string.save_set, Icons.Default.Save, true) { viewModel.beginSetSave(PromptOwner.Character(character.id)) }
                SmallIconButton(R.string.move_up, Icons.Default.ArrowUpward, index > 0) {
                    viewModel.moveCharacter(character.id, MoveDirection.UP)
                }
                SmallIconButton(R.string.move_down, Icons.Default.ArrowDownward, index < count - 1) {
                    viewModel.moveCharacter(character.id, MoveDirection.DOWN)
                }
                SmallIconButton(R.string.delete, Icons.Default.Delete, true) { viewModel.removeCharacter(character.id) }
            }
            PromptSectionContent(
                owner = PromptOwner.Character(character.id),
                pair = character.prompts,
                selectedPolarity = character.selectedPolarity,
                textRendering = character.textRendering,
                showFormatter = showFormatter,
                viewModel = viewModel,
            )
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
    showFormatter: Boolean,
    viewModel: MainViewModel,
) {
    ElevatedCard {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                SmallIconButton(R.string.load_set, Icons.Default.FolderOpen, true) { viewModel.beginSetLoad(owner) }
                SmallIconButton(R.string.save_set, Icons.Default.Save, true) { viewModel.beginSetSave(owner) }
            }
            PromptSectionContent(owner, pair, selectedPolarity, textRendering, showFormatter, viewModel)
        }
    }
}

@Composable
private fun PromptSectionContent(
    owner: PromptOwner,
    pair: PromptPair,
    selectedPolarity: PromptPolarity,
    textRendering: TextRenderingState,
    showFormatter: Boolean,
    viewModel: MainViewModel,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PromptPolarity.entries.forEach { polarity ->
            FilterChip(
                selected = selectedPolarity == polarity,
                onClick = { viewModel.selectPolarity(owner, polarity) },
                label = { Text(stringResource(polarity.labelResource)) },
                modifier = Modifier.weight(1f),
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
            block = block,
            canMoveUp = index > 0,
            canMoveDown = index < blocks.lastIndex,
            showFormatter = showFormatter,
            onUpdate = { transform -> viewModel.updateBlock(owner, selectedPolarity, block.id, transform) },
            onEnabledChange = { viewModel.setBlockEnabled(owner, selectedPolarity, block.id, it) },
            onCollapsedChange = { viewModel.setBlockCollapsed(owner, selectedPolarity, block.id, it) },
            onLockedChange = { viewModel.setBlockLocked(owner, selectedPolarity, block.id, it) },
            onMove = { viewModel.moveBlock(owner, selectedPolarity, block.id, it) },
            onDelete = { viewModel.removeBlock(owner, selectedPolarity, block.id) },
            onLoad = { viewModel.beginBlockLoad(owner, selectedPolarity, block.id) },
            onSave = { viewModel.beginBlockSave(block) },
            onFormat = { viewModel.formatBlock(owner, selectedPolarity, block.id, it) },
        )
    }
    OutlinedButton(
        onClick = { viewModel.addBlock(owner, selectedPolarity, newBlockName) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.add_block))
    }
    if (selectedPolarity == PromptPolarity.POSITIVE) {
        TextRenderingSlot(
            state = textRendering,
            onEnabledChange = { enabled -> viewModel.updateTextRendering(owner) { it.copy(enabled = enabled) } },
            onContentChange = { content -> viewModel.updateTextRendering(owner) { it.copy(content = content) } },
        )
    }
}

@Composable
private fun TextRenderingSlot(
    state: TextRenderingState,
    onEnabledChange: (Boolean) -> Unit,
    onContentChange: (String) -> Unit,
) {
    OutlinedCard {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.text_rendering),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(if (state.enabled) R.string.on else R.string.off), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(4.dp))
                Switch(
                    checked = state.enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.heightIn(max = 40.dp),
                )
            }
            if (state.enabled) {
                Text(stringResource(R.string.text_rendering_prefix), style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = state.content,
                    onValueChange = onContentChange,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.text_rendering_hint)) },
                )
            }
        }
    }
}

@Composable
private fun PromptBlockCard(
    block: PromptBlock,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    showFormatter: Boolean,
    onUpdate: ((PromptBlock) -> PromptBlock) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onCollapsedChange: (Boolean) -> Unit,
    onLockedChange: (Boolean) -> Unit,
    onMove: (MoveDirection) -> Unit,
    onDelete: () -> Unit,
    onLoad: () -> Unit,
    onSave: () -> Unit,
    onFormat: (BlockFormatter) -> Unit,
) {
    val validation = remember(block.content) { PromptProcessor.validateWeights(block.content) }
    val randomizerValidation = remember(block.content) { PromptProcessor.validateRandomizers(block.content) }
    var confirmDelete by rememberSaveable(block.id) { mutableStateOf(false) }
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = block.name,
                    onValueChange = { name -> onUpdate { it.copy(name = name) } },
                    enabled = !block.locked,
                    label = { Text(stringResource(R.string.block_name)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = block.enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                SmallIconButton(
                    if (block.locked) R.string.locked else R.string.unlocked,
                    if (block.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                    true,
                ) { onLockedChange(!block.locked) }
                SmallIconButton(
                    if (block.collapsed) R.string.expand else R.string.collapse,
                    if (block.collapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                    true,
                ) { onCollapsedChange(!block.collapsed) }
                SmallIconButton(R.string.move_up, Icons.Default.ArrowUpward, canMoveUp && !block.locked) { onMove(MoveDirection.UP) }
                SmallIconButton(R.string.move_down, Icons.Default.ArrowDownward, canMoveDown && !block.locked) { onMove(MoveDirection.DOWN) }
                SmallIconButton(R.string.load_saved_block, Icons.Default.FolderOpen, !block.locked, onLoad)
                SmallIconButton(R.string.save_block, Icons.Default.BookmarkAdd, true, onSave)
                SmallIconButton(R.string.delete, Icons.Default.Delete, !block.locked) { confirmDelete = true }
            }
            if (!block.collapsed) {
                val highColor = MaterialTheme.colorScheme.error
                val lowColor = MaterialTheme.colorScheme.primary
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
                OutlinedTextField(
                    value = block.content,
                    onValueChange = { content -> onUpdate { it.copy(content = content) } },
                    enabled = !block.locked,
                    label = { Text(stringResource(R.string.prompt_content)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = remember(highColor, lowColor, commentColor, randomizerColor) {
                        PromptVisualTransformation(highColor, lowColor, commentColor, randomizerColor)
                    },
                )
                Text(stringResource(R.string.comment_hint), style = MaterialTheme.typography.bodySmall)
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
                if (showFormatter) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onFormat(BlockFormatter.MULTILINE) },
                            enabled = !block.locked,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.format_multiline), maxLines = 1) }
                        OutlinedButton(
                            onClick = { onFormat(BlockFormatter.SINGLE_LINE) },
                            enabled = !block.locked,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.format_single_line), maxLines = 1) }
                    }
                }
            } else {
                Text(block.content.replace('\n', ' '), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun GenerationSettingsCard(settings: GenerationSettings, viewModel: MainViewModel) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    ElevatedCard {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.generation_settings), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight, contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand))
                }
            }
            if (expanded) {
                Text(stringResource(R.string.model_unverified_hint), style = MaterialTheme.typography.bodySmall)
                CatalogDropdown(
                    label = R.string.model_id,
                    selectedId = settings.modelId,
                    options = NaiGenerationCatalog.models,
                ) { value -> viewModel.updateGenerationSettings { it.copy(modelId = value) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberSettingField(R.string.width, settings.width.toString(), Modifier.weight(1f)) { value ->
                        value.toIntOrNull()?.takeIf { it > 0 }?.let { width -> viewModel.updateGenerationSettings { it.copy(width = width) } }
                    }
                    NumberSettingField(R.string.height, settings.height.toString(), Modifier.weight(1f)) { value ->
                        value.toIntOrNull()?.takeIf { it > 0 }?.let { height -> viewModel.updateGenerationSettings { it.copy(height = height) } }
                    }
                }
                CatalogDropdown(
                    label = R.string.sampler,
                    selectedId = settings.samplerId,
                    options = NaiGenerationCatalog.samplers,
                ) { value -> viewModel.updateGenerationSettings { it.copy(samplerId = value) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberSettingField(R.string.steps, settings.steps?.toString().orEmpty(), Modifier.weight(1f)) { value ->
                        viewModel.updateGenerationSettings { it.copy(steps = value.toIntOrNull()) }
                    }
                    NumberSettingField(R.string.scale, settings.scale?.toString().orEmpty(), Modifier.weight(1f), decimal = true) { value ->
                        viewModel.updateGenerationSettings { it.copy(scale = value.toFloatOrNull()) }
                    }
                }
                Text(stringResource(R.string.seed), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeedMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.seedMode == mode,
                            onClick = { viewModel.updateGenerationSettings { it.copy(seedMode = mode, seed = if (mode == SeedMode.RANDOM) null else it.seed) } },
                            label = { Text(stringResource(if (mode == SeedMode.RANDOM) R.string.seed_random else R.string.seed_fixed)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (settings.seedMode == SeedMode.FIXED) {
                    NumberSettingField(R.string.seed, settings.seed?.toString().orEmpty(), Modifier.fillMaxWidth()) { value ->
                        viewModel.updateGenerationSettings { it.copy(seed = value.toLongOrNull()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptPreviewCard(session: Session, normalize: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    OutlinedCard {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.prompt_preview_debug), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(40.dp)) {
                    Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight, contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand))
                }
            }
            if (expanded) {
                PreviewLine(R.string.base_positive_preview, PromptProcessor.appendTextRendering(PromptProcessor.joinEnabledBlocks(session.base.prompts.positiveBlocks, normalize), session.base.textRendering))
                PreviewLine(R.string.base_negative_preview, PromptProcessor.joinEnabledBlocks(session.base.prompts.negativeBlocks, normalize))
                session.characters.sortedBy { it.order }.forEachIndexed { index, character ->
                    PreviewLine(R.string.character_positive_preview, PromptProcessor.appendTextRendering(PromptProcessor.joinEnabledBlocks(character.prompts.positiveBlocks, normalize), character.textRendering), index + 1)
                    PreviewLine(R.string.character_negative_preview, PromptProcessor.joinEnabledBlocks(character.prompts.negativeBlocks, normalize), index + 1)
                }
            }
        }
    }
}

@Composable
private fun PreviewLine(@StringRes label: Int, prompt: String, formatArg: Int? = null) {
    Text(if (formatArg == null) stringResource(label) else stringResource(label, formatArg), style = MaterialTheme.typography.labelLarge)
    SelectionContainer { Text(prompt.ifBlank { stringResource(R.string.empty_prompt) }, style = MaterialTheme.typography.bodySmall) }
}

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

private class PromptVisualTransformation(
    private val highColor: Color,
    private val lowColor: Color,
    private val commentColor: Color,
    private val randomizerColor: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text)
        PromptProcessor.weightSpans(text.text).forEach { span ->
            val color = when {
                span.weight > 1f -> highColor.copy(alpha = ((span.weight - 1f) / 1f).coerceIn(0.55f, 1f))
                span.weight < 1f -> lowColor.copy(alpha = ((1f - span.weight) / 0.7f).coerceIn(0.55f, 1f))
                else -> Color.Unspecified
            }
            if (color != Color.Unspecified) builder.addStyle(SpanStyle(color = color), span.start, span.endExclusive)
        }
        PromptProcessor.randomizerSpans(text.text).forEach { span ->
            builder.addStyle(SpanStyle(color = randomizerColor), span.start, span.endExclusive)
        }
        // Apply comments last so they take precedence over an enclosing weight span.
        PromptProcessor.commentSpans(text.text).forEach { span ->
            builder.addStyle(SpanStyle(color = commentColor), span.start, span.endExclusive)
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
