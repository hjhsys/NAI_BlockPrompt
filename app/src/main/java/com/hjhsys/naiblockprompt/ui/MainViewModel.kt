package com.hjhsys.naiblockprompt.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hjhsys.naiblockprompt.AppContainer
import com.hjhsys.naiblockprompt.domain.editor.*
import com.hjhsys.naiblockprompt.domain.model.AppSettings
import com.hjhsys.naiblockprompt.domain.model.*
import com.hjhsys.naiblockprompt.data.generation.*
import com.hjhsys.naiblockprompt.data.network.nai.*
import com.hjhsys.naiblockprompt.data.library.*
import com.hjhsys.naiblockprompt.data.local.entity.*
import com.hjhsys.naiblockprompt.domain.generation.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.hjhsys.naiblockprompt.domain.autocomplete.PromptFragment
import com.hjhsys.naiblockprompt.domain.autocomplete.TagSuggestion
import com.hjhsys.naiblockprompt.domain.autocomplete.AutocompleteDeduplicator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.asSharedFlow
import com.hjhsys.naiblockprompt.domain.tags.TagTranslationImportPreview
import com.hjhsys.naiblockprompt.domain.tags.TagTranslationExportFile
import com.hjhsys.naiblockprompt.domain.image.NaiImageMetadata

@OptIn(FlowPreview::class)
class MainViewModel(private val container: AppContainer) : ViewModel() {
    data class TransferFile(val name: String, val mimeType: String, val bytes: ByteArray)
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()
    private val saveSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val saveMutex = Mutex()
    private val requestMapper = NaiRequestMapper()
    private var retryGeneration: PreparedGeneration? = null
    private var duplicateGeneration: PreparedGeneration? = null
    private val _duplicateWarning = MutableStateFlow(false)
    val duplicateWarning: StateFlow<Boolean> = _duplicateWarning.asStateFlow()

