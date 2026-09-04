package com.hjhsys.naiblockprompt.ui.generate

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.onFocusChanged
import com.hjhsys.naiblockprompt.R
import com.hjhsys.naiblockprompt.domain.editor.*
import com.hjhsys.naiblockprompt.domain.model.*
import com.hjhsys.naiblockprompt.domain.prompt.PromptProcessor
import com.hjhsys.naiblockprompt.ui.MainViewModel
import com.hjhsys.naiblockprompt.ui.GenerationUiState
import com.hjhsys.naiblockprompt.ui.SubscriptionUiState
import com.hjhsys.naiblockprompt.ui.components.AppTitleBar
import com.hjhsys.naiblockprompt.ui.components.AppTitleMenuItem
import com.hjhsys.naiblockprompt.data.network.nai.NaiApiFailure
import com.hjhsys.naiblockprompt.domain.generation.MissingGenerationField
import com.hjhsys.naiblockprompt.domain.generation.NaiCatalogOption
import com.hjhsys.naiblockprompt.domain.generation.NaiGenerationCatalog
import com.hjhsys.naiblockprompt.domain.generation.CharacterPositioning
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File
import android.net.Uri
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.hjhsys.naiblockprompt.domain.image.NaiPngMetadataParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.hjhsys.naiblockprompt.domain.autocomplete.PromptAutocomplete
import com.hjhsys.naiblockprompt.domain.autocomplete.TagSuggestion

@Composable
fun GenerateScreen(
    session: Session?,
    appSettings: AppSettings,
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenGenerationSettings: () -> Unit,
    onOpenTagDatabase: () -> Unit,
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
    var showCharacterTypeDialog by rememberSaveable { mutableStateOf(false) }
    val hasStash by viewModel.hasStash.collectAsStateWithLifecycle()
    val subscriptionStatus by viewModel.subscriptionStatus.collectAsStateWithLifecycle()
    val modelSummary = compactModelName(session.generationSettings.modelId)
    val seedSummary = stringResource(if (session.generationSettings.seedMode == SeedMode.RANDOM) R.string.seed_status_random else R.string.seed_status_fixed)
    var activeTagTarget by remember { mutableStateOf<TagEditorTarget?>(null) }
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
            AppTitleBar(
                R.string.generate_title,
                subtitle = stringResource(R.string.generate_status_summary, modelSummary, seedSummary),
                trailingSubtitle = (subscriptionStatus as? SubscriptionUiState.Available)?.let { balance ->
                    stringResource(R.string.balance_summary, balance.anlas?.toString() ?: "—", balance.opusPercent?.toString() ?: "—")
                },
                menuItems = listOf(
                    AppTitleMenuItem(R.string.load_preset, Icons.Default.FolderOpen, onClick = viewModel::beginPresetLoad),
                    AppTitleMenuItem(R.string.save_preset, Icons.Default.Save, onClick = viewModel::beginPresetSave),
                    AppTitleMenuItem(R.string.previous_work, Icons.Default.Restore, enabled = hasStash, onClick = viewModel::swapStash),
                    AppTitleMenuItem(R.string.nav_settings, Icons.Default.Settings, onClick = onOpenSettings),
                ),
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Button(
                        onClick = viewModel::generate,
                        enabled = generationState !is GenerationUiState.Loading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        if (generationState is GenerationUiState.Loading) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(generateButtonLabel(generationState, session.generationSettings.imageInput))
                    }
                }
            }
        },
    ) { innerPadding -> Box(Modifier.fillMaxSize().padding(innerPadding)) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PromptSectionCard(
                title = stringResource(R.string.base_prompt),
                owner = PromptOwner.Base,
                pair = session.base.prompts,
                selectedPolarity = session.base.selectedPolarity,
                textRendering = session.base.textRendering,
                showFormatter = appSettings.showFormatterActions,
                viewModel = viewModel,
                onTagEditorState = { owner, polarity, blockId, focused, cursor ->
                    activeTagTarget = if (focused) TagEditorTarget(owner, polarity, blockId, cursor)
                    else activeTagTarget?.takeUnless { it.blockId == blockId }
                },
            )
        }
        itemsIndexed(session.characters.sortedBy { it.order }, key = { _, item -> item.id }) { index, character ->
            CharacterSectionCard(index, character, session.characters.size, appSettings.showFormatterActions, viewModel) { owner, polarity, blockId, focused, cursor ->
                activeTagTarget = if (focused) TagEditorTarget(owner, polarity, blockId, cursor)
                else activeTagTarget?.takeUnless { it.blockId == blockId }
            }
        }
        if (session.characters.isNotEmpty()) item {
            CharacterPositioningButton(session, viewModel)
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
        item { PromptPreviewCard(session, appSettings.normalizeWeightClosings) }
        item {
            Text(stringResource(R.string.not_official_notice), style = MaterialTheme.typography.bodySmall)
        }
      }
      activeTagTarget?.let { target ->
          SmallFloatingActionButton(
              onClick = {
                  viewModel.beginTagInsert(target.owner, target.polarity, target.blockId, target.cursor)
                  onOpenTagDatabase()
              },
              modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
          ) { Icon(Icons.Default.Storage, stringResource(R.string.open_tag_dictionary)) }
      }
    } }
}

