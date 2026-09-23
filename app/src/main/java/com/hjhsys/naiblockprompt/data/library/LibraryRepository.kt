package com.hjhsys.naiblockprompt.data.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.hjhsys.naiblockprompt.data.generation.GeneratedImageStore
import com.hjhsys.naiblockprompt.data.local.dao.HistoryDao
import com.hjhsys.naiblockprompt.data.local.dao.SavedDao
import com.hjhsys.naiblockprompt.data.local.entity.*
import com.hjhsys.naiblockprompt.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

data class HistoryItem(val entity: HistoryEntryEntity, val snapshot: SessionSnapshot?, val originalExists: Boolean, val inputImageExists: Boolean)
data class HistoryStorageSample(val averageOriginalBytes: Long? = null, val sampleCount: Int = 0)
data class PresetItem(val entity: PresetEntity, val session: Session?)
data class SavedSetItem(val entity: SavedSetEntity, val set: SavedPromptSet?)
sealed interface HistoryDeleteResult {
    data object Success : HistoryDeleteResult
    data object OriginalDeleteFailed : HistoryDeleteResult
}
object HistoryDeletionPolicy {
    fun result(deleteOriginal: Boolean, originalExists: Boolean, deleteSucceeded: Boolean): HistoryDeleteResult =
        if (!deleteOriginal || !originalExists || deleteSucceeded) HistoryDeleteResult.Success
        else HistoryDeleteResult.OriginalDeleteFailed
}

object HistoryRetentionPolicy {
    fun entriesToTrim(entries: List<HistoryEntryEntity>, keepNormal: Int): List<HistoryEntryEntity> = entries
        .filterNot { it.favorite }
        .sortedByDescending { it.createdAt }
        .drop(keepNormal.coerceIn(1, 100))
}

object HistoryStorageEstimator {
    const val MIN_SAMPLE_COUNT = 3

    fun estimatedBytes(sample: HistoryStorageSample, historyLimit: Int): Long? =
        sample.averageOriginalBytes
            ?.takeIf { sample.sampleCount >= MIN_SAMPLE_COUNT }
            ?.times(historyLimit.coerceIn(1, 100).toLong())
}

