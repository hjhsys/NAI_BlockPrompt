package com.hjhsys.naiblockprompt.domain.generation

import com.hjhsys.naiblockprompt.domain.model.GeneratedPromptSnapshot

object GenerationHistoryPolicy {
    fun shouldWarnForDuplicate(
        previous: GeneratedPromptSnapshot?,
        previousOriginalExists: Boolean,
        candidate: GeneratedPromptSnapshot,
    ): Boolean = previousOriginalExists && previous == candidate

    fun shouldRepairExisting(
        existing: GeneratedPromptSnapshot?,
        originalExists: Boolean,
        candidate: GeneratedPromptSnapshot,
    ): Boolean = !originalExists && existing == candidate
}
