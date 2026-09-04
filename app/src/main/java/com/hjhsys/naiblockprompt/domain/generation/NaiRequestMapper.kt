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
        fun positive(blocks: List<PromptBlock>, textRendering: TextRenderingState) =
            PromptProcessor.appendTextRendering(joined(blocks), textRendering)
        val basePositive = positive(session.base.prompts.positiveBlocks, session.base.textRendering)
        val baseNegative = joined(session.base.prompts.negativeBlocks)
        val characters = session.characters.sortedBy { it.order }
        val useCoordinates = characters.isNotEmpty() && characters.all { it.position != null }
        val positiveCharacters = characters.map {
            NaiV4CharacterCaption(
                positive(it.prompts.positiveBlocks, it.textRendering),
                centers = listOf(it.position.toApiCoordinate()),
            )
        }
        val negativeCharacters = characters.map {
            NaiV4CharacterCaption(joined(it.prompts.negativeBlocks), centers = listOf(it.position.toApiCoordinate()))
        }

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
                guidanceRescale = settings.guidanceRescale,
                seed = seed,
                prompt = basePositive,
                negativePrompt = baseNegative,
                uc = baseNegative,
                useCoordinates = useCoordinates,
                characterPrompts = characters.mapIndexed { index, character ->
                    NaiLegacyCharacterPrompt(
                        prompt = positiveCharacters[index].characterCaption,
                        uc = negativeCharacters[index].characterCaption,
                        center = character.position.toApiCoordinate(),
                    )
                },
                v4Prompt = condition(basePositive, positiveCharacters, useCoordinates),
                v4NegativePrompt = condition(baseNegative, negativeCharacters, false),
            ),
        )
        return PrepareGenerationResult.Ready(PreparedGeneration(session, request, seed))
    }

    private fun condition(base: String, characters: List<NaiV4CharacterCaption>, useCoordinates: Boolean) = NaiV4ConditionInput(
        caption = NaiV4ExternalCaption(baseCaption = base, characterCaptions = characters),
        useCoordinates = useCoordinates,
        useOrder = true,
    )

    private fun CharacterPosition?.toApiCoordinate() = NaiCoordinate(
        x = this?.normalizedX ?: 0.5f,
        y = this?.normalizedY ?: 0.5f,
    )
}
