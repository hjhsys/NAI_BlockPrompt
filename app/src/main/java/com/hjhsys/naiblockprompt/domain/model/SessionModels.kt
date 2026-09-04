package com.hjhsys.naiblockprompt.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

const val CURRENT_SNAPSHOT_VERSION = 1

@Serializable
data class PromptBlock(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val content: String = "",
    val enabled: Boolean = true,
    val locked: Boolean = false,
    val collapsed: Boolean = false,
    val order: Int = 0,
)

@Serializable
data class PromptPair(
    val positiveBlocks: List<PromptBlock> = emptyList(),
    val negativeBlocks: List<PromptBlock> = emptyList(),
)

/** NovelAI V4+ Text Rendering content, kept separate from movable prompt blocks. */
@Serializable
data class TextRenderingState(
    val enabled: Boolean = false,
    val content: String = "",
)

@Serializable
enum class PromptPolarity { POSITIVE, NEGATIVE }

@Serializable
data class BasePrompt(
    val prompts: PromptPair = PromptPair(),
    val selectedPolarity: PromptPolarity = PromptPolarity.POSITIVE,
    val textRendering: TextRenderingState = TextRenderingState(),
)

@Serializable
enum class CharacterType { GIRL, BOY, OTHER }

/** Normalized image-space center. V4/V4.5 values are snapped to 5x5 cell centers. */
@Serializable
data class CharacterPosition(
    val normalizedX: Float,
    val normalizedY: Float,
)

@Serializable
data class CharacterPrompt(
    val id: String = UUID.randomUUID().toString(),
    val type: CharacterType = CharacterType.OTHER,
    val prompts: PromptPair = PromptPair(),
    val order: Int = 0,
    val position: CharacterPosition? = null,
    val selectedPolarity: PromptPolarity = PromptPolarity.POSITIVE,
    val textRendering: TextRenderingState = TextRenderingState(),
)

@Serializable
enum class SeedMode { RANDOM, FIXED }

@Serializable
enum class ImageInputMode { IMAGE_TO_IMAGE, VIBE_TRANSFER, PRECISE_REFERENCE }

@Serializable
enum class PreciseReferenceType(val apiValue: String) {
    CHARACTER_AND_STYLE("character&style"), CHARACTER("character"), STYLE("style")
}

@Serializable
data class ImageInputState(
    val uri: String,
    val mode: ImageInputMode = ImageInputMode.IMAGE_TO_IMAGE,
    val strength: Float = 0.6f,
    val noise: Float = 0f,
    val informationExtracted: Float = 1f,
    val fidelity: Float = 0f,
    val preciseType: PreciseReferenceType = PreciseReferenceType.CHARACTER_AND_STYLE,
)

/**
 * App-domain generation state. Values whose NovelAI meaning is not yet verified remain nullable;
 * this model is deliberately not an API request DTO.
 */
@Serializable
data class GenerationSettings(
    val modelId: String? = null,
    val width: Int = 832,
    val height: Int = 1216,
    val samplerId: String? = null,
    val steps: Int? = null,
    val scale: Float? = null,
    val seedMode: SeedMode = SeedMode.RANDOM,
    val seed: Long? = null,
    val guidanceRescale: Float? = null,
    val imageInput: ImageInputState? = null,
)

@Serializable
data class Session(
    val id: String = DEFAULT_SESSION_ID,
    val base: BasePrompt = BasePrompt(),
    val characters: List<CharacterPrompt> = emptyList(),
    val generationSettings: GenerationSettings = GenerationSettings(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
) {
    companion object {
        const val DEFAULT_SESSION_ID = "current"
        fun empty() = Session(
            base = BasePrompt(
                PromptPair(
                    positiveBlocks = listOf(PromptBlock(name = "Block 1")),
                    negativeBlocks = listOf(PromptBlock(name = "Block 1")),
                ),
            ),
        )
    }
}

@Serializable
data class SessionSnapshot(
    val snapshotVersion: Int = CURRENT_SNAPSHOT_VERSION,
    val session: Session,
    val generation: GeneratedPromptSnapshot? = null,
)

@Serializable
enum class SavedSetKind { BASE, CHARACTER }

@Serializable
data class SavedPromptSet(
    val kind: SavedSetKind,
    val prompts: PromptPair,
    val selectedPolarity: PromptPolarity,
    val characterType: CharacterType? = null,
    val textRendering: TextRenderingState = TextRenderingState(),
)

/** Exact processed prompt payload retained only for a successful generation history entry. */
@Serializable
data class GeneratedPromptSnapshot(
    val basePositive: String,
    val baseNegative: String,
    val characterPositive: List<String>,
    val characterNegative: List<String>,
    val usedSeed: Long,
    val modelId: String,
    val samplerId: String,
    val width: Int,
    val height: Int,
    val steps: Int,
    val scale: Float,
    val guidanceRescale: Float? = null,
    val noiseSchedule: String = "karras",
    val sm: Boolean = false,
    val smDynamic: Boolean = false,
    val dynamicThresholding: Boolean = false,
    val useCoordinates: Boolean = false,
    val useOrder: Boolean = true,
    val characterPositions: List<CharacterPosition?> = emptyList(),
    val imageInput: ImageInputState? = null,
)
