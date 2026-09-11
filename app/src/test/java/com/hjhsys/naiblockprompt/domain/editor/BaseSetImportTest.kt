package com.hjhsys.naiblockprompt.domain.editor

import com.hjhsys.naiblockprompt.domain.model.BasePrompt
import com.hjhsys.naiblockprompt.domain.model.PromptBlock
import com.hjhsys.naiblockprompt.domain.model.PromptPair
import com.hjhsys.naiblockprompt.domain.model.PromptPolarity
import com.hjhsys.naiblockprompt.domain.model.SavedPromptSet
import com.hjhsys.naiblockprompt.domain.model.SavedSetKind
import com.hjhsys.naiblockprompt.domain.model.TextRenderingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BaseSetImportTest {
    private val currentPositive = listOf(PromptBlock(id = "current-positive", name = "Current +", content = "old +", enabled = false, locked = true, collapsed = true, order = 3))
    private val currentNegative = listOf(PromptBlock(id = "current-negative", name = "Current -", content = "old -", order = 4))
    private val sourcePositive = listOf(PromptBlock(id = "source-positive", name = "Source +", content = "new +", enabled = false, locked = true, collapsed = true, order = 8))
    private val sourceNegative = listOf(PromptBlock(id = "source-negative", name = "Source -", content = "new -", enabled = false, locked = true, collapsed = true, order = 9))
    private val currentTextRendering = TextRenderingState(enabled = true, content = "current text")
    private val sourceTextRendering = TextRenderingState(enabled = true, content = "source text")
    private val current = BasePrompt(
        prompts = PromptPair(currentPositive, currentNegative),
        selectedPolarity = PromptPolarity.NEGATIVE,
        textRendering = currentTextRendering,
    )
    private val source = SavedPromptSet(
        kind = SavedSetKind.BASE,
        prompts = PromptPair(sourcePositive, sourceNegative),
        selectedPolarity = PromptPolarity.NEGATIVE,
        textRendering = sourceTextRendering,
    )

    @Test
    fun `positive only replaces positive blocks and text rendering`() {
        val result = BaseSetImport.apply(current, source, BaseSetImportSelection(positive = true, negative = false))

        assertEquals(sourcePositive, result.prompts.positiveBlocks)
        assertEquals(currentNegative, result.prompts.negativeBlocks)
        assertEquals(sourceTextRendering, result.textRendering)
        assertEquals(PromptPolarity.POSITIVE, result.selectedPolarity)
    }

    @Test
    fun `negative only preserves all positive state`() {
        val result = BaseSetImport.apply(current, source, BaseSetImportSelection(positive = false, negative = true))

        assertEquals(currentPositive, result.prompts.positiveBlocks)
        assertEquals(sourceNegative, result.prompts.negativeBlocks)
        assertEquals(currentTextRendering, result.textRendering)
        assertEquals(PromptPolarity.NEGATIVE, result.selectedPolarity)
    }

    @Test
    fun `both replaces both directions`() {
        val result = BaseSetImport.apply(current, source, BaseSetImportSelection(positive = true, negative = true))

        assertEquals(source.prompts, result.prompts)
        assertEquals(sourceTextRendering, result.textRendering)
        assertEquals(PromptPolarity.POSITIVE, result.selectedPolarity)
    }

    @Test
    fun `neither cannot be applied`() {
        val selection = BaseSetImportSelection(positive = false, negative = false)

        assertFalse(selection.canApply)
        assertThrows(IllegalArgumentException::class.java) {
            BaseSetImport.apply(current, source, selection)
        }
    }

    @Test
    fun `history positive only preserves current negative direction`() {
        val sourceBase = BasePrompt(source.prompts, source.selectedPolarity, source.textRendering)

        val result = BaseSetImport.apply(current, sourceBase, BaseSetImportSelection(positive = true, negative = false))

        assertEquals(sourcePositive, result.prompts.positiveBlocks)
        assertEquals(currentNegative, result.prompts.negativeBlocks)
        assertEquals(sourceTextRendering, result.textRendering)
    }

    @Test
    fun `history negative only preserves current positive direction`() {
        val sourceBase = BasePrompt(source.prompts, source.selectedPolarity, source.textRendering)

        val result = BaseSetImport.apply(current, sourceBase, BaseSetImportSelection(positive = false, negative = true))

        assertEquals(currentPositive, result.prompts.positiveBlocks)
        assertEquals(sourceNegative, result.prompts.negativeBlocks)
        assertEquals(currentTextRendering, result.textRendering)
    }

    @Test
    fun `history both replaces both directions`() {
        val sourceBase = BasePrompt(source.prompts, source.selectedPolarity, source.textRendering)

        val result = BaseSetImport.applyOrKeep(current, sourceBase, BaseSetImportSelection(positive = true, negative = true))

        assertEquals(source.prompts, result.prompts)
        assertEquals(sourceTextRendering, result.textRendering)
    }

    @Test
    fun `history neither keeps current base unchanged`() {
        val sourceBase = BasePrompt(source.prompts, source.selectedPolarity, source.textRendering)

        val result = BaseSetImport.applyOrKeep(current, sourceBase, BaseSetImportSelection(positive = false, negative = false))

        assertEquals(current, result)
    }
}
