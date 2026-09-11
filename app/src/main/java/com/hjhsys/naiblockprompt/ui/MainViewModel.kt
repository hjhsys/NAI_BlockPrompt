package com.hjhsys.naiblockprompt.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hjhsys.naiblockprompt.AppContainer
import com.hjhsys.naiblockprompt.BuildConfig
import android.util.Log
import com.hjhsys.naiblockprompt.domain.editor.*
import com.hjhsys.naiblockprompt.domain.model.AppSettings
import com.hjhsys.naiblockprompt.domain.model.*
import com.hjhsys.naiblockprompt.data.generation.*
import com.hjhsys.naiblockprompt.data.network.nai.*
import com.hjhsys.naiblockprompt.data.library.*
import com.hjhsys.naiblockprompt.data.local.entity.*
import com.hjhsys.naiblockprompt.domain.generation.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.hjhsys.naiblockprompt.domain.autocomplete.PromptFragment
import com.hjhsys.naiblockprompt.domain.autocomplete.PromptAutocomplete
import com.hjhsys.naiblockprompt.domain.autocomplete.TagSuggestion
import com.hjhsys.naiblockprompt.domain.autocomplete.AutocompleteDeduplicator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.asSharedFlow
import com.hjhsys.naiblockprompt.domain.tags.TagTranslationImportPreview
import com.hjhsys.naiblockprompt.domain.tags.TagTranslationExportFile
import com.hjhsys.naiblockprompt.domain.image.NaiImageMetadata
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MainViewModel(private val container: AppContainer) : ViewModel() {
    data class TransferFile(val name: String, val mimeType: String, val bytes: ByteArray)
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()
    private val saveSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val saveMutex = Mutex()
    private val _transferFailed = MutableStateFlow(false)
    val transferFailed = _transferFailed.asStateFlow()
    fun dismissTransferFailure() { _transferFailed.value = false }
    private val _wildcardSaveFailed = MutableStateFlow(false)
    val wildcardSaveFailed = _wildcardSaveFailed.asStateFlow()
    fun dismissWildcardSaveFailure() { _wildcardSaveFailed.value = false }
    private val requestMapper = NaiRequestMapper()
    private var retryGeneration: PreparedGeneration? = null
    private var duplicateGeneration: PreparedGeneration? = null
    private val _duplicateWarning = MutableStateFlow(false)
    val duplicateWarning: StateFlow<Boolean> = _duplicateWarning.asStateFlow()

    private val _generationState = MutableStateFlow<GenerationUiState>(GenerationUiState.Idle)
    val generationState: StateFlow<GenerationUiState> = _generationState.asStateFlow()
    private val _currentResult = MutableStateFlow<GenerationRecord?>(null)
    val currentResult: StateFlow<GenerationRecord?> = _currentResult.asStateFlow()
    private val generationGate = GenerationRequestGate()
    val generationInProgress: StateFlow<Boolean> = generationGate.inProgress
    private val _tokenConfigured = MutableStateFlow(container.tokenStore.isConfigured())
    val tokenConfigured: StateFlow<Boolean> = _tokenConfigured.asStateFlow()
    private val _connectionState = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Idle)
    val connectionState: StateFlow<ConnectionUiState> = _connectionState.asStateFlow()
    private val _subscriptionStatus = MutableStateFlow<SubscriptionUiState>(SubscriptionUiState.Unavailable)
    val subscriptionStatus: StateFlow<SubscriptionUiState> = _subscriptionStatus.asStateFlow()
    val history = container.libraryRepository.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val historyStorageSample = history.mapLatest { items -> container.libraryRepository.sampleHistoryStorage(items) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.hjhsys.naiblockprompt.data.library.HistoryStorageSample())
    val savedBlocks = container.libraryRepository.blocks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val savedFolders = container.libraryRepository.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val presets = container.libraryRepository.presets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val savedSets = container.libraryRepository.sets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _hasStash = MutableStateFlow(false)
    val hasStash: StateFlow<Boolean> = _hasStash.asStateFlow()
    private val previousWork = PreviousWorkMemory()
    private val promptUndoHistory = PromptUndoHistory()
    private val _undoablePromptBlocks = MutableStateFlow<Set<PromptUndoKey>>(emptySet())
    val undoablePromptBlocks: StateFlow<Set<PromptUndoKey>> = _undoablePromptBlocks.asStateFlow()
    private val _loadUiRevision = MutableStateFlow(0L)
    val loadUiRevision: StateFlow<Long> = _loadUiRevision.asStateFlow()
    private val _savedWorkflow = MutableStateFlow<SavedWorkflow?>(null)
    val savedWorkflow: StateFlow<SavedWorkflow?> = _savedWorkflow.asStateFlow()
    private val _autocomplete = MutableStateFlow(AutocompleteUiState())
    val autocomplete: StateFlow<AutocompleteUiState> = _autocomplete.asStateFlow()
    val collectedTags = container.autocompleteRepository.observedTags.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val tagCount = container.autocompleteRepository.tagCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val missingTranslationCount = container.autocompleteRepository.missingTranslationCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val missingCategoryCount = container.autocompleteRepository.missingCategoryCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val deferredTranslationCount = container.autocompleteRepository.deferredTranslationCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    private val tagDictionaryQuery = MutableStateFlow("")
    private val tagDictionaryFilter = MutableStateFlow(TagDictionaryFilter.ALL)
    private val tagDictionaryCategory = MutableStateFlow("")
    private val tagDictionarySort = MutableStateFlow(TagDictionarySort.POPULAR)
    private val tagExclusionOrigin = MutableStateFlow<TagExclusionOrigin?>(null)
    private var tagInsertTarget: TagInsertTarget? = null
    private val _tagInsertAvailable = MutableStateFlow(false)
    val tagInsertAvailable: StateFlow<Boolean> = _tagInsertAvailable.asStateFlow()
    val usedTagCategories = container.autocompleteRepository.usedCategories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val userTagCategories = container.autocompleteRepository.userCategories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val wildcards = container.autocompleteRepository.wildcards.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val excludedTags = tagExclusionOrigin.flatMapLatest(container.autocompleteRepository::exclusions)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val dictionaryTags = combine(tagDictionaryQuery, tagDictionaryFilter, tagDictionaryCategory, tagDictionarySort) { query, filter, category, sort ->
        DictionarySearch(query, filter, category, sort)
    }.flatMapLatest { search -> container.autocompleteRepository.dictionary(search.query, search.filter, search.category, search.sort) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val dictionaryCount = combine(tagDictionaryQuery, tagDictionaryFilter, tagDictionaryCategory) { query, filter, category ->
        Triple(query, filter, category)
    }.flatMapLatest { (query, filter, category) -> container.autocompleteRepository.dictionaryCount(query, filter, category) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    private var autocompleteLocalJob: Job? = null
    private var autocompleteRemoteJob: Job? = null
    private val autocompleteRequestGuard = AutocompleteRequestGuard()
    private val _translationExport = MutableSharedFlow<TagTranslationExportFile>(extraBufferCapacity = 1)
    val translationExport = _translationExport.asSharedFlow()
    private val _translationClipboard = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val translationClipboard = _translationClipboard.asSharedFlow()
    private val _translationImportPreview = MutableStateFlow<TagTranslationImportPreview?>(null)
    val translationImportPreview = _translationImportPreview.asStateFlow()
    private val _translationImportFailed = MutableStateFlow(false)
    val translationImportFailed = _translationImportFailed.asStateFlow()
    fun dismissTranslationImportFailure() { _translationImportFailed.value = false }
    private val _transferExport = MutableSharedFlow<TransferFile>(extraBufferCapacity = 1)
    val transferExport = _transferExport.asSharedFlow()

    fun exportLatestInpaintDiagnostics() = viewModelScope.launch {
        val bytes = container.generationRepository.exportLatestInpaintDiagnostics()
        if (bytes == null) {
            _transferFailed.value = true
        } else {
            _transferExport.emit(
                TransferFile("nai_inpaint_diagnostics.zip", "application/zip", bytes),
            )
        }
    }

    val settings = container.settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    init {
        viewModelScope.launch { _session.value = container.sessionRepository.restoreOrCreate() }
        viewModelScope.launch {
            container.libraryRepository.latestHistoryWithOriginal()?.let { latest ->
                val seed = latest.snapshot?.generation?.usedSeed
                    ?: latest.snapshot?.session?.generationSettings?.seed
                    ?: return@let
                publishGenerationState(GenerationUiState.Success(
                    GenerationRecord(latest.entity.imagePath, latest.entity.thumbnailPath, seed),
                    autoOpenResult = false,
                ))
            }
        }
        viewModelScope.launch {
            previousWork.load(container.sessionRepository.restoreStash())
            _hasStash.value = previousWork.hasPrevious
        }
        if (_tokenConfigured.value) refreshSubscriptionStatus()
        viewModelScope.launch {
            saveSignals.debounce(350).collect {
                persistCurrent()
            }
        }
    }

    fun selectPolarity(owner: PromptOwner, polarity: PromptPolarity) = edit {
        SessionEditor.selectPolarity(it, owner, polarity)
    }

    fun addCharacter(type: CharacterType, defaultBlockName: String) = edit {
        SessionEditor.addCharacter(it, type, defaultBlockName)
    }

    fun removeCharacter(id: String) = edit { SessionEditor.removeCharacter(it, id) }
    fun moveCharacter(id: String, direction: MoveDirection) = edit { SessionEditor.moveCharacter(it, id, direction) }
    fun setCharacterType(id: String, type: CharacterType) = edit { SessionEditor.setCharacterType(it, id, type) }
    fun setCharacterCollapsed(id: String, collapsed: Boolean) = edit { SessionEditor.setCharacterCollapsed(it, id, collapsed) }
    fun setCharacterPositioningEnabled(enabled: Boolean) = edit { SessionEditor.setCharacterPositioningEnabled(it, enabled) }
    fun setCharacterPosition(id: String, position: CharacterPosition) = edit { SessionEditor.setCharacterPosition(it, id, position) }

    fun addBlock(owner: PromptOwner, polarity: PromptPolarity, name: String) = edit {
        SessionEditor.addBlock(it, owner, polarity, name)
    }

    fun removeBlock(owner: PromptOwner, polarity: PromptPolarity, id: String) = edit {
        SessionEditor.removeBlock(it, owner, polarity, id)
    }

    fun moveBlock(owner: PromptOwner, polarity: PromptPolarity, id: String, direction: MoveDirection) = edit {
        SessionEditor.moveBlock(it, owner, polarity, id, direction)
    }

    fun updateBlock(
        owner: PromptOwner,
        polarity: PromptPolarity,
        id: String,
        transform: (PromptBlock) -> PromptBlock,
    ) = edit { SessionEditor.updateBlock(it, owner, polarity, id, transform) }

    fun updateBlockContent(
        owner: PromptOwner,
        polarity: PromptPolarity,
        id: String,
        content: String,
        previousSelectionStart: Int,
        previousSelectionEnd: Int,
        kind: PromptEditKind,
    ) {
        val current = _session.value ?: return
        val block = current.findPromptBlock(owner, polarity, id) ?: return
        if (block.locked || block.content == content) return
        val key = PromptUndoKey(owner, polarity, id)
        promptUndoHistory.recordBeforeChange(
            key,
            PromptEditorSnapshot(block.content, previousSelectionStart, previousSelectionEnd),
            kind,
        )
        refreshUndoable(key)
        edit { SessionEditor.updateBlock(it, owner, polarity, id) { item -> item.copy(content = content) } }
    }

    fun undoBlockContent(owner: PromptOwner, polarity: PromptPolarity, id: String): PromptEditorSnapshot? {
        val current = _session.value ?: return null
        val block = current.findPromptBlock(owner, polarity, id) ?: return null
        if (block.locked) return null
        val key = PromptUndoKey(owner, polarity, id)
        val restored = promptUndoHistory.undo(key) ?: return null
        edit { SessionEditor.updateBlock(it, owner, polarity, id) { item -> item.copy(content = restored.content) } }
        refreshUndoable(key)
        clearAutocomplete()
        return restored
    }

    fun setBlockEnabled(owner: PromptOwner, polarity: PromptPolarity, id: String, enabled: Boolean) = edit {
        SessionEditor.setBlockEnabled(it, owner, polarity, id, enabled)
    }

    fun setBlockCollapsed(owner: PromptOwner, polarity: PromptPolarity, id: String, collapsed: Boolean) = edit {
        SessionEditor.setBlockCollapsed(it, owner, polarity, id, collapsed)
    }

    fun setBlockLocked(owner: PromptOwner, polarity: PromptPolarity, id: String, locked: Boolean) = edit {
        SessionEditor.setBlockLocked(it, owner, polarity, id, locked)
    }

    fun formatBlock(
        owner: PromptOwner,
        polarity: PromptPolarity,
        id: String,
        formatter: BlockFormatter,
        selectionStart: Int,
        selectionEnd: Int,
    ) {
        val current = _session.value ?: return
        val block = current.findPromptBlock(owner, polarity, id) ?: return
        val updated = SessionEditor.formatBlock(current, owner, polarity, id, formatter)
        if (updated == current) return
        val key = PromptUndoKey(owner, polarity, id)
        promptUndoHistory.recordBeforeChange(
            key,
            PromptEditorSnapshot(block.content, selectionStart, selectionEnd),
            PromptEditKind.DISCRETE,
        )
        refreshUndoable(key)
        edit { updated }
    }

    private fun refreshUndoable(key: PromptUndoKey) {
        _undoablePromptBlocks.value = if (promptUndoHistory.canUndo(key)) {
            _undoablePromptBlocks.value + key
        } else {
            _undoablePromptBlocks.value - key
        }
    }

    private fun clearPromptUndo() {
        promptUndoHistory.clear()
        _undoablePromptBlocks.value = emptySet()
    }

    fun updateTextRendering(owner: PromptOwner, transform: (TextRenderingState) -> TextRenderingState) = edit { current ->
        when (owner) {
            PromptOwner.Base -> current.copy(base = current.base.copy(textRendering = transform(current.base.textRendering)))
            is PromptOwner.Character -> current.copy(characters = current.characters.map { character ->
                if (character.id == owner.id) character.copy(textRendering = transform(character.textRendering)) else character
            })
        }
    }

    fun updateGenerationSettings(transform: (GenerationSettings) -> GenerationSettings) = edit {
        it.copy(generationSettings = transform(it.generationSettings))
    }

    fun importImageMetadata(
        metadata: NaiImageMetadata,
        blockName: String,
        includePrompt: Boolean,
        includeNegative: Boolean,
        includeCharacters: Boolean,
        includeSettings: Boolean,
        includeSeed: Boolean,
    ) {
        val current = _session.value ?: return
        fun blocks(value: String?) = value?.let { listOf(PromptBlock(name = blockName, content = it)) }.orEmpty()
        val base = current.base.copy(prompts = current.base.prompts.copy(
            positiveBlocks = if (includePrompt && metadata.prompt != null) blocks(metadata.prompt) else current.base.prompts.positiveBlocks,
            negativeBlocks = if (includeNegative && metadata.negativePrompt != null) blocks(metadata.negativePrompt) else current.base.prompts.negativeBlocks,
        ))
        val characters = if (includeCharacters && metadata.characterPrompts.isNotEmpty()) {
            metadata.characterPrompts.mapIndexed { index, positive ->
                CharacterPrompt(
                    type = CharacterType.OTHER,
                    order = index,
                    position = metadata.characterPositions.getOrNull(index),
                    prompts = PromptPair(
                        positiveBlocks = blocks(positive),
                        negativeBlocks = blocks(metadata.characterNegativePrompts.getOrNull(index)),
                    ),
                )
            }
        } else current.characters
        val old = current.generationSettings
        val importedModel = metadata.model?.takeIf { id -> NaiGenerationCatalog.models.any { it.apiId == id } }
        val importedSampler = metadata.sampler?.takeIf { id -> NaiGenerationCatalog.samplers.any { it.apiId == id } }
        val generationSettings = old.copy(
            modelId = if (includeSettings) importedModel ?: old.modelId else old.modelId,
            width = if (includeSettings) metadata.width ?: old.width else old.width,
            height = if (includeSettings) metadata.height ?: old.height else old.height,
            samplerId = if (includeSettings) importedSampler ?: old.samplerId else old.samplerId,
            steps = if (includeSettings) metadata.steps ?: old.steps else old.steps,
            scale = if (includeSettings) metadata.scale ?: old.scale else old.scale,
            seedMode = if (includeSeed && metadata.seed != null) SeedMode.FIXED else old.seedMode,
            seed = if (includeSeed && metadata.seed != null) metadata.seed else old.seed,
        )
        replaceWithStash(current.copy(base = base, characters = characters, generationSettings = generationSettings))
    }

    private fun edit(transform: (Session) -> Session) {
        val current = _session.value ?: return
        val updated = transform(current)
        if (updated != current) {
            _session.value = updated
            saveSignals.tryEmit(Unit)
        }
    }

    fun flushAutosave() {
        viewModelScope.launch { persistCurrent() }
    }

    private suspend fun persistCurrent() = saveMutex.withLock {
        _session.value?.let { container.sessionRepository.save(it) }
    }

    fun setShowFormatter(value: Boolean) = viewModelScope.launch {
        container.settingsRepository.setShowFormatter(value)
    }

    fun setNormalizeWeights(value: Boolean) = viewModelScope.launch {
        container.settingsRepository.setNormalizeWeights(value)
    }

    fun setShowTokenEstimates(value: Boolean) = viewModelScope.launch {
        container.settingsRepository.setShowTokenEstimates(value)
    }
    fun setUseTextRendering(value: Boolean) = viewModelScope.launch {
        container.settingsRepository.setUseTextRendering(value)
    }
    fun setColorHelperMode(value: ColorHelperMode) = viewModelScope.launch { container.settingsRepository.setColorHelperMode(value) }
    fun toggleFavoriteColor(hex: String) = viewModelScope.launch {
        val current = settings.value.favoriteColors
        val removing = hex in current
        container.settingsRepository.setFavoriteColors(if (removing) current - hex else listOf(hex) + current)
        val dates = settings.value.favoriteColorAddedAt.toMutableMap()
        if (removing) dates.remove(hex) else dates[hex] = System.currentTimeMillis()
        container.settingsRepository.setFavoriteColorAddedAt(dates)
        recordRecentColor(hex)
    }
    fun recordRecentColor(hex: String) = viewModelScope.launch {
        container.settingsRepository.setRecentColors(listOf(hex) + settings.value.recentColors.filterNot { it == hex })
    }

    fun insertColor(owner: PromptOwner, polarity: PromptPolarity, blockId: String, cursor: Int, hex: String) {
        val block = _session.value?.findPromptBlock(owner, polarity, blockId) ?: return
        val at = cursor.coerceIn(0, block.content.length)
        updateBlockContent(
            owner, polarity, blockId,
            block.content.substring(0, at) + hex + block.content.substring(at),
            at, at, PromptEditKind.DISCRETE,
        )
        recordRecentColor(hex)
    }

    fun setHistoryLimit(value: Int) = viewModelScope.launch {
        container.settingsRepository.setHistoryLimit(value)
        container.libraryRepository.trimHistory(value)
    }

    fun setAutocompleteSource(value: AutocompleteSource) = viewModelScope.launch {
        container.settingsRepository.setAutocompleteSource(value)
    }

    fun setAppearanceMode(value: AppearanceMode) = viewModelScope.launch {
        container.settingsRepository.setAppearanceMode(value)
    }
    fun setImageSaveTreeUri(value: String?) = viewModelScope.launch {
        container.settingsRepository.setImageSaveTreeUri(value)
    }

    fun requestAutocomplete(blockId: String, fragment: PromptFragment?) {
        autocompleteLocalJob?.cancel()
        autocompleteRemoteJob?.cancel()
        val requestRevision = autocompleteRequestGuard.next()
        if (fragment == null) {
            _autocomplete.value = AutocompleteUiState()
            return
        }
        val requestStartedAtNanos = System.nanoTime()
        val remotePending = PromptAutocomplete.shouldQueryRemote(fragment)
        _autocomplete.value = AutocompleteUiState(blockId, fragment, loading = remotePending, requestStartedAtNanos = requestStartedAtNanos)
        fun isCurrentRequest(): Boolean = autocompleteRequestGuard.isCurrent(requestRevision, blockId, fragment, _autocomplete.value)
        autocompleteLocalJob = viewModelScope.launch {
            if (BuildConfig.DEBUG) {
                Log.d("AutocompletePerf", "inputToLocalStartMs=${(System.nanoTime() - requestStartedAtNanos) / 1_000_000.0} chars=${fragment.text.length}")
            }
            if (fragment.text.startsWith("__")) {
                val prefix = fragment.text.removePrefix("__").lowercase()
                val matches = wildcards.value.filter { it.name.lowercase().startsWith(prefix) }.take(12).map {
                    TagSuggestion("__${it.name}__", com.hjhsys.naiblockprompt.domain.autocomplete.SuggestionSource.WILDCARD)
                }
                if (isCurrentRequest()) {
                    _autocomplete.value = _autocomplete.value.copy(wildcards = matches, loading = false)
                }
                return@launch
            }
            val prefixLocal = container.autocompleteRepository.localPrefix(fragment.text)
            if (isCurrentRequest()) {
                _autocomplete.value = _autocomplete.value.withLocalResults(prefixLocal)
                logLocalStateTiming(requestStartedAtNanos, "prefix", prefixLocal.size)
            }
            val local = container.autocompleteRepository.local(fragment.text)
            if (isCurrentRequest()) {
                _autocomplete.value = _autocomplete.value.withLocalResults(local)
                logLocalStateTiming(requestStartedAtNanos, "rich", local.size)
            }
        }
        if (!remotePending) return
        autocompleteRemoteJob = viewModelScope.launch {
            delay(400)
            val source = settings.value.autocompleteSource
            val token = container.tokenStore.load()
            // The official Primary API Swagger currently verifies this suggest-tags model value.
            val model = "nai-diffusion-3"
            val result = container.autocompleteRepository.suggest(fragment.text, source, token, model)
            if (isCurrentRequest()) {
                _autocomplete.value = _autocomplete.value.withRemoteResults(result)
            }
        }
    }

    private fun logLocalStateTiming(startedAtNanos: Long, stage: String, count: Int) {
        if (BuildConfig.DEBUG) {
            Log.d("AutocompletePerf", "inputToLocalStateMs=${(System.nanoTime() - startedAtNanos) / 1_000_000.0} stage=$stage count=$count")
        }
    }

    fun clearAutocomplete() {
        autocompleteLocalJob?.cancel()
        autocompleteRemoteJob?.cancel()
        autocompleteRequestGuard.invalidate()
        _autocomplete.value = AutocompleteUiState()
    }

    fun recordAutocompleteUse(suggestion: TagSuggestion) = viewModelScope.launch {
        container.autocompleteRepository.recordSelection(suggestion)
    }

    fun searchDictionary(query: String) { tagDictionaryQuery.value = query }
    fun filterDictionary(filter: TagDictionaryFilter) { tagDictionaryFilter.value = filter }
    fun filterDictionaryCategory(category: String) { tagDictionaryCategory.value = category }
    fun sortDictionary(sort: TagDictionarySort) { tagDictionarySort.value = sort }
    fun filterExcludedTags(origin: TagExclusionOrigin?) { tagExclusionOrigin.value = origin }
    fun addTagCategory(name: String) = viewModelScope.launch { container.autocompleteRepository.addCategory(name) }
    fun saveWildcard(id: String?, name: String, valuesText: String, folder: String? = null) = viewModelScope.launch {
        try {
            container.autocompleteRepository.saveWildcard(id, name, valuesText, folder)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _wildcardSaveFailed.value = true
        }
    }
    fun deleteWildcard(item: WildcardEntity) = viewModelScope.launch { container.autocompleteRepository.deleteWildcard(item) }
    fun setTagThumbnail(item: TagDictionaryItem, uri: android.net.Uri) = viewModelScope.launch { container.autocompleteRepository.setThumbnail(item, uri) }
    fun setTagThumbnailFromFile(item: TagDictionaryItem, path: String) = viewModelScope.launch { container.autocompleteRepository.setThumbnailFromFile(item, path) }
    fun removeTagThumbnail(item: TagDictionaryItem) = viewModelScope.launch { container.autocompleteRepository.removeThumbnail(item) }
    fun prepareTranslationExport(missingTranslation: Boolean, missingCategory: Boolean, includeDeferred: Boolean = false) = viewModelScope.launch {
        _translationExport.emit(container.autocompleteRepository.exportTranslationBatch(missingTranslation, missingCategory, includeDeferred = includeDeferred))
    }
    fun prepareAllTranslationExport(missingTranslation: Boolean, missingCategory: Boolean, includeDeferred: Boolean = false) = viewModelScope.launch {
        _translationExport.emit(container.autocompleteRepository.exportTranslationBatch(missingTranslation, missingCategory, allBatches = true, includeDeferred = includeDeferred))
    }
    fun copySelectedTagsForAi(items: List<TagDictionaryItem>) = viewModelScope.launch {
        if (items.isNotEmpty()) _translationClipboard.emit(container.autocompleteRepository.exportSelectedTranslations(items))
    }
    fun previewTranslationImport(text: String) = viewModelScope.launch {
        _translationImportPreview.value = container.autocompleteRepository.previewTranslationImport(text)
    }
    fun previewTranslationFile(uri: android.net.Uri) = viewModelScope.launch {
        _translationImportPreview.value = null
        try {
            _translationImportPreview.value = container.autocompleteRepository.previewTranslationFile(uri)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _translationImportFailed.value = true
        }
    }
    fun dismissTranslationImport() { _translationImportPreview.value = null }
    fun applyTranslationImport(overwriteExisting: Boolean, selectedDeleteIds: Set<String> = emptySet(), deferReviewed: Boolean = true) = viewModelScope.launch {
        val preview = _translationImportPreview.value ?: return@launch
        _translationImportPreview.value = null
        try {
            container.autocompleteRepository.applyTranslationImport(preview, overwriteExisting, selectedDeleteIds, deferReviewed)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _translationImportFailed.value = true
        }
    }
    fun setTagFavorite(item: TagDictionaryItem, favorite: Boolean) = viewModelScope.launch {
        container.autocompleteRepository.setFavorite(item, favorite)
    }
    fun saveTagDetails(item: TagDictionaryItem, korean: String?, aliases: String?, appCategory: String?) = viewModelScope.launch {
        container.autocompleteRepository.saveUserDetails(item, korean, aliases, appCategory)
    }
    fun resetTagDetails(item: TagDictionaryItem) = viewModelScope.launch {
        container.autocompleteRepository.resetUserDetails(item)
    }
    fun addUserTag(canonical: String, korean: String?, aliases: String?, appCategory: String?) = viewModelScope.launch {
        container.autocompleteRepository.addUserTag(canonical, korean, aliases, appCategory)
    }
    fun deleteUserOnlyTag(item: TagDictionaryItem) = viewModelScope.launch {
        container.autocompleteRepository.deleteUserOnlyTag(item)
    }
    fun excludeTag(item: TagDictionaryItem) = viewModelScope.launch {
        container.autocompleteRepository.excludeTag(item.canonicalTag)
        clearAutocomplete()
    }
    fun restoreExcludedTag(item: ExcludedTagItem) = viewModelScope.launch {
        container.autocompleteRepository.restoreExcludedTag(item.canonicalTag)
        clearAutocomplete()
    }
    fun confirmExcludedTag(item: ExcludedTagItem) = viewModelScope.launch {
        container.autocompleteRepository.confirmExcludedTag(item.canonicalTag)
    }
    fun resetTagDatabaseToBundled() = viewModelScope.launch {
        container.autocompleteRepository.resetToBundledTags()
    }
    fun exportSharedTagDatabase() = viewModelScope.launch {
        _transferExport.emit(TransferFile("nai_user_tag_db.zip", "application/zip", container.backupRepository.exportTagData()))
    }
    fun importSharedTagDatabase(bytes: ByteArray) = viewModelScope.launch {
        runCatching { container.backupRepository.importTagData(bytes) }.onFailure { _transferFailed.value = true }
    }
    fun exportAppBackup() = viewModelScope.launch {
        persistCurrent()
        _transferExport.emit(TransferFile("nai_blockprompt_backup.zip", "application/zip", container.backupRepository.exportAppBackup()))
    }
    fun importAppBackup(bytes: ByteArray) = viewModelScope.launch {
        saveMutex.withLock {
        runCatching { container.backupRepository.importAppBackup(bytes) }.onFailure { _transferFailed.value = true }.onSuccess {
            clearPromptUndo()
            _session.value = container.sessionRepository.restoreOrCreate()
            _hasStash.value = container.sessionRepository.hasStash()
        }
        }
    }
    fun beginTagInsert(owner: PromptOwner, polarity: PromptPolarity, blockId: String, cursor: Int) {
        clearAutocomplete()
        tagInsertTarget = TagInsertTarget(owner, polarity, blockId, cursor)
        _tagInsertAvailable.value = true
    }
    fun insertDictionaryTags(tags: List<String>) {
        val target = tagInsertTarget ?: return
        if (tags.isEmpty()) return
        val block = _session.value?.findPromptBlock(target.owner, target.polarity, target.blockId) ?: return
        updateBlockContent(
            target.owner,
            target.polarity,
            target.blockId,
            TagInsertion.insert(block.content, target.cursor, tags),
            target.cursor,
            target.cursor,
            PromptEditKind.DISCRETE,
        )
        tags.forEach { tag ->
            viewModelScope.launch { container.autocompleteRepository.recordUse(tag) }
        }
        cancelTagInsert()
    }
    fun cancelTagInsert() {
        tagInsertTarget = null
        _tagInsertAvailable.value = false
    }

    fun saveBlock(block: PromptBlock, name: String, folderId: String?) = viewModelScope.launch { container.libraryRepository.saveBlock(block, name, folderId) }
    fun beginBlockSave(block: PromptBlock) { _savedWorkflow.value = SavedWorkflow.SaveBlock(block) }
    fun beginPresetSave() { _session.value?.let { _savedWorkflow.value = SavedWorkflow.SavePreset(it) } }
    fun beginPresetLoad() { _savedWorkflow.value = SavedWorkflow.LoadPreset }
    fun beginSetSave(owner: PromptOwner) {
        val current = _session.value ?: return
        val set = when (owner) {
            PromptOwner.Base -> SavedPromptSet(SavedSetKind.BASE, current.base.prompts, current.base.selectedPolarity, textRendering = current.base.textRendering)
            is PromptOwner.Character -> current.characters.firstOrNull { it.id == owner.id }?.let { SavedPromptSet(SavedSetKind.CHARACTER, it.prompts, it.selectedPolarity, it.type, it.textRendering) }
        } ?: return
        _savedWorkflow.value = SavedWorkflow.SaveSet(set)
    }
    fun beginSetLoad(owner: PromptOwner) { _savedWorkflow.value = SavedWorkflow.LoadSet(owner) }
    fun beginBlockLoad(owner: PromptOwner, polarity: PromptPolarity, blockId: String) {
        _savedWorkflow.value = SavedWorkflow.LoadBlock(owner, polarity, blockId)
    }
    fun finishBlockSave(name: String, folderId: String?) {
        val workflow = _savedWorkflow.value as? SavedWorkflow.SaveBlock ?: return
        viewModelScope.launch { container.libraryRepository.saveBlock(workflow.block, name, folderId) }
        _savedWorkflow.value = null
    }
    fun finishBlockLoad(saved: SavedBlockEntity) {
        val workflow = _savedWorkflow.value as? SavedWorkflow.LoadBlock ?: return
        val block = _session.value?.findPromptBlock(workflow.owner, workflow.polarity, workflow.blockId) ?: return
        if (block.content != saved.content) {
            val key = PromptUndoKey(workflow.owner, workflow.polarity, workflow.blockId)
            promptUndoHistory.recordBeforeChange(
                key,
                PromptEditorSnapshot(block.content, block.content.length, block.content.length),
                PromptEditKind.DISCRETE,
            )
            refreshUndoable(key)
        }
        updateBlock(workflow.owner, workflow.polarity, workflow.blockId) { current ->
            current.copy(name = saved.name, content = saved.content, enabled = saved.enabled, locked = saved.locked, collapsed = saved.collapsed)
        }
        _savedWorkflow.value = null
    }
    fun finishPresetSave(name: String, folderId: String?) {
        val workflow = _savedWorkflow.value as? SavedWorkflow.SavePreset ?: return
        viewModelScope.launch { container.libraryRepository.savePreset(name, workflow.session, folderId) }
        _savedWorkflow.value = null
    }
    fun finishSetSave(name: String, folderId: String?) {
        val workflow = _savedWorkflow.value as? SavedWorkflow.SaveSet ?: return
        viewModelScope.launch { container.libraryRepository.saveSet(name, workflow.set, folderId) }
        _savedWorkflow.value = null
    }
    fun finishSetLoad(
        item: SavedSetItem,
        baseSelection: BaseSetImportSelection = BaseSetImportSelection(positive = true, negative = true),
    ) {
        val workflow = _savedWorkflow.value as? SavedWorkflow.LoadSet ?: return
        val set = item.set ?: return
        if (workflow.owner is PromptOwner.Base && !baseSelection.canApply) return
        clearPromptUndo()
        edit { current -> when (val owner = workflow.owner) {
            PromptOwner.Base -> if (set.kind == SavedSetKind.BASE) current.copy(base = BaseSetImport.apply(current.base, set, baseSelection)) else current
            is PromptOwner.Character -> if (set.kind == SavedSetKind.CHARACTER) current.copy(characters = current.characters.map { if (it.id == owner.id) it.copy(prompts = set.prompts, selectedPolarity = set.selectedPolarity, type = set.characterType ?: it.type, textRendering = set.textRendering) else it }) else current
        } }
        _savedWorkflow.value = null
    }
    fun cancelSavedWorkflow() { _savedWorkflow.value = null }
    fun savePreset(name: String) {
        val current = _session.value ?: return
        viewModelScope.launch { container.libraryRepository.savePreset(name.trim().ifBlank { "Preset" }, current) }
    }
    fun createFolder(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) container.libraryRepository.createFolder(name.trim(), savedFolders.value.size)
    }
    fun deleteSavedBlock(item: SavedBlockEntity) = viewModelScope.launch { container.libraryRepository.deleteBlock(item) }
    fun moveSavedBlock(item: SavedBlockEntity, folderId: String?) = viewModelScope.launch { container.libraryRepository.moveBlock(item, folderId) }
    fun movePreset(item: PresetEntity, folderId: String?) = viewModelScope.launch { container.libraryRepository.movePreset(item, folderId) }
    fun moveSavedSet(item: SavedSetEntity, folderId: String?) = viewModelScope.launch { container.libraryRepository.moveSet(item, folderId) }
    fun deleteFolder(item: SavedFolderEntity) = viewModelScope.launch { container.libraryRepository.deleteFolder(item) }
    fun deletePreset(item: PresetEntity) = viewModelScope.launch { container.libraryRepository.deletePreset(item) }
    fun deleteSavedSet(item: SavedSetEntity) = viewModelScope.launch { container.libraryRepository.deleteSet(item) }
    fun deleteHistory(item: HistoryEntryEntity) = viewModelScope.launch { container.libraryRepository.deleteHistory(item) }
    fun setHistoryFavorite(item: HistoryEntryEntity, favorite: Boolean) = viewModelScope.launch {
        container.libraryRepository.setHistoryFavorite(item, favorite)
    }
    fun enforceHistoryLimit() = viewModelScope.launch {
        container.libraryRepository.trimHistory(settings.value.historyLimit)
    }

    fun addSavedBlockToBase(item: SavedBlockEntity) = edit { current ->
        val blocks = current.base.prompts.positiveBlocks
        val block = PromptBlock(name = item.name, content = item.content, enabled = item.enabled, locked = item.locked, order = blocks.size)
        current.copy(base = current.base.copy(prompts = current.base.prompts.copy(positiveBlocks = blocks + block)))
    }

    fun restoreHistory(item: HistoryItem, options: RestoreOptions) {
        val source = item.snapshot?.session ?: return
        val resolved = item.snapshot.generation
        val current = _session.value ?: return
        var generationSettings = if (options.settings) {
            source.generationSettings.copy(imageInput = if (options.inputImage) source.generationSettings.imageInput else current.generationSettings.imageInput)
        } else {
            current.generationSettings.copy(imageInput = if (options.inputImage) source.generationSettings.imageInput else current.generationSettings.imageInput)
        }
        if (options.seed) {
            val importedSeed = item.snapshot.generation?.usedSeed ?: source.generationSettings.seed
            if (importedSeed != null) generationSettings = generationSettings.copy(seedMode = SeedMode.FIXED, seed = importedSeed)
        } else if (options.settings) {
            generationSettings = generationSettings.copy(seedMode = current.generationSettings.seedMode, seed = current.generationSettings.seed)
        }
        val restoreOriginal = options.wildcardOriginal || resolved == null
        val restoredBase = if (restoreOriginal) source.base else source.base.copy(
            prompts = PromptPair(
                positiveBlocks = resolvedBlocks(source.base.prompts.positiveBlocks, resolved.basePositive),
                negativeBlocks = resolvedBlocks(source.base.prompts.negativeBlocks, resolved.baseNegative),
            ),
            textRendering = TextRenderingState(),
        )
        val restoredCharacters = if (restoreOriginal) source.characters else source.characters.sortedBy { it.order }.mapIndexed { index, character ->
            character.copy(
                prompts = PromptPair(
                    positiveBlocks = resolvedBlocks(character.prompts.positiveBlocks, resolved.characterPositive.getOrElse(index) { "" }),
                    negativeBlocks = resolvedBlocks(character.prompts.negativeBlocks, resolved.characterNegative.getOrElse(index) { "" }),
                ),
                textRendering = TextRenderingState(),
            )
        }
        val baseSelection = BaseSetImportSelection(options.basePositive, options.baseNegative)
        replaceWithStash(current.copy(
            base = BaseSetImport.applyOrKeep(current.base, restoredBase, baseSelection),
            characters = if (options.characters) restoredCharacters else current.characters,
            generationSettings = generationSettings,
        ))
    }

    private fun resolvedBlocks(source: List<PromptBlock>, content: String): List<PromptBlock> =
        listOf((source.firstOrNull() ?: PromptBlock(name = "Resolved prompt")).copy(content = content, enabled = true, locked = false, order = 0))

    fun restorePreset(item: PresetItem) { item.session?.let(::replaceWithStash) }
    fun swapStash() {
        val current = _session.value ?: return
        val previous = previousWork.swap(current) ?: return
        val displayed = LoadedSessionDisplayPolicy.prepare(previous)

        // Invalidate the old editor's local/remote queries before publishing the new Session.
        clearAutocomplete()
        clearPromptUndo()
        _session.value = displayed
        _hasStash.value = previousWork.hasPrevious
        _loadUiRevision.value += 1

        viewModelScope.launch {
            saveMutex.withLock {
                container.sessionRepository.stashAndReplace(current, displayed)
            }
        }
    }

    private fun replaceWithStash(replacement: Session) {
        val current = _session.value ?: return
        viewModelScope.launch {
            val displayed = LoadedSessionDisplayPolicy.prepare(replacement)
            saveMutex.withLock {
                container.sessionRepository.stashAndReplace(current, displayed)
            }
            previousWork.replaceWith(current)
            clearPromptUndo()
            _session.value = displayed
            _hasStash.value = previousWork.hasPrevious
            _loadUiRevision.value += 1
        }
    }

    fun saveToken(token: String) {
        if (token.isBlank()) return
        runCatching { container.tokenStore.save(token) }
            .onSuccess { _tokenConfigured.value = true; refreshSubscriptionStatus() }
            .onFailure { _connectionState.value = ConnectionUiState.Failed }
    }

    fun clearToken() {
        container.tokenStore.clear()
        _tokenConfigured.value = false
        _connectionState.value = ConnectionUiState.Idle
        _subscriptionStatus.value = SubscriptionUiState.Unavailable
    }

    fun refreshSubscriptionStatus() {
        val token = container.tokenStore.load() ?: return
        viewModelScope.launch {
            _subscriptionStatus.value = SubscriptionUiState.Loading
            _subscriptionStatus.value = when (val result = container.generationRepository.subscriptionStatus(token)) {
                is NaiApiResult.Success -> SubscriptionUiState.Available(
                    anlas = result.value.trainingStepsLeft?.let { it.fixedTrainingStepsLeft + it.purchasedTrainingSteps },
                    opusPercent = result.value.usage?.percent?.takeUnless { result.value.usage.isNegative },
                )
                is NaiApiResult.Failure -> SubscriptionUiState.Unavailable
            }
        }
    }

    fun testConnection() {
        val token = container.tokenStore.load() ?: run {
            _connectionState.value = ConnectionUiState.MissingToken
            return
        }
        viewModelScope.launch {
            _connectionState.value = ConnectionUiState.Testing
            _connectionState.value = when (val result = container.generationRepository.testConnection(token)) {
                is NaiApiResult.Success -> ConnectionUiState.Success
                is NaiApiResult.Failure -> when (result.error) {
                    NaiApiFailure.Authentication -> ConnectionUiState.AuthenticationFailed
                    is NaiApiFailure.Network -> ConnectionUiState.NetworkFailed
                    is NaiApiFailure.Api -> ConnectionUiState.ApiFailed(result.error.statusCode)
                    else -> ConnectionUiState.ApiFailed(null)
                }
            }
            if (_connectionState.value == ConnectionUiState.Success) refreshSubscriptionStatus()
        }
    }

    fun generate() {
        if (!beginGeneration()) return
        val current = _session.value ?: return endGeneration()
        val wildcardValues = wildcards.value.associate { item -> item.name to item.valuesText.lines().filter(String::isNotBlank) }
        when (val prepared = requestMapper.prepare(
            current,
            settings.value.normalizeWeightClosings,
            wildcardValues,
            includeTextRendering = settings.value.useTextRendering,
        )) {
            is PrepareGenerationResult.Invalid -> {
                publishGenerationState(GenerationUiState.Invalid(prepared.field))
                endGeneration()
            }
            is PrepareGenerationResult.Ready -> viewModelScope.launch {
                try {
                    val previous = container.libraryRepository.latestGenerationWithOriginal()
                    val candidate = HistorySnapshotFactory.from(prepared.generation, prepared.generation.usedSeed).generation
                    if (previous != null && previous == candidate) {
                        duplicateGeneration = prepared.generation
                        _duplicateWarning.value = true
                    } else executeClaimed(prepared.generation)
                } finally {
                    endGeneration()
                }
            }
        }
    }

    fun confirmDuplicateGeneration() {
        _duplicateWarning.value = false
        duplicateGeneration?.also(::execute)
        duplicateGeneration = null
    }

    fun cancelDuplicateGeneration() { _duplicateWarning.value = false; duplicateGeneration = null }

    fun retry() { retryGeneration?.let(::execute) }
    private var selectedImageSeed: Long? = null
    fun selectImageSeed(seed: Long?) { selectedImageSeed = seed }
    fun applyHistorySeed(seed: Long) {
        if (!SeedSelection.isValid(seed)) return
        selectedImageSeed = seed
        updateGenerationSettings { SeedSelection.applyFixedSeed(it, seed) }
    }
    private val _imageInputApplied = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val imageInputApplied = _imageInputApplied.asSharedFlow()
    fun useImageInput(input: ImageInputState) {
        updateGenerationSettings { it.copy(imageInput = input) }
        _imageInputApplied.tryEmit(Unit)
    }

    private fun selectedOrCurrentSeed(): Long? =
        listOf(selectedImageSeed, _currentResult.value?.seed).firstOrNull(SeedSelection::isValid)

    private fun lastSuccessfulSeed(): Long? =
        settings.value.lastUsedSeed?.takeIf(SeedSelection::isValid)
            ?: history.value.firstNotNullOfOrNull { item ->
                item.snapshot?.generation?.usedSeed?.takeIf(SeedSelection::isValid)
            }

    fun changeSeedMode(mode: SeedMode) {
        updateGenerationSettings { SeedSelection.changeMode(it, mode, selectedOrCurrentSeed(), lastSuccessfulSeed()) }
    }

    fun toggleSeedMode() {
        updateGenerationSettings { SeedSelection.toggleMode(it, selectedOrCurrentSeed(), lastSuccessfulSeed()) }
    }

    private fun execute(generation: PreparedGeneration) {
        if (!beginGeneration()) return
        viewModelScope.launch {
            try {
                executeClaimed(generation)
            } finally {
                endGeneration()
            }
        }
    }

    private suspend fun executeClaimed(generation: PreparedGeneration) {
        val token = container.tokenStore.load() ?: run {
            publishGenerationState(GenerationUiState.MissingToken)
            return
        }
        publishGenerationState(GenerationUiState.Loading)
        when (val result = container.generationRepository.generate(token, generation)) {
            is GenerationResult.Success -> {
                val promptText = buildString {
                    append(generation.request.input)
                    append(' ').append(generation.request.parameters.negativePrompt)
                    generation.request.parameters.characterPrompts.forEach { append(' ').append(it.prompt).append(' ').append(it.uc) }
                }
                val usedFavoriteColors = settings.value.favoriteColors.filter { promptText.contains(it, ignoreCase = true) }
                if (usedFavoriteColors.isNotEmpty()) container.settingsRepository.recordFavoriteColorUsage(usedFavoriteColors)
                retryGeneration = null
                publishGenerationState(GenerationUiState.Success(result.record, autoOpenResult = true))
                container.settingsRepository.setLastUsedSeed(result.record.seed)
                selectedImageSeed = result.record.seed
                container.libraryRepository.trimHistory(settings.value.historyLimit)
                refreshSubscriptionStatus()
            }
            is GenerationResult.Failure -> {
                retryGeneration = generation
                publishGenerationState(GenerationUiState.Failed(result.error))
            }
        }
    }

    private fun publishGenerationState(state: GenerationUiState) {
        _currentResult.value = GenerationResultRetention.next(_currentResult.value, state)
        _generationState.value = state
    }

    private fun beginGeneration(): Boolean {
        return generationGate.tryStart()
    }

    private fun endGeneration() {
        generationGate.finish()
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
        }
    }
}

