package com.hjhsys.naiblockprompt.domain.generation

import com.hjhsys.naiblockprompt.data.network.nai.dto.*
import com.hjhsys.naiblockprompt.domain.model.*
import com.hjhsys.naiblockprompt.domain.prompt.PromptProcessor
import java.security.SecureRandom

data class PreparedGeneration(
    val sourceSession: Session,
    val request: NaiImageGenerationRequest,
    val usedSeed: Long,
)

sealed interface PrepareGenerationResult {
    data class Ready(val generation: PreparedGeneration) : PrepareGenerationResult
    data class Invalid(val field: MissingGenerationField) : PrepareGenerationResult
}

enum class MissingGenerationField { MODEL, UNSUPPORTED_MODEL, SAMPLER, STEPS, SCALE, FIXED_SEED }

class NaiRequestMapper(
    private val randomSeed: () -> Long = { SecureRandom().nextInt().toLong() and 0xffff_ffffL },
) {
    fun prepare(session: Session, normalizeWeights: Boolean): PrepareGenerationResult {
        val settings = session.generationSettings
        val model = settings.modelId?.trim().takeUnless { it.isNullOrEmpty() }
            ?: return PrepareGenerationResult.Invalid(MissingGenerationField.MODEL)
        if (NaiGenerationCatalog.models.none { it.apiId == model }) {
            return PrepareGenerationResult.Invalid(MissingGenerationField.UNSUPPORTED_MODEL)
        }
        val sampler = settings.samplerId?.trim().takeUnless { it.isNullOrEmpty() }
            ?: return PrepareGenerationResult.Invalid(MissingGenerationField.SAMPLER)
        val steps = settings.steps ?: return PrepareGenerationResult.Invalid(MissingGenerationField.STEPS)
        val scale = settings.scale ?: return PrepareGenerationResult.Invalid(MissingGenerationField.SCALE)
        val seed = when (settings.seedMode) {
            SeedMode.RANDOM -> randomSeed()
            SeedMode.FIXED -> settings.seed ?: return PrepareGenerationResult.Invalid(MissingGenerationField.FIXED_SEED)
        }

        fun joined(blocks: List<PromptBlock>) = PromptProcessor.joinEnabledBlocks(blocks, normalizeWeights)
        val basePositive = joined(session.base.prompts.positiveBlocks)
        val baseNegative = joined(session.base.prompts.negativeBlocks)
        val characters = session.characters.sortedBy { it.order }
        val positiveCharacters = characters.map { NaiV4CharacterCaption(joined(it.prompts.positiveBlocks)) }
        val negativeCharacters = characters.map { NaiV4CharacterCaption(joined(it.prompts.negativeBlocks)) }

        val request = NaiImageGenerationRequest(
            input = basePositive,
            model = model,
            parameters = NaiRequestParameters(
                paramsVersion = NaiGenerationCatalog.parameterVersion(model),
                width = settings.width,
                height = settings.height,
                sampler = sampler,
                steps = steps,
                scale = scale,
                seed = seed,
                prompt = basePositive,
                negativePrompt = baseNegative,
                uc = baseNegative,
                v4Prompt = condition(basePositive, positiveCharacters),
                v4NegativePrompt = condition(baseNegative, negativeCharacters),
            ),
        )
        return PrepareGenerationResult.Ready(PreparedGeneration(session, request, seed))
    }

    private fun condition(base: String, characters: List<NaiV4CharacterCaption>) = NaiV4ConditionInput(
        caption = NaiV4ExternalCaption(baseCaption = base, characterCaptions = characters),
        useCoordinates = false,
        useOrder = true,
    )
}
