package com.hjhsys.naiblockprompt.domain.editor

import com.hjhsys.naiblockprompt.domain.model.CharacterPrompt
import java.util.UUID

enum class CharacterImportMode { APPEND, REPLACE }

object CharacterImport {
    fun apply(
        current: List<CharacterPrompt>,
        source: List<CharacterPrompt>,
        mode: CharacterImportMode,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): List<CharacterPrompt> = when (mode) {
        CharacterImportMode.REPLACE -> source
        CharacterImportMode.APPEND -> (current + source.map { character ->
            character.copy(
                id = newId(),
                prompts = character.prompts.copy(
                    positiveBlocks = character.prompts.positiveBlocks.map { it.copy(id = newId()) },
                    negativeBlocks = character.prompts.negativeBlocks.map { it.copy(id = newId()) },
                ),
            )
        }).mapIndexed { index, character -> character.copy(order = index) }
    }
}
