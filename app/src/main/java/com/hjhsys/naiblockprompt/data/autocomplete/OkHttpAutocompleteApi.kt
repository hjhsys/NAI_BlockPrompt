package com.hjhsys.naiblockprompt.data.autocomplete

import com.hjhsys.naiblockprompt.domain.autocomplete.SuggestionSource
import com.hjhsys.naiblockprompt.domain.autocomplete.TagSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class OkHttpAutocompleteApi(
    private val client: OkHttpClient,
    private val json: Json,
    private val naiBaseUrl: okhttp3.HttpUrl = "https://image.novelai.net/".toHttpUrl(),
    private val danbooruBaseUrl: okhttp3.HttpUrl = "https://danbooru.donmai.us/".toHttpUrl(),
) : AutocompleteApi {
    override suspend fun novelAi(token: String, model: String, query: String) = get(
        naiBaseUrl.newBuilder()
            .addPathSegments("ai/generate-image/suggest-tags")
            .addQueryParameter("model", model)
            .addQueryParameter("prompt", query)
            .addQueryParameter("lang", "en")
            .build().toString(),
        token,
    ) { body -> json.decodeFromString<NaiTagsResponse>(body).tags.map {
        TagSuggestion(tag = it.tag, source = SuggestionSource.NOVEL_AI, naiCount = it.count, naiConfidence = it.confidence)
    } }

    override suspend fun danbooru(query: String) = get(
        danbooruBaseUrl.newBuilder()
            .addPathSegment("tags.json")
            .addQueryParameter("search[name_matches]", "${query.replace(' ', '_')}*")
            .addQueryParameter("search[order]", "count")
            .addQueryParameter("limit", "12")
            .build().toString(),
    ) { body -> json.decodeFromString<List<DanbooruTag>>(body).map {
        TagSuggestion(tag = it.name, source = SuggestionSource.DANBOORU, danbooruPostCount = it.postCount, category = com.hjhsys.naiblockprompt.domain.tags.DanbooruCategory.normalize(it.category.toString()))
    } }

    private suspend fun <T> get(url: String, token: String? = null, parse: (String) -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).header("User-Agent", "NAI-BlockPrompt/0.1")
                .apply { token?.let { header("Authorization", if (it.startsWith("Bearer ", true)) it else "Bearer $it") } }.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(IOException("HTTP ${response.code}"))
                runCatching { parse(response.body?.string().orEmpty()) }
            }
        } catch (error: IOException) { Result.failure(error) }
    }
}

@Serializable private data class NaiTagsResponse(val tags: List<NaiTag>)
@Serializable private data class NaiTag(val tag: String, val count: Double, val confidence: Double)
@Serializable private data class DanbooruTag(val name: String, val category: Int, @SerialName("post_count") val postCount: Long)
