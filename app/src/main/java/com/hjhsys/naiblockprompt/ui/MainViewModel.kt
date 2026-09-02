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
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.hjhsys.naiblockprompt.domain.autocomplete.PromptFragment
import com.hjhsys.naiblockprompt.domain.autocomplete.TagSuggestion
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(FlowPreview::class)
class MainViewModel(private val container: AppContainer) : ViewModel() {
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
    private var autocompleteJob: Job? = null

    val settings = container.settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    init {
        viewModelScope.launch { _session.value = container.sessionRepository.restoreOrCreate() }
        viewModelScope.launch { _hasStash.value = container.sessionRepository.hasStash() }
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

    fun requestAutocomplete(blockId: String, fragment: PromptFragment?) {
        autocompleteJob?.cancel()
        if (fragment == null) {
            _autocomplete.value = AutocompleteUiState()
            return
        }
        val requestKey = "$blockId:${fragment.text}"
        _autocomplete.value = AutocompleteUiState(blockId, fragment, loading = true)
        autocompleteJob = viewModelScope.launch {
            delay(400)
            val source = settings.value.autocompleteSource
            val token = container.tokenStore.load()
            // The official Primary API Swagger currently verifies this suggest-tags model value.
            val model = "nai-diffusion-3"
            val result = container.autocompleteRepository.suggest(fragment.text, source, token, model)
            if (requestKey == "$blockId:${_autocomplete.value.fragment?.text}") {
                _autocomplete.value = AutocompleteUiState(blockId, fragment, result.novelAi, result.danbooru, failed = result.failed)
            }
        }
    }

    fun clearAutocomplete() {
        autocompleteJob?.cancel()
        _autocomplete.value = AutocompleteUiState()
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

    fun addSavedBlockToBase(item: SavedBlockEntity) = edit { current ->
        val blocks = current.base.prompts.positiveBlocks
        val block = PromptBlock(name = item.name, content = item.content, enabled = item.enabled, locked = item.locked, order = blocks.size)
        current.copy(base = current.base.copy(prompts = current.base.prompts.copy(positiveBlocks = blocks + block)))
    }

    fun restoreHistory(item: HistoryItem, options: RestoreOptions) {
        val source = item.snapshot?.session ?: return
        val current = _session.value ?: return
        var generationSettings = if (options.settings) source.generationSettings else current.generationSettings
        if (!options.settings && options.seed) {
            generationSettings = generationSettings.copy(seedMode = source.generationSettings.seedMode, seed = source.generationSettings.seed)
        } else if (options.settings && !options.seed) {
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
            .onSuccess { _tokenConfigured.value = true }
            .onFailure { _connectionState.value = ConnectionUiState.Failed }
    }

    fun clearToken() {
        container.tokenStore.clear()
        _tokenConfigured.value = false
        _connectionState.value = ConnectionUiState.Idle
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
        }
    }

    fun generate() {
        val current = _session.value ?: return
        when (val prepared = requestMapper.prepare(current, settings.value.normalizeWeightClosings)) {
            is PrepareGenerationResult.Invalid -> _generationState.value = GenerationUiState.Invalid(prepared.field)
            is PrepareGenerationResult.Ready -> viewModelScope.launch {
                val previous = container.libraryRepository.latestSnapshot()?.generation
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
                    _generationState.value = GenerationUiState.Success(result.record)
                    container.libraryRepository.trimHistory(settings.value.historyLimit)
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
    data class Success(val record: GenerationRecord) : GenerationUiState
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

data class AutocompleteUiState(
    val blockId: String? = null,
    val fragment: PromptFragment? = null,
    val novelAi: List<TagSuggestion> = emptyList(),
    val danbooru: List<TagSuggestion> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
)
