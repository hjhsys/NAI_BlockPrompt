package com.hjhsys.naiblockprompt.domain.editor

import com.hjhsys.naiblockprompt.domain.model.*
import com.hjhsys.naiblockprompt.domain.prompt.PromptProcessor
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

    @Test fun `character collapse does not alter nested block collapse state`() {
        val created = SessionEditor.addCharacter(Session.empty(), CharacterType.GIRL, "Block 1")
        val character = created.characters.single()
        val block = character.prompts.positiveBlocks.single()
        val nestedCollapsed = SessionEditor.setBlockCollapsed(
            created, PromptOwner.Character(character.id), PromptPolarity.POSITIVE, block.id, true,
        )
        val collapsed = SessionEditor.setCharacterCollapsed(nestedCollapsed, character.id, true)
        val reopened = SessionEditor.setCharacterCollapsed(collapsed, character.id, false)
        assertTrue(collapsed.characters.single().collapsed)
        assertFalse(reopened.characters.single().collapsed)
        assertTrue(reopened.characters.single().prompts.positiveBlocks.single().collapsed)
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

    @Test fun `split inserts a new default block after source and preserves source properties`() {
        val original = PromptBlock(name = "Imported", content = "ABC, DEF, GHI", enabled = false, order = 0)
        val before = Session.empty().copy(base = BasePrompt(PromptPair(positiveBlocks = listOf(original))))

        val after = SessionEditor.splitBlockAtCursor(
            before, PromptOwner.Base, PromptPolarity.POSITIVE, original.id, 10, "Block 2",
        )
        val blocks = after.base.prompts.positiveBlocks

        assertEquals(listOf("ABC, DEF,", "GHI"), blocks.map { it.content })
        assertEquals(listOf(0, 1), blocks.map { it.order })
        assertEquals("Block 2", blocks[1].name)
        assertEquals(original.id, blocks[0].id)
        assertNotEquals(original.id, blocks[1].id)
        assertFalse(blocks[1].enabled)
        assertFalse(blocks[1].locked)
        assertFalse(blocks[1].collapsed)
    }

    @Test fun `split stays in character negative parent`() {
        val block = PromptBlock(name = "Negative", content = "bad hands, text")
        val character = CharacterPrompt(prompts = PromptPair(negativeBlocks = listOf(block)))
        val before = Session.empty().copy(characters = listOf(character))

        val after = SessionEditor.splitBlockAtCursor(
            before, PromptOwner.Character(character.id), PromptPolarity.NEGATIVE, block.id, 11, "Block 2",
        )

        assertEquals(2, after.characters.single().prompts.negativeBlocks.size)
        assertEquals(before.base, after.base)
        assertTrue(after.characters.single().prompts.positiveBlocks.isEmpty())
    }

    @Test fun `split rejects tag middle protected empty and locked boundaries`() {
        val invalidContents = listOf(
            "blue eyes",
            "1.2::ABC, DEF ::",
            "|| ABC, DEF ||",
            "## ABC, DEF ##",
        )
        invalidContents.forEach { content ->
            val block = PromptBlock(name = "Block", content = content)
            val source = Session.empty().copy(base = BasePrompt(PromptPair(positiveBlocks = listOf(block))))
            val cursor = if (',' in content) content.indexOf(',') + 2 else 4
            assertEquals(source, SessionEditor.splitBlockAtCursor(source, PromptOwner.Base, PromptPolarity.POSITIVE, block.id, cursor, "Block 2"))
        }
        val lockedBlock = PromptBlock(name = "Locked", content = "ABC, DEF", locked = true)
        val lockedSession = Session.empty().copy(base = BasePrompt(PromptPair(positiveBlocks = listOf(lockedBlock))))
        assertEquals(lockedSession, SessionEditor.splitBlockAtCursor(lockedSession, PromptOwner.Base, PromptPolarity.POSITIVE, lockedBlock.id, 5, "Block 2"))
    }

    @Test fun `split preserves final prompt tag sequence`() {
        val block = PromptBlock(name = "Imported", content = "ABC, DEF, GHI")
        val before = Session.empty().copy(base = BasePrompt(PromptPair(positiveBlocks = listOf(block))))
        val after = SessionEditor.splitBlockAtCursor(before, PromptOwner.Base, PromptPolarity.POSITIVE, block.id, 10, "Block 2")
        fun apiMeaning(session: Session) = PromptProcessor.joinEnabledBlocks(session.base.prompts.positiveBlocks, false)
            .replace(Regex("\\s+"), " ")

        assertEquals(apiMeaning(before), apiMeaning(after))
    }

    @Test fun `merge keeps upper metadata removes current and preserves final prompt`() {
        val upper = PromptBlock(name = "Keep me", content = "ABC, DEF", collapsed = true, order = 0)
        val current = PromptBlock(name = "Remove me", content = "GHI", order = 1)
        val before = Session.empty().copy(base = BasePrompt(PromptPair(positiveBlocks = listOf(upper, current))))
        val beforePrompt = PromptProcessor.joinEnabledBlocks(before.base.prompts.positiveBlocks, false)

        val after = SessionEditor.mergeBlockWithPrevious(before, PromptOwner.Base, PromptPolarity.POSITIVE, current.id)
        val merged = after.base.prompts.positiveBlocks.single()

        assertEquals(upper.id, merged.id)
        assertEquals(upper.name, merged.name)
        assertTrue(merged.collapsed)
        assertEquals("ABC, DEF,\n\nGHI", merged.content)
        assertEquals(beforePrompt, PromptProcessor.joinEnabledBlocks(listOf(merged), false))
    }

    @Test fun `merge rejects first mismatched enabled and either locked block`() {
        fun sessionOf(first: PromptBlock, second: PromptBlock) = Session.empty().copy(
            base = BasePrompt(PromptPair(positiveBlocks = listOf(first.copy(order = 0), second.copy(order = 1)))),
        )
        val first = PromptBlock(name = "First", content = "ABC")
        val second = PromptBlock(name = "Second", content = "DEF")
        val ordinary = sessionOf(first, second)
        assertEquals(ordinary, SessionEditor.mergeBlockWithPrevious(ordinary, PromptOwner.Base, PromptPolarity.POSITIVE, first.id))

        listOf(
            sessionOf(first.copy(enabled = false), second),
            sessionOf(first.copy(locked = true), second),
            sessionOf(first, second.copy(locked = true)),
        ).forEach { source ->
            val currentId = source.base.prompts.positiveBlocks[1].id
            assertEquals(source, SessionEditor.mergeBlockWithPrevious(source, PromptOwner.Base, PromptPolarity.POSITIVE, currentId))
        }
    }

    @Test fun `disabled blocks merge while remaining excluded from generation`() {
        val upper = PromptBlock(name = "Upper", content = "ABC", enabled = false, order = 0)
        val current = PromptBlock(name = "Current", content = "DEF", enabled = false, order = 1)
        val before = Session.empty().copy(base = BasePrompt(PromptPair(positiveBlocks = listOf(upper, current))))

        val after = SessionEditor.mergeBlockWithPrevious(before, PromptOwner.Base, PromptPolarity.POSITIVE, current.id)

        assertEquals(1, after.base.prompts.positiveBlocks.size)
        assertFalse(after.base.prompts.positiveBlocks.single().enabled)
        assertEquals("", PromptProcessor.joinEnabledBlocks(after.base.prompts.positiveBlocks, false))
    }
}