    private val _generationState = MutableStateFlow<GenerationUiState>(GenerationUiState.Idle)
    val generationState: StateFlow<GenerationUiState> = _generationState.asStateFlow()
    private val _tokenConfigured = MutableStateFlow(container.tokenStore.isConfigured())
    val tokenConfigured: StateFlow<Boolean> = _tokenConfigured.asStateFlow()
    private val _connectionState = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Idle)
    val connectionState: StateFlow<ConnectionUiState> = _connectionState.asStateFlow()
    private val _subscriptionStatus = MutableStateFlow<SubscriptionUiState>(SubscriptionUiState.Unavailable)
    val subscriptionStatus: StateFlow<SubscriptionUiState> = _subscriptionStatus.asStateFlow()
    val history = container.libraryRepository.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val savedBlocks = container.libraryRepository.blocks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val savedFolders = container.libraryRepository.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val presets = container.libraryRepository.presets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val savedSets = container.libraryRepository.sets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _hasStash = MutableStateFlow(false)
    val hasStash: StateFlow<Boolean> = _hasStash.asStateFlow()
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
    private val tagDictionaryQuery = MutableStateFlow("")
    private val tagDictionaryFilter = MutableStateFlow(TagDictionaryFilter.ALL)
    private val tagDictionaryCategory = MutableStateFlow("")
    private val tagDictionarySort = MutableStateFlow(TagDictionarySort.POPULAR)
    private var tagInsertTarget: TagInsertTarget? = null
    private val _tagInsertAvailable = MutableStateFlow(false)
    val tagInsertAvailable: StateFlow<Boolean> = _tagInsertAvailable.asStateFlow()
    val usedTagCategories = container.autocompleteRepository.usedCategories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val userTagCategories = container.autocompleteRepository.userCategories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val dictionaryTags = combine(tagDictionaryQuery, tagDictionaryFilter, tagDictionaryCategory, tagDictionarySort) { query, filter, category, sort ->
        DictionarySearch(query, filter, category, sort)
    }.flatMapLatest { search -> container.autocompleteRepository.dictionary(search.query, search.filter, search.category, search.sort) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private var autocompleteJob: Job? = null
    private val _translationExport = MutableSharedFlow<TagTranslationExportFile>(extraBufferCapacity = 1)
    val translationExport = _translationExport.asSharedFlow()
    private val _translationImportPreview = MutableStateFlow<TagTranslationImportPreview?>(null)
    val translationImportPreview = _translationImportPreview.asStateFlow()
    private val _transferExport = MutableSharedFlow<TransferFile>(extraBufferCapacity = 1)
    val transferExport = _transferExport.asSharedFlow()

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
                _generationState.value = GenerationUiState.Success(
                    GenerationRecord(latest.entity.imagePath, latest.entity.thumbnailPath, seed),
                    autoOpenResult = false,
                )
            }
        }
        viewModelScope.launch { _hasStash.value = container.sessionRepository.hasStash() }
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

    fun setBlockEnabled(owner: PromptOwner, polarity: PromptPolarity, id: String, enabled: Boolean) = edit {
        SessionEditor.setBlockEnabled(it, owner, polarity, id, enabled)
    }

    fun setBlockCollapsed(owner: PromptOwner, polarity: PromptPolarity, id: String, collapsed: Boolean) = edit {
        SessionEditor.setBlockCollapsed(it, owner, polarity, id, collapsed)
    }

    fun setBlockLocked(owner: PromptOwner, polarity: PromptPolarity, id: String, locked: Boolean) = edit {
        SessionEditor.setBlockLocked(it, owner, polarity, id, locked)
    }

    fun formatBlock(owner: PromptOwner, polarity: PromptPolarity, id: String, formatter: BlockFormatter) = edit {
        SessionEditor.formatBlock(it, owner, polarity, id, formatter)
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
    ) = edit { current ->
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
        current.copy(base = base, characters = characters, generationSettings = generationSettings)
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
        autocompleteJob?.cancel()
        if (fragment == null) {
            _autocomplete.value = AutocompleteUiState()
            return
        }
        val requestKey = "$blockId:${fragment.text}"
        _autocomplete.value = AutocompleteUiState(blockId, fragment, loading = true)
        autocompleteJob = viewModelScope.launch {
            val local = container.autocompleteRepository.local(fragment.text)
            if (requestKey == "$blockId:${_autocomplete.value.fragment?.text}") {
                _autocomplete.value = _autocomplete.value.copy(local = local)
            }
            delay(400)
            val source = settings.value.autocompleteSource
            val token = container.tokenStore.load()
            // The official Primary API Swagger currently verifies this suggest-tags model value.
            val model = "nai-diffusion-3"
            val result = container.autocompleteRepository.suggest(fragment.text, source, token, model)
            if (requestKey == "$blockId:${_autocomplete.value.fragment?.text}") {
                _autocomplete.value = AutocompleteUiState(
                    blockId,
                    fragment,
                    local,
                    AutocompleteDeduplicator.excludeLocal(local, result.novelAi),
                    AutocompleteDeduplicator.excludeLocal(local, result.danbooru),
                    novelAiFailed = result.novelAiFailed,
                    danbooruFailed = result.danbooruFailed,
                )
            }
        }
    }

    fun clearAutocomplete() {
        autocompleteJob?.cancel()
        _autocomplete.value = AutocompleteUiState()
    }

    fun recordAutocompleteUse(suggestion: TagSuggestion) = viewModelScope.launch {
        container.autocompleteRepository.recordSelection(suggestion)
    }

    fun searchDictionary(query: String) { tagDictionaryQuery.value = query }
    fun filterDictionary(filter: TagDictionaryFilter) { tagDictionaryFilter.value = filter }
    fun filterDictionaryCategory(category: String) { tagDictionaryCategory.value = category }
    fun sortDictionary(sort: TagDictionarySort) { tagDictionarySort.value = sort }
    fun addTagCategory(name: String) = viewModelScope.launch { container.autocompleteRepository.addCategory(name) }
    fun setTagThumbnail(item: TagDictionaryItem, uri: android.net.Uri) = viewModelScope.launch { container.autocompleteRepository.setThumbnail(item, uri) }
    fun setTagThumbnailFromFile(item: TagDictionaryItem, path: String) = viewModelScope.launch { container.autocompleteRepository.setThumbnailFromFile(item, path) }
    fun prepareTranslationExport(missingTranslation: Boolean, missingCategory: Boolean) = viewModelScope.launch {
        _translationExport.emit(container.autocompleteRepository.exportTranslationBatch(missingTranslation, missingCategory))
    }
    fun prepareAllTranslationExport(missingTranslation: Boolean, missingCategory: Boolean) = viewModelScope.launch {
        _translationExport.emit(container.autocompleteRepository.exportTranslationBatch(missingTranslation, missingCategory, allBatches = true))
    }
    fun previewTranslationImport(text: String) = viewModelScope.launch {
        _translationImportPreview.value = container.autocompleteRepository.previewTranslationImport(text)
    }
    fun dismissTranslationImport() { _translationImportPreview.value = null }
    fun applyTranslationImport(overwriteExisting: Boolean) = viewModelScope.launch {
        val preview = _translationImportPreview.value ?: return@launch
        container.autocompleteRepository.applyTranslationImport(preview, overwriteExisting)
        _translationImportPreview.value = null
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
    fun resetTagDatabaseToBundled() = viewModelScope.launch {
        container.autocompleteRepository.resetToBundledTags()
    }
    fun exportSharedTagDatabase() = viewModelScope.launch {
        _transferExport.emit(TransferFile("nai_user_tag_db.zip", "application/zip", container.backupRepository.exportTagData()))
    }
    fun importSharedTagDatabase(bytes: ByteArray) = viewModelScope.launch {
        runCatching { container.backupRepository.importTagData(bytes) }
    }
    fun exportAppBackup() = viewModelScope.launch {
        flushAutosave()
        _transferExport.emit(TransferFile("nai_blockprompt_backup.zip", "application/zip", container.backupRepository.exportAppBackup()))
    }
    fun importAppBackup(bytes: ByteArray) = viewModelScope.launch {
        runCatching { container.backupRepository.importAppBackup(bytes) }.onSuccess {
            _session.value = container.sessionRepository.restoreOrCreate()
            _hasStash.value = container.sessionRepository.hasStash()
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
        updateBlock(target.owner, target.polarity, target.blockId) { block ->
            block.copy(content = TagInsertion.insert(block.content, target.cursor, tags))
        }
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
    fun finishSetLoad(item: SavedSetItem) {
        val workflow = _savedWorkflow.value as? SavedWorkflow.LoadSet ?: return
        val set = item.set ?: return
        edit { current -> when (val owner = workflow.owner) {
            PromptOwner.Base -> if (set.kind == SavedSetKind.BASE) current.copy(base = current.base.copy(prompts = set.prompts, selectedPolarity = set.selectedPolarity, textRendering = set.textRendering)) else current
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
        replaceWithStash(current.copy(
            base = if (options.base) source.base else current.base,
            characters = if (options.characters) source.characters else current.characters,
            generationSettings = generationSettings,
        ))
    }

    fun restorePreset(item: PresetItem) { item.session?.let(::replaceWithStash) }
    fun swapStash() {
        val current = _session.value ?: return
        viewModelScope.launch {
            container.sessionRepository.swapWithStash(current)?.let {
                _session.value = it
                container.sessionRepository.save(it)
            }
            _hasStash.value = container.sessionRepository.hasStash()
        }
    }

    private fun replaceWithStash(replacement: Session) {
        val current = _session.value ?: return
        viewModelScope.launch {
            container.sessionRepository.stashAndReplace(current, replacement)
            _session.value = replacement
            _hasStash.value = true
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
        val current = _session.value ?: return
        when (val prepared = requestMapper.prepare(current, settings.value.normalizeWeightClosings)) {
            is PrepareGenerationResult.Invalid -> _generationState.value = GenerationUiState.Invalid(prepared.field)
            is PrepareGenerationResult.Ready -> viewModelScope.launch {
                val previous = container.libraryRepository.latestGenerationWithOriginal()
                val candidate = HistorySnapshotFactory.from(prepared.generation, prepared.generation.usedSeed).generation
                if (previous != null && previous == candidate) {
                    duplicateGeneration = prepared.generation
                    _duplicateWarning.value = true
                } else execute(prepared.generation)
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

    private fun execute(generation: PreparedGeneration) {
        if (_generationState.value is GenerationUiState.Loading) return
        val token = container.tokenStore.load() ?: run {
            _generationState.value = GenerationUiState.MissingToken
            return
        }
        viewModelScope.launch {
            _generationState.value = GenerationUiState.Loading
            when (val result = container.generationRepository.generate(token, generation)) {
                is GenerationResult.Success -> {
                    retryGeneration = null
                    _generationState.value = GenerationUiState.Success(result.record, autoOpenResult = true)
                    container.libraryRepository.trimHistory(settings.value.historyLimit)
                    refreshSubscriptionStatus()
                }
                is GenerationResult.Failure -> {
                    retryGeneration = generation
                    _generationState.value = GenerationUiState.Failed(result.error)
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
        }
    }
}

data class RestoreOptions(
    val settings: Boolean = true,
    val base: Boolean = true,
    val characters: Boolean = true,
    val seed: Boolean = true,
    val inputImage: Boolean = false,
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
    val loading: Boolean = false,
    val novelAiFailed: Boolean = false,
    val danbooruFailed: Boolean = false,
)

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
