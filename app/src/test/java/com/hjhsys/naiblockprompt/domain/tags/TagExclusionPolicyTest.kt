package com.hjhsys.naiblockprompt.domain.tags

import com.hjhsys.naiblockprompt.domain.autocomplete.SuggestionSource
import com.hjhsys.naiblockprompt.domain.autocomplete.TagSuggestion
import com.hjhsys.naiblockprompt.domain.model.TagExclusionOrigin
import com.hjhsys.naiblockprompt.domain.model.TagExclusionFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `exclusion review filters separate pending confirmed AI and direct user decisions`() {
        assertTrue(TagExclusionPolicy.matchesFilter(TagExclusionOrigin.AI, false, TagExclusionFilter.NEEDS_REVIEW))
        assertFalse(TagExclusionPolicy.matchesFilter(TagExclusionOrigin.AI, true, TagExclusionFilter.NEEDS_REVIEW))
        assertTrue(TagExclusionPolicy.matchesFilter(TagExclusionOrigin.AI, true, TagExclusionFilter.USER_CONFIRMED))
        assertFalse(TagExclusionPolicy.matchesFilter(TagExclusionOrigin.USER, true, TagExclusionFilter.USER_CONFIRMED))
        assertTrue(TagExclusionPolicy.matchesFilter(TagExclusionOrigin.AI, false, TagExclusionFilter.AI_SUGGESTED))
        assertTrue(TagExclusionPolicy.matchesFilter(TagExclusionOrigin.AI, true, TagExclusionFilter.AI_SUGGESTED))
        assertTrue(TagExclusionPolicy.matchesFilter(TagExclusionOrigin.USER, true, TagExclusionFilter.USER_DIRECT))
        assertFalse(TagExclusionPolicy.matchesFilter(TagExclusionOrigin.AI, true, TagExclusionFilter.USER_DIRECT))
    }
}
