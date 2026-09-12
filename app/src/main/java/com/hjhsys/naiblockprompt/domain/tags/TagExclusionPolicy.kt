package com.hjhsys.naiblockprompt.domain.tags

import com.hjhsys.naiblockprompt.domain.autocomplete.TagSuggestion
import com.hjhsys.naiblockprompt.domain.model.TagExclusionOrigin
import com.hjhsys.naiblockprompt.domain.model.TagExclusionFilter

object TagExclusionPolicy {
    fun canonical(value: String): String = value.trim().replace(' ', '_').lowercase()

    fun filter(suggestions: List<TagSuggestion>, excludedCanonicals: Set<String>): List<TagSuggestion> =
        suggestions.filterNot { canonical(it.tag) in excludedCanonicals }

    fun matchesFilter(
        origin: TagExclusionOrigin,
        userConfirmed: Boolean,
        filter: TagExclusionFilter,
    ): Boolean = when (filter) {
        TagExclusionFilter.ALL -> true
        TagExclusionFilter.NEEDS_REVIEW -> origin == TagExclusionOrigin.AI && !userConfirmed
        TagExclusionFilter.USER_CONFIRMED -> origin == TagExclusionOrigin.AI && userConfirmed
        TagExclusionFilter.AI_SUGGESTED -> origin == TagExclusionOrigin.AI
        TagExclusionFilter.USER_DIRECT -> origin == TagExclusionOrigin.USER
    }
}
