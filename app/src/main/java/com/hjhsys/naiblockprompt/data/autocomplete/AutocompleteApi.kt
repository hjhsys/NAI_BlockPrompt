package com.hjhsys.naiblockprompt.data.autocomplete

import com.hjhsys.naiblockprompt.domain.autocomplete.TagSuggestion

interface AutocompleteApi {
    suspend fun novelAi(token: String, model: String, query: String): Result<List<TagSuggestion>>
    suspend fun danbooru(query: String): Result<List<TagSuggestion>>
}
