package com.hjhsys.naiblockprompt.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.hjhsys.naiblockprompt.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM current_session WHERE id = 'current' LIMIT 1")
    fun observeCurrent(): Flow<CurrentSessionEntity?>

    @Query("SELECT * FROM current_session WHERE id = 'current' LIMIT 1")
    suspend fun getCurrent(): CurrentSessionEntity?

    @Upsert suspend fun upsert(entity: CurrentSessionEntity)
    @Upsert suspend fun upsertStash(entity: StashEntity)
    @Query("SELECT * FROM stash WHERE slot = 0 LIMIT 1") suspend fun getStash(): StashEntity?
    @Query("DELETE FROM stash WHERE slot = 0") suspend fun clearStash()
}

@Dao
interface SavedDao {
    @Query("SELECT * FROM saved_folders ORDER BY orderIndex, name") fun observeFolders(): Flow<List<SavedFolderEntity>>
    @Query("SELECT * FROM saved_blocks ORDER BY updatedAt DESC") fun observeBlocks(): Flow<List<SavedBlockEntity>>
    @Query("SELECT * FROM saved_blocks WHERE name = :name COLLATE NOCASE LIMIT 1") suspend fun findBlockByName(name: String): SavedBlockEntity?
    @Query("SELECT * FROM presets ORDER BY updatedAt DESC") fun observePresets(): Flow<List<PresetEntity>>
    @Query("SELECT * FROM saved_sets ORDER BY updatedAt DESC") fun observeSets(): Flow<List<SavedSetEntity>>
    @Query("SELECT * FROM presets WHERE name = :name COLLATE NOCASE LIMIT 1") suspend fun findPresetByName(name: String): PresetEntity?
    @Query("SELECT * FROM saved_sets WHERE name = :name COLLATE NOCASE AND kind = :kind LIMIT 1") suspend fun findSetByName(name: String, kind: String): SavedSetEntity?
    @Upsert suspend fun upsertFolder(entity: SavedFolderEntity)
    @Upsert suspend fun upsertBlock(entity: SavedBlockEntity)
    @Upsert suspend fun upsertPreset(entity: PresetEntity)
    @Upsert suspend fun upsertSet(entity: SavedSetEntity)
    @Delete suspend fun deleteBlock(entity: SavedBlockEntity)
    @Delete suspend fun deletePreset(entity: PresetEntity)
    @Delete suspend fun deleteSet(entity: SavedSetEntity)
    @Delete suspend fun deleteFolder(entity: SavedFolderEntity)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_entries ORDER BY createdAt DESC") fun observeAll(): Flow<List<HistoryEntryEntity>>
    @Upsert suspend fun upsert(entity: HistoryEntryEntity)
    @Query("DELETE FROM history_entries WHERE id IN (SELECT id FROM history_entries ORDER BY createdAt DESC LIMIT -1 OFFSET :keepCount)")
    suspend fun trimToLimit(keepCount: Int)
    @Query("SELECT * FROM history_entries ORDER BY createdAt DESC LIMIT 1") suspend fun latest(): HistoryEntryEntity?
    @Query("SELECT * FROM history_entries ORDER BY createdAt DESC LIMIT -1 OFFSET :keepCount")
    suspend fun entriesBeyondLimit(keepCount: Int): List<HistoryEntryEntity>
    @Delete suspend fun delete(entity: HistoryEntryEntity)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags WHERE canonicalTag = :canonical LIMIT 1")
    suspend fun findByCanonical(canonical: String): TagEntity?
    @Query("SELECT * FROM tags WHERE canonicalTag LIKE '%' || :query || '%' ORDER BY postCount DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int = 50): List<TagEntity>
    @Upsert suspend fun upsertTag(entity: TagEntity)
    @Upsert suspend fun upsertBaseTranslation(entity: BaseTranslationEntity)
    @Upsert suspend fun upsertUserOverride(entity: UserTagOverrideEntity)
    @Query("DELETE FROM user_tag_overrides WHERE tagId = :tagId") suspend fun clearUserOverride(tagId: String)
}
