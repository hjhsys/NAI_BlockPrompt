package com.hjhsys.naiblockprompt.data.autocomplete

import com.hjhsys.naiblockprompt.data.local.dao.TagDao
import com.hjhsys.naiblockprompt.data.local.entity.TagEntity
import com.hjhsys.naiblockprompt.domain.autocomplete.*
import com.hjhsys.naiblockprompt.domain.model.AutocompleteSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.util.UUID

data class AutocompleteResults(val novelAi: List<TagSuggestion> = emptyList(), val danbooru: List<TagSuggestion> = emptyList(), val failed: Boolean = false)

class AutocompleteRepository(
    private val api: AutocompleteApi,
    private val tagDao: TagDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Cached(val at: Long, val values: List<TagSuggestion>)
    private val cache = mutableMapOf<String, Cached>()
    private val lastRequest = mutableMapOf<SuggestionSource, Long>()

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
            failed = missingNaiToken || listOfNotNull(naiResult, danResult).any { it.isFailure },
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
            postCount = suggestion.postCount ?: old?.postCount,
            novelAiSource = old?.novelAiSource == true || suggestion.source == SuggestionSource.NOVEL_AI,
            danbooruSource = old?.danbooruSource == true || suggestion.source == SuggestionSource.DANBOORU,
            userCreated = old?.userCreated ?: false,
            lastSeenAt = now(),
        ))
    }
}
