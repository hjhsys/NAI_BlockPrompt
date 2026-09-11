package com.hjhsys.naiblockprompt

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hjhsys.naiblockprompt.data.local.AppDatabase
import com.hjhsys.naiblockprompt.data.session.SessionRepository
import com.hjhsys.naiblockprompt.data.settings.SettingsRepository
import com.hjhsys.naiblockprompt.data.generation.GenerationRepository
import com.hjhsys.naiblockprompt.data.library.LibraryRepository
import com.hjhsys.naiblockprompt.data.network.nai.OkHttpNaiImageApi
import com.hjhsys.naiblockprompt.data.security.KeystoreTokenStore
import com.hjhsys.naiblockprompt.data.autocomplete.AutocompleteRepository
import com.hjhsys.naiblockprompt.data.autocomplete.OkHttpAutocompleteApi
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import com.hjhsys.naiblockprompt.data.tags.BundledTagImporter
import com.hjhsys.naiblockprompt.data.backup.BackupRepository
import com.hjhsys.naiblockprompt.data.diagnostics.CrashLogStore

class NaiBlockPromptApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLogStore(this).install()
        val database = Room.databaseBuilder(this, AppDatabase::class.java, "nai_block_prompt.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
            .build()
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
        val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .build()
        val api = OkHttpNaiImageApi(client, json)
        val settingsRepository = SettingsRepository(this)
        container = AppContainer(
            sessionRepository = SessionRepository(database.sessionDao(), json),
            settingsRepository = settingsRepository,
            tokenStore = KeystoreTokenStore(this),
            generationRepository = GenerationRepository(this, api, database.historyDao(), json, settingsRepository),
            libraryRepository = LibraryRepository(this, database.savedDao(), database.historyDao(), json),
            autocompleteRepository = AutocompleteRepository(this@NaiBlockPromptApplication, OkHttpAutocompleteApi(client, json), database.tagDao()),
            backupRepository = BackupRepository(this@NaiBlockPromptApplication, database, settingsRepository, json),
        )
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            BundledTagImporter(this@NaiBlockPromptApplication, database, settingsRepository).importIfNeeded()
        }
    }
}

private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS tag_exclusions (canonicalTag TEXT NOT NULL PRIMARY KEY, origin TEXT NOT NULL, reasonCode TEXT NOT NULL, reasonText TEXT, userConfirmed INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tag_exclusions_origin ON tag_exclusions(origin)")
    }
}

private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE tags SET danbooruCategory = CASE danbooruCategory WHEN '0' THEN 'general' WHEN '1' THEN 'artist' WHEN '3' THEN 'copyright' WHEN '4' THEN 'character' WHEN '5' THEN 'meta' ELSE danbooruCategory END WHERE danbooruCategory IN ('0', '1', '3', '4', '5')")
    }
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_tag_overrides ADD COLUMN translationDeferred INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE presets_new (id TEXT NOT NULL PRIMARY KEY, folderId TEXT, name TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, snapshotVersion INTEGER NOT NULL, snapshotJson TEXT NOT NULL, FOREIGN KEY(folderId) REFERENCES saved_folders(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
        db.execSQL("INSERT INTO presets_new (id, folderId, name, createdAt, updatedAt, snapshotVersion, snapshotJson) SELECT id, NULL, name, createdAt, updatedAt, snapshotVersion, snapshotJson FROM presets")
        db.execSQL("DROP TABLE presets")
        db.execSQL("ALTER TABLE presets_new RENAME TO presets")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_presets_name ON presets(name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_presets_folderId ON presets(folderId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS saved_sets (id TEXT NOT NULL PRIMARY KEY, folderId TEXT, name TEXT NOT NULL, kind TEXT NOT NULL, snapshotVersion INTEGER NOT NULL, snapshotJson TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(folderId) REFERENCES saved_folders(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_saved_sets_name ON saved_sets(name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_saved_sets_folderId ON saved_sets(folderId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_saved_sets_kind ON saved_sets(kind)")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE history_entries ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tags ADD COLUMN danbooruPostCount INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE tags ADD COLUMN naiCount REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE tags ADD COLUMN naiConfidence REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE tags ADD COLUMN useCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tags ADD COLUMN lastUsedAt INTEGER DEFAULT NULL")
        db.execSQL("UPDATE tags SET danbooruPostCount = postCount WHERE danbooruSource = 1 AND novelAiSource = 0")
        db.execSQL("UPDATE tags SET naiCount = CAST(postCount AS REAL) WHERE novelAiSource = 1 AND danbooruSource = 0")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS tag_aliases (id TEXT NOT NULL PRIMARY KEY, tagId TEXT NOT NULL, alias TEXT NOT NULL, source TEXT NOT NULL, FOREIGN KEY(tagId) REFERENCES tags(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tag_aliases_tagId ON tag_aliases(tagId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tag_aliases_alias ON tag_aliases(alias)")
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS user_tag_categories (name TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(name))")
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE base_translations ADD COLUMN suggestedCategory TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE base_translations ADD COLUMN needsReview INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Some development builds created this column before advancing Room's
        // schema version. Keep those installations upgradeable without data loss.
        if (!db.hasColumn("tags", "bundled")) {
            db.execSQL("ALTER TABLE tags ADD COLUMN bundled INTEGER NOT NULL DEFAULT 0")
        }
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS wildcards (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, valuesText TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_wildcards_name ON wildcards(name)")
    }
}

private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!db.hasColumn("wildcards", "folder")) {
            db.execSQL("ALTER TABLE wildcards ADD COLUMN folder TEXT DEFAULT NULL")
        }
    }
}

private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean =
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
            .any { it == column }
    }

data class AppContainer(
    val sessionRepository: SessionRepository,
    val settingsRepository: SettingsRepository,
    val tokenStore: KeystoreTokenStore,
    val generationRepository: GenerationRepository,
    val libraryRepository: LibraryRepository,
    val autocompleteRepository: AutocompleteRepository,
    val backupRepository: BackupRepository,
)
