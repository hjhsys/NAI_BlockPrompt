package com.hjhsys.naiblockprompt.data.autocomplete

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hjhsys.naiblockprompt.data.local.dao.TagDao
import com.hjhsys.naiblockprompt.data.local.entity.TagEntity
import com.hjhsys.naiblockprompt.domain.autocomplete.*
import com.hjhsys.naiblockprompt.domain.model.AutocompleteSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.util.UUID
import com.hjhsys.naiblockprompt.domain.model.TagDictionaryFilter
import com.hjhsys.naiblockprompt.domain.model.TagDictionaryItem
import com.hjhsys.naiblockprompt.domain.model.TagDictionarySort
import com.hjhsys.naiblockprompt.data.local.entity.UserTagOverrideEntity
import com.hjhsys.naiblockprompt.data.local.entity.UserTagCategoryEntity
import kotlinx.coroutines.flow.Flow
import java.io.File
import com.hjhsys.naiblockprompt.domain.tags.*
import com.hjhsys.naiblockprompt.domain.model.AppTagCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AutocompleteResults(
    val novelAi: List<TagSuggestion> = emptyList(),
    val danbooru: List<TagSuggestion> = emptyList(),
    val novelAiFailed: Boolean = false,
    val danbooruFailed: Boolean = false,
)