private fun Session.findPromptBlock(
    owner: PromptOwner,
    polarity: PromptPolarity,
    blockId: String,
): PromptBlock? {
    val pair = when (owner) {
        PromptOwner.Base -> base.prompts
        is PromptOwner.Character -> characters.firstOrNull { it.id == owner.id }?.prompts
    } ?: return null
    val blocks = if (polarity == PromptPolarity.POSITIVE) pair.positiveBlocks else pair.negativeBlocks
    return blocks.firstOrNull { it.id == blockId }
}

internal class PreviousWorkMemory {
    private var previous: Session? = null
    private var initialized = false

    val hasPrevious: Boolean get() = previous != null

    fun load(value: Session?) {
        if (!initialized) {
            previous = value
            initialized = true
        }
    }

    fun replaceWith(current: Session) {
        previous = current
        initialized = true
    }

    fun swap(current: Session): Session? {
        val replacement = previous ?: return null
        previous = current
        initialized = true
        return replacement
    }
}

internal class GenerationRequestGate {
    private val claimed = AtomicBoolean(false)
    private val _inProgress = MutableStateFlow(false)
    val inProgress: StateFlow<Boolean> = _inProgress.asStateFlow()

    fun tryStart(): Boolean {
        if (!claimed.compareAndSet(false, true)) return false
        _inProgress.value = true
        return true
    }

