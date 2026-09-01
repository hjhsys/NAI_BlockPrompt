package com.hjhsys.naiblockprompt.data.library

import com.hjhsys.naiblockprompt.data.local.dao.HistoryDao
import com.hjhsys.naiblockprompt.data.local.dao.SavedDao
import com.hjhsys.naiblockprompt.data.local.entity.*
import com.hjhsys.naiblockprompt.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

data class HistoryItem(val entity: HistoryEntryEntity, val snapshot: SessionSnapshot?, val originalExists: Boolean)
data class PresetItem(val entity: PresetEntity, val session: Session?)
data class SavedSetItem(val entity: SavedSetEntity, val set: SavedPromptSet?)

class LibraryRepository(
    private val savedDao: SavedDao,
    private val historyDao: HistoryDao,
    private val json: Json,
) {
    val history: Flow<List<HistoryItem>> = historyDao.observeAll().map { rows -> rows.map(::historyItem) }
    val blocks: Flow<List<SavedBlockEntity>> = savedDao.observeBlocks()
    val folders: Flow<List<SavedFolderEntity>> = savedDao.observeFolders()
    val presets: Flow<List<PresetItem>> = savedDao.observePresets().map { rows ->
        rows.map { row -> PresetItem(row, decode(row.snapshotVersion, row.snapshotJson)?.session) }
    }
    val sets: Flow<List<SavedSetItem>> = savedDao.observeSets().map { rows -> rows.map { SavedSetItem(it, runCatching { json.decodeFromString<SavedPromptSet>(it.snapshotJson) }.getOrNull()) } }

    suspend fun saveBlock(block: PromptBlock, name: String = block.name, folderId: String? = null) {
        val now = System.currentTimeMillis()
        val savedName = name.trim().ifBlank { block.name }
        val existing = savedDao.findBlockByName(savedName)
        savedDao.upsertBlock(SavedBlockEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            folderId = folderId,
            name = savedName,
            content = block.content,
            enabled = block.enabled,
            locked = block.locked,
            collapsed = block.collapsed,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        ))
    }

    suspend fun savePreset(name: String, session: Session, folderId: String? = null) {
        val now = System.currentTimeMillis()
        val savedName = name.trim().ifBlank { "Preset" }
        val existing = savedDao.findPresetByName(savedName)
        savedDao.upsertPreset(PresetEntity(existing?.id ?: UUID.randomUUID().toString(), folderId, savedName, existing?.createdAt ?: now, now, CURRENT_SNAPSHOT_VERSION, json.encodeToString(SessionSnapshot(session = session))))
    }

    suspend fun saveSet(name: String, set: SavedPromptSet, folderId: String?) {
        val now = System.currentTimeMillis(); val savedName = name.trim()
        val existing = savedDao.findSetByName(savedName, set.kind.name)
        savedDao.upsertSet(SavedSetEntity(existing?.id ?: UUID.randomUUID().toString(), folderId, savedName, set.kind.name, CURRENT_SNAPSHOT_VERSION, json.encodeToString(set), existing?.createdAt ?: now, now))
    }

    suspend fun createFolder(name: String, order: Int) {
        val now = System.currentTimeMillis()
        savedDao.upsertFolder(SavedFolderEntity(UUID.randomUUID().toString(), name, order, now, now))
    }

    suspend fun deleteBlock(item: SavedBlockEntity) = savedDao.deleteBlock(item)
    suspend fun moveBlock(item: SavedBlockEntity, folderId: String?) = savedDao.upsertBlock(item.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))
    suspend fun movePreset(item: PresetEntity, folderId: String?) = savedDao.upsertPreset(item.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))
    suspend fun moveSet(item: SavedSetEntity, folderId: String?) = savedDao.upsertSet(item.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))
    suspend fun deleteFolder(item: SavedFolderEntity) = savedDao.deleteFolder(item)
    suspend fun deletePreset(item: PresetEntity) = savedDao.deletePreset(item)
    suspend fun deleteSet(item: SavedSetEntity) = savedDao.deleteSet(item)

    suspend fun deleteHistory(item: HistoryEntryEntity) {
        historyDao.delete(item)
        File(item.thumbnailPath).delete()
        File(item.imagePath).delete()
    }

    suspend fun latestSnapshot(): SessionSnapshot? = historyDao.latest()?.let { decode(it.snapshotVersion, it.snapshotJson) }
    suspend fun trimHistory(limit: Int) {
        val keep = limit.coerceIn(1, 100)
        historyDao.entriesBeyondLimit(keep).forEach {
            File(it.thumbnailPath).delete()
            File(it.imagePath).delete()
        }
        historyDao.trimToLimit(keep)
    }

    private fun historyItem(row: HistoryEntryEntity) = HistoryItem(row, decode(row.snapshotVersion, row.snapshotJson), File(row.imagePath).isFile)
    private fun decode(version: Int, value: String): SessionSnapshot? = runCatching {
        require(version == CURRENT_SNAPSHOT_VERSION)
        json.decodeFromString<SessionSnapshot>(value)
    }.getOrNull()
}
