package com.hjhsys.naiblockprompt.data.tags

import android.content.Context
import androidx.sqlite.db.SupportSQLiteStatement
import com.hjhsys.naiblockprompt.data.local.AppDatabase
import com.hjhsys.naiblockprompt.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.zip.GZIPInputStream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class BundledTagImporter(
    private val context: Context,
    private val database: AppDatabase,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

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
        importTranslations(db)
        settings.setBundledTagVersion(BUNDLED_VERSION)
    }

    private fun importTranslations(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        val baseTagStatement = db.compileStatement(BASE_TAG_SQL)
        val translationStatement = db.compileStatement(TRANSLATION_SQL)
        var batchCount = 0
        db.beginTransaction()
        try {
            GZIPInputStream(context.assets.open(TRANSLATION_ASSET)).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val row = json.decodeFromString<BundledTranslationRow>(line)
                    val tagId = stableId(row.tag)
                    baseTagStatement.clearBindings()
                    baseTagStatement.bindString(1, tagId)
                    baseTagStatement.bindString(2, row.tag)
                    row.sourceCategory?.let { baseTagStatement.bindString(3, it) } ?: baseTagStatement.bindNull(3)
                    row.appCategory.takeIf(String::isNotBlank)?.let { baseTagStatement.bindString(4, it) } ?: baseTagStatement.bindNull(4)
                    row.postCount?.let { baseTagStatement.bindLong(5, it) } ?: baseTagStatement.bindNull(5)
                    baseTagStatement.bindLong(6, System.currentTimeMillis())
                    baseTagStatement.executeInsert()

                    translationStatement.clearBindings()
                    translationStatement.bindString(1, stableId("base-translation:${row.tag}"))
                    translationStatement.bindString(2, tagId)
                    translationStatement.bindString(3, row.korean)
                    translationStatement.bindString(4, row.koreanAliases.joinToString(", "))
                    translationStatement.bindString(5, TRANSLATION_SOURCE)
                    translationStatement.bindLong(6, System.currentTimeMillis())
                    row.suggestedCategory.takeIf(String::isNotBlank)?.let { translationStatement.bindString(7, it) } ?: translationStatement.bindNull(7)
                    translationStatement.bindLong(8, if (row.needsReview) 1 else 0)
                    translationStatement.executeInsert()

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
        const val BUNDLED_VERSION = 20260906
        const val ASSET = "danbooru_tags_pt20.csv"
        // Do not use a .gz suffix: Android's asset packager strips it after inflating the file.
        const val TRANSLATION_ASSET = "tag_translations_ko.jsonl.gzip"
        private const val SOURCE = "DraconicDragon/dbr-e621-lists-archive@2026-04-01"
        private const val TRANSLATION_SOURCE = "translated_all_checked@2026-09-05"
        private const val BATCH_SIZE = 2_000
        private const val TAG_SQL = """
            INSERT INTO tags (id, canonicalTag, danbooruCategory, appCategory, postCount,
                danbooruPostCount, naiCount, naiConfidence, novelAiSource, danbooruSource,
                userCreated, lastSeenAt, useCount, lastUsedAt, bundled)
            VALUES (?, ?, ?, NULL, NULL, ?, NULL, NULL, 0, 0, 0, ?, 0, NULL, 1)
            ON CONFLICT(canonicalTag) DO UPDATE SET
                danbooruCategory=excluded.danbooruCategory,
                danbooruPostCount=excluded.danbooruPostCount,
                danbooruSource=CASE WHEN tags.bundled=0 THEN 0 ELSE tags.danbooruSource END,
                lastSeenAt=excluded.lastSeenAt,
                bundled=1
        """
        private const val ALIAS_SQL = "INSERT OR IGNORE INTO tag_aliases (id, tagId, alias, source) VALUES (?, ?, ?, ?)"
        private const val BASE_TAG_SQL = """
            INSERT INTO tags (id, canonicalTag, danbooruCategory, appCategory, postCount,
                danbooruPostCount, naiCount, naiConfidence, novelAiSource, danbooruSource,
                userCreated, lastSeenAt, useCount, lastUsedAt, bundled)
            VALUES (?, ?, ?, ?, NULL, ?, NULL, NULL, 0, 0, 0, ?, 0, NULL, 1)
            ON CONFLICT(canonicalTag) DO UPDATE SET appCategory=excluded.appCategory, bundled=1
        """
        private const val TRANSLATION_SQL = """
            INSERT INTO base_translations
                (id, tagId, korean, koreanAliases, source, updatedAt, suggestedCategory, needsReview)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(tagId) DO UPDATE SET
                korean=excluded.korean,
                koreanAliases=excluded.koreanAliases,
                source=excluded.source,
                updatedAt=excluded.updatedAt,
                suggestedCategory=excluded.suggestedCategory,
                needsReview=excluded.needsReview
        """

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

@Serializable
private data class BundledTranslationRow(
    val tag: String,
    @SerialName("ko") val korean: String,
    @SerialName("aliases_ko") val koreanAliases: List<String> = emptyList(),
    @SerialName("app_category") val appCategory: String = "",
    @SerialName("suggested_category") val suggestedCategory: String = "",
    @SerialName("needs_review") val needsReview: Boolean = false,
    @SerialName("source_category") val sourceCategory: String? = null,
    @SerialName("post_count") val postCount: Long? = null,
)