    fun finish() {
        claimed.set(false)
        _inProgress.value = false
    }
}

data class RestoreOptions(
    val settings: Boolean = true,
    val basePositive: Boolean = true,
    val baseNegative: Boolean = true,
    val characters: Boolean = true,
    val seed: Boolean = true,
    val inputImage: Boolean = false,
    val wildcardOriginal: Boolean = true,
)

sealed interface SavedWorkflow {
    data class SaveBlock(val block: PromptBlock) : SavedWorkflow
    data class SavePreset(val session: Session) : SavedWorkflow
    data class SaveSet(val set: SavedPromptSet) : SavedWorkflow
    data class LoadSet(val owner: PromptOwner) : SavedWorkflow
    data class LoadBlock(val owner: PromptOwner, val polarity: PromptPolarity, val blockId: String) : SavedWorkflow
    data object LoadPreset : SavedWorkflow
}

sealed interface GenerationUiState {
    data object Idle : GenerationUiState
    data object Loading : GenerationUiState
    data object MissingToken : GenerationUiState
    data class Invalid(val field: MissingGenerationField) : GenerationUiState
    data class Success(val record: GenerationRecord, val autoOpenResult: Boolean = true) : GenerationUiState
    data class Failed(val error: NaiApiFailure) : GenerationUiState
}

