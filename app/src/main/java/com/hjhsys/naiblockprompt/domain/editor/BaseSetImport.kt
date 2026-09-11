package com.hjhsys.naiblockprompt.domain.editor

import com.hjhsys.naiblockprompt.domain.model.BasePrompt
import com.hjhsys.naiblockprompt.domain.model.PromptPolarity
import com.hjhsys.naiblockprompt.domain.model.SavedPromptSet
import com.hjhsys.naiblockprompt.domain.model.SavedSetKind

data class BaseSetImportSelection(
    val positive: Boolean,
    val negative: Boolean,
) {
    val canApply: Boolean get() = positive || negative
}

object BaseSetImport {
    fun applyOrKeep(
        current: BasePrompt,
        source: BasePrompt,
        selection: BaseSetImportSelection,
    ): BasePrompt = if (selection.canApply) apply(current, source, selection) else current

    fun apply(
        current: BasePrompt,
        source: BasePrompt,
        selection: BaseSetImportSelection,
    ): BasePrompt {
        require(selection.canApply) { "At least one Base direction must be selected." }

        return current.copy(
            prompts = current.prompts.copy(
                positiveBlocks = if (selection.positive) source.prompts.positiveBlocks else current.prompts.positiveBlocks,
                negativeBlocks = if (selection.negative) source.prompts.negativeBlocks else current.prompts.negativeBlocks,
            ),
            selectedPolarity = if (selection.positive) PromptPolarity.POSITIVE else PromptPolarity.NEGATIVE,
            // Text Rendering contributes to the positive prompt, so it follows Positive imports.
            textRendering = if (selection.positive) source.textRendering else current.textRendering,
        )
    }

    fun apply(
        current: BasePrompt,
        source: SavedPromptSet,
        selection: BaseSetImportSelection,
    ): BasePrompt {
        require(source.kind == SavedSetKind.BASE) { "Only Base sets can be imported into Base." }
        require(selection.canApply) { "At least one Base direction must be selected." }

        return apply(
            current = current,
            source = BasePrompt(
                prompts = source.prompts,
                selectedPolarity = source.selectedPolarity,
                textRendering = source.textRendering,
            ),
            selection = selection,
        )
    }
}
