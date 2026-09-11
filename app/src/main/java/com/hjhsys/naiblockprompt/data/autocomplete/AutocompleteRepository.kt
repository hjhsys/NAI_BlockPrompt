package com.hjhsys.naiblockprompt.data.autocomplete

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.hjhsys.naiblockprompt.BuildConfig
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
import com.hjhsys.naiblockprompt.data.local.entity.TagExclusionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import com.hjhsys.naiblockprompt.domain.tags.*
import com.hjhsys.naiblockprompt.domain.model.AppTagCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.hjhsys.naiblockprompt.domain.model.ExcludedTagItem
import com.hjhsys.naiblockprompt.domain.model.TagExclusionOrigin
import com.hjhsys.naiblockprompt.domain.model.TagExclusionReason

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
    val deferredTranslationCount = tagDao.observeDeferredTranslationCount()
    val usedCategories = tagDao.observeUsedCategories()
    val userCategories = tagDao.observeUserCategories()
    val wildcards = tagDao.observeWildcards()
    fun exclusions(origin: TagExclusionOrigin?): Flow<List<ExcludedTagItem>> =
        tagDao.observeTagExclusions(origin?.name.orEmpty()).map { rows ->
            rows.map { row ->
                ExcludedTagItem(
                    canonicalTag = row.canonicalTag,
                    origin = runCatching { TagExclusionOrigin.valueOf(row.origin) }.getOrDefault(TagExclusionOrigin.USER),
                    reasonCode = row.reasonCode,
                    reasonText = row.reasonText,
                    userConfirmed = row.userConfirmed,
                    updatedAt = row.updatedAt,
                )
            }
        }
    fun dictionary(query: String, filter: TagDictionaryFilter, category: String, sort: TagDictionarySort): Flow<List<TagDictionaryItem>> =
        tagDao.observeDictionary(query.trim(), query.trim().replace(' ', '_'), filter.name, category, sort.name)
    fun dictionaryCount(query: String, filter: TagDictionaryFilter, category: String): Flow<Int> =
        tagDao.observeDictionaryCount(query.trim(), query.trim().replace(' ', '_'), filter.name, category)

    suspend fun addCategory(name: String) {
        name.trim().takeIf(String::isNotBlank)?.let { tagDao.upsertUserCategory(UserTagCategoryEntity(it, now())) }
    }

    suspend fun excludeTag(
        canonicalTag: String,
        origin: TagExclusionOrigin = TagExclusionOrigin.USER,
        reasonCode: String = TagExclusionReason.USER_HIDDEN.storageValue,
        reasonText: String? = null,
        userConfirmed: Boolean = origin == TagExclusionOrigin.USER,
    ) {
        val canonical = TagExclusionPolicy.canonical(canonicalTag)
        require(canonical.isNotBlank())
        val existing = tagDao.findTagExclusion(canonical)
        val timestamp = now()
        // Confirming an AI decision must not erase its original provenance or explanation.
        val entity = mergeTagExclusion(existing, canonical, origin, reasonCode, reasonText, userConfirmed, timestamp)
        tagDao.upsertTagExclusion(entity)
    }

    suspend fun restoreExcludedTag(canonicalTag: String) {
        tagDao.deleteTagExclusion(TagExclusionPolicy.canonical(canonicalTag))
    }

    suspend fun confirmExcludedTag(canonicalTag: String) {
        val canonical = TagExclusionPolicy.canonical(canonicalTag)
        val existing = tagDao.findTagExclusion(canonical) ?: return
        tagDao.upsertTagExclusion(existing.copy(userConfirmed = true, updatedAt = now()))
    }

    suspend fun saveWildcard(id: String?, name: String, valuesText: String, folder: String? = null) {
        val timestamp = now()
        val normalizedName = name.trim().removePrefix("__").removeSuffix("__").replace(' ', '_')
        val existing = tagDao.findWildcardByName(normalizedName)
        require(normalizedName.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid wildcard name" }
        require(existing == null || existing.id == id) { "Wildcard name already exists" }
        tagDao.upsertWildcard(com.hjhsys.naiblockprompt.data.local.entity.WildcardEntity(
            id = id ?: existing?.id ?: UUID.randomUUID().toString(),
            name = normalizedName,
            valuesText = valuesText.lines().map(String::trim).filter(String::isNotBlank).joinToString("\n"),
            folder = folder?.trim()?.takeIf(String::isNotBlank),
            createdAt = existing?.createdAt ?: timestamp,
            updatedAt = timestamp,
        ))
    }

    suspend fun deleteWildcard(item: com.hjhsys.naiblockprompt.data.local.entity.WildcardEntity) = tagDao.deleteWildcard(item)

    suspend fun exportTranslationBatch(
        missingTranslation: Boolean,
        missingCategory: Boolean,
        limit: Int = 1_000,
        allBatches: Boolean = false,
        includeDeferred: Boolean = false,
    ): TagTranslationExportFile = withContext(Dispatchers.IO) {
        require(missingTranslation || missingCategory || includeDeferred)
        val candidates = tagDao.translationCandidates(if (allBatches) Int.MAX_VALUE else limit, missingTranslation, missingCategory, includeDeferred).map {
            TagTranslationCandidate(it.canonicalTag, it.danbooruCategory, it.danbooruPostCount, it.korean, it.appCategory, it.koreanAliases)
        }
        val categories = (AppTagCategory.entries.map { it.value } + tagDao.getUsedCategories() + tagDao.getUserCategories()).distinct()
        TagTranslationExportFile(
            fileName = if (allBatches) "nai_tags_translation_all_batches.zip" else "nai_tags_translation_batch.zip",
            content = TagTranslationExchange.exportBundle(candidates, categories, splitBatches = allBatches),
        )
    }

    suspend fun exportSelectedTranslations(items: List<TagDictionaryItem>): String {
        val categories = (AppTagCategory.entries.map { it.value } + tagDao.getUsedCategories() + tagDao.getUserCategories()).distinct()
        return TagTranslationExchange.exportClipboard(items.map { item ->
            TagTranslationCandidate(
                tag = item.canonicalTag,
                sourceCategory = item.danbooruCategory,
                postCount = item.danbooruPostCount,
                korean = item.korean,
                appCategory = item.appCategory,
                koreanAliases = item.koreanAliases,
            )
        }, categories)
    }

    suspend fun previewTranslationImport(text: String): TagTranslationImportPreview {
        val parsed = TagTranslationExchange.parse(text)
        val knownCategories = (AppTagCategory.entries.map { it.value } + tagDao.getUsedCategories() + tagDao.getUserCategories()).toSet()
        val valid = mutableListOf<ValidatedTagTranslation>()
        val exclusions = mutableListOf<ValidatedTagExclusion>()
        val unknown = mutableListOf<String>()
        val newCategories = linkedSetOf<String>()
        parsed.rows.distinctBy { it.tag }.forEach { row ->
            val tag = tagDao.findByCanonical(row.tag)
            if (tag == null) unknown += row.tag
            else {
                if (row.status == TagTranslationStatus.EXCLUDED_CANDIDATE) {
                    exclusions += ValidatedTagExclusion(tag.id, row)
                    return@forEach
                }
                val category = row.appCategory ?: row.suggestedCategory
                if (category != null && category !in knownCategories) newCategories += category
                valid += ValidatedTagTranslation(tag.id, row, canDelete = row.needsReview && tagDao.canDeleteTranslationTypo(tag.id))
            }
        }
        return TagTranslationImportPreview(
            validRows = valid,
            invalidLines = parsed.invalidLines,
            unknownTags = unknown,
            newCategories = newCategories.toList(),
            reviewCount = valid.count { it.row.status == TagTranslationStatus.REVIEW || it.row.needsReview },
            excludedCandidates = exclusions,
            unchangedCount = valid.count { it.row.status == TagTranslationStatus.UNCHANGED },
        )
    }

    suspend fun previewTranslationFile(uri: Uri): TagTranslationImportPreview = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use(TranslationResultReader::read)
            ?: error("Cannot open translation result")
        val preview = previewTranslationImport(text)
        require(preview.validRows.isNotEmpty() || preview.excludedCandidates.isNotEmpty() || preview.unknownTags.isNotEmpty()) { "No translation rows" }
        preview
    }

    suspend fun applyTranslationImport(preview: TagTranslationImportPreview, overwriteExisting: Boolean, selectedDeleteIds: Set<String> = emptySet(), deferReviewed: Boolean = true) {
        val deleteIds = preview.validRows.filter { it.canDelete && it.row.needsReview && it.tagId in selectedDeleteIds }.map { it.tagId }.toSet()
        val applicable = preview.validRows.filter { it.row.status == TagTranslationStatus.TRANSLATED && !it.row.needsReview }
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
        val deferred = if (deferReviewed) preview.validRows.filter { it.row.needsReview && it.tagId !in deleteIds }.map { validated ->
            val old = tagDao.findOverride(validated.tagId)
            old?.copy(translationDeferred = true) ?: UserTagOverrideEntity(
                id = UUID.nameUUIDFromBytes("override:${validated.tagId}".toByteArray()).toString(),
                tagId = validated.tagId, korean = null, koreanAliases = null, appCategory = null,
                favorite = false, thumbnailPath = null, updatedAt = now(), translationDeferred = true,
            )
        } else emptyList()
        tagDao.applyTranslationImport(overrides + deferred, categories, deleteIds)
        preview.excludedCandidates.forEach { candidate ->
            excludeTag(
                canonicalTag = candidate.row.tag,
                origin = TagExclusionOrigin.AI,
                reasonCode = requireNotNull(candidate.row.exclusionReasonCode),
                reasonText = candidate.row.exclusionReasonText,
                userConfirmed = false,
            )
        }
        if (deleteIds.isNotEmpty()) cache.clear()
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
        if (!old.favorite && old.korean == null && old.koreanAliases == null && old.appCategory == null && !old.translationDeferred) tagDao.clearUserOverride(item.id)
        else tagDao.upsertUserOverride(old.copy(thumbnailPath = null, updatedAt = now()))
    }

    private suspend fun saveThumbnailPath(item: TagDictionaryItem, path: String) {
        val old = tagDao.findOverride(item.id)
        tagDao.upsertUserOverride(UserTagOverrideEntity(
            id = old?.id ?: UUID.nameUUIDFromBytes("override:${item.id}".toByteArray()).toString(),
            tagId = item.id, korean = old?.korean, koreanAliases = old?.koreanAliases,
            appCategory = old?.appCategory, favorite = old?.favorite ?: item.favorite,
            translationDeferred = old?.translationDeferred ?: false,
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
            tagDao.upsertUserOverride(old.copy(korean = null, koreanAliases = null, appCategory = null, updatedAt = now(), translationDeferred = false))
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

    suspend fun localPrefix(query: String): List<TagSuggestion> {
        val normalizeStarted = System.nanoTime()
        val canonicalPrefix = query.trim().replace(' ', '_').lowercase()
        val normalizedAt = System.nanoTime()
        if (canonicalPrefix.isBlank()) return emptyList()
        val rows = tagDao.searchPrefix(canonicalPrefix)
        val queriedAt = System.nanoTime()
        val suggestions = rows.map { it.toLocalSuggestion() }
        logLocalTiming("prefix", query.length, normalizedAt - normalizeStarted, queriedAt - normalizedAt, System.nanoTime() - queriedAt, suggestions.size)
        return suggestions
    }

    suspend fun local(query: String): List<TagSuggestion> {
        val normalizeStarted = System.nanoTime()
        val trimmed = query.trim()
        val canonicalPrefix = trimmed.replace(' ', '_')
        val normalizedAt = System.nanoTime()
        val rows = tagDao.searchAutocomplete(trimmed, canonicalPrefix)
        val queriedAt = System.nanoTime()
        val suggestions = rows.map {
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
        logLocalTiming("rich", query.length, normalizedAt - normalizeStarted, queriedAt - normalizedAt, System.nanoTime() - queriedAt, suggestions.size)
        return suggestions
    }

    private fun TagEntity.toLocalSuggestion() = TagSuggestion(
        tag = canonicalTag,
        source = SuggestionSource.LOCAL,
        danbooruPostCount = danbooruPostCount,
        naiCount = naiCount,
        naiConfidence = naiConfidence,
        category = appCategory ?: danbooruCategory,
        useCount = useCount,
        lastUsedAt = lastUsedAt,
    )

    private fun logLocalTiming(kind: String, queryLength: Int, normalizeNanos: Long, queryNanos: Long, mapNanos: Long, count: Int) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            "AutocompletePerf",
            "local=$kind chars=$queryLength normalizeMs=${normalizeNanos / 1_000_000.0} dbFilterRankMs=${queryNanos / 1_000_000.0} mapMs=${mapNanos / 1_000_000.0} count=$count",
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

    /** Selection records usage; verified API responses are also persisted on lookup. */
    suspend fun recordSelection(suggestion: TagSuggestion) {
        if (suggestion.source == SuggestionSource.WILDCARD) return
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
        val excluded = excludedCanonicals(values)
        AutocompleteResults(
            novelAi = TagExclusionPolicy.filter(naiResult?.getOrNull().orEmpty(), excluded),
            danbooru = TagExclusionPolicy.filter(danResult?.getOrNull().orEmpty(), excluded),
            novelAiFailed = missingNaiToken || naiResult?.isFailure == true,
            danbooruFailed = danResult?.isFailure == true,
        )
    }

    private suspend fun excludedCanonicals(values: List<TagSuggestion>): Set<String> {
        val canonical = values.map { TagExclusionPolicy.canonical(it.tag) }.filter(String::isNotBlank).distinct()
        return if (canonical.isEmpty()) emptySet() else tagDao.findExcludedCanonicals(canonical).toSet()
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
        val canonical = TagExclusionPolicy.canonical(suggestion.tag)
        val old = tagDao.findByCanonical(canonical)
        tagDao.upsertTag(TagEntity(
            id = old?.id ?: UUID.nameUUIDFromBytes(canonical.toByteArray()).toString(),
            canonicalTag = canonical,
            danbooruCategory = DanbooruCategory.normalize(suggestion.category ?: old?.danbooruCategory),
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
            bundled = old?.bundled ?: false,
        ))
    }
}

internal fun mergeTagExclusion(
    existing: TagExclusionEntity?,
    canonical: String,
    origin: TagExclusionOrigin,
    reasonCode: String,
    reasonText: String?,
    userConfirmed: Boolean,
    timestamp: Long,
): TagExclusionEntity = if (existing != null) {
    existing.copy(userConfirmed = existing.userConfirmed || userConfirmed, updatedAt = timestamp)
} else {
    TagExclusionEntity(
        canonicalTag = canonical,
        origin = origin.name,
        reasonCode = reasonCode,
        reasonText = reasonText?.trim()?.takeIf(String::isNotBlank),
        userConfirmed = userConfirmed,
        createdAt = existing?.createdAt ?: timestamp,
        updatedAt = timestamp,
    )
}