internal object GenerationResultRetention {
    fun next(current: GenerationRecord?, state: GenerationUiState): GenerationRecord? =
        (state as? GenerationUiState.Success)?.record ?: current
}

sealed interface ConnectionUiState {
    data object Idle : ConnectionUiState
    data object Testing : ConnectionUiState
    data object Success : ConnectionUiState
    data object Failed : ConnectionUiState
    data object AuthenticationFailed : ConnectionUiState
    data object NetworkFailed : ConnectionUiState
    data class ApiFailed(val statusCode: Int?) : ConnectionUiState
    data object MissingToken : ConnectionUiState
}

sealed interface SubscriptionUiState {
    data object Unavailable : SubscriptionUiState
    data object Loading : SubscriptionUiState
    data class Available(val anlas: Int?, val opusPercent: Int?) : SubscriptionUiState
}

data class AutocompleteUiState(
    val blockId: String? = null,
    val fragment: PromptFragment? = null,
    val local: List<TagSuggestion> = emptyList(),
    val novelAi: List<TagSuggestion> = emptyList(),
    val danbooru: List<TagSuggestion> = emptyList(),
    val wildcards: List<TagSuggestion> = emptyList(),
    val loading: Boolean = false,
    val novelAiFailed: Boolean = false,
    val danbooruFailed: Boolean = false,
    val requestStartedAtNanos: Long = 0L,
)

