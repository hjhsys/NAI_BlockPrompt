package com.hjhsys.naiblockprompt.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction
import com.hjhsys.naiblockprompt.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import com.hjhsys.naiblockprompt.domain.model.TagDictionaryItem

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
    @Query("SELECT * FROM saved_folders") suspend fun listFolders(): List<SavedFolderEntity>
    @Query("SELECT * FROM saved_blocks") suspend fun listBlocks(): List<SavedBlockEntity>
    @Query("SELECT * FROM presets") suspend fun listPresets(): List<PresetEntity>
    @Query("SELECT * FROM saved_sets") suspend fun listSets(): List<SavedSetEntity>
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
    @Upsert suspend fun upsertFolders(entities: List<SavedFolderEntity>)
    @Upsert suspend fun upsertBlocks(entities: List<SavedBlockEntity>)
    @Upsert suspend fun upsertPresets(entities: List<PresetEntity>)
    @Upsert suspend fun upsertSets(entities: List<SavedSetEntity>)
    @Delete suspend fun deleteBlock(entity: SavedBlockEntity)
    @Delete suspend fun deletePreset(entity: PresetEntity)
    @Delete suspend fun deleteSet(entity: SavedSetEntity)
    @Delete suspend fun deleteFolder(entity: SavedFolderEntity)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_entries ORDER BY createdAt DESC") fun observeAll(): Flow<List<HistoryEntryEntity>>
    @Query("SELECT * FROM history_entries ORDER BY createdAt DESC") suspend fun listAll(): List<HistoryEntryEntity>
    @Upsert suspend fun upsert(entity: HistoryEntryEntity)
    @Upsert suspend fun upsertAll(entities: List<HistoryEntryEntity>)
    @Query("DELETE FROM history_entries WHERE id IN (SELECT id FROM history_entries WHERE favorite = 0 ORDER BY createdAt DESC LIMIT -1 OFFSET :keepCount)")
    suspend fun trimToLimit(keepCount: Int)
    @Query("SELECT * FROM history_entries ORDER BY createdAt DESC LIMIT 1") suspend fun latest(): HistoryEntryEntity?
    @Query("SELECT * FROM history_entries WHERE favorite = 0 ORDER BY createdAt DESC LIMIT -1 OFFSET :keepCount")
    suspend fun entriesBeyondLimit(keepCount: Int): List<HistoryEntryEntity>
    @Query("UPDATE history_entries SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)
    @Delete suspend fun delete(entity: HistoryEntryEntity)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM wildcards ORDER BY name COLLATE NOCASE") fun observeWildcards(): Flow<List<WildcardEntity>>
    @Query("SELECT * FROM wildcards ORDER BY name COLLATE NOCASE") suspend fun listWildcards(): List<WildcardEntity>
    @Query("SELECT * FROM wildcards WHERE name = :name COLLATE NOCASE LIMIT 1") suspend fun findWildcardByName(name: String): WildcardEntity?
    @Upsert suspend fun upsertWildcards(entities: List<WildcardEntity>)
    @Upsert suspend fun upsertWildcard(entity: WildcardEntity)
    @Delete suspend fun deleteWildcard(entity: WildcardEntity)
    @Query("SELECT * FROM tags WHERE bundled = 0 OR useCount > 0 OR lastUsedAt IS NOT NULL OR novelAiSource = 1 OR danbooruSource = 1 OR userCreated = 1") suspend fun listPortableTags(): List<TagEntity>
    @Query("SELECT * FROM tag_aliases WHERE tagId IN (SELECT id FROM tags WHERE bundled = 0)") suspend fun listPortableAliases(): List<TagAliasEntity>
    @Query("SELECT * FROM user_tag_overrides") suspend fun listUserOverrides(): List<UserTagOverrideEntity>
    @Query("SELECT * FROM tag_exclusions") suspend fun listTagExclusions(): List<TagExclusionEntity>
    @Query("SELECT * FROM user_tag_categories") suspend fun listUserCategoryEntities(): List<UserTagCategoryEntity>
    @Upsert suspend fun upsertTags(entities: List<TagEntity>)
    @Upsert suspend fun upsertAliases(entities: List<TagAliasEntity>)
    @Upsert suspend fun upsertAliasForBackup(entity: TagAliasEntity)
    @Upsert suspend fun upsertUserOverrides(entities: List<UserTagOverrideEntity>)
    @Upsert suspend fun upsertTagExclusions(entities: List<TagExclusionEntity>)
    @Upsert suspend fun upsertUserCategories(entities: List<UserTagCategoryEntity>)
    @Query("SELECT DISTINCT COALESCE(u.appCategory, t.appCategory, t.danbooruCategory) FROM tags t LEFT JOIN user_tag_overrides u ON u.tagId = t.id WHERE COALESCE(u.appCategory, t.appCategory, t.danbooruCategory, '') != '' ORDER BY 1")
    fun observeUsedCategories(): Flow<List<String>>
    @Query("SELECT name FROM user_tag_categories ORDER BY name") fun observeUserCategories(): Flow<List<String>>
    @Upsert suspend fun upsertUserCategory(entity: UserTagCategoryEntity)
    @Query("SELECT DISTINCT COALESCE(u.appCategory, t.appCategory, t.danbooruCategory) FROM tags t LEFT JOIN user_tag_overrides u ON u.tagId = t.id WHERE COALESCE(u.appCategory, t.appCategory, t.danbooruCategory, '') != '' ORDER BY 1")
    suspend fun getUsedCategories(): List<String>
    @Query("SELECT name FROM user_tag_categories ORDER BY name") suspend fun getUserCategories(): List<String>
    @Query("SELECT * FROM tags ORDER BY useCount DESC, lastUsedAt DESC, lastSeenAt DESC, danbooruPostCount DESC")
    fun observeAll(): Flow<List<TagEntity>>
    @Query("SELECT * FROM tags WHERE canonicalTag = :canonical LIMIT 1")
    suspend fun findByCanonical(canonical: String): TagEntity?
    @Query("SELECT * FROM tags WHERE canonicalTag LIKE '%' || :query || '%' ORDER BY useCount DESC, lastUsedAt DESC, danbooruPostCount DESC, naiConfidence DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int = 50): List<TagEntity>
    @Query("SELECT * FROM tags WHERE canonicalTag LIKE :prefix || '%' AND NOT EXISTS(SELECT 1 FROM tag_exclusions x WHERE x.canonicalTag = tags.canonicalTag) ORDER BY EXISTS(SELECT 1 FROM user_tag_overrides u WHERE u.tagId = tags.id AND u.favorite = 1) DESC, useCount DESC, lastUsedAt DESC, danbooruPostCount DESC, naiConfidence DESC LIMIT :limit")
    suspend fun searchPrefix(prefix: String, limit: Int = 12): List<TagEntity>
    @Query("""
        SELECT t.id, t.canonicalTag, t.danbooruCategory,
            COALESCE(u.appCategory, t.appCategory) AS appCategory,
            t.danbooruPostCount, t.naiCount, t.naiConfidence, t.novelAiSource, t.danbooruSource, t.userCreated,
            t.useCount, t.lastUsedAt, t.lastSeenAt,
            COALESCE(u.korean, b.korean) AS korean,
            COALESCE(u.koreanAliases, b.koreanAliases) AS koreanAliases,
            (SELECT GROUP_CONCAT(a.alias, ', ') FROM tag_aliases a WHERE a.tagId = t.id) AS englishAliases,
            COALESCE(u.favorite, 0) AS favorite, u.thumbnailPath AS thumbnailPath, t.bundled
        FROM tags t
        LEFT JOIN base_translations b ON b.tagId = t.id
        LEFT JOIN user_tag_overrides u ON u.tagId = t.id
        WHERE NOT EXISTS(SELECT 1 FROM tag_exclusions x WHERE x.canonicalTag = t.canonicalTag)
          AND (t.canonicalTag LIKE :canonicalPrefix || '%' COLLATE NOCASE
           OR COALESCE(u.korean, b.korean, '') LIKE '%' || :query || '%' COLLATE NOCASE
           OR COALESCE(u.koreanAliases, b.koreanAliases, '') LIKE '%' || :query || '%' COLLATE NOCASE
           OR EXISTS(SELECT 1 FROM tag_aliases a WHERE a.tagId = t.id AND a.alias LIKE :canonicalPrefix || '%' COLLATE NOCASE))
        ORDER BY COALESCE(u.favorite, 0) DESC, t.useCount DESC, t.lastUsedAt DESC,
            t.danbooruPostCount DESC, t.naiConfidence DESC
        LIMIT :limit
    """)
    suspend fun searchAutocomplete(query: String, canonicalPrefix: String, limit: Int = 12): List<TagDictionaryItem>
    @Query("SELECT canonicalTag FROM tag_exclusions WHERE canonicalTag IN (:canonicalTags)")
    suspend fun findExcludedCanonicals(canonicalTags: List<String>): List<String>
    @Query("SELECT * FROM tag_exclusions WHERE canonicalTag = :canonicalTag LIMIT 1")
    suspend fun findTagExclusion(canonicalTag: String): TagExclusionEntity?
    @Upsert suspend fun upsertTagExclusion(entity: TagExclusionEntity)
    @Query("DELETE FROM tag_exclusions WHERE canonicalTag = :canonicalTag")
    suspend fun deleteTagExclusion(canonicalTag: String)
    @Query("SELECT * FROM tag_exclusions WHERE (:origin = '' OR origin = :origin) ORDER BY updatedAt DESC, canonicalTag COLLATE NOCASE")
    fun observeTagExclusions(origin: String): Flow<List<TagExclusionEntity>>
    @Query("UPDATE tags SET useCount = useCount + 1, lastUsedAt = :usedAt WHERE canonicalTag = :canonical")
    suspend fun recordUse(canonical: String, usedAt: Long)
    @Upsert suspend fun upsertTag(entity: TagEntity)
    @Upsert suspend fun upsertBaseTranslation(entity: BaseTranslationEntity)
    @Upsert suspend fun upsertUserOverride(entity: UserTagOverrideEntity)
    @Query("DELETE FROM user_tag_overrides WHERE tagId = :tagId") suspend fun clearUserOverride(tagId: String)
    @Query("SELECT COUNT(*) FROM tags") fun observeCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM tags t LEFT JOIN base_translations b ON b.tagId = t.id LEFT JOIN user_tag_overrides u ON u.tagId = t.id WHERE COALESCE(u.korean, b.korean, '') = '' AND COALESCE(u.translationDeferred, 0) = 0")
    fun observeMissingTranslationCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM tags t LEFT JOIN user_tag_overrides u ON u.tagId = t.id WHERE COALESCE(u.appCategory, t.appCategory, t.danbooruCategory, '') = '' AND COALESCE(u.translationDeferred, 0) = 0")
    fun observeMissingCategoryCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM user_tag_overrides WHERE translationDeferred = 1")
    fun observeDeferredTranslationCount(): Flow<Int>
    @Query("""
        SELECT COUNT(*) FROM tags t
        LEFT JOIN base_translations b ON b.tagId = t.id
        LEFT JOIN user_tag_overrides u ON u.tagId = t.id
        WHERE NOT EXISTS(SELECT 1 FROM tag_exclusions x WHERE x.canonicalTag = t.canonicalTag)
          AND (:query = '' OR t.canonicalTag LIKE '%' || :canonicalQuery || '%' COLLATE NOCASE
            OR COALESCE(u.korean, b.korean, '') LIKE '%' || :query || '%' COLLATE NOCASE
            OR COALESCE(u.koreanAliases, b.koreanAliases, '') LIKE '%' || :query || '%' COLLATE NOCASE
            OR EXISTS(SELECT 1 FROM tag_aliases a WHERE a.tagId = t.id AND a.alias LIKE '%' || :query || '%' COLLATE NOCASE))
          AND (:filter != 'FAVORITES' OR COALESCE(u.favorite, 0) = 1)
          AND (:category = '' OR COALESCE(u.appCategory, t.appCategory, t.danbooruCategory, '') = :category)
    """)
    fun observeDictionaryCount(query: String, canonicalQuery: String, filter: String, category: String): Flow<Int>
    @Query("""
        SELECT t.id, t.canonicalTag, t.danbooruCategory,
            COALESCE(u.appCategory, t.appCategory) AS appCategory,
            t.danbooruPostCount, t.naiCount, t.naiConfidence, t.novelAiSource, t.danbooruSource, t.userCreated,
            t.useCount, t.lastUsedAt, t.lastSeenAt,
            COALESCE(u.korean, b.korean) AS korean,
            COALESCE(u.koreanAliases, b.koreanAliases) AS koreanAliases,
            (SELECT GROUP_CONCAT(a.alias, ', ') FROM tag_aliases a WHERE a.tagId = t.id) AS englishAliases,
            COALESCE(u.favorite, 0) AS favorite, u.thumbnailPath AS thumbnailPath, t.bundled
        FROM tags t
        LEFT JOIN base_translations b ON b.tagId = t.id
        LEFT JOIN user_tag_overrides u ON u.tagId = t.id
        WHERE NOT EXISTS(SELECT 1 FROM tag_exclusions x WHERE x.canonicalTag = t.canonicalTag)
          AND (:query = '' OR t.canonicalTag LIKE '%' || :canonicalQuery || '%' COLLATE NOCASE
            OR COALESCE(u.korean, b.korean, '') LIKE '%' || :query || '%' COLLATE NOCASE
            OR COALESCE(u.koreanAliases, b.koreanAliases, '') LIKE '%' || :query || '%' COLLATE NOCASE
            OR EXISTS(SELECT 1 FROM tag_aliases a WHERE a.tagId = t.id AND a.alias LIKE '%' || :query || '%' COLLATE NOCASE))
          AND (:filter != 'FAVORITES' OR COALESCE(u.favorite, 0) = 1)
          AND (:category = '' OR COALESCE(u.appCategory, t.appCategory, t.danbooruCategory, '') = :category)
        ORDER BY COALESCE(u.favorite, 0) DESC,
          CASE WHEN :sort = 'POPULAR' THEN COALESCE(t.danbooruPostCount, 0) END DESC,
          CASE WHEN :sort = 'APP_USAGE' THEN t.useCount END DESC,
          CASE WHEN :sort = 'RECENT' THEN COALESCE(t.lastUsedAt, t.lastSeenAt, 0) END DESC,
          CASE WHEN :sort = 'NAME' THEN t.canonicalTag END COLLATE NOCASE ASC,
          t.useCount DESC, t.danbooruPostCount DESC
        LIMIT :limit
    """)
    fun observeDictionary(query: String, canonicalQuery: String, filter: String, category: String, sort: String, limit: Int = 200): Flow<List<TagDictionaryItem>>
    @Query("SELECT * FROM user_tag_overrides WHERE tagId = :tagId LIMIT 1") suspend fun findOverride(tagId: String): UserTagOverrideEntity?
    @Query("""
        INSERT INTO user_tag_overrides (id, tagId, korean, koreanAliases, appCategory, favorite, thumbnailPath, updatedAt)
        VALUES (:id, :tagId, NULL, NULL, NULL, :favorite, NULL, :updatedAt)
        ON CONFLICT(tagId) DO UPDATE SET favorite=:favorite, updatedAt=:updatedAt
    """)
    suspend fun setFavorite(id: String, tagId: String, favorite: Boolean, updatedAt: Long)

    @Query("""
        SELECT t.id, t.canonicalTag, t.danbooruCategory,
            COALESCE(u.appCategory, t.appCategory) AS appCategory,
            t.danbooruPostCount, t.naiCount, t.naiConfidence, t.novelAiSource, t.danbooruSource, t.userCreated,
            t.useCount, t.lastUsedAt, t.lastSeenAt,
            COALESCE(u.korean, b.korean) AS korean,
            COALESCE(u.koreanAliases, b.koreanAliases) AS koreanAliases,
            (SELECT GROUP_CONCAT(a.alias, ', ') FROM tag_aliases a WHERE a.tagId = t.id) AS englishAliases,
            COALESCE(u.favorite, 0) AS favorite, u.thumbnailPath AS thumbnailPath, t.bundled
        FROM tags t
        LEFT JOIN base_translations b ON b.tagId = t.id
        LEFT JOIN user_tag_overrides u ON u.tagId = t.id
        WHERE (COALESCE(u.translationDeferred, 0) = 0 AND (
            (:missingTranslation = 1 AND COALESCE(u.korean, b.korean, '') = '')
            OR (:missingCategory = 1 AND COALESCE(u.appCategory, t.appCategory, t.danbooruCategory, '') = '')
        )) OR (:includeDeferred = 1 AND u.translationDeferred = 1)
        ORDER BY t.useCount DESC, t.danbooruPostCount DESC, t.canonicalTag ASC
        LIMIT :limit
    """)
    suspend fun translationCandidates(limit: Int, missingTranslation: Boolean, missingCategory: Boolean, includeDeferred: Boolean = false): List<TagDictionaryItem>

    @Query("DELETE FROM tags WHERE id = :tagId AND userCreated = 1 AND novelAiSource = 0 AND danbooruSource = 0 AND bundled = 0")
    suspend fun deleteUserOnlyTag(tagId: String): Int
    @Query("SELECT EXISTS(SELECT 1 FROM tags WHERE id = :tagId AND bundled = 0 AND userCreated = 0 AND useCount = 0 AND lastUsedAt IS NULL AND NOT EXISTS(SELECT 1 FROM user_tag_overrides WHERE tagId = :tagId AND (korean IS NOT NULL OR koreanAliases IS NOT NULL OR appCategory IS NOT NULL OR favorite = 1 OR thumbnailPath IS NOT NULL)) AND NOT EXISTS(SELECT 1 FROM base_translations WHERE tagId = :tagId))")
    suspend fun canDeleteTranslationTypo(tagId: String): Boolean

    @Query("DELETE FROM tags WHERE id = :tagId AND bundled = 0 AND userCreated = 0 AND useCount = 0 AND lastUsedAt IS NULL AND NOT EXISTS(SELECT 1 FROM user_tag_overrides WHERE tagId = :tagId AND (korean IS NOT NULL OR koreanAliases IS NOT NULL OR appCategory IS NOT NULL OR favorite = 1 OR thumbnailPath IS NOT NULL)) AND NOT EXISTS(SELECT 1 FROM base_translations WHERE tagId = :tagId)")
    suspend fun deleteTranslationTypo(tagId: String): Int
    @Query("DELETE FROM user_tag_overrides") suspend fun clearAllUserTagOverrides()
    @Query("DELETE FROM user_tag_categories") suspend fun clearAllUserTagCategories()
    @Query("DELETE FROM tag_exclusions") suspend fun clearAllTagExclusions()
    @Query("DELETE FROM tags WHERE bundled = 0") suspend fun deleteNonBundledTags()
    @Query("UPDATE tags SET novelAiSource = 0, danbooruSource = 0, userCreated = 0, naiCount = NULL, naiConfidence = NULL, useCount = 0, lastUsedAt = NULL WHERE bundled = 1")
    suspend fun resetBundledTagMetadata()

    @Transaction
    suspend fun resetToBundledTags() {
        clearAllUserTagOverrides()
        clearAllUserTagCategories()
        clearAllTagExclusions()
        deleteNonBundledTags()
        resetBundledTagMetadata()
    }

    @Transaction
    suspend fun applyTranslationImport(overrides: List<UserTagOverrideEntity>, categories: List<UserTagCategoryEntity>, deleteIds: Set<String> = emptySet()) {
        deleteIds.forEach { deleteTranslationTypo(it) }
        categories.forEach { upsertUserCategory(it) }
        overrides.forEach { upsertUserOverride(it) }
    }
}
