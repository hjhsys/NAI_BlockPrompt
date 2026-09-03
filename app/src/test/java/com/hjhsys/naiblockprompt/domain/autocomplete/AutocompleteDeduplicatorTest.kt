package com.hjhsys.naiblockprompt.domain.autocomplete

import org.junit.Assert.assertEquals
import org.junit.Test

class AutocompleteDeduplicatorTest {
    @Test fun `remote results already available locally are removed`() {
        val local = listOf(TagSuggestion("blue_hair", SuggestionSource.LOCAL))
        val remote = listOf(
            TagSuggestion("Blue Hair", SuggestionSource.NOVEL_AI),
            TagSuggestion("blue eyes", SuggestionSource.NOVEL_AI),
            TagSuggestion("blue_eyes", SuggestionSource.NOVEL_AI),
        )
        assertEquals(listOf("blue eyes"), AutocompleteDeduplicator.excludeLocal(local, remote).map { it.tag })
    }
}
