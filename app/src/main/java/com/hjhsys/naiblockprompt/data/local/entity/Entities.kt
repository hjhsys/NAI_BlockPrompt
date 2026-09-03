package com.hjhsys.naiblockprompt.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "current_session")
data class CurrentSessionEntity(
    @PrimaryKey val id: String = "current",
    val snapshotVersion: Int,
    val snapshotJson: String,
    val updatedAt: Long,
)

@Entity(tableName = "stash")
data class StashEntity(
    @PrimaryKey val slot: Int = 0,
    val snapshotVersion: Int,
    val snapshotJson: String,
    val updatedAt: Long,
)

@Entity(tableName = "saved_folders", indices = [Index(value = ["name"], unique = true)])
data class SavedFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val orderIndex: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "saved_blocks",
    foreignKeys = [ForeignKey(
        entity = SavedFolderEntity::class,
        parentColumns = ["id"],
        childColumns = ["folderId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("folderId"), Index("name")],
)
data class SavedBlockEntity(
    @PrimaryKey val id: String,
    val folderId: String?,
    val name: String,
    val content: String,
    val enabled: Boolean,
    val locked: Boolean,
    val collapsed: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "presets", foreignKeys = [ForeignKey(entity = SavedFolderEntity::class, parentColumns = ["id"], childColumns = ["folderId"], onDelete = ForeignKey.SET_NULL)], indices = [Index("name"), Index("folderId")])
data class PresetEntity(
    @PrimaryKey val id: String,
    val folderId: String?,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val snapshotVersion: Int,
    val snapshotJson: String,
)

@Entity(tableName = "saved_sets", foreignKeys = [ForeignKey(entity = SavedFolderEntity::class, parentColumns = ["id"], childColumns = ["folderId"], onDelete = ForeignKey.SET_NULL)], indices = [Index("name"), Index("folderId"), Index("kind")])
data class SavedSetEntity(
    @PrimaryKey val id: String,
    val folderId: String?,
    val name: String,
    val kind: String,
    val snapshotVersion: Int,
    val snapshotJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "history_entries", indices = [Index("createdAt"), Index("model")])
data class HistoryEntryEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val imagePath: String,
    val thumbnailPath: String,
    val model: String?,
    val snapshotVersion: Int,
    val snapshotJson: String,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
)

@Entity(tableName = "tags", indices = [Index("canonicalTag", unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    val canonicalTag: String,
    val danbooruCategory: String?,
    val appCategory: String?,
    @ColumnInfo(name = "postCount") val legacyPostCount: Long?,
    @ColumnInfo(defaultValue = "NULL") val danbooruPostCount: Long?,
    @ColumnInfo(defaultValue = "NULL") val naiCount: Double?,
    @ColumnInfo(defaultValue = "NULL") val naiConfidence: Double?,
    val novelAiSource: Boolean,
    val danbooruSource: Boolean,
    val userCreated: Boolean,
    val lastSeenAt: Long?,
    @ColumnInfo(defaultValue = "0") val useCount: Int = 0,
    @ColumnInfo(defaultValue = "NULL") val lastUsedAt: Long? = null,
)

@Entity(
    tableName = "tag_aliases",
    foreignKeys = [ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tagId"), Index("alias")],
)
data class TagAliasEntity(
    @PrimaryKey val id: String,
    val tagId: String,
    val alias: String,
    val source: String,
)

@Entity(
    tableName = "base_translations",
    foreignKeys = [ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tagId", unique = true)],
)
data class BaseTranslationEntity(
    @PrimaryKey val id: String,
    val tagId: String,
    val korean: String,
    val koreanAliases: String,
    val source: String,
    val updatedAt: Long,
)

@Entity(
    tableName = "user_tag_overrides",
    foreignKeys = [ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tagId", unique = true)],
)
data class UserTagOverrideEntity(
    @PrimaryKey val id: String,
    val tagId: String,
    val korean: String?,
    val koreanAliases: String?,
    val appCategory: String?,
    val favorite: Boolean,
    val thumbnailPath: String?,
    val updatedAt: Long,
)

@Entity(tableName = "user_tag_categories")
data class UserTagCategoryEntity(
    @PrimaryKey val name: String,
    val createdAt: Long,
)
