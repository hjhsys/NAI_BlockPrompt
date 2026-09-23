package com.hjhsys.naiblockprompt.domain.editor

import com.hjhsys.naiblockprompt.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class CharacterImportTest {
    private fun character(id: String, tag: String) = CharacterPrompt(id = id, prompts = PromptPair(positiveBlocks = listOf(PromptBlock(id = "$id-b", name = tag, content = tag))))

    @Test fun `replace preserves existing behavior`() {
        val source = listOf(character("s", "source"))
        assertEquals(source, CharacterImport.apply(listOf(character("c", "current")), source, CharacterImportMode.REPLACE))
    }

    @Test fun `append preserves current order and clones all ids`() {
        var next = 0
        val current = listOf(character("c", "current"))
        val source = listOf(character("s1", "one"), character("s2", "two"))
        val result = CharacterImport.apply(current, source, CharacterImportMode.APPEND) { "new-${next++}" }
        assertEquals(listOf("current", "one", "two"), result.map { it.prompts.positiveBlocks.single().content })
        assertEquals("c", result.first().id)
        assertTrue(result.drop(1).none { it.id.startsWith("s") })
        assertTrue(result.drop(1).flatMap { it.prompts.positiveBlocks }.none { it.id.endsWith("-b") })
        assertEquals(listOf(0, 1, 2), result.map { it.order })
    }
}
