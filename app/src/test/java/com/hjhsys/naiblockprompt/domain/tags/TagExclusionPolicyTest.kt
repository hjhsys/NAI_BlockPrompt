package com.hjhsys.naiblockprompt.domain.tags

import com.hjhsys.naiblockprompt.domain.autocomplete.SuggestionSource
import com.hjhsys.naiblockprompt.domain.autocomplete.TagSuggestion
import org.junit.Assert.assertEquals
import org.junit.Test

class TagExclusionPolicyTest {
    @Test
    fun `canonical normalization matches local and remote spellings`() {
        assertEquals("long_hair", TagExclusionPolicy.canonical(" Long Hair "))
    }

    @Test
    fun `excluded canonical is removed from every suggestion source`() {
        val suggestions = listOf(
            TagSuggestion("bad tag", SuggestionSource.LOCAL),
            TagSuggestion("bad_tag", SuggestionSource.NOVEL_AI),
            TagSuggestion("BAD_TAG", SuggestionSource.DANBOORU),
            TagSuggestion("good_tag", SuggestionSource.DANBOORU),
        )

        assertEquals(listOf("good_tag"), TagExclusionPolicy.filter(suggestions, setOf("bad_tag")).map { it.tag })
    }
}
