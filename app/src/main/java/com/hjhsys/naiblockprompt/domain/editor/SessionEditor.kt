package com.hjhsys.naiblockprompt.domain.editor

import com.hjhsys.naiblockprompt.domain.model.*
import com.hjhsys.naiblockprompt.domain.prompt.PromptProcessor
import java.util.UUID

sealed interface PromptOwner {
    data object Base : PromptOwner
    data class Character(val id: String) : PromptOwner
}

enum class MoveDirection(val delta: Int) { UP(-1), DOWN(1) }
enum class BlockFormatter { MULTILINE, SINGLE_LINE }

object SessionEditor {
    fun selectPolarity(session: Session, owner: PromptOwner, polarity: PromptPolarity): Session =
        session.updateOwner(owner) { pair, _ -> pair to polarity }

    fun addCharacter(session: Session, type: CharacterType, defaultBlockName: String): Session {
        val order = session.characters.size
        val character = CharacterPrompt(
            id = UUID.randomUUID().toString(),
            type = type,
            order = order,
            prompts = PromptPair(
                positiveBlocks = listOf(
                    PromptBlock(
                        name = defaultBlockName,
                        content = when (type) {
                            CharacterType.GIRL -> "girl"
                            CharacterType.BOY -> "boy"
                            CharacterType.OTHER -> "other"
                        },
                    ),
                ),
                negativeBlocks = emptyList(),
            ),
        )
        return session.copy(characters = session.characters + character)
    }

    fun removeCharacter(session: Session, characterId: String): Session = session.copy(
        characters = session.characters.filterNot { it.id == characterId }.reindexCharacters(),
    )

    fun moveCharacter(session: Session, characterId: String, direction: MoveDirection): Session {
        val list = session.characters.sortedBy { it.order }.toMutableList()
        val from = list.indexOfFirst { it.id == characterId }
        val to = from + direction.delta
        if (from !in list.indices || to !in list.indices) return session
        val item = list.removeAt(from)
        list.add(to, item)
        return session.copy(characters = list.reindexCharacters())
    }

    fun setCharacterType(session: Session, characterId: String, type: CharacterType): Session = session.copy(
        characters = session.characters.map { if (it.id == characterId) it.copy(type = type) else it },
    )

    fun setCharacterPositioningEnabled(session: Session, enabled: Boolean): Session = session.copy(
        characters = session.characters.mapIndexed { index, character ->
            character.copy(position = if (enabled) character.position ?: defaultPosition(index, session.characters.size) else null)
        },
    )

    fun setCharacterPosition(session: Session, characterId: String, position: CharacterPosition): Session = session.copy(
        characters = session.characters.map { character ->
            if (character.id == characterId) character.copy(
                position = CharacterPosition(position.normalizedX.coerceIn(0f, 1f), position.normalizedY.coerceIn(0f, 1f)),
            ) else character
        },
    )

    private fun defaultPosition(index: Int, count: Int) = CharacterPosition((index + 1f) / (count + 1f), 0.5f)

    fun addBlock(session: Session, owner: PromptOwner, polarity: PromptPolarity, name: String): Session =
        session.updateBlocks(owner, polarity) { blocks -> blocks + PromptBlock(name = name, order = blocks.size) }

    fun updateBlock(
        session: Session,
        owner: PromptOwner,
        polarity: PromptPolarity,
        blockId: String,
        transform: (PromptBlock) -> PromptBlock,
    ): Session = mutateBlock(session, owner, polarity, blockId, false, transform)

    fun setBlockEnabled(session: Session, owner: PromptOwner, polarity: PromptPolarity, blockId: String, enabled: Boolean) =
        mutateBlock(session, owner, polarity, blockId, true) { it.copy(enabled = enabled) }

    fun setBlockCollapsed(session: Session, owner: PromptOwner, polarity: PromptPolarity, blockId: String, collapsed: Boolean) =
        mutateBlock(session, owner, polarity, blockId, true) { it.copy(collapsed = collapsed) }

    fun setBlockLocked(session: Session, owner: PromptOwner, polarity: PromptPolarity, blockId: String, locked: Boolean) =
        mutateBlock(session, owner, polarity, blockId, true) { it.copy(locked = locked) }

    private fun mutateBlock(
        session: Session,
        owner: PromptOwner,
        polarity: PromptPolarity,
        blockId: String,
        allowWhenLocked: Boolean,
        transform: (PromptBlock) -> PromptBlock,
    ): Session = session.updateBlocks(owner, polarity) { blocks ->
        blocks.map { block ->
            if (block.id != blockId || (block.locked && !allowWhenLocked)) block else transform(block)
        }
    }

    fun removeBlock(session: Session, owner: PromptOwner, polarity: PromptPolarity, blockId: String): Session =
        session.updateBlocks(owner, polarity) { blocks ->
            val target = blocks.firstOrNull { it.id == blockId }
            if (target?.locked == true) blocks else blocks.filterNot { it.id == blockId }.reindexBlocks()
        }

    fun moveBlock(
        session: Session,
        owner: PromptOwner,
        polarity: PromptPolarity,
        blockId: String,
        direction: MoveDirection,
    ): Session = session.updateBlocks(owner, polarity) { source ->
        val blocks = source.sortedBy { it.order }.toMutableList()
        val from = blocks.indexOfFirst { it.id == blockId }
        val to = from + direction.delta
        if (from !in blocks.indices || to !in blocks.indices || blocks[from].locked) return@updateBlocks source
        val item = blocks.removeAt(from)
        blocks.add(to, item)
        blocks.reindexBlocks()
    }

    fun formatBlock(
        session: Session,
        owner: PromptOwner,
        polarity: PromptPolarity,
        blockId: String,
        formatter: BlockFormatter,
    ): Session = updateBlock(session, owner, polarity, blockId, transform = { block ->
        block.copy(
            content = when (formatter) {
                BlockFormatter.MULTILINE -> PromptProcessor.formatMultiline(block.content)
                BlockFormatter.SINGLE_LINE -> PromptProcessor.formatSingleLine(block.content)
            },
        )
    })

    private fun Session.updateBlocks(
        owner: PromptOwner,
        polarity: PromptPolarity,
        transform: (List<PromptBlock>) -> List<PromptBlock>,
    ): Session = updateOwner(owner) { pair, selected ->
        val updatedPair = when (polarity) {
            PromptPolarity.POSITIVE -> pair.copy(positiveBlocks = transform(pair.positiveBlocks))
            PromptPolarity.NEGATIVE -> pair.copy(negativeBlocks = transform(pair.negativeBlocks))
        }
        updatedPair to selected
    }

    private fun Session.updateOwner(
        owner: PromptOwner,
        transform: (PromptPair, PromptPolarity) -> Pair<PromptPair, PromptPolarity>,
    ): Session = when (owner) {
        PromptOwner.Base -> {
            val (pair, selected) = transform(base.prompts, base.selectedPolarity)
            copy(base = base.copy(prompts = pair, selectedPolarity = selected))
        }
        is PromptOwner.Character -> copy(characters = characters.map { character ->
            if (character.id != owner.id) character else {
                val (pair, selected) = transform(character.prompts, character.selectedPolarity)
                character.copy(prompts = pair, selectedPolarity = selected)
            }
        })
    }

    private fun List<PromptBlock>.reindexBlocks() = mapIndexed { index, block -> block.copy(order = index) }
    private fun List<CharacterPrompt>.reindexCharacters() = mapIndexed { index, character -> character.copy(order = index) }
}
