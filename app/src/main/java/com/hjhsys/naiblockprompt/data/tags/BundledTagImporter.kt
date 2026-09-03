package com.hjhsys.naiblockprompt.data.tags

import android.content.Context
import androidx.sqlite.db.SupportSQLiteStatement
import com.hjhsys.naiblockprompt.data.local.AppDatabase
import com.hjhsys.naiblockprompt.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class BundledTagImporter(
    private val context: Context,
    private val database: AppDatabase,
    private val settings: SettingsRepository,
) {
    suspend fun importIfNeeded() = withContext(Dispatchers.IO) {
        if (settings.bundledTagVersion() >= BUNDLED_VERSION) return@withContext
        val db = database.openHelper.writableDatabase
        val tagStatement = db.compileStatement(TAG_SQL)
        val aliasStatement = db.compileStatement(ALIAS_SQL)
        var batchCount = 0
        db.beginTransaction()
        try {
            context.assets.open(ASSET).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val fields = parseCsvLine(line)
                    if (fields.size < 3) return@forEach
                    val canonical = fields[0].trim()
                    val category = categoryName(fields[1].toIntOrNull())
                    val postCount = fields[2].toLongOrNull()
                    if (canonical.isBlank() || postCount == null) return@forEach
                    val tagId = stableId(canonical)
                    bindTag(tagStatement, tagId, canonical, category, postCount)
                    tagStatement.executeInsert()
                    fields.getOrNull(3).orEmpty().split(',').asSequence()
                        .map(String::trim).filter { it.isNotBlank() && it != canonical }.forEach { alias ->
                            aliasStatement.clearBindings()
                            aliasStatement.bindString(1, stableId("$canonical\u0000$alias"))
                            aliasStatement.bindString(2, tagId)
                            aliasStatement.bindString(3, alias)
                            aliasStatement.bindString(4, SOURCE)
                            aliasStatement.executeInsert()
                        }
                    batchCount++
                    if (batchCount >= BATCH_SIZE) {
                        db.setTransactionSuccessful()
                        db.endTransaction()
                        db.beginTransaction()
                        batchCount = 0
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            if (db.inTransaction()) db.endTransaction()
        }
        settings.setBundledTagVersion(BUNDLED_VERSION)
    }

    private fun bindTag(statement: SupportSQLiteStatement, id: String, canonical: String, category: String?, posts: Long) {
        statement.clearBindings()
        statement.bindString(1, id)
        statement.bindString(2, canonical)
        if (category == null) statement.bindNull(3) else statement.bindString(3, category)
        statement.bindLong(4, posts)
        statement.bindLong(5, System.currentTimeMillis())
    }

    companion object {
        const val BUNDLED_VERSION = 20260401
        const val ASSET = "danbooru_tags_pt20.csv"
        private const val SOURCE = "DraconicDragon/dbr-e621-lists-archive@2026-04-01"
        private const val BATCH_SIZE = 2_000
        private const val TAG_SQL = """
            INSERT INTO tags (id, canonicalTag, danbooruCategory, appCategory, postCount,
                danbooruPostCount, naiCount, naiConfidence, novelAiSource, danbooruSource,
                userCreated, lastSeenAt, useCount, lastUsedAt)
            VALUES (?, ?, ?, NULL, NULL, ?, NULL, NULL, 0, 1, 0, ?, 0, NULL)
            ON CONFLICT(canonicalTag) DO UPDATE SET
                danbooruCategory=excluded.danbooruCategory,
                danbooruPostCount=excluded.danbooruPostCount,
                danbooruSource=1,
                lastSeenAt=excluded.lastSeenAt
        """
        private const val ALIAS_SQL = "INSERT OR IGNORE INTO tag_aliases (id, tagId, alias, source) VALUES (?, ?, ?, ?)"

        fun stableId(value: String): String = UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()

        fun categoryName(value: Int?): String? = when (value) {
            0 -> "general"
            1 -> "artist"
            3 -> "copyright"
            4 -> "character"
            5 -> "meta"
            else -> null
        }

        fun parseCsvLine(line: String): List<String> {
            val fields = mutableListOf<String>()
            val current = StringBuilder()
            var quoted = false
            var index = 0
            while (index < line.length) {
                val char = line[index]
                when {
                    char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                        current.append('"'); index++
                    }
                    char == '"' -> quoted = !quoted
                    char == ',' && !quoted -> { fields += current.toString(); current.clear() }
                    else -> current.append(char)
                }
                index++
            }
            fields += current.toString()
            return fields
        }
    }
}
