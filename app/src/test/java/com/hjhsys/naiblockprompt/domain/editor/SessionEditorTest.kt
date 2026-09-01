package com.hjhsys.naiblockprompt.domain.editor

import com.hjhsys.naiblockprompt.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class SessionEditorTest {
    private val locked = PromptBlock(name = "locked", locked = true, order = 0)
    private val normal = PromptBlock(name = "normal", order = 1)
    private val session = Session.empty().copy(
        base = BasePrompt(PromptPair(positiveBlocks = listOf(locked, normal))),
    )

    @Test fun `locked block rejects content name delete format and direct move`() {
        val owner = PromptOwner.Base
        val polarity = PromptPolarity.POSITIVE
        val changed = SessionEditor.updateBlock(
            session, owner, polarity, locked.id,
            transform = { it.copy(content = "changed") },
        )
        assertEquals("", changed.base.prompts.positiveBlocks.first().content)
        assertEquals(session, SessionEditor.removeBlock(session, owner, polarity, locked.id))
        assertEquals(session, SessionEditor.moveBlock(session, owner, polarity, locked.id, MoveDirection.DOWN))
        assertEquals(session, SessionEditor.formatBlock(session, owner, polarity, locked.id, BlockFormatter.SINGLE_LINE))
    }

    @Test fun `locked block allows enabled collapsed and unlock`() {
        var updated = SessionEditor.setBlockEnabled(session, PromptOwner.Base, PromptPolarity.POSITIVE, locked.id, false)
        updated = SessionEditor.setBlockCollapsed(updated, PromptOwner.Base, PromptPolarity.POSITIVE, locked.id, true)
        updated = SessionEditor.setBlockLocked(updated, PromptOwner.Base, PromptPolarity.POSITIVE, locked.id, false)
        val block = updated.base.prompts.positiveBlocks.first()
        assertFalse(block.enabled)
        assertTrue(block.collapsed)
        assertFalse(block.locked)
    }

    @Test fun `moving normal block can change locked relative position`() {
        val updated = SessionEditor.moveBlock(
            session, PromptOwner.Base, PromptPolarity.POSITIVE, normal.id, MoveDirection.UP,
        )
        assertEquals(listOf(normal.id, locked.id), updated.base.prompts.positiveBlocks.map { it.id })
    }

    @Test fun `character moves with all prompt state`() {
        var current = Session.empty()
        current = SessionEditor.addCharacter(current, CharacterType.GIRL, "Block 1")
        current = SessionEditor.addCharacter(current, CharacterType.BOY, "Block 1")
        val secondId = current.characters[1].id
        val updated = SessionEditor.moveCharacter(current, secondId, MoveDirection.UP)
        assertEquals(secondId, updated.characters.first().id)
        assertEquals(listOf(0, 1), updated.characters.map { it.order })
    }

    @Test fun `character type creates one ordinary positive block with matching initial content`() {
        val expected = mapOf(
            CharacterType.GIRL to "girl",
            CharacterType.BOY to "boy",
            CharacterType.OTHER to "other",
        )
        expected.forEach { (type, content) ->
            val character = SessionEditor.addCharacter(Session.empty(), type, "Block 1").characters.single()
            assertEquals(type, character.type)
            assertEquals(content, character.prompts.positiveBlocks.single().content)
            assertTrue(character.prompts.negativeBlocks.isEmpty())
        }
    }

    @Test fun `initial character block stays ordinary and independent from type metadata`() {
        var current = SessionEditor.addCharacter(Session.empty(), CharacterType.GIRL, "Block 1")
        val character = current.characters.single()
        val block = character.prompts.positiveBlocks.single()
        current = SessionEditor.updateBlock(
            current,
            PromptOwner.Character(character.id),
            PromptPolarity.POSITIVE,
            block.id,
        ) { it.copy(content = "", name = "Custom") }
        current = SessionEditor.setCharacterType(current, character.id, CharacterType.BOY)
        val edited = current.characters.single().prompts.positiveBlocks.single()
        assertEquals("", edited.content)
        assertEquals("Custom", edited.name)
        current = SessionEditor.removeBlock(current, PromptOwner.Character(character.id), PromptPolarity.POSITIVE, block.id)
        assertTrue(current.characters.single().prompts.positiveBlocks.isEmpty())
    }
}