internal fun AutocompleteUiState.withLocalResults(results: List<TagSuggestion>): AutocompleteUiState = copy(
    local = results,
    novelAi = AutocompleteDeduplicator.excludeLocal(results, novelAi),
    danbooru = AutocompleteDeduplicator.excludeLocal(results, danbooru),
)

internal fun AutocompleteUiState.withRemoteResults(
    results: com.hjhsys.naiblockprompt.data.autocomplete.AutocompleteResults,
): AutocompleteUiState = copy(
    novelAi = AutocompleteDeduplicator.excludeLocal(local, results.novelAi),
    danbooru = AutocompleteDeduplicator.excludeLocal(local, results.danbooru),
    loading = false,
    novelAiFailed = results.novelAiFailed,
    danbooruFailed = results.danbooruFailed,
)

internal class AutocompleteRequestGuard {
    private var revision = 0L

    fun next(): Long = ++revision

    fun invalidate() {
        revision++
    }

    fun isCurrent(requestRevision: Long, blockId: String, fragment: PromptFragment, state: AutocompleteUiState): Boolean =
        requestRevision == revision && state.blockId == blockId && state.fragment == fragment
}

private data class TagInsertTarget(
    val owner: PromptOwner,
    val polarity: PromptPolarity,
    val blockId: String,
    val cursor: Int,
)

private data class DictionarySearch(
    val query: String,
    val filter: TagDictionaryFilter,
    val category: String,
    val sort: TagDictionarySort,
)
