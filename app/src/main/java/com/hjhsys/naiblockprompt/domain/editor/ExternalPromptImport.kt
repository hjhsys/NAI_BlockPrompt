package com.hjhsys.naiblockprompt.domain.editor

import com.hjhsys.naiblockprompt.domain.model.*

object ExternalPromptImport {
    fun apply(
        current: Session,
        blockName: String,
        positive: String?,
        negative: String?,
        sourceCharacters: List<CharacterPrompt>,
        includePositive: Boolean,
        includeNegative: Boolean,
        includeCharacters: Boolean,
        characterMode: CharacterImportMode,
    ): Session {
        fun blocks(value: String?) = value?.let {
            listOf(PromptBlock(name = blockName, content = it, scaleMode = BlockScaleMode.CUSTOM, customScale = "1.0"))
        }.orEmpty()
        return current.copy(
            base = current.base.copy(prompts = current.base.prompts.copy(
                positiveBlocks = if (includePositive && positive != null) blocks(positive) else current.base.prompts.positiveBlocks,
                negativeBlocks = if (includeNegative && negative != null) blocks(negative) else current.base.prompts.negativeBlocks,
            )),
            characters = if (includeCharacters && sourceCharacters.isNotEmpty()) {
                CharacterImport.apply(current.characters, sourceCharacters, characterMode)
            } else current.characters,
        )
    }
}
