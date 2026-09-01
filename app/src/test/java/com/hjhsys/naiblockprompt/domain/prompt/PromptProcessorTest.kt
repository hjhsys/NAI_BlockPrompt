package com.hjhsys.naiblockprompt.domain.prompt

import com.hjhsys.naiblockprompt.domain.model.PromptBlock
import org.junit.Assert.*
import org.junit.Test

class PromptProcessorTest {
    @Test fun `closed and open comments are removed`() {
        assertEquals("a, c", PromptProcessor.stripComments("a, ## memo ##c"))
        assertEquals("a, ", PromptProcessor.stripComments("a, ## memo\ncontinues"))
    }

    @Test fun `comment inside weight leaves natural weight syntax`() {
        assertEquals("1.2::red  eyes ::", PromptProcessor.stripComments("1.2::red ##memo## eyes ::"))
    }

    @Test fun `comment spans include delimiters and open comment reaches block end`() {
        val input = "tag ##closed## next ##open\nuntil end"
        assertEquals(
            listOf(
                CommentSpan(input.indexOf("##closed"), input.indexOf("## next") + 2),
                CommentSpan(input.indexOf("##open"), input.length),
            ),
            PromptProcessor.commentSpans(input),
        )
    }

    @Test fun `weight containing a comment exposes both ranges for UI priority`() {
        val input = "1.4::red ##note::still comment## eyes ::"
        val weight = PromptProcessor.weightSpans(input).single()
        val comment = PromptProcessor.commentSpans(input).single()
        assertTrue(comment.start >= weight.start)
        assertTrue(comment.endExclusive <= weight.endExclusive)
    }

    @Test fun `weight validation ignores delimiters in comments`() {
        assertFalse(PromptProcessor.validateWeights("1.2::tag :: ## :: ##").hasUnclosedWeight)
        assertTrue(PromptProcessor.validateWeights("1.2::tag").hasUnclosedWeight)
    }

    @Test fun `numeric character before closing weight receives a space`() {
        assertEquals("2::abc123 ::", PromptProcessor.normalizeWeightClosings("2::abc123::"))
        assertEquals("2::artist ::", PromptProcessor.normalizeWeightClosings("2::artist ::"))
    }

    @Test fun `formatters preserve weighted comma group`() {
        val input = "girl\n1.2::red eyes, blue hair ::\nsmile,,"
        assertEquals("girl, 1.2::red eyes, blue hair ::, smile", PromptProcessor.formatSingleLine(input))
        assertEquals("girl,\n1.2::red eyes, blue hair ::,\nsmile", PromptProcessor.formatMultiline(input))
    }

    @Test fun `enabled blocks join in order with comments stripped`() {
        val blocks = listOf(
            PromptBlock(name = "b", content = "second", order = 1),
            PromptBlock(name = "off", content = "hidden", enabled = false, order = 2),
            PromptBlock(name = "a", content = "first, ##note##", order = 0),
        )
        assertEquals("first, second,", PromptProcessor.joinEnabledBlocks(blocks, true))
    }

    @Test fun `weight spans exclude comments and retain strength`() {
        val spans = PromptProcessor.weightSpans("0.7::soft :: ## 2.0::ignored :: ## 1.5::strong ::")
        assertEquals(listOf(0.7f, 1.5f), spans.map { it.weight })
    }
}
