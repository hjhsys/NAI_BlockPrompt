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
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class NaiBlockPromptApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(this, AppDatabase::class.java, "nai_block_prompt.db")
            .addMigrations(MIGRATION_1_2)
            .build()
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
        val api = OkHttpNaiImageApi(
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .build(),
            json,
        )
        container = AppContainer(
            sessionRepository = SessionRepository(database.sessionDao(), json),
            settingsRepository = SettingsRepository(this),
            tokenStore = KeystoreTokenStore(this),
            generationRepository = GenerationRepository(this, api, database.historyDao(), json),
            libraryRepository = LibraryRepository(database.savedDao(), database.historyDao(), json),
        )
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

data class AppContainer(
    val sessionRepository: SessionRepository,
    val settingsRepository: SettingsRepository,
    val tokenStore: KeystoreTokenStore,
    val generationRepository: GenerationRepository,
    val libraryRepository: LibraryRepository,
)
