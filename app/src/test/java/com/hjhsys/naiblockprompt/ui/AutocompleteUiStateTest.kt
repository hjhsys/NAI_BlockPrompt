package com.hjhsys.naiblockprompt.ui

import com.hjhsys.naiblockprompt.data.autocomplete.AutocompleteResults
import com.hjhsys.naiblockprompt.domain.autocomplete.PromptFragment
import com.hjhsys.naiblockprompt.domain.autocomplete.SuggestionSource
import com.hjhsys.naiblockprompt.domain.autocomplete.TagSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutocompleteUiStateTest {
    private val fragment = PromptFragment("blu", 0, 3)

    @Test fun `local results appear while remote remains loading`() {
        val local = listOf(TagSuggestion("blue_hair", SuggestionSource.LOCAL))
        val state = AutocompleteUiState("block", fragment, loading = true, requestStartedAtNanos = 10L)
            .withLocalResults(local)

        assertEquals(local, state.local)
        assertTrue(state.loading)
        assertEquals(10L, state.requestStartedAtNanos)
    }

    @Test fun `remote merge preserves local and provider sources without flicker`() {
        val local = listOf(TagSuggestion("blue_hair", SuggestionSource.LOCAL))
        val remote = AutocompleteResults(
            novelAi = listOf(
                TagSuggestion("blue hair", SuggestionSource.NOVEL_AI),
                TagSuggestion("blue_eyes", SuggestionSource.NOVEL_AI),
            ),
            danbooru = listOf(TagSuggestion("blue_sky", SuggestionSource.DANBOORU)),
        )
        val state = AutocompleteUiState("block", fragment, local = local, loading = true)
            .withRemoteResults(remote)

        assertEquals(local, state.local)
        assertEquals(listOf("blue_eyes"), state.novelAi.map { it.tag })
        assertEquals(SuggestionSource.NOVEL_AI, state.novelAi.single().source)
        assertEquals(SuggestionSource.DANBOORU, state.danbooru.single().source)
        assertFalse(state.loading)
    }

    @Test fun `late rich local results re-deduplicate already returned remote results`() {
        val remoteFirst = AutocompleteUiState("block", fragment, loading = true).withRemoteResults(
            AutocompleteResults(novelAi = listOf(TagSuggestion("blue_hair", SuggestionSource.NOVEL_AI))),
        )
        val final = remoteFirst.withLocalResults(listOf(TagSuggestion("blue hair", SuggestionSource.LOCAL)))

        assertEquals(listOf("blue hair"), final.local.map { it.tag })
        assertTrue(final.novelAi.isEmpty())
    }

    @Test fun `new query and invalidation reject stale local or remote results`() {
        val guard = AutocompleteRequestGuard()
        val firstRevision = guard.next()
        val firstState = AutocompleteUiState("block", fragment)
        assertTrue(guard.isCurrent(firstRevision, "block", fragment, firstState))

        val nextFragment = PromptFragment("blue", 0, 4)
        val secondRevision = guard.next()
        val secondState = AutocompleteUiState("block", nextFragment)
        assertFalse(guard.isCurrent(firstRevision, "block", fragment, secondState))
        assertTrue(guard.isCurrent(secondRevision, "block", nextFragment, secondState))

        guard.invalidate()
        assertFalse(guard.isCurrent(secondRevision, "block", nextFragment, secondState))
    }
}
