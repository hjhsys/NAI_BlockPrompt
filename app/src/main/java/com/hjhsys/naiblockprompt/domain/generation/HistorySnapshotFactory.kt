package com.hjhsys.naiblockprompt.domain.generation

import com.hjhsys.naiblockprompt.domain.model.SessionSnapshot
import com.hjhsys.naiblockprompt.domain.model.GeneratedPromptSnapshot

object HistorySnapshotFactory {
    fun from(generation: PreparedGeneration, actualSeed: Long): SessionSnapshot {
        val settings = generation.sourceSession.generationSettings.copy(seed = actualSeed)
        val parameters = generation.request.parameters
        return SessionSnapshot(
            session = generation.sourceSession.copy(generationSettings = settings),
            generation = GeneratedPromptSnapshot(
                basePositive = parameters.v4Prompt.caption.baseCaption,
                baseNegative = parameters.v4NegativePrompt.caption.baseCaption,
                characterPositive = parameters.v4Prompt.caption.characterCaptions.map { it.characterCaption },
                characterNegative = parameters.v4NegativePrompt.caption.characterCaptions.map { it.characterCaption },
                usedSeed = actualSeed,
                modelId = generation.request.model,
                samplerId = parameters.sampler,
                width = parameters.width,
                height = parameters.height,
                steps = parameters.steps,
                scale = parameters.scale,
            ),
        )
    }
}
