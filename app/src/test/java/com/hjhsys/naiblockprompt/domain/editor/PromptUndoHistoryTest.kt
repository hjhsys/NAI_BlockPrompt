package com.hjhsys.naiblockprompt.domain.editor

import com.hjhsys.naiblockprompt.domain.model.PromptPolarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptUndoHistoryTest {
    private val first = PromptUndoKey(PromptOwner.Base, PromptPolarity.POSITIVE, "first")
    private val second = PromptUndoKey(PromptOwner.Base, PromptPolarity.POSITIVE, "second")

    @Test fun `continuous typing is one undo step`() {
        val history = PromptUndoHistory(typingCoalesceMillis = 900)
        history.recordBeforeChange(first, snapshot(""), PromptEditKind.TYPING, 0)
        history.recordBeforeChange(first, snapshot("a", 1), PromptEditKind.TYPING, 100)
        history.recordBeforeChange(first, snapshot("ab", 2), PromptEditKind.TYPING, 200)

        assertEquals(snapshot(""), history.undo(first))
        assertNull(history.undo(first))
    }

    @Test fun `typing pauses and explicit actions create separate steps`() {
        val history = PromptUndoHistory(typingCoalesceMillis = 900)
        history.recordBeforeChange(first, snapshot(""), PromptEditKind.TYPING, 0)
        history.recordBeforeChange(first, snapshot("a", 1), PromptEditKind.TYPING, 1_000)
        history.recordBeforeChange(first, snapshot("ab", 2), PromptEditKind.DISCRETE, 1_001)

        assertEquals(snapshot("ab", 2), history.undo(first))
        assertEquals(snapshot("a", 1), history.undo(first))
        assertEquals(snapshot(""), history.undo(first))
    }

    @Test fun `histories are independent per block`() {
        val history = PromptUndoHistory()
        history.recordBeforeChange(first, snapshot("one", 3), PromptEditKind.DISCRETE)
        history.recordBeforeChange(second, snapshot("two", 3), PromptEditKind.DISCRETE)

        assertTrue(history.canUndo(first))
        assertTrue(history.canUndo(second))
        assertEquals("one", history.undo(first)?.content)
        assertFalse(history.canUndo(first))
        assertEquals("two", history.undo(second)?.content)
    }

    @Test fun `selection is clamped to restored text`() {
        val history = PromptUndoHistory()
        history.recordBeforeChange(first, PromptEditorSnapshot("abc", -2, 99), PromptEditKind.DISCRETE)

        assertEquals(PromptEditorSnapshot("abc", 0, 3), history.undo(first))
    }

    private fun snapshot(content: String, cursor: Int = 0) = PromptEditorSnapshot(content, cursor, cursor)
}
