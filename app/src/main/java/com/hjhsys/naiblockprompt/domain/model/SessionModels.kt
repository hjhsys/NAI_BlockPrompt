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

/** Domain-level position only. Its future API mapping must be based on the Phase 0.5 spike. */
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
)
