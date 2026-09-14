package com.hjhsys.naiblockprompt.domain.editor

import com.hjhsys.naiblockprompt.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class PromptCherryPickTest {
    private fun block(name: String, content: String, order: Int = 0) = PromptBlock(name = name, content = content, order = order)

    @Test fun `base selection appends without replacing state or reusing ids`() {
        val existing = block("Existing", "A")
        val current = Session.empty().copy(
            base = BasePrompt(PromptPair(listOf(existing))),
            generationSettings = GenerationSettings(modelId = "model", seed = 42),
        )
        val p = block("P", "B")
        val n = block("N", "bad")
        val draft = PromptCherryPickDraft(PromptPair(listOf(p), listOf(n)), emptyList())
        val result = PromptCherryPick.append(current, draft, setOf(p.id), setOf(n.id), emptyList())

        assertEquals(listOf("A", "B"), result.base.prompts.positiveBlocks.map { it.content })
        assertEquals(listOf("bad"), result.base.prompts.negativeBlocks.map { it.content })
        assertEquals(current.generationSettings, result.generationSettings)
        assertEquals(existing.id, result.base.prompts.positiveBlocks.first().id)
        assertNotEquals(p.id, result.base.prompts.positiveBlocks.last().id)
        assertTrue(result.base.prompts.positiveBlocks.last().enabled)
        assertFalse(result.base.prompts.positiveBlocks.last().locked)
    }

    @Test fun `character auto creates for zero and uses existing for one`() {
        val sourceBlock = block("Face", "blue eyes")
        val source = CharacterPrompt(type = CharacterType.GIRL, prompts = PromptPair(listOf(sourceBlock)))
        val draft = PromptCherryPickDraft(PromptPair(), listOf(source))
        val plan = CharacterCherryPick(source.id, setOf(sourceBlock.id), emptySet())

        val created = PromptCherryPick.append(Session.empty(), draft, emptySet(), emptySet(), listOf(plan))
        assertEquals(CharacterType.GIRL, created.characters.single().type)
        assertEquals("blue eyes", created.characters.single().prompts.positiveBlocks.single().content)
        assertNull(created.characters.single().position)

        val destination = CharacterPrompt(type = CharacterType.BOY, position = CharacterPosition(.2f, .3f))
        val one = Session.empty().copy(characters = listOf(destination))
        val appended = PromptCherryPick.append(one, draft, emptySet(), emptySet(), listOf(plan))
        assertEquals(destination.id, appended.characters.single().id)
        assertEquals(destination.type, appended.characters.single().type)
        assertEquals(destination.position, appended.characters.single().position)
    }

    @Test fun `multi destination receives independent copies and keeps unrelated characters`() {
        val sourceBlock = block("Tag", "ABC")
        val source = CharacterPrompt(prompts = PromptPair(listOf(sourceBlock)))
        val first = CharacterPrompt(order = 0)
        val second = CharacterPrompt(order = 1)
        val current = Session.empty().copy(characters = listOf(first, second))
        val result = PromptCherryPick.append(
            current,
            PromptCherryPickDraft(PromptPair(), listOf(source)),
            emptySet(), emptySet(),
            listOf(CharacterCherryPick(source.id, setOf(sourceBlock.id), emptySet(), setOf(first.id, second.id))),
        )
        val imported = result.characters.map { it.prompts.positiveBlocks.single() }
        assertEquals(listOf("ABC", "ABC"), imported.map { it.content })
        assertNotEquals(imported[0].id, imported[1].id)
        assertEquals(current.characters.map { it.id }, result.characters.map { it.id })
    }

    @Test fun `temporary split never mutates source draft`() {
        val block = block("Source", "ABC, DEF")
        val source = PromptCherryPickDraft(PromptPair(listOf(block)), emptyList())
        val split = PromptCherryPick.splitBlock(source, PromptOwner.Base, PromptPolarity.POSITIVE, block.id, 5, "Part")
        assertEquals(listOf("ABC,", "DEF"), split.base.positiveBlocks.map { it.content })
        assertEquals(listOf("ABC, DEF"), source.base.positiveBlocks.map { it.content })
    }

    @Test fun `temporary split accepts top level newline but rejects protected newline`() {
        val plain = block("Source", "ABC\nDEF")
        val source = PromptCherryPickDraft(PromptPair(listOf(plain)), emptyList())
        val split = PromptCherryPick.splitBlock(source, PromptOwner.Base, PromptPolarity.POSITIVE, plain.id, 4, "Part")
        assertEquals(listOf("ABC", "DEF"), split.base.positiveBlocks.map { it.content })

        val protected = block("Protected", "1.2::ABC\nDEF ::")
        val protectedSource = PromptCherryPickDraft(PromptPair(listOf(protected)), emptyList())
        assertEquals(protectedSource, PromptCherryPick.splitBlock(protectedSource, PromptOwner.Base, PromptPolarity.POSITIVE, protected.id, 9, "Part"))
    }

    @Test fun `one source selection can append to opposite base polarity or a new character`() {
        val sourceBlock = block("Picked", "ABC")
        val current = Session.empty()
        val negative = PromptCherryPick.appendSelection(current, listOf(sourceBlock), setOf(sourceBlock.id), CherryPickDestination.Base(PromptPolarity.NEGATIVE))
        assertEquals("ABC", negative.base.prompts.negativeBlocks.last().content)
        assertEquals(current.base.prompts.positiveBlocks, negative.base.prompts.positiveBlocks)

        val character = PromptCherryPick.appendSelection(current, listOf(sourceBlock), setOf(sourceBlock.id), CherryPickDestination.NewCharacter(CharacterType.GIRL, PromptPolarity.POSITIVE))
        assertEquals(CharacterType.GIRL, character.characters.single().type)
        assertEquals("ABC", character.characters.single().prompts.positiveBlocks.single().content)
        assertNotEquals(sourceBlock.id, character.characters.single().prompts.positiveBlocks.single().id)
    }
}
