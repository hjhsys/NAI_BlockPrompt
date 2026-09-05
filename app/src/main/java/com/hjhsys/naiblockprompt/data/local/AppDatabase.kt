package com.hjhsys.naiblockprompt.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.hjhsys.naiblockprompt.data.local.dao.*
import com.hjhsys.naiblockprompt.data.local.entity.*

@Database(
    entities = [
        CurrentSessionEntity::class, StashEntity::class, SavedFolderEntity::class,
        SavedBlockEntity::class, PresetEntity::class, SavedSetEntity::class, HistoryEntryEntity::class,
        TagEntity::class, TagAliasEntity::class, BaseTranslationEntity::class, UserTagOverrideEntity::class, UserTagCategoryEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun savedDao(): SavedDao
    abstract fun historyDao(): HistoryDao
    abstract fun tagDao(): TagDao
}
