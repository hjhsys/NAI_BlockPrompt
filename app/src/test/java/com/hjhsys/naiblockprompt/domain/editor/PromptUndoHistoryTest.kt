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

        assertEquals(snapshot(""), history.undo(first, snapshot("abc", 3)))
        assertNull(history.undo(first, snapshot("")))
    }

    @Test fun `typing pauses and explicit actions create separate steps`() {
        val history = PromptUndoHistory(typingCoalesceMillis = 900)
        history.recordBeforeChange(first, snapshot(""), PromptEditKind.TYPING, 0)
        history.recordBeforeChange(first, snapshot("a", 1), PromptEditKind.TYPING, 1_000)
        history.recordBeforeChange(first, snapshot("ab", 2), PromptEditKind.DISCRETE, 1_001)

        assertEquals(snapshot("ab", 2), history.undo(first, snapshot("abc", 3)))
        assertEquals(snapshot("a", 1), history.undo(first, snapshot("ab", 2)))
        assertEquals(snapshot(""), history.undo(first, snapshot("a", 1)))
    }

    @Test fun `histories are independent per block`() {
        val history = PromptUndoHistory()
        history.recordBeforeChange(first, snapshot("one", 3), PromptEditKind.DISCRETE)
        history.recordBeforeChange(second, snapshot("two", 3), PromptEditKind.DISCRETE)

        assertTrue(history.canUndo(first))
        assertTrue(history.canUndo(second))
        assertEquals("one", history.undo(first, snapshot("one changed"))?.content)
        assertFalse(history.canUndo(first))
        assertEquals("two", history.undo(second, snapshot("two changed"))?.content)
    }

    @Test fun `selection is clamped to restored text`() {
        val history = PromptUndoHistory()
        history.recordBeforeChange(first, PromptEditorSnapshot("abc", -2, 99), PromptEditKind.DISCRETE)

        assertEquals(PromptEditorSnapshot("abc", 0, 3), history.undo(first, snapshot("changed")))
    }

    @Test fun `redo restores undone content and undo restores it again`() {
        val history = PromptUndoHistory()
        history.recordBeforeChange(first, snapshot("A", 1), PromptEditKind.DISCRETE)
        assertEquals("A", history.undo(first, snapshot("B", 1))?.content)
        assertTrue(history.canRedo(first))
        assertEquals("B", history.redo(first, snapshot("A", 1))?.content)
        assertEquals("A", history.undo(first, snapshot("B", 1))?.content)
    }

    @Test fun `new edit after undo clears redo branch`() {
        val history = PromptUndoHistory()
        history.recordBeforeChange(first, snapshot("A"), PromptEditKind.DISCRETE)
        history.undo(first, snapshot("B"))
        history.recordBeforeChange(first, snapshot("A"), PromptEditKind.DISCRETE)
        assertFalse(history.canRedo(first))
        assertNull(history.redo(first, snapshot("C")))
    }

    @Test fun `continuous mixed weight adjustments are one undo and redo step`() {
        val history = PromptUndoHistory()
        history.recordBeforeChange(first, snapshot("1.0::tag ::"), PromptEditKind.WEIGHT_ADJUSTMENT, coalesceKey = "tag")
        history.recordBeforeChange(first, snapshot("1.1::tag ::"), PromptEditKind.WEIGHT_ADJUSTMENT, coalesceKey = "tag")
        history.recordBeforeChange(first, snapshot("1.2::tag ::"), PromptEditKind.WEIGHT_ADJUSTMENT, coalesceKey = "tag")
        assertEquals("1.0::tag ::", history.undo(first, snapshot("1.1::tag ::"))?.content)
        assertEquals("1.1::tag ::", history.redo(first, snapshot("1.0::tag ::"))?.content)
    }

    @Test fun `selection change and typing end weight coalescing`() {
        val history = PromptUndoHistory()
        history.recordBeforeChange(first, snapshot("A"), PromptEditKind.WEIGHT_ADJUSTMENT, coalesceKey = "a")
        history.recordBeforeChange(first, snapshot("B"), PromptEditKind.WEIGHT_ADJUSTMENT, coalesceKey = "b")
        history.recordBeforeChange(first, snapshot("C"), PromptEditKind.TYPING, 10)
        assertEquals("C", history.undo(first, snapshot("D"))?.content)
        assertEquals("B", history.undo(first, snapshot("C"))?.content)
        assertEquals("A", history.undo(first, snapshot("B"))?.content)
    }

    private fun snapshot(content: String, cursor: Int = 0) = PromptEditorSnapshot(content, cursor, cursor)
}
