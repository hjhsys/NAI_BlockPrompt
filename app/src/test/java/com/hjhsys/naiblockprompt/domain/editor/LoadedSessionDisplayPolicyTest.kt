package com.hjhsys.naiblockprompt.domain.editor

import com.hjhsys.naiblockprompt.domain.model.CharacterPrompt
import com.hjhsys.naiblockprompt.domain.model.GenerationSettings
import com.hjhsys.naiblockprompt.domain.model.PromptBlock
import com.hjhsys.naiblockprompt.domain.model.PromptPair
import com.hjhsys.naiblockprompt.domain.model.PromptPolarity
import com.hjhsys.naiblockprompt.domain.model.Session
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadedSessionDisplayPolicyTest {
    @Test
    fun `whole session load selects positive without changing snapshot content`() {
        val loaded = Session.empty().copy(
            base = Session.empty().base.copy(
                prompts = PromptPair(
                    positiveBlocks = listOf(PromptBlock(name = "positive", content = "1girl", locked = true, collapsed = true)),
                    negativeBlocks = listOf(PromptBlock(name = "negative", content = "lowres", enabled = false)),
                ),
                selectedPolarity = PromptPolarity.NEGATIVE,
            ),
            characters = listOf(
                CharacterPrompt(
                    prompts = PromptPair(positiveBlocks = listOf(PromptBlock(name = "character", content = "blue hair"))),
                    selectedPolarity = PromptPolarity.NEGATIVE,
                    collapsed = true,
                ),
            ),
            generationSettings = GenerationSettings(width = 1024, height = 1024, steps = 28),
        )

        val displayed = LoadedSessionDisplayPolicy.prepare(loaded)

        assertEquals(PromptPolarity.POSITIVE, displayed.base.selectedPolarity)
        assertEquals(PromptPolarity.POSITIVE, displayed.characters.single().selectedPolarity)
        assertEquals(loaded.base.prompts, displayed.base.prompts)
        assertEquals(loaded.characters.single().copy(selectedPolarity = PromptPolarity.POSITIVE), displayed.characters.single())
        assertEquals(loaded.generationSettings, displayed.generationSettings)
        assertEquals(PromptPolarity.NEGATIVE, loaded.base.selectedPolarity)
        assertEquals(PromptPolarity.NEGATIVE, loaded.characters.single().selectedPolarity)
    }
}
