package com.hjhsys.naiblockprompt.domain.prompt

import com.hjhsys.naiblockprompt.domain.model.PromptBlock
import com.hjhsys.naiblockprompt.domain.model.TextRenderingState
import org.junit.Assert.*
import org.junit.Test

class PromptProcessorTest {
    @Test fun extraCommaCleanupRemovesOnlyEmptyElementsAndPreservesMeaningfulWhitespace() {
        assertEquals("1girl,     solo, long hair", PromptProcessor.cleanupExtraCommas("1girl, ,,     solo,, long hair"))
    }

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
        assertEquals("1.2::artist123 ::", PromptProcessor.normalizeWeightClosings("1.2::artist123::"))
        assertEquals("2::abc123 ::", PromptProcessor.normalizeWeightClosings("2::abc123::"))
        assertEquals("2::artist ::", PromptProcessor.normalizeWeightClosings("2::artist ::"))
    }

    @Test fun `formatters preserve weighted comma group`() {
        val input = "girl\n1.2::red eyes, blue hair ::\nsmile,,"
        assertEquals("girl, 1.2::red eyes, blue hair ::, smile", PromptProcessor.formatSingleLine(input))
        assertEquals("girl,\n1.2::red eyes, blue hair ::,\nsmile", PromptProcessor.formatMultiline(input))
    }

    @Test fun `formatters preserve randomizer contents as one protected region`() {
        val randomizer = "||option A| option B, wide\tspace|option C||"
        val input = "girl,\n$randomizer, smile"
        assertEquals("girl, $randomizer, smile", PromptProcessor.formatSingleLine(input))
        assertEquals("girl,\n$randomizer,\nsmile", PromptProcessor.formatMultiline(input))
    }

    @Test fun `odd randomizer delimiter count produces non blocking validation warning`() {
        val validation = PromptProcessor.validateRandomizers("tag, ||option A|option B")
        assertEquals(1, validation.delimiterCount)
        assertTrue(validation.hasUnclosedRandomizer)
        assertFalse(PromptProcessor.validateRandomizers("||a|b||").hasUnclosedRandomizer)
    }

    @Test fun `randomizer spans include delimiters and open randomizer reaches block end`() {
        val input = "tag ||a|b|| next ||open|value"
        assertEquals(
            listOf(
                RandomizerSpan(input.indexOf("||a"), input.indexOf("|| next") + 2),
                RandomizerSpan(input.indexOf("||open"), input.length),
            ),
            PromptProcessor.randomizerSpans(input),
        )
    }

    @Test fun `text rendering is appended only when enabled with content`() {
        val off = TextRenderingState(enabled = false, content = "HELLO")
        assertEquals("base,", PromptProcessor.appendTextRendering("base,", off))
        assertEquals("base,", PromptProcessor.appendTextRendering("base,", TextRenderingState(enabled = true)))
        assertEquals(
            "base,\nText: HELLO WORLD\n\nGOOD MORNING",
            PromptProcessor.appendTextRendering("base,", TextRenderingState(true, "HELLO WORLD\n\nGOOD MORNING")),
        )
        assertEquals(
            "base,\nred handwritten sign,\nText: HELLO",
            PromptProcessor.appendTextRendering(
                "base,",
                TextRenderingState(enabled = true, content = "HELLO", description = "red handwritten sign,"),
            ),
        )
    }

    @Test fun `enabled blocks join in order with comments stripped`() {
        val blocks = listOf(
            PromptBlock(name = "b", content = "second", order = 1),
            PromptBlock(name = "off", content = "hidden", enabled = false, order = 2),
            PromptBlock(name = "a", content = "first, ##note##", order = 0),
        )
        assertEquals("first,\n\nsecond,", PromptProcessor.joinEnabledBlocks(blocks, true))
    }

    @Test fun `weight spans exclude comments and retain strength`() {
        val spans = PromptProcessor.weightSpans("0.7::soft :: ## 2.0::ignored :: ## 1.5::strong ::")
        assertEquals(listOf(0.7f, 1.5f), spans.map { it.weight })
    }

    @Test fun `editable and incomplete syntax always produces valid highlight ranges`() {
        val cases = listOf(
            "plain prompt",
            "1.2::shirt ::",
            "1.2::shirt, long hair ::",
            "1.2:shirt ::",
            "1.2::shirt :",
            "1.2::shirt",
            "1.2::shirt ## comment::inside ## ::",
            "1.2::shirt ||red|blue|| ::",
            "::",
            ":",
            "",
        )
        cases.forEach { input ->
            val ranges = PromptProcessor.weightSpans(input).map { it.start to it.endExclusive } +
                PromptProcessor.commentSpans(input).map { it.start to it.endExclusive } +
                PromptProcessor.randomizerSpans(input).map { it.start to it.endExclusive }
            assertTrue("Invalid highlight range for '$input': $ranges", ranges.all { (start, end) -> start >= 0 && end in start..input.length })
        }
    }
}