@Composable
private fun CharacterPositioningButton(session: Session, viewModel: MainViewModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.ControlCamera, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(
                R.string.character_positioning_status,
                stringResource(if (session.characters.all { it.position != null }) R.string.position_custom else R.string.position_ai_choice),
            ),
        )
    }
    if (open) CharacterPositioningDialog(session, viewModel) { open = false }
}

@Composable
private fun CharacterPositioningDialog(session: Session, viewModel: MainViewModel, onDismiss: () -> Unit) {
    val characters = session.characters.sortedBy { it.order }
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
                            Surface(
                                modifier = Modifier.offset(maxWidth * position.normalizedX - 18.dp, maxHeight * position.normalizedY - 18.dp).size(36.dp),
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

@Composable
fun GenerationSettingsScreen(session: Session?, viewModel: MainViewModel, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    Scaffold(
        topBar = {
            AppTitleBar(
                R.string.generation_settings,
                menuItems = listOf(AppTitleMenuItem(R.string.nav_settings, Icons.Default.Settings, onClick = onOpenSettings)),
                onBack = onBack,
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
                item { GenerationSettingsCard(session.generationSettings, viewModel, onMetadataImported = onBack) }
                item { Text(stringResource(R.string.generation_settings_swipe_hint), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun GenerationCard(state: GenerationUiState, viewModel: MainViewModel) {
    var showOriginal by rememberSaveable { mutableStateOf(false) }
    val success = state as? GenerationUiState.Success
    if (showOriginal && success != null) {
        Dialog(onDismissRequest = { showOriginal = false }) {
            Surface(shape = MaterialTheme.shapes.large) {
                AsyncImage(
                    model = imageModel(success.record.imagePath),
                    contentDescription = stringResource(R.string.generated_image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).clickable { showOriginal = false },
                )
            }
        }
    }
    ElevatedCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (success != null) {
                AsyncImage(
                    model = imageModel(success.record.imagePath),
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
fun ResultScreen(viewModel: MainViewModel, onOpenHistory: () -> Unit) {
    val state by viewModel.generationState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            AppTitleBar(
                R.string.workspace_result,
                directAction = AppTitleMenuItem(R.string.history_title, Icons.Default.History, onClick = onOpenHistory),
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = viewModel::generate,
                    enabled = state !is GenerationUiState.Loading,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).heightIn(min = 48.dp),
                ) {
                    if (state is GenerationUiState.Loading) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(if (state is GenerationUiState.Loading) R.string.generating else R.string.generate_one_image))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(12.dp), contentAlignment = Alignment.Center) {
            GenerationCard(state, viewModel)
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
    showFormatter: Boolean,
    viewModel: MainViewModel,
    onTagEditorState: (PromptOwner, PromptPolarity, String, Boolean, Int) -> Unit,
) {
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
    ElevatedCard {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.character_prompt, index + 1),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
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
            PromptSectionContent(
                owner = PromptOwner.Character(character.id),
                pair = character.prompts,
                selectedPolarity = character.selectedPolarity,
                textRendering = character.textRendering,
                showFormatter = showFormatter,
                viewModel = viewModel,
                onTagEditorState = onTagEditorState,
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
    onTagEditorState: (PromptOwner, PromptPolarity, String, Boolean, Int) -> Unit,
) {
    ElevatedCard {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                SmallIconButton(R.string.load_set, Icons.Default.FolderOpen, true) { viewModel.beginSetLoad(owner) }
                SmallIconButton(R.string.save_set, Icons.Default.Save, true) { viewModel.beginSetSave(owner) }
            }
            PromptSectionContent(owner, pair, selectedPolarity, textRendering, showFormatter, viewModel, onTagEditorState)
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
    onTagEditorState: (PromptOwner, PromptPolarity, String, Boolean, Int) -> Unit,
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
            viewModel = viewModel,
            onEditorState = { focused, cursor -> onTagEditorState(owner, selectedPolarity, block.id, focused, cursor) },
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
    if (owner == PromptOwner.Base) {
        Text(stringResource(R.string.comment_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TextRenderingSlot(
    state: TextRenderingState,
    onEnabledChange: (Boolean) -> Unit,
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
    viewModel: MainViewModel,
    onEditorState: (Boolean, Int) -> Unit,
) {
    val validation = remember(block.content) { PromptProcessor.validateWeights(block.content) }
    val randomizerValidation = remember(block.content) { PromptProcessor.validateRandomizers(block.content) }
    var confirmDelete by rememberSaveable(block.id) { mutableStateOf(false) }
    var renameBlock by rememberSaveable(block.id) { mutableStateOf(false) }
    var renameValue by rememberSaveable(block.id) { mutableStateOf(block.name) }
    var showMore by remember { mutableStateOf(false) }
    var editorFocused by remember { mutableStateOf(false) }
    val autocomplete by viewModel.autocomplete.collectAsStateWithLifecycle()
    var editorValue by remember(block.id) {
        mutableStateOf(TextFieldValue(block.content, TextRange(block.content.length)))
    }
    LaunchedEffect(block.content) {
        if (editorValue.text != block.content) {
            editorValue = editorValue.copy(
                text = block.content,
                selection = TextRange(editorValue.selection.end.coerceAtMost(block.content.length)),
            )
        }
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = block.enabled, onCheckedChange = onEnabledChange)
                    Text(block.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    SmallIconButton(
                        if (block.collapsed) R.string.expand else R.string.collapse,
                        if (block.collapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                        true,
                    ) { onCollapsedChange(!block.collapsed) }
                }
                SmallIconButton(R.string.move_up, Icons.Default.ArrowUpward, canMoveUp && !block.locked) { onMove(MoveDirection.UP) }
                SmallIconButton(R.string.move_down, Icons.Default.ArrowDownward, canMoveDown && !block.locked) { onMove(MoveDirection.DOWN) }
                if (block.locked) {
                    Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.locked), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
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
                            text = { Text(stringResource(if (block.locked) R.string.unlock_block else R.string.lock_block)) },
                            leadingIcon = { Icon(if (block.locked) Icons.Default.LockOpen else Icons.Default.Lock, contentDescription = null) },
                            onClick = { showMore = false; onLockedChange(!block.locked) },
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
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            enabled = !block.locked,
                            onClick = { showMore = false; confirmDelete = true },
                        )
                    }
                }
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
                    value = editorValue,
                    onValueChange = { value ->
                        editorValue = value
                        if (editorFocused) onEditorState(true, value.selection.end)
                        onUpdate { it.copy(content = value.text) }
                        viewModel.requestAutocomplete(block.id, PromptAutocomplete.currentFragment(value.text, value.selection.end))
                    },
                    enabled = !block.locked,
                    label = { Text(stringResource(R.string.prompt_content)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().onFocusChanged {
                        editorFocused = it.isFocused
                        onEditorState(it.isFocused, editorValue.selection.end)
                    },
                    visualTransformation = remember(highColor, lowColor, commentColor, randomizerColor) {
                        PromptVisualTransformation(highColor, lowColor, commentColor, randomizerColor)
                    },
                )
                if (autocomplete.blockId == block.id) {
                    AutocompleteSuggestions(
                        local = autocomplete.local,
                        novelAi = autocomplete.novelAi,
                        danbooru = autocomplete.danbooru,
                        loading = autocomplete.loading,
                        novelAiFailed = autocomplete.novelAiFailed,
                        danbooruFailed = autocomplete.danbooruFailed,
                        onSelect = { suggestion ->
                            val fragment = autocomplete.fragment ?: return@AutocompleteSuggestions
                            val replacement = PromptAutocomplete.replace(editorValue.text, fragment, suggestion.tag)
                            editorValue = TextFieldValue(replacement.text, TextRange(replacement.cursor))
                            onUpdate { it.copy(content = replacement.text) }
                            viewModel.recordAutocompleteUse(suggestion)
                            viewModel.clearAutocomplete()
                        },
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
private fun AutocompleteSuggestions(
    local: List<TagSuggestion>,
    novelAi: List<TagSuggestion>,
    danbooru: List<TagSuggestion>,
    loading: Boolean,
    novelAiFailed: Boolean,
    danbooruFailed: Boolean,
    onSelect: (TagSuggestion) -> Unit,
) {
    if (local.isNotEmpty()) SuggestionRow(R.string.autocomplete_local_row, local, onSelect)
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (novelAi.isNotEmpty()) SuggestionRow(R.string.autocomplete_nai_row, novelAi, onSelect)
    if (danbooru.isNotEmpty()) SuggestionRow(R.string.autocomplete_danbooru_row, danbooru, onSelect)
    if (novelAiFailed) Text(stringResource(R.string.autocomplete_nai_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    if (danbooruFailed) Text(stringResource(R.string.autocomplete_danbooru_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SuggestionRow(@StringRes label: Int, suggestions: List<TagSuggestion>, onSelect: (TagSuggestion) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(label),
            fontSize = 9.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(40.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(suggestions, key = { "${it.source}:${it.tag}" }) { suggestion ->
                SuggestionChip(onClick = { onSelect(suggestion) }, label = { Text(suggestion.tag.replace('_', ' '), maxLines = 1) })
            }
        }
    }
}

@Composable
private fun GenerationSettingsCard(settings: GenerationSettings, viewModel: MainViewModel, onMetadataImported: () -> Unit) {
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    var pendingImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            pendingImageUri = it.toString()
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
        var includeSettings by rememberSaveable(uri) { mutableStateOf(true) }
        var includeSeed by rememberSaveable(uri) { mutableStateOf(true) }
        val importedBlockName = stringResource(R.string.imported_block_name)
        AlertDialog(
            onDismissRequest = { pendingImageUri = null },
            title = { Text(stringResource(R.string.choose_image_use)) },
            text = {
                Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AsyncImage(model = Uri.parse(uri), contentDescription = stringResource(R.string.import_image), modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp), contentScale = ContentScale.Fit)
                    Button(onClick = {
                        viewModel.updateGenerationSettings { it.copy(imageInput = ImageInputState(uri)) }
                        pendingImageUri = null
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.image_to_image)) }
                    OutlinedButton(onClick = {
                        viewModel.updateGenerationSettings { it.copy(imageInput = ImageInputState(uri, mode = ImageInputMode.VIBE_TRANSFER)) }
                        pendingImageUri = null
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.vibe_transfer)) }
                    OutlinedButton(
                        onClick = {
                            viewModel.updateGenerationSettings { it.copy(imageInput = ImageInputState(uri, mode = ImageInputMode.PRECISE_REFERENCE, strength = 1f)) }
                            pendingImageUri = null
                        },
                        enabled = settings.modelId?.startsWith("nai-diffusion-4-5-") == true,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.precise_reference)) }
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
                            MetadataChoice(R.string.import_prompt, includePrompt) { includePrompt = it }
                            MetadataChoice(R.string.import_negative, includeNegative) { includeNegative = it }
                            MetadataChoice(R.string.import_characters, includeCharacters) { includeCharacters = it }
                            MetadataChoice(R.string.import_settings, includeSettings) { includeSettings = it }
                            MetadataChoice(R.string.import_seed, includeSeed) { includeSeed = it }
                        }
                        Button(onClick = {
                            viewModel.importImageMetadata(metadata!!, importedBlockName, includePrompt, includeNegative, includeCharacters, includeSettings, includeSeed)
                            pendingImageUri = null
                            onMetadataImported()
                        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_metadata)) }
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElevatedCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.model_id), style = MaterialTheme.typography.titleMedium)
                CatalogDropdown(R.string.model_id, settings.modelId, NaiGenerationCatalog.models) { value ->
                    viewModel.updateGenerationSettings { it.copy(modelId = value) }
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
                    SliderSettingRow(R.string.image_strength, input.strength, 0f..1f, 19, decimal = true) { value ->
                        viewModel.updateGenerationSettings { current -> current.copy(imageInput = input.copy(strength = (value * 20).toInt() / 20f)) }
                    }
                    if (input.mode == ImageInputMode.IMAGE_TO_IMAGE) {
                        SliderSettingRow(R.string.image_noise, input.noise, 0f..1f, 19, decimal = true) { value ->
                            viewModel.updateGenerationSettings { current -> current.copy(imageInput = input.copy(noise = (value * 20).toInt() / 20f)) }
                        }
                    } else {
                        SliderSettingRow(R.string.information_extracted, input.informationExtracted, 0f..1f, 19, decimal = true) { value ->
                            viewModel.updateGenerationSettings { current -> current.copy(imageInput = input.copy(informationExtracted = (value * 20).toInt() / 20f)) }
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
                        SliderSettingRow(R.string.reference_fidelity, input.fidelity, 0f..1f, 19, decimal = true) { value ->
                            viewModel.updateGenerationSettings { current -> current.copy(imageInput = input.copy(fidelity = (value * 20).toInt() / 20f)) }
                        }
                    }
                } ?: OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/png", "image/jpeg", "image/webp")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AddPhotoAlternate, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.import_image))
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
                SliderSettingRow(R.string.steps, (settings.steps ?: 28).toFloat(), 1f..50f, 48) { value ->
                    viewModel.updateGenerationSettings { it.copy(steps = value.toInt()) }
                }
                SliderSettingRow(R.string.prompt_guidance, settings.scale ?: 5f, 0f..10f, 99, decimal = true) { value ->
                    viewModel.updateGenerationSettings { it.copy(scale = (value * 10).toInt() / 10f) }
                }
                Text(stringResource(R.string.seed), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    SliderSettingRow(
                        R.string.guidance_rescale,
                        settings.guidanceRescale ?: 0.4f,
                        0f..1f,
                        19,
                        decimal = true,
                    ) { value ->
                        viewModel.updateGenerationSettings { it.copy(guidanceRescale = (value * 20).toInt() / 20f) }
                    }
                    Text(stringResource(R.string.guidance_rescale_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
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
private fun generateButtonLabel(state: GenerationUiState, input: ImageInputState?): String {
    if (state is GenerationUiState.Loading) return stringResource(R.string.generating)
    val extra = if (input?.mode == ImageInputMode.PRECISE_REFERENCE) 5 else 0
    return if (extra > 0) stringResource(R.string.generate_one_image_with_extra_anlas, extra)
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
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    decimal: Boolean = false,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(
                    if (decimal) String.format(java.util.Locale.US, "%.1f", value) else value.toInt().toString(),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValueChange, valueRange = range, steps = steps, modifier = Modifier.weight(1f))
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
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
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
    SelectionContainer { Text(prompt.ifBlank { stringResource(R.string.empty_prompt) }, style = MaterialTheme.typography.labelSmall, maxLines = 3, overflow = TextOverflow.Ellipsis) }
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

private val ImageInputMode.labelResource: Int get() = when (this) {
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