class LibraryRepository(
    private val context: Context,
    private val savedDao: SavedDao,
    private val historyDao: HistoryDao,
    private val json: Json,
) {
    private val imageStore = GeneratedImageStore(context)
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
            scaleMode = block.scaleMode.name,
            customScale = block.customScale,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            thumbnailPath = existing?.thumbnailPath,
        ))
    }

    suspend fun savePreset(name: String, session: Session, folderId: String? = null) {
        val now = System.currentTimeMillis()
        val savedName = name.trim().ifBlank { "Preset" }
        val existing = savedDao.findPresetByName(savedName)
        savedDao.upsertPreset(PresetEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            folderId = folderId,
            name = savedName,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            snapshotVersion = CURRENT_SNAPSHOT_VERSION,
            snapshotJson = json.encodeToString(SessionSnapshot(session = session)),
            thumbnailPath = existing?.thumbnailPath,
        ))
    }

    suspend fun saveSet(name: String, set: SavedPromptSet, folderId: String?) {
        val now = System.currentTimeMillis(); val savedName = name.trim()
        val existing = savedDao.findSetByName(savedName, set.kind.name)
        savedDao.upsertSet(SavedSetEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            folderId = folderId,
            name = savedName,
            kind = set.kind.name,
            snapshotVersion = CURRENT_SNAPSHOT_VERSION,
            snapshotJson = json.encodeToString(set),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            thumbnailPath = existing?.thumbnailPath,
        ))
    }

    suspend fun createFolder(name: String, kind: SavedFolderKind, order: Int) {
        val now = System.currentTimeMillis()
        savedDao.upsertFolder(SavedFolderEntity(UUID.randomUUID().toString(), name, kind.name, order, now, now))
    }

    suspend fun deleteBlock(item: SavedBlockEntity) {
        savedDao.deleteBlock(item)
        item.thumbnailPath?.let(::File)?.delete()
    }
    suspend fun moveBlock(item: SavedBlockEntity, folderId: String?) = savedDao.upsertBlock(item.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))
    suspend fun movePreset(item: PresetEntity, folderId: String?) = savedDao.upsertPreset(item.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))
    suspend fun moveSet(item: SavedSetEntity, folderId: String?) = savedDao.upsertSet(item.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))
    suspend fun moveBlocks(items: List<SavedBlockEntity>, folderId: String?) = savedDao.upsertBlocks(items.map { it.copy(folderId = folderId, updatedAt = System.currentTimeMillis()) })
    suspend fun movePresets(items: List<PresetEntity>, folderId: String?) = savedDao.upsertPresets(items.map { it.copy(folderId = folderId, updatedAt = System.currentTimeMillis()) })
    suspend fun moveSets(items: List<SavedSetEntity>, folderId: String?) = savedDao.upsertSets(items.map { it.copy(folderId = folderId, updatedAt = System.currentTimeMillis()) })
    suspend fun deleteFolder(item: SavedFolderEntity) = savedDao.deleteFolder(item)
    suspend fun deletePreset(item: PresetEntity) {
        savedDao.deletePreset(item)
        item.thumbnailPath?.let(::File)?.delete()
    }
    suspend fun deleteSet(item: SavedSetEntity) {
        savedDao.deleteSet(item)
        item.thumbnailPath?.let(::File)?.delete()
    }

    suspend fun setLibraryThumbnail(kind: SavedFolderKind, id: String, previousPath: String?, uri: Uri) {
        storeLibraryThumbnail(kind, id, previousPath) { options ->
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    }

    suspend fun setLibraryThumbnailFromFile(kind: SavedFolderKind, id: String, previousPath: String?, sourcePath: String) {
        val uri = sourcePath.takeIf { it.startsWith("content://") }?.let(Uri::parse)
        if (uri != null) setLibraryThumbnail(kind, id, previousPath, uri)
        else storeLibraryThumbnail(kind, id, previousPath) { options -> BitmapFactory.decodeFile(sourcePath, options) }
    }

    suspend fun removeLibraryThumbnail(kind: SavedFolderKind, id: String, previousPath: String?) = withContext(Dispatchers.IO) {
        previousPath?.let(::File)?.takeIf(File::isFile)?.delete()
        libraryThumbnailFile(kind, id).takeIf(File::isFile)?.delete()
        updateLibraryThumbnailPath(kind, id, null)
    }

    private suspend fun storeLibraryThumbnail(
        kind: SavedFolderKind,
        id: String,
        previousPath: String?,
        decode: (BitmapFactory.Options) -> Bitmap?,
    ) = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext
        var sample = 1
        while (bounds.outWidth / sample > 768 || bounds.outHeight / sample > 768) sample *= 2
        val bitmap = decode(BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext
        val destination = libraryThumbnailFile(kind, id).also { it.parentFile?.mkdirs() }
        destination.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP, 86, it) }
        bitmap.recycle()
        previousPath?.takeIf { it != destination.absolutePath }?.let(::File)?.takeIf(File::isFile)?.delete()
        updateLibraryThumbnailPath(kind, id, destination.absolutePath)
    }

    private suspend fun updateLibraryThumbnailPath(kind: SavedFolderKind, id: String, path: String?) {
        val now = System.currentTimeMillis()
        when (kind) {
            SavedFolderKind.BLOCK -> savedDao.setBlockThumbnail(id, path, now)
            SavedFolderKind.SET -> savedDao.setSetThumbnail(id, path, now)
            SavedFolderKind.PRESET -> savedDao.setPresetThumbnail(id, path, now)
        }
    }

    private fun libraryThumbnailFile(kind: SavedFolderKind, id: String) =
        File(File(context.filesDir, "library_thumbnails"), "${kind.name.lowercase()}_$id.webp")

    suspend fun deleteHistory(item: HistoryEntryEntity, deleteOriginal: Boolean): HistoryDeleteResult {
        historyDao.delete(item)
        File(item.thumbnailPath).delete()
        val exists = imageStore.exists(item.imagePath)
        val deleted = !deleteOriginal || !exists || imageStore.delete(item.imagePath)
        return HistoryDeletionPolicy.result(deleteOriginal, exists, deleted)
    }

    suspend fun setHistoryFavorite(item: HistoryEntryEntity, favorite: Boolean) =
        historyDao.setFavorite(item.id, favorite)

    suspend fun latestGenerationWithOriginal(): GeneratedPromptSnapshot? = historyDao.latest()?.let { row ->
        if (imageStore.exists(row.imagePath)) decode(row.snapshotVersion, row.snapshotJson)?.generation else null
    }
    suspend fun latestHistoryWithOriginal(): HistoryItem? = historyDao.latest()
        ?.let(::historyItem)
        ?.takeIf { it.originalExists }

    suspend fun sampleHistoryStorage(items: List<HistoryItem>, maxSamples: Int = 12): HistoryStorageSample =
        withContext(Dispatchers.IO) {
            val sizes = items.asSequence()
                .filter { it.originalExists }
                .take(maxSamples.coerceAtLeast(1))
                .mapNotNull { imageStore.sizeBytes(it.entity.imagePath) }
                .filter { it > 0L }
                .toList()
            HistoryStorageSample(
                averageOriginalBytes = sizes.takeIf { it.isNotEmpty() }?.average()?.toLong(),
                sampleCount = sizes.size,
            )
        }
    suspend fun trimHistory(limit: Int) {
        val keep = limit.coerceIn(1, 100)
        HistoryRetentionPolicy.entriesToTrim(historyDao.listAll(), keep).forEach {
            File(it.thumbnailPath).delete()
            imageStore.delete(it.imagePath)
            historyDao.delete(it)
        }
    }

    private fun historyItem(row: HistoryEntryEntity): HistoryItem {
        val snapshot = decode(row.snapshotVersion, row.snapshotJson)
        val inputReference = snapshot?.session?.generationSettings?.imageInput?.uri
        return HistoryItem(row, snapshot, imageStore.exists(row.imagePath), inputReference != null && imageStore.exists(inputReference))
    }
    private fun decode(version: Int, value: String): SessionSnapshot? = runCatching {
        require(version == CURRENT_SNAPSHOT_VERSION)
        json.decodeFromString<SessionSnapshot>(value)
    }.getOrNull()
}
