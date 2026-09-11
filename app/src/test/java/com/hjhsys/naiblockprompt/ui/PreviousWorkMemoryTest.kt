package com.hjhsys.naiblockprompt.ui

import com.hjhsys.naiblockprompt.domain.model.PromptBlock
import com.hjhsys.naiblockprompt.domain.model.PromptPair
import com.hjhsys.naiblockprompt.domain.model.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviousWorkMemoryTest {
    @Test
    fun `swap exchanges current and previous immediately`() {
        val memory = PreviousWorkMemory()
        memory.load(session("B"))

        val replacement = memory.swap(session("A"))

        assertEquals("B", replacement?.name())
        assertEquals("A", memory.swap(session("B"))?.name())
    }

    @Test
    fun `rapid swaps preserve alternating semantics`() {
        val memory = PreviousWorkMemory()
        memory.load(session("B"))

        val first = memory.swap(session("A"))!!
        val second = memory.swap(first)!!
        val third = memory.swap(second)!!

        assertEquals(listOf("B", "A", "B"), listOf(first.name(), second.name(), third.name()))
        assertTrue(memory.hasPrevious)
    }

    @Test
    fun `missing previous work does not change memory`() {
        val memory = PreviousWorkMemory()

        assertNull(memory.swap(session("A")))
        assertFalse(memory.hasPrevious)
    }

    @Test
    fun `late initial load cannot overwrite newer replacement`() {
        val memory = PreviousWorkMemory()
        memory.replaceWith(session("A"))

        memory.load(session("stale"))

        assertEquals("A", memory.swap(session("B"))?.name())
    }

    private fun session(name: String): Session = Session.empty().copy(
        base = Session.empty().base.copy(
            prompts = PromptPair(positiveBlocks = listOf(PromptBlock(name = name, content = name))),
        ),
    )

    private fun Session.name(): String = base.prompts.positiveBlocks.single().content
}
