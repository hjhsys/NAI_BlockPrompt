package com.hjhsys.naiblockprompt.domain.editor

import com.hjhsys.naiblockprompt.domain.image.NaiImageMetadata
import com.hjhsys.naiblockprompt.domain.model.*
import com.hjhsys.naiblockprompt.domain.prompt.PromptProcessor
import java.util.UUID

/** Detached, editable copy used by the additive prompt-import dialog. */
data class PromptCherryPickDraft(
    val base: PromptPair,
    val characters: List<CharacterPrompt>,
)

data class CharacterCherryPick(
    val sourceCharacterId: String,
    val positiveBlockIds: Set<String>,
    val negativeBlockIds: Set<String>,
    /** Empty means the documented automatic 0/1-character destination rule. */
    val destinationCharacterIds: Set<String> = emptySet(),
)

sealed interface CherryPickDestination {
    data class Base(val polarity: PromptPolarity) : CherryPickDestination
    data class Character(val characterId: String, val polarity: PromptPolarity) : CherryPickDestination
    data class NewCharacter(val type: CharacterType, val polarity: PromptPolarity) : CherryPickDestination
}

object PromptCherryPick {
    fun fromSession(source: Session): PromptCherryPickDraft = PromptCherryPickDraft(
        base = source.base.prompts.deepCopy(),
        characters = source.characters.sortedBy { it.order }.map { it.deepCopy() },
    )

    fun fromMetadata(metadata: NaiImageMetadata, blockName: String): PromptCherryPickDraft {
        fun block(content: String?) = content?.takeIf(String::isNotBlank)?.let {
            listOf(PromptBlock(name = blockName, content = it))
        }.orEmpty()
        val count = maxOf(metadata.characterPrompts.size, metadata.characterNegativePrompts.size)
        return PromptCherryPickDraft(
            base = PromptPair(block(metadata.prompt), block(metadata.negativePrompt)),
            characters = (0 until count).map { index ->
                CharacterPrompt(
                    type = CharacterType.OTHER,
                    order = index,
                    prompts = PromptPair(
                        positiveBlocks = block(metadata.characterPrompts.getOrNull(index)),
                        negativeBlocks = block(metadata.characterNegativePrompts.getOrNull(index)),
                    ),
                    position = null,
                )
            },
        )
    }

    fun splitBlock(
        draft: PromptCherryPickDraft,
        owner: PromptOwner,
        polarity: PromptPolarity,
        blockId: String,
        cursor: Int,
        newBlockName: String,
    ): PromptCherryPickDraft {
        fun split(blocks: List<PromptBlock>): List<PromptBlock> {
            val index = blocks.indexOfFirst { it.id == blockId }
            val block = blocks.getOrNull(index) ?: return blocks
            val parts = PromptProcessor.splitAtTopLevelBoundary(block.content, cursor) ?: return blocks
            return blocks.toMutableList().apply {
                this[index] = block.copy(content = parts.left)
                add(index + 1, PromptBlock(name = newBlockName, content = parts.right, order = index + 1))
            }.mapIndexed { order, item -> item.copy(order = order) }
        }
        fun pair(source: PromptPair) = when (polarity) {
            PromptPolarity.POSITIVE -> source.copy(positiveBlocks = split(source.positiveBlocks))
            PromptPolarity.NEGATIVE -> source.copy(negativeBlocks = split(source.negativeBlocks))
        }
        return when (owner) {
            PromptOwner.Base -> draft.copy(base = pair(draft.base))
            is PromptOwner.Character -> draft.copy(characters = draft.characters.map {
                if (it.id == owner.id) it.copy(prompts = pair(it.prompts)) else it
            })
        }
    }

    fun updateBlockContent(draft: PromptCherryPickDraft, owner: PromptOwner, polarity: PromptPolarity, blockId: String, content: String): PromptCherryPickDraft {
        fun update(pair: PromptPair) = when (polarity) {
            PromptPolarity.POSITIVE -> pair.copy(positiveBlocks = pair.positiveBlocks.map { if (it.id == blockId) it.copy(content = content) else it })
            PromptPolarity.NEGATIVE -> pair.copy(negativeBlocks = pair.negativeBlocks.map { if (it.id == blockId) it.copy(content = content) else it })
        }
        return when (owner) {
            PromptOwner.Base -> draft.copy(base = update(draft.base))
            is PromptOwner.Character -> draft.copy(characters = draft.characters.map { if (it.id == owner.id) it.copy(prompts = update(it.prompts)) else it })
        }
    }