class AutocompleteRepository(
    private val context: Context,
    private val api: AutocompleteApi,
    private val tagDao: TagDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val observedTags = tagDao.observeAll()
    val tagCount = tagDao.observeCount()
    val missingTranslationCount = tagDao.observeMissingTranslationCount()
    val missingCategoryCount = tagDao.observeMissingCategoryCount()
    val usedCategories = tagDao.observeUsedCategories()
    val userCategories = tagDao.observeUserCategories()
    fun dictionary(query: String, filter: TagDictionaryFilter, category: String, sort: TagDictionarySort): Flow<List<TagDictionaryItem>> =
        tagDao.observeDictionary(query.trim(), query.trim().replace(' ', '_'), filter.name, category, sort.name)

    suspend fun addCategory(name: String) {
        name.trim().takeIf(String::isNotBlank)?.let { tagDao.upsertUserCategory(UserTagCategoryEntity(it, now())) }
    }

    suspend fun exportTranslationBatch(
        missingTranslation: Boolean,
        missingCategory: Boolean,
        limit: Int = 1_000,
        allBatches: Boolean = false,
    ): TagTranslationExportFile = withContext(Dispatchers.IO) {
        require(missingTranslation || missingCategory)
        val candidates = tagDao.translationCandidates(if (allBatches) Int.MAX_VALUE else limit, missingTranslation, missingCategory).map {
            TagTranslationCandidate(it.canonicalTag, it.danbooruCategory, it.danbooruPostCount, it.korean, it.appCategory)
        }
        val categories = (AppTagCategory.entries.map { it.value } + tagDao.getUsedCategories() + tagDao.getUserCategories()).distinct()
        TagTranslationExportFile(
            fileName = if (allBatches) "nai_tags_translation_all_batches.zip" else "nai_tags_translation_batch.zip",
            content = TagTranslationExchange.exportBundle(candidates, categories, splitBatches = allBatches),
        )
    }

    suspend fun previewTranslationImport(text: String): TagTranslationImportPreview {
        val parsed = TagTranslationExchange.parse(text)
        val knownCategories = (AppTagCategory.entries.map { it.value } + tagDao.getUsedCategories() + tagDao.getUserCategories()).toSet()
        val valid = mutableListOf<ValidatedTagTranslation>()
        val unknown = mutableListOf<String>()
        val newCategories = linkedSetOf<String>()
        parsed.rows.distinctBy { it.tag }.forEach { row ->
            val tag = tagDao.findByCanonical(row.tag)
            if (tag == null) unknown += row.tag
            else {
                val category = row.appCategory ?: row.suggestedCategory
                if (category != null && category !in knownCategories) newCategories += category
                valid += ValidatedTagTranslation(tag.id, row)
            }
        }
        return TagTranslationImportPreview(valid, parsed.invalidLines, unknown, newCategories.toList(), valid.count { it.row.needsReview })
    }

    suspend fun applyTranslationImport(preview: TagTranslationImportPreview, overwriteExisting: Boolean) {
        val applicable = preview.validRows.filterNot { it.row.needsReview }
        val knownCategories = (AppTagCategory.entries.map { it.value } + tagDao.getUsedCategories() + tagDao.getUserCategories()).toSet()
        val categories = emptyList<UserTagCategoryEntity>()
        val overrides = applicable.map { validated ->
            val old = tagDao.findOverride(validated.tagId)
            val row = validated.row
            UserTagOverrideEntity(
                id = old?.id ?: UUID.nameUUIDFromBytes("override:${validated.tagId}".toByteArray()).toString(),
                tagId = validated.tagId,
                korean = if (!overwriteExisting && !old?.korean.isNullOrBlank()) old?.korean else row.korean ?: old?.korean,
                koreanAliases = if (!overwriteExisting && !old?.koreanAliases.isNullOrBlank()) old?.koreanAliases else row.aliases.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: old?.koreanAliases,
                appCategory = if (!overwriteExisting && !old?.appCategory.isNullOrBlank()) old?.appCategory else row.appCategory?.takeIf { it in knownCategories } ?: old?.appCategory,
                favorite = old?.favorite ?: false,
                thumbnailPath = old?.thumbnailPath,
                updatedAt = now(),
            )
        }
        tagDao.applyTranslationImport(overrides, categories)
    }

    suspend fun setThumbnail(item: TagDictionaryItem, uri: Uri) {
        storeThumbnail(item) { options -> context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } }
    }

    suspend fun setThumbnailFromFile(item: TagDictionaryItem, sourcePath: String) {
        val uri = sourcePath.takeIf { it.startsWith("content://") }?.let(Uri::parse)
        if (uri != null) setThumbnail(item, uri)
        else storeThumbnail(item) { options -> BitmapFactory.decodeFile(sourcePath, options) }
    }

    private suspend fun storeThumbnail(item: TagDictionaryItem, decode: (BitmapFactory.Options) -> Bitmap?) = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext
        var sample = 1
        while (bounds.outWidth / sample > 768 || bounds.outHeight / sample > 768) sample *= 2
        val bitmap = decode(BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext
        val directory = File(context.filesDir, "tag_thumbnails").apply { mkdirs() }
        val destination = File(directory, "${item.id}.webp")
        destination.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP, 86, it) }
        bitmap.recycle()
        item.thumbnailPath?.takeIf { it != destination.absolutePath }?.let(::File)?.takeIf(File::isFile)?.delete()
        saveThumbnailPath(item, destination.absolutePath)
    }

    suspend fun removeThumbnail(item: TagDictionaryItem) {
        item.thumbnailPath?.let(::File)?.takeIf(File::isFile)?.delete()
        val old = tagDao.findOverride(item.id) ?: return
        if (!old.favorite && old.korean == null && old.koreanAliases == null && old.appCategory == null) tagDao.clearUserOverride(item.id)
        else tagDao.upsertUserOverride(old.copy(thumbnailPath = null, updatedAt = now()))
    }

    private suspend fun saveThumbnailPath(item: TagDictionaryItem, path: String) {
        val old = tagDao.findOverride(item.id)
        tagDao.upsertUserOverride(UserTagOverrideEntity(
            id = old?.id ?: UUID.nameUUIDFromBytes("override:${item.id}".toByteArray()).toString(),
            tagId = item.id, korean = old?.korean, koreanAliases = old?.koreanAliases,
            appCategory = old?.appCategory, favorite = old?.favorite ?: item.favorite,
            thumbnailPath = path, updatedAt = now(),
        ))
    }

    suspend fun setFavorite(item: TagDictionaryItem, favorite: Boolean) {
        tagDao.setFavorite(UUID.nameUUIDFromBytes("override:${item.id}".toByteArray()).toString(), item.id, favorite, now())
    }

    suspend fun saveUserDetails(item: TagDictionaryItem, korean: String?, aliases: String?, appCategory: String?) {
        val old = tagDao.findOverride(item.id)
        tagDao.upsertUserOverride(UserTagOverrideEntity(
            id = old?.id ?: UUID.nameUUIDFromBytes("override:${item.id}".toByteArray()).toString(),
            tagId = item.id,
            korean = korean?.trim()?.takeIf(String::isNotBlank),
            koreanAliases = aliases?.trim()?.takeIf(String::isNotBlank),
            appCategory = appCategory?.trim()?.takeIf(String::isNotBlank),
            favorite = old?.favorite ?: item.favorite,
            thumbnailPath = old?.thumbnailPath,
            updatedAt = now(),
        ))
    }

    suspend fun resetUserDetails(item: TagDictionaryItem) {
        val old = tagDao.findOverride(item.id) ?: return
        if (old.favorite || old.thumbnailPath != null) {
            tagDao.upsertUserOverride(old.copy(korean = null, koreanAliases = null, appCategory = null, updatedAt = now()))
        } else tagDao.clearUserOverride(item.id)
    }

    suspend fun addUserTag(canonical: String, korean: String?, aliases: String?, appCategory: String?) {
        val normalized = canonical.trim().replace(' ', '_').lowercase()
        require(normalized.isNotBlank())
        val existing = tagDao.findByCanonical(normalized)
        val id = existing?.id ?: UUID.nameUUIDFromBytes(normalized.toByteArray()).toString()
        if (existing == null) tagDao.upsertTag(TagEntity(
            id, normalized, null, appCategory?.trim()?.takeIf(String::isNotBlank), null, null, null, null,
            novelAiSource = false, danbooruSource = false, userCreated = true, lastSeenAt = now(),
        ))
        val item = TagDictionaryItem(
            id = id,
            canonicalTag = normalized,
            danbooruCategory = existing?.danbooruCategory,
            appCategory = existing?.appCategory,
            danbooruPostCount = existing?.danbooruPostCount,
            naiCount = existing?.naiCount,
            naiConfidence = existing?.naiConfidence,
            novelAiSource = existing?.novelAiSource == true,
            danbooruSource = existing?.danbooruSource == true,
            userCreated = existing?.userCreated ?: true,
            useCount = existing?.useCount ?: 0,
            lastUsedAt = existing?.lastUsedAt,
            lastSeenAt = existing?.lastSeenAt,
            korean = null,
            koreanAliases = null,
            englishAliases = null,
            favorite = false,
            thumbnailPath = null,
            bundled = existing?.bundled ?: false,
        )
        saveUserDetails(item, korean, aliases, appCategory)
    }
    private data class Cached(val at: Long, val values: List<TagSuggestion>)
    private val cache = mutableMapOf<String, Cached>()
    private val lastRequest = mutableMapOf<SuggestionSource, Long>()

    suspend fun local(query: String): List<TagSuggestion> = tagDao.searchAutocomplete(query, query.replace(' ', '_')).map {
        TagSuggestion(
            tag = it.canonicalTag,
            source = SuggestionSource.LOCAL,
            danbooruPostCount = it.danbooruPostCount,
            naiCount = it.naiCount,
            naiConfidence = it.naiConfidence,
            category = it.appCategory ?: it.danbooruCategory,
            useCount = it.useCount,
            lastUsedAt = it.lastUsedAt,
        )
    }

    suspend fun recordUse(tag: String) {
        tagDao.recordUse(tag.replace(' ', '_'), now())
    }

    suspend fun deleteUserOnlyTag(item: TagDictionaryItem): Boolean {
        val deleted = tagDao.deleteUserOnlyTag(item.id) > 0
        if (deleted) item.thumbnailPath?.let { File(it).takeIf(File::isFile)?.delete() }
        return deleted
    }

    suspend fun resetToBundledTags() {
        tagDao.resetToBundledTags()
        File(context.filesDir, "tag_thumbnails").deleteRecursively()
    }

    /** Remote suggestions become local data only after the user explicitly selects one. */
    suspend fun recordSelection(suggestion: TagSuggestion) {
        persist(suggestion)
        recordUse(suggestion.tag)
    }

    suspend fun suggest(query: String, source: AutocompleteSource, token: String?, model: String): AutocompleteResults = coroutineScope {
        val missingNaiToken = source != AutocompleteSource.DANBOORU && token == null
        val nai = if (source != AutocompleteSource.DANBOORU && token != null) async { fetch(SuggestionSource.NOVEL_AI, "$model:$query") { api.novelAi(token, model, query) } } else null
        val dan = if (source != AutocompleteSource.NOVEL_AI) async { fetch(SuggestionSource.DANBOORU, query) { api.danbooru(query) } } else null
        val naiResult = nai?.await()
        val danResult = dan?.await()
        val values = naiResult?.getOrNull().orEmpty() + danResult?.getOrNull().orEmpty()
        values.forEach { persist(it) }
        AutocompleteResults(
            novelAi = naiResult?.getOrNull().orEmpty(),
            danbooru = danResult?.getOrNull().orEmpty(),
            novelAiFailed = missingNaiToken || naiResult?.isFailure == true,
            danbooruFailed = danResult?.isFailure == true,
        )
    }

    private suspend fun fetch(source: SuggestionSource, keyPart: String, call: suspend () -> Result<List<TagSuggestion>>): Result<List<TagSuggestion>> {
        val key = "$source:$keyPart"
        cache[key]?.takeIf { now() - it.at < 120_000 }?.let { return Result.success(it.values) }
        val wait = 1_000 - (now() - (lastRequest[source] ?: 0L))
        if (wait > 0) delay(wait)
        lastRequest[source] = now()
        return call().onSuccess { cache[key] = Cached(now(), it) }
    }

    private suspend fun persist(suggestion: TagSuggestion) {
        val canonical = suggestion.tag.replace(' ', '_')
        val old = tagDao.findByCanonical(canonical)
        tagDao.upsertTag(TagEntity(
            id = old?.id ?: UUID.nameUUIDFromBytes(canonical.toByteArray()).toString(),
            canonicalTag = canonical,
            danbooruCategory = suggestion.category ?: old?.danbooruCategory,
            appCategory = old?.appCategory,
            legacyPostCount = old?.legacyPostCount,
            danbooruPostCount = suggestion.danbooruPostCount ?: old?.danbooruPostCount,
            naiCount = suggestion.naiCount ?: old?.naiCount,
            naiConfidence = suggestion.naiConfidence ?: old?.naiConfidence,
            novelAiSource = old?.novelAiSource == true || suggestion.source == SuggestionSource.NOVEL_AI,
            danbooruSource = old?.danbooruSource == true || suggestion.source == SuggestionSource.DANBOORU,
            userCreated = old?.userCreated ?: false,
            lastSeenAt = now(),
            useCount = old?.useCount ?: 0,
            lastUsedAt = old?.lastUsedAt,
        ))
    }
}
