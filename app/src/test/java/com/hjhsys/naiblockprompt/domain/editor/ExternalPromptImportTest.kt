package com.hjhsys.naiblockprompt.domain.editor

import com.hjhsys.naiblockprompt.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPromptImportTest {
    private val current = Session(
        base = BasePrompt(PromptPair(listOf(PromptBlock(name="p", content="old p")), listOf(PromptBlock(name="n", content="old n")))),
        characters = listOf(CharacterPrompt(id="current", prompts=PromptPair(listOf(PromptBlock(name="c", content="current"))))),
    )
    private fun apply(p: Boolean, n: Boolean, chars: Boolean = false, mode: CharacterImportMode = CharacterImportMode.REPLACE) = ExternalPromptImport.apply(
        current, "Imported", "new p", "new n",
        listOf(CharacterPrompt(id="source", prompts=PromptPair(listOf(PromptBlock(name="s", content="source"))))),
        p, n, chars, mode,
    )

    @Test fun `positive and negative selections are independent`() {
        assertEquals(listOf("new p", "old n"), listOf(apply(true,false).base.prompts.positiveBlocks.single().content, apply(true,false).base.prompts.negativeBlocks.single().content))
        assertEquals(listOf("old p", "new n"), listOf(apply(false,true).base.prompts.positiveBlocks.single().content, apply(false,true).base.prompts.negativeBlocks.single().content))
        assertEquals(listOf("new p", "new n"), listOf(apply(true,true).base.prompts.positiveBlocks.single().content, apply(true,true).base.prompts.negativeBlocks.single().content))
        assertEquals(listOf("old p", "old n"), listOf(apply(false,false).base.prompts.positiveBlocks.single().content, apply(false,false).base.prompts.negativeBlocks.single().content))
    }

    @Test fun `external blocks are custom one and characters can append`() {
        val result = apply(true, true, true, CharacterImportMode.APPEND)
        assertEquals(BlockScaleMode.CUSTOM, result.base.prompts.positiveBlocks.single().scaleMode)
        assertEquals(listOf("current", "source"), result.characters.map { it.prompts.positiveBlocks.single().content })
        assertTrue(result.characters.last().id != "source")
    }
}