    fun append(
        current: Session,
        draft: PromptCherryPickDraft,
        basePositiveBlockIds: Set<String>,
        baseNegativeBlockIds: Set<String>,
        characterImports: List<CharacterCherryPick>,
    ): Session {
        fun selected(blocks: List<PromptBlock>, ids: Set<String>, startOrder: Int) = blocks
            .filter { it.id in ids }
            .mapIndexed { index, block -> block.importCopy(startOrder + index) }
        val basePositive = current.base.prompts.positiveBlocks
        val baseNegative = current.base.prompts.negativeBlocks
        var characters = current.characters
        characterImports.forEach { plan ->
            val source = draft.characters.firstOrNull { it.id == plan.sourceCharacterId } ?: return@forEach
            var destinations = plan.destinationCharacterIds.filterTo(linkedSetOf()) { id -> characters.any { it.id == id } }
            if (destinations.isEmpty()) {
                when (characters.size) {
                    0 -> {
                        val created = CharacterPrompt(type = source.type, order = 0)
                        characters = listOf(created)
                        destinations = linkedSetOf(created.id)
                    }
                    1 -> destinations = linkedSetOf(characters.single().id)
                }
            }
            characters = characters.map { destination ->
                if (destination.id !in destinations) destination else {
                    val positive = destination.prompts.positiveBlocks
                    val negative = destination.prompts.negativeBlocks
                    destination.copy(prompts = PromptPair(
                        positiveBlocks = positive + selected(source.prompts.positiveBlocks, plan.positiveBlockIds, positive.size),
                        negativeBlocks = negative + selected(source.prompts.negativeBlocks, plan.negativeBlockIds, negative.size),
                    ))
                }
            }
        }
        return current.copy(
            base = current.base.copy(prompts = PromptPair(
                positiveBlocks = basePositive + selected(draft.base.positiveBlocks, basePositiveBlockIds, basePositive.size),
                negativeBlocks = baseNegative + selected(draft.base.negativeBlocks, baseNegativeBlockIds, baseNegative.size),
            )),
            characters = characters.mapIndexed { index, character -> character.copy(order = index) },
        )
    }

    /** Appends one checked source field to an arbitrary destination without source mutation. */
    fun appendSelection(
        current: Session,
        sourceBlocks: List<PromptBlock>,
        selectedBlockIds: Set<String>,
        destination: CherryPickDestination,
    ): Session {
        val selected = sourceBlocks.filter { it.id in selectedBlockIds }
        if (selected.isEmpty()) return current
        fun append(pair: PromptPair, polarity: PromptPolarity): PromptPair = when (polarity) {
            PromptPolarity.POSITIVE -> pair.copy(
                positiveBlocks = pair.positiveBlocks + selected.mapIndexed { index, block -> block.importCopy(pair.positiveBlocks.size + index) },
            )
            PromptPolarity.NEGATIVE -> pair.copy(
                negativeBlocks = pair.negativeBlocks + selected.mapIndexed { index, block -> block.importCopy(pair.negativeBlocks.size + index) },
            )
        }
        return when (destination) {
            is CherryPickDestination.Base -> current.copy(base = current.base.copy(prompts = append(current.base.prompts, destination.polarity)))
            is CherryPickDestination.Character -> current.copy(characters = current.characters.map { character ->
                if (character.id == destination.characterId) character.copy(prompts = append(character.prompts, destination.polarity)) else character
            })
            is CherryPickDestination.NewCharacter -> {
                val created = CharacterPrompt(type = destination.type, order = current.characters.size)
                current.copy(characters = current.characters + created.copy(prompts = append(created.prompts, destination.polarity)))
            }
        }
    }

    private fun PromptPair.deepCopy() = PromptPair(positiveBlocks.map { it.copy() }, negativeBlocks.map { it.copy() })
    private fun CharacterPrompt.deepCopy() = copy(prompts = prompts.deepCopy())
    private fun PromptBlock.importCopy(order: Int) = copy(
        id = UUID.randomUUID().toString(),
        enabled = true,
        locked = false,
        collapsed = false,
        order = order,
    )
}
