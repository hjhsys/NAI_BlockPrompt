package com.hjhsys.naiblockprompt.data.backup

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import com.hjhsys.naiblockprompt.data.local.AppDatabase
import com.hjhsys.naiblockprompt.data.local.entity.*
import com.hjhsys.naiblockprompt.data.settings.SettingsRepository
import com.hjhsys.naiblockprompt.domain.model.AppSettings
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PortableTagData(
    val version: Int = 2,
    val tags: List<TagEntity>,
    val aliases: List<TagAliasEntity>,
    val overrides: List<UserTagOverrideEntity>,
    val categories: List<UserTagCategoryEntity>,
    val exclusions: List<TagExclusionEntity> = emptyList(),
)

@Serializable
data class AppBackupData(
    val version: Int = 3,
    val settings: AppSettings,
    val currentSession: CurrentSessionEntity?,
    val stash: StashEntity?,
    val folders: List<SavedFolderEntity>,
    val blocks: List<SavedBlockEntity>,
    val presets: List<PresetEntity>,
    val sets: List<SavedSetEntity>,
    val history: List<HistoryEntryEntity>,
    val tagData: PortableTagData,
    // Read compatibility for v1/v2 backups, which embedded bytes in backup.json.
    val historyThumbnails: Map<String, ByteArray> = emptyMap(),
    val tagThumbnails: Map<String, ByteArray> = emptyMap(),
    val wildcards: List<WildcardEntity> = emptyList(),
    val mediaManifest: BackupMediaManifest = BackupMediaManifest(),
)

@Serializable
data class BackupMediaManifest(
    val historyThumbnails: Map<String, String> = emptyMap(),
    val tagThumbnails: Map<String, String> = emptyMap(),
)

private data class BackupExportSnapshot(
    val payload: AppBackupData,
    val history: List<HistoryEntryEntity>,
    val tagOverrides: List<UserTagOverrideEntity>,
)

class BackupRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val settings: SettingsRepository,
    private val json: Json,
) {
    private val tagDao = database.tagDao()

    suspend fun exportTagData(): ByteArray {
        val source = portableTags()
        val data = source.copy(overrides = source.overrides.map { it.copy(thumbnailPath = null) })
        return BackupArchiveCodec.encode(json, "tag-data.json", PortableTagData.serializer(), data)
    }

    suspend fun importTagData(bytes: ByteArray) {
        val payload = BackupArchiveCodec.decodeMetadata(bytes, "tag-data.json", json, PortableTagData.serializer())
        require(payload.version in 1..2) { "Unsupported tag backup version" }
        database.withTransaction { importPortable(payload) }
    }

    suspend fun exportAppBackup(): ByteArray = withContext(Dispatchers.IO) {
        val currentSettings = settings.current()
        val snapshot = database.withTransaction {
            val sessionDao = database.sessionDao()
            val savedDao = database.savedDao()
            val historyDao = database.historyDao()
            val history = historyDao.listAll()
            val tagData = portableTags()
            BackupExportSnapshot(
                payload = AppBackupData(
                    settings = currentSettings,
                    currentSession = sessionDao.getCurrent(),
                    stash = sessionDao.getStash(),
                    folders = savedDao.listFolders(), blocks = savedDao.listBlocks(),
                    presets = savedDao.listPresets(), sets = savedDao.listSets(),
                    history = history.map { it.copy(imagePath = "", thumbnailPath = "") },
                    tagData = tagData.copy(overrides = tagData.overrides.map { it.copy(thumbnailPath = null) }),
                    wildcards = tagDao.listWildcards(),
                ),
                history = history,
                tagOverrides = tagData.overrides,
            )
        }
        val sources = mutableListOf<BackupArchiveSource>()
        val historyManifest = linkedMapOf<String, String>()
        snapshot.history.forEach { row ->
            val source = File(row.thumbnailPath).takeIf { it.isFile && it.length() > 0 } ?: return@forEach
            val entry = "media/history/${historyManifest.size}.img"
            historyManifest[row.id] = entry
            sources += BackupArchiveSource(entry, source)
        }
        val tagManifest = linkedMapOf<String, String>()
        snapshot.tagOverrides.forEach { row ->
            val source = row.thumbnailPath?.let(::File)?.takeIf { it.isFile && it.length() > 0 } ?: return@forEach
            val entry = "media/tags/${tagManifest.size}.img"
            tagManifest[row.tagId] = entry
            sources += BackupArchiveSource(entry, source)
        }
        val payload = snapshot.payload.copy(mediaManifest = BackupMediaManifest(historyManifest, tagManifest))
        BackupArchiveCodec.encode(json, "backup.json", AppBackupData.serializer(), payload, sources)
    }

    suspend fun importAppBackup(bytes: ByteArray) {
        val payload = BackupArchiveCodec.decodeMetadata(bytes, "backup.json", json, AppBackupData.serializer())
        require(payload.version in 1..3 && payload.tagData.version in 1..2) { "Unsupported backup version" }
        withContext(Dispatchers.IO) {
            val historyDirectory = File(context.filesDir, "backup_history_thumbnails")
            val tagDirectory = File(context.filesDir, "tag_thumbnails")
            val restoredMedia = BackupArchiveCodec.extractMedia(bytes, payload.mediaManifest, historyDirectory, tagDirectory)
            try {
                database.withTransaction {
                    val sessionDao = database.sessionDao()
                    val savedDao = database.savedDao()
                    val historyDao = database.historyDao()
                    val existingFolders = savedDao.listFolders()
                    val folderIds = payload.folders.associate { incoming ->
                        incoming.id to (existingFolders.firstOrNull { it.name == incoming.name }?.id ?: incoming.id)
                    }
                    payload.currentSession?.let { sessionDao.upsert(it) }
                    payload.stash?.let { sessionDao.upsertStash(it) }
                    savedDao.upsertFolders(payload.folders.map { it.copy(id = folderIds.getValue(it.id)) })
                    savedDao.upsertBlocks(payload.blocks.map { it.copy(folderId = folderIds[it.folderId] ?: it.folderId) })
                    savedDao.upsertPresets(payload.presets.map { it.copy(folderId = folderIds[it.folderId] ?: it.folderId) })
                    savedDao.upsertSets(payload.sets.map { it.copy(folderId = folderIds[it.folderId] ?: it.folderId) })
                    val existingHistory = historyDao.listAll().associateBy { it.id }
                    historyDirectory.mkdirs()
                    val restoredHistory = payload.history.map { row ->
                        val original = existingHistory[row.id]?.imagePath.orEmpty()
                        restoredMedia.historyThumbnails[row.id]?.let { path ->
                            row.copy(imagePath = original, thumbnailPath = path)
                        } ?: payload.historyThumbnails[row.id]?.let { legacyBytes ->
                            val file = File(historyDirectory, "${UUID.randomUUID()}.img")
                            file.writeBytes(legacyBytes)
                            row.copy(imagePath = original, thumbnailPath = file.absolutePath)
                        } ?: row.copy(imagePath = original, thumbnailPath = existingHistory[row.id]?.thumbnailPath.orEmpty())
                    }
                    historyDao.upsertAll(restoredHistory)
                    tagDirectory.mkdirs()
                    val restoredTagData = payload.tagData.copy(overrides = payload.tagData.overrides.map { row ->
                        restoredMedia.tagThumbnails[row.tagId]?.let { path ->
                            row.copy(thumbnailPath = path)
                        } ?: payload.tagThumbnails[row.tagId]?.let { legacyBytes ->
                            val file = File(tagDirectory, "${UUID.randomUUID()}.img")
                            file.writeBytes(legacyBytes)
                            row.copy(thumbnailPath = file.absolutePath)
                        } ?: row.copy(thumbnailPath = null)
                    })
                    importPortable(restoredTagData)
                    payload.wildcards.forEach { row ->
                        val existing = tagDao.findWildcardByName(row.name)
                        tagDao.upsertWildcard(row.copy(id = existing?.id ?: row.id))
                    }
                }
            } catch (error: Throwable) {
                restoredMedia.createdFiles.forEach(File::delete)
                throw error
            }
        }
        // A persisted SAF tree permission is device-specific and cannot be transferred safely.
        settings.apply(payload.settings.copy(imageSaveTreeUri = null))
    }

    private suspend fun portableTags() = PortableTagData(
        tags = tagDao.listPortableTags(), aliases = tagDao.listPortableAliases(),
        overrides = tagDao.listUserOverrides(), categories = tagDao.listUserCategoryEntities(),
        exclusions = tagDao.listTagExclusions(),
    )

    private suspend fun importPortable(payload: PortableTagData) {
        val ids = mutableMapOf<String, String>()
        payload.tags.forEach { incoming ->
            val old = tagDao.findByCanonical(incoming.canonicalTag)
            val merged = old?.copy(
                novelAiSource = old.novelAiSource || incoming.novelAiSource,
                danbooruSource = old.danbooruSource || incoming.danbooruSource,
                userCreated = old.userCreated || incoming.userCreated,
                useCount = maxOf(old.useCount, incoming.useCount),
                lastUsedAt = listOfNotNull(old.lastUsedAt, incoming.lastUsedAt).maxOrNull(),
                lastSeenAt = listOfNotNull(old.lastSeenAt, incoming.lastSeenAt).maxOrNull(),
            ) ?: incoming.copy(bundled = false)
            ids[incoming.id] = merged.id
            tagDao.upsertTag(merged.copy(danbooruCategory = com.hjhsys.naiblockprompt.domain.tags.DanbooruCategory.normalize(merged.danbooruCategory)))
        }
        payload.aliases.forEach { tagDao.upsertAliasForBackup(it.copy(tagId = ids[it.tagId] ?: it.tagId)) }
        payload.categories.forEach { tagDao.upsertUserCategory(it) }
        tagDao.upsertTagExclusions(payload.exclusions)
        payload.overrides.forEach { override ->
            if (tagDao.findByCanonical(payload.tags.firstOrNull { it.id == override.tagId }?.canonicalTag.orEmpty()) != null ||
                database.openHelper.readableDatabase.query("SELECT id FROM tags WHERE id = ?", arrayOf(override.tagId)).use { it.moveToFirst() }) {
                val target = ids[override.tagId] ?: override.tagId
                val old = tagDao.findOverride(target)
                tagDao.upsertUserOverride(override.copy(id = old?.id ?: override.id, tagId = target, thumbnailPath = override.thumbnailPath ?: old?.thumbnailPath))
            }
        }
    }

}
