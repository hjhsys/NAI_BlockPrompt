package com.hjhsys.naiblockprompt.domain.tags

import com.hjhsys.naiblockprompt.domain.autocomplete.TagSuggestion

object TagExclusionPolicy {
    fun canonical(value: String): String = value.trim().replace(' ', '_').lowercase()

    fun filter(suggestions: List<TagSuggestion>, excludedCanonicals: Set<String>): List<TagSuggestion> =
        suggestions.filterNot { canonical(it.tag) in excludedCanonicals }
}
